package com.lecturasgas.beta.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lecturasgas.beta.data.db.LocalStore
import com.lecturasgas.beta.data.excel.ExcelComparer
import com.lecturasgas.beta.data.excel.ExcelExporter
import com.lecturasgas.beta.data.excel.ExcelImporter
import com.lecturasgas.beta.data.excel.NoProgress
import com.lecturasgas.beta.data.excel.Progress
import com.lecturasgas.beta.data.excel.TaskCancelled
import com.lecturasgas.beta.review.ReadingOrder
import com.lecturasgas.beta.review.ReadingsReport
import com.lecturasgas.beta.data.excel.slice
import com.lecturasgas.beta.data.excel.ExportVerifier
import com.lecturasgas.beta.data.excel.ImportValidator
import com.lecturasgas.beta.data.excel.XlsxPaths
import com.lecturasgas.beta.review.MeterHistory
import com.lecturasgas.beta.data.excel.ImportDiffer
import com.lecturasgas.beta.data.excel.NewMeter
import com.lecturasgas.beta.data.model.RouteSummary
import com.lecturasgas.beta.data.model.Delivery
import com.lecturasgas.beta.delivery.DeliveryNotes
import com.lecturasgas.beta.data.model.DeliverySession
import com.lecturasgas.beta.data.model.GeoFix
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.data.model.RouteInfo
import com.lecturasgas.beta.data.model.SessionSummary
import com.lecturasgas.beta.data.model.TrackPoint
import com.lecturasgas.beta.location.LocationFusion
import com.lecturasgas.beta.location.Spot
import com.lecturasgas.beta.util.Geo
import com.lecturasgas.beta.util.TextUtil
import com.lecturasgas.beta.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * Repositorio: el ÚNICO lugar que habla con la base de datos y los archivos.
 * Las pantallas solo observan sus StateFlow y llaman a sus funciones.
 *
 * Todo el trabajo pesado (Excel, base de datos) corre en Dispatchers.IO,
 * nunca en el hilo de la interfaz: así la pantalla no se congela.
 */
class LecturasRepository(
    private val context: Context,
    private val store: LocalStore,
    private val prefs: UiPrefs
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trackMutex = Mutex()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _route = MutableStateFlow<RouteInfo?>(null)
    val route: StateFlow<RouteInfo?> = _route.asStateFlow()

    private val _records = MutableStateFlow<List<MeterRecord>>(emptyList())
    val records: StateFlow<List<MeterRecord>> = _records.asStateFlow()

    private val _search = MutableStateFlow(SearchEngine(emptyList()))
    val search: StateFlow<SearchEngine> = _search.asStateFlow()

    private val _deliveries = MutableStateFlow<List<Delivery>>(emptyList())
    val deliveries: StateFlow<List<Delivery>> = _deliveries.asStateFlow()
    private val _notes = MutableStateFlow(DeliveryNotes())
    /** Notas de "dónde se dejó la factura": última por medidor y las más usadas. */
    val deliveryNotes: StateFlow<DeliveryNotes> = _notes.asStateFlow()

    /** true si la jornada la empezó la pantalla Lecturas (el mapa no debe saltar al modo Entrega). */
    @Volatile var startedFromReadings = false

    private val _session = MutableStateFlow<DeliverySession?>(null)
    val session: StateFlow<DeliverySession?> = _session.asStateFlow()

    private val _track = MutableStateFlow<List<TrackPoint>>(emptyList())
    val track: StateFlow<List<TrackPoint>> = _track.asStateFlow()

    private val _learned = MutableStateFlow<Map<String, Double>>(emptyMap())
    val learned: StateFlow<Map<String, Double>> = _learned.asStateFlow()

    /** Lecturas tomadas en la app después de la última exportación. */
    private val _unexported = MutableStateFlow(0)
    val unexported: StateFlow<Int> = _unexported.asStateFlow()

    /** Facturas que el repartidor dejó "para después" en esta jornada (no se guardan: es solo el orden de hoy). */
    private val _skippedForNow = MutableStateFlow<Set<Int>>(emptySet())
    val skippedForNow: StateFlow<Set<Int>> = _skippedForNow.asStateFlow()
    fun skipForNow(row: Int) { _skippedForNow.update { it + row } }
    fun clearSkippedForNow() { _skippedForNow.value = emptySet() }

    private var rowIndex: Map<Int, Int> = emptyMap()

    init {
        scope.launch {
            runCatching { migrateLegacyIfNeeded() }
            loadAll()
            _loaded.value = true
        }
    }

    // ------------------------------------------------------------------
    // CARGA
    // ------------------------------------------------------------------

    private fun loadAll() {
        val route = store.currentRoute()
        _route.value = route
        val list = if (route != null) store.loadRecords(route.id) else emptyList()
        setRecords(list, rebuildSearch = true)
        _deliveries.value = if (route != null) store.deliveriesForRoute(route.id) else emptyList()
        val session = store.activeSession()
        _session.value = session
        _track.value = if (session != null) store.trackPoints(session.id) else emptyList()
        val allDeliveries = store.allDeliveries()
        _learned.value = RoutePlanner.learnRanks(allDeliveries)
        _notes.value = DeliveryNotes.from(allDeliveries)
        recountUnexported()
    }

    private fun setRecords(list: List<MeterRecord>, rebuildSearch: Boolean) {
        if (rebuildSearch) {
            rowIndex = list.withIndex().associate { it.value.row to it.index }
            _search.value = SearchEngine(list)
        }
        _records.value = list
    }

    /** Cambia UN medidor en memoria sin recargar toda la lista. */
    private fun updateRecord(row: Int, change: (MeterRecord) -> MeterRecord) {
        val idx = rowIndex[row] ?: return
        _records.update { list ->
            if (idx >= list.size || list[idx].row != row) list
            else list.toMutableList().also { it[idx] = change(it[idx]) }
        }
    }

    private fun updateByMeterKey(key: String, change: (MeterRecord) -> MeterRecord) {
        _records.update { list -> list.map { if (it.meterKey == key) change(it) else it } }
    }

    private fun recountUnexported() {
        val last = _route.value?.lastExportAt ?: 0L
        _unexported.value = _records.value.count { it.appReading != null && (it.readAt ?: 0L) > last }
    }

    fun recordByRow(row: Int): MeterRecord? = rowIndex[row]?.let { _records.value.getOrNull(it) }

    // ------------------------------------------------------------------
    // IMPORTAR
    // ------------------------------------------------------------------

    /** Lo que se le muestra al usuario ANTES de importar, para que confirme a qué ruta pertenece el Excel. */
    data class ImportPreview(
        val fileName: String,
        val period: String,
        val total: Int,
        val barrios: Int,
        /** Identidad propuesta a partir del nombre del archivo ("RUTA A"). */
        val suggestedKey: String,
        val routes: List<RouteSummary>,
        /** Revisión del Excel (encabezados, repetidos, fórmulas…) y prueba de exportación. */
        val check: ImportValidator.Report? = null
    )

    /** Qué pasaría con la ruta elegida (para mostrarlo mientras el usuario decide). */
    data class ImportPlan(
        val key: String,
        /** La ruta que se actualizaría; null = es una ruta nueva para la app. */
        val updates: RouteSummary?,
        val samePeriod: Boolean,
        val newMeters: Int,
        val renumbered: Int,
        val removed: Int,
        val newBarrios: List<String>,
        /** Se va a perder algo (lecturas sin exportar de otro mes) si se confirma. */
        val blockedBySession: Boolean
    )

    data class ImportReport(
        val total: Int,
        val withReading: Int,
        val pending: Int,
        val withGps: Int,
        val routeName: String,
        val carriedReadings: Int,
        val warnings: List<String>,
        val updatedExisting: Boolean = false,
        val newMeters: Int = 0,
        val renumbered: Int = 0,
        val removed: List<String> = emptyList(),
        val removedCount: Int = 0,
        val newBarrios: List<String> = emptyList(),
        val barrios: Int = 0
    )

    private class PendingImport(val file: File, val fileName: String, val parsed: ExcelImporter.Parsed)
    private var pendingImport: PendingImport? = null

    /** Paso 1: lee el Excel (sin guardar nada todavía) y devuelve lo que hay que confirmar. */
    suspend fun previewImport(uri: Uri, onProgress: Progress = NoProgress): ImportPreview = withContext(Dispatchers.IO) {
        cancelImport()
        val fileName = displayName(uri) ?: "ruta.xlsx"
        val dir = File(context.filesDir, "routes").apply { mkdirs() }
        val target = File(dir, "pending_${System.currentTimeMillis()}.xlsx")
        onProgress("Copiando el archivo…", 0f)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "No se pudo abrir el archivo." }
            target.outputStream().use { input.copyTo(it) }
        }
        val parsed = try {
            ExcelImporter.parse(target) { done, total ->
                if (total > 0) onProgress("Leyendo los medidores…", 0.05f + 0.50f * (done.toFloat() / total).coerceIn(0f, 1f))
            }
        } catch (e: TaskCancelled) {
            target.delete()
            throw e
        } catch (e: Exception) {
            target.delete()
            throw IllegalStateException("El archivo no parece un Excel válido (.xlsx).", e)
        }
        if (parsed.rows.isEmpty()) {
            target.delete()
            error("No se encontraron medidores en el Excel.")
        }
        pendingImport = PendingImport(target, fileName, parsed)
        val check = runCatching {
            onProgress("Revisando el Excel…", 0.56f)
            ZipFile(target).use { z ->
                val xml = XlsxPaths.readText(z, parsed.sheetPath)
                val sheets = runCatching { XlsxPaths.findTags(XlsxPaths.readText(z, "xl/workbook.xml"), "<sheet ").size }.getOrDefault(1)
                ImportValidator.validate(parsed, xml, sheets, onProgress.slice(0.58f, 1f))
            }
        }.getOrElse { e ->
            if (e is TaskCancelled) { target.delete(); pendingImport = null; throw e }
            val msg = "No se pudo revisar el archivo: ${e.message ?: e.javaClass.simpleName}"
            ImportValidator.Report(
                listOf(ImportValidator.Issue(ImportValidator.Level.WARNING, "No se pudo revisar el Excel", msg)),
                ImportValidator.SelfTest(false, false, msg, 0)
            )
        }
        ImportPreview(
            fileName = fileName,
            period = parsed.period,
            total = parsed.rows.size,
            barrios = parsed.rows.map { it.barrio.trim().uppercase() }.filter { it.isNotEmpty() }.distinct().size,
            suggestedKey = ExcelImporter.routeKeyOf(ExcelImporter.routeNameFromFile(fileName)),
            routes = store.listRoutes(),
            check = check
        )
    }

    /** Qué pasaría si el Excel pendiente se importa como la ruta [key]. */
    suspend fun planImport(key: String): ImportPlan? = withContext(Dispatchers.IO) {
        val pending = pendingImport ?: return@withContext null
        val clean = key.trim().uppercase().ifBlank { return@withContext null }
        val existing = store.routeByKey(clean)
        val samePeriod = store.isSamePeriod(existing, pending.parsed.period)
        val diff = ImportDiffer.compute(
            existing?.let { store.metersForDiff(it.id) },
            pending.parsed.rows.map { NewMeter(TextUtil.meterKey(it.meter), it.nir, it.barrio) },
            samePeriod
        )
        val summary = existing?.let { e -> store.listRoutes().firstOrNull { it.id == e.id } }
        ImportPlan(
            key = clean,
            updates = summary,
            samePeriod = samePeriod,
            newMeters = diff.newCount,
            renumbered = diff.renumbered,
            removed = diff.removed.size,
            newBarrios = diff.newBarrios,
            blockedBySession = _session.value != null && !(existing != null && samePeriod)
        )
    }

    fun cancelImport() {
        pendingImport?.file?.delete()
        pendingImport = null
    }

    /** Paso 2: guarda el Excel pendiente como la ruta [key]. */
    suspend fun commitImport(key: String): ImportReport = withContext(Dispatchers.IO) {
        val pending = pendingImport ?: error("No hay un Excel pendiente. Elígelo otra vez.")
        val routeKey = key.trim().uppercase().ifBlank { error("Escribe el nombre de la ruta (por ejemplo: RUTA A).") }
        val existing = store.routeByKey(routeKey)
        val samePeriod = store.isSamePeriod(existing, pending.parsed.period)
        if (_session.value != null && !(existing != null && samePeriod)) {
            error("Tienes una jornada activa (recorrido grabándose). Termínala con el botón ■ de la tarjeta de arriba (en Lecturas o en Entrega) antes de importar otra ruta u otro mes.")
        }
        val derivedName = ExcelImporter.routeNameFromFile(pending.fileName)
        val name = if (ExcelImporter.routeKeyOf(derivedName) == routeKey) derivedName
        else routeKey + (pending.parsed.period.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
        val now = System.currentTimeMillis()
        val dir = File(context.filesDir, "routes").apply { mkdirs() }
        val finalFile = File(dir, "original_$now.xlsx")
        check(pending.file.renameTo(finalFile)) { "No se pudo guardar el archivo." }
        pendingImport = null

        val outcome = try {
            store.replaceRoute(name, routeKey, pending.fileName, finalFile.absolutePath, pending.parsed, now)
        } catch (e: Exception) {
            finalFile.delete()
            throw e
        }

        // Borra los Excel que ya no usa ninguna ruta (cada ruta conserva el suyo).
        val live = store.liveOriginalPaths()
        dir.listFiles()?.forEach { if (it.absolutePath !in live) it.delete() }

        _skippedForNow.value = emptySet()
        loadAll()
        val list = _records.value
        val diff = outcome.diff
        ImportReport(
            total = list.size,
            withReading = list.count { it.isRead },
            pending = list.count { !it.isRead },
            withGps = list.count { it.hasLocation },
            routeName = name,
            carriedReadings = list.count { it.appReading != null },
            warnings = pending.parsed.warnings,
            updatedExisting = outcome.previous != null,
            newMeters = diff.newCount,
            renumbered = diff.renumbered,
            removed = diff.removed.take(15).map { "NIR ${it.nir} · ${it.user.ifBlank { "sin usuario" }}" },
            removedCount = diff.removed.size,
            newBarrios = diff.newBarrios,
            barrios = list.map { it.barrio.trim().uppercase() }.filter { it.isNotEmpty() }.distinct().size
        )
    }

    /** Las rutas guardadas (A, B, C…) con su avance. */
    suspend fun routeList(): List<RouteSummary> = withContext(Dispatchers.IO) { store.listRoutes() }

    /** Cambia de ruta sin borrar nada de la anterior. Devuelve un mensaje si no se puede. */
    suspend fun switchRoute(routeId: Long): String? = withContext(Dispatchers.IO) {
        if (_route.value?.id == routeId) return@withContext null
        if (_session.value != null) {
            return@withContext "Tienes una jornada activa. Termínala con el botón ■ antes de cambiar de ruta."
        }
        store.setActiveRoute(routeId)
        _skippedForNow.value = emptySet()
        loadAll()
        null
    }

    /**
     * Borra una ruta guardada (medidores, lecturas, entregas y recorridos). Los puntos GPS se conservan.
     * Devuelve un mensaje si no se puede.
     */
    suspend fun deleteRoute(routeId: Long, keepHistory: Boolean): String? = withContext(Dispatchers.IO) {
        val session = _session.value
        if (session != null && session.routeId == routeId) {
            return@withContext "Tienes una jornada de entrega activa en esa ruta. Termínala con el botón ■ antes de borrarla."
        }
        val info = store.routeById(routeId) ?: return@withContext "Esa ruta ya no existe."
        store.deleteRoute(routeId, keepHistory)
        // Se borra solo el Excel de esa ruta (si ninguna otra lo usa).
        if (info.originalPath !in store.liveOriginalPaths()) File(info.originalPath).delete()
        _skippedForNow.value = emptySet()
        loadAll()
        null
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    // ------------------------------------------------------------------
    // EXPORTAR
    // ------------------------------------------------------------------

    data class ExportReport(
        val written: Int,
        val unchanged: Int,
        val skippedFormula: Int,
        val totalWithReading: Int,
        val verified: Boolean,
        val problems: List<String>,
        /** Celdas de la columna de lectura que cambian respecto al original (resultado de la comparación). */
        val changedVsOriginal: Int = 0,
        /** Filas cuyo NIR y número de medidor se comprobaron una por una. */
        val rowsChecked: Int = 0,
        /** true = se volvió a leer el archivo guardado y es idéntico al verificado; null = no se pudo releer. */
        val readBackOk: Boolean? = null
    )

    /** Resultado de probar la exportación sin guardar nada ("Comprobar exportación"). */
    data class ExportCheck(
        val ok: Boolean,
        val written: Int,
        val unchanged: Int,
        val skippedFormula: Int,
        val rowsChecked: Int,
        val readingsChecked: Int,
        val changedCells: Int,
        val problems: List<String>
    )

    fun suggestedExportName(): String = _route.value?.fileName ?: "Lecturas.xlsx"

    /**
     * Lee el Excel generado y lo compara de TRES maneras: (1) cada fila tiene el mismo NIR y medidor que la app y
     * cada lectura quedó en la fila de su medidor; (2) celda por celda contra el original, solo cambia la columna de
     * lectura; (3) las filas que cambiaron son exactamente las que debían cambiar.
     */
    private fun verifyGenerated(
        route: RouteInfo, original: File, temp: File,
        updates: Map<Int, Long>, result: ExcelExporter.Result,
        onProgress: Progress = NoProgress
    ): ExportCheck {
        val problems = mutableListOf<String>()
        onProgress("Leyendo el Excel generado…", 0f)
        val parsed = ExcelImporter.parse(temp) { done, total ->
            if (total > 0) onProgress("Leyendo el Excel generado…", 0.25f * done / total)
        }
        onProgress("Comprobando fila por fila…", 0.28f)
        // Lo que la app tiene en la base (updates) manda; el resto de datos del medidor no cambia entre importaciones.
        val records = _records.value.map { it.copy(appReading = updates[it.row]) }
        val skipped = result.skippedFormula.toSet()
        val ver = ExportVerifier.verify(parsed, records, updates, skipped)
        problems.addAll(ver.problems)
        if (result.missingRows.isNotEmpty()) {
            problems.add("Estas filas no existen en el Excel original: ${result.missingRows.take(6).joinToString()}.")
        }
        val cmp = ExcelComparer.compare(original, temp, route.readingColumn, onProgress.slice(0.32f, 1f))
        problems.addAll(cmp.problems)
        if (cmp.ok) {
            val allowed = updates.keys
            val strange = cmp.changedRows.filter { it !in allowed }
            if (strange.isNotEmpty()) problems.add("Cambió la lectura de filas que no tenían lectura nueva: ${strange.take(6).joinToString()}.")
            val mustChange = records.filter { it.row in allowed && it.row !in skipped && it.excelReading != updates[it.row] }.map { it.row }
            val missing = mustChange.filter { it !in cmp.changedRows }
            if (missing.isNotEmpty()) problems.add("No cambió la lectura de las filas: ${missing.take(6).joinToString()}.")
        }
        return ExportCheck(
            ok = problems.isEmpty(),
            written = result.written, unchanged = result.unchanged, skippedFormula = result.skippedFormula.size,
            rowsChecked = ver.rowsChecked, readingsChecked = ver.readingsChecked,
            changedCells = cmp.changedCells, problems = problems.take(10)
        )
    }

    /** Genera el Excel en un archivo temporal. */
    private fun generate(
        route: RouteInfo, temp: File, updates: Map<Int, Long>, onProgress: Progress = NoProgress
    ): Pair<File, ExcelExporter.Result> {
        val original = File(route.originalPath)
        require(original.exists()) { "No se encontró el Excel original. Vuelve a importarlo." }
        val result = temp.outputStream().use { ExcelExporter.export(original, it, updates, route.readingColumn, onProgress) }
        return original to result
    }

    private fun checksum(input: InputStream): Pair<Long, Long> {
        val crc = CRC32()
        val buf = ByteArray(64 * 1024)
        var size = 0L
        input.use { while (true) { val n = it.read(buf); if (n < 0) break; crc.update(buf, 0, n); size += n } }
        return crc.value to size
    }

    /**
     * "Comprobar exportación": hace TODO lo de exportar (genera el Excel y lo verifica) pero no guarda ningún archivo ni
     * marca nada como exportado. Sirve para ver, cuando quieras, que lo que saldría es correcto.
     */
    suspend fun checkExport(onProgress: Progress = NoProgress): ExportCheck = withContext(Dispatchers.IO) {
        val route = _route.value ?: error("Primero importa un Excel.")
        val updates = store.appReadings(route.id)
        val temp = File(context.cacheDir, "check_tmp.xlsx")
        try {
            val (original, result) = generate(route, temp, updates, onProgress.slice(0f, 0.15f))
            verifyGenerated(route, original, temp, updates, result, onProgress.slice(0.15f, 1f))
        } finally {
            temp.delete()
        }
    }

    /**
     * 1) Genera el Excel en un archivo temporal (solo cambia la columna D).
     * 2) Lo vuelve a leer y VERIFICA fila por fila que cada lectura quedó en la fila de su medidor.
     * 3) Solo entonces lo copia al destino que eligió el usuario.
     * 4) Vuelve a leer el archivo guardado y comprueba que es idéntico al verificado.
     */
    suspend fun exportExcel(
        destination: Uri, verify: Boolean = true, onProgress: Progress = NoProgress
    ): ExportReport = withContext(Dispatchers.IO) {
        val route = _route.value ?: error("Primero importa un Excel.")
        val updates = store.appReadings(route.id)
        val temp = File(context.cacheDir, "export_tmp.xlsx")
        var totalWithReading = updates.size
        var check: ExportCheck? = null
        val result: ExcelExporter.Result = try {
        val (original, generated) = generate(route, temp, updates, onProgress.slice(0f, if (verify) 0.12f else 0.55f))
        if (verify) {
            val c = verifyGenerated(route, original, temp, updates, generated, onProgress.slice(0.12f, 0.80f))
            if (!c.ok) {
                temp.delete()
                error("La verificación falló, no se guardó el archivo:\n" + c.problems.joinToString("\n"))
            }
            check = c
            onProgress("Contando las lecturas del archivo…", 0.80f)
            totalWithReading = ExcelImporter.parse(temp).rows.count { it.reading != null }
        }
        // Un aviso final antes de tocar el destino: aquí todavía se puede cancelar sin dejar nada a medias.
        onProgress("Preparando el guardado…", if (verify) 0.86f else 0.58f)
        generated
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }

        onProgress("Guardando el archivo…", if (verify) 0.88f else 0.60f)
        val expected = checksum(temp.inputStream())
        context.contentResolver.openOutputStream(destination, "wt").use { out ->
            requireNotNull(out) { "No se pudo crear el archivo de destino." }
            temp.inputStream().use { it.copyTo(out) }
        }
        // Se vuelve a leer lo que quedó guardado: debe ser idéntico a lo que se verificó.
        onProgress("Comprobando el archivo guardado…", if (verify) 0.95f else 0.85f)
        val readBack = runCatching {
            context.contentResolver.openInputStream(destination)?.let { checksum(it) }
        }.getOrNull()
        temp.delete()
        if (readBack != null && readBack != expected) {
            error("El archivo guardado no coincide con el que se verificó (tamaño ${readBack.second} en vez de ${expected.second}). Vuelve a exportar a otra carpeta.")
        }

        val now = System.currentTimeMillis()
        store.markExported(route.id, now)
        _route.value = route.copy(lastExportAt = now)
        recountUnexported()

        ExportReport(
            written = result.written,
            unchanged = result.unchanged,
            skippedFormula = result.skippedFormula.size,
            totalWithReading = totalWithReading,
            verified = verify,
            problems = emptyList(),
            changedVsOriginal = check?.changedCells ?: 0,
            rowsChecked = check?.rowsChecked ?: 0,
            readBackOk = readBack?.let { true }
        )
    }

    /**
     * Compara un Excel que ya exportaste (elegido por el usuario) con el original de la ruta activa.
     * Devuelve el resultado y cuántas lecturas hay en la columna de lectura del archivo.
     */
    suspend fun compareWithOriginal(other: Uri, onProgress: Progress = NoProgress): ExcelComparer.Result = withContext(Dispatchers.IO) {
        val route = _route.value ?: error("Primero importa un Excel.")
        val original = File(route.originalPath)
        require(original.exists()) { "No se encontró el Excel original. Vuelve a importarlo." }
        val temp = File(context.cacheDir, "compare_tmp.xlsx")
        try {
            onProgress("Copiando el archivo a comparar…", 0f)
            context.contentResolver.openInputStream(other).use { input ->
                requireNotNull(input) { "No se pudo abrir el archivo." }
                temp.outputStream().use { input.copyTo(it) }
            }
            ExcelComparer.compare(original, temp, route.readingColumn, onProgress.slice(0.08f, 1f))
        } finally {
            temp.delete()
        }
    }

    /** Recorrido de entregas en CSV (se abre en Excel). */
    suspend fun exportDeliveriesCsv(destination: Uri): Int = withContext(Dispatchers.IO) {
        val list = _deliveries.value.sortedBy { it.order }
        val sb = StringBuilder()
        sb.append('﻿') // BOM: para que Excel muestre bien las tildes
        sb.append("Orden;NIR;Medidor;Usuario;Dirección;Barrio;Fecha;Hora;Latitud;Longitud;Precisión (m);Dónde se dejó\r\n")
        fun esc(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        for (d in list) {
            sb.append(d.order).append(';')
                .append(esc(d.nir)).append(';')
                .append(esc(d.meter)).append(';')
                .append(esc(d.user)).append(';')
                .append(esc(d.address)).append(';')
                .append(esc(d.barrio)).append(';')
                .append(TimeUtil.fileStamp(d.deliveredAt).substringBefore('_')).append(';')
                .append(TimeUtil.hour(d.deliveredAt)).append(';')
                .append(d.lat?.toString() ?: "").append(';')
                .append(d.lon?.toString() ?: "").append(';')
                .append(d.accuracy?.let { Math.round(it).toString() } ?: "").append(';')
                .append(esc(d.note))
                .append("\r\n")
        }
        context.contentResolver.openOutputStream(destination, "wt").use { out ->
            requireNotNull(out) { "No se pudo crear el archivo." }
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        list.size
    }

    /**
     * Respaldo en TEXTO de las lecturas tomadas en la app, pensado para leerse: encabezado, resumen, lo que conviene
     * revisar y la lista completa en el orden de las filas del Excel (ver [ReadingsReport]).
     * Para pasar lecturas a Excel está la «Lista de lecturas (CSV)».
     */
    suspend fun exportReadingsText(destination: Uri): Int = withContext(Dispatchers.IO) {
        val route = _route.value ?: error("Primero importa un Excel.")
        val records = _records.value
        val text = ReadingsReport.build(route.name, route.period, records, System.currentTimeMillis())
        context.contentResolver.openOutputStream(destination, "wt").use { out ->
            requireNotNull(out) { "No se pudo crear el archivo." }
            out.write("\uFEFF".toByteArray(Charsets.UTF_8)) // BOM: el Bloc de notas de Windows reconoce así las tildes y los símbolos
            out.write(text.toByteArray(Charsets.UTF_8))
        }
        records.count { it.appReading != null }
    }

    /** Orden de lectura (1.ª, 2.ª, 3.ª…) como documento de texto. Devuelve cuántas lecturas tiene. */
    suspend fun exportReadingOrderText(destination: Uri): Int = withContext(Dispatchers.IO) {
        val route = _route.value ?: error("Primero importa un Excel.")
        val records = _records.value
        val text = ReadingOrder.toText(route.name, route.period, records, System.currentTimeMillis())
        context.contentResolver.openOutputStream(destination, "wt").use { out ->
            requireNotNull(out) { "No se pudo crear el archivo." }
            out.write("\uFEFF".toByteArray(Charsets.UTF_8))
            out.write(text.toByteArray(Charsets.UTF_8))
        }
        records.count { it.appReading != null }
    }

    data class ReadingsListResult(val total: Int, val fromApp: Int, val fromExcel: Int, val notTaken: Int)

    /**
     * Lista de respaldo de las lecturas (CSV): una fila por medidor, en el orden del Excel, con
     * fila, medidor, NIR, nombre y lectura. Si no se tomó lectura dice «lectura no tomada».
     * Sirve para copiar las lecturas al Excel original si algo sale mal al exportar.
     */
    suspend fun exportReadingsList(destination: Uri): ReadingsListResult = withContext(Dispatchers.IO) {
        val list = _records.value.sortedBy { it.row }
        val order = ReadingOrder.sequence(_records.value)
        fun esc(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("Fila Excel;Medidor;NIR;Nombre;Lectura tomada;Origen;Medidor corregido;Orden de lectura\r\n")
        var fromApp = 0; var fromExcel = 0; var notTaken = 0
        for (r in list) {
            val app = r.appReading
            val excel = r.excelReading
            val (value, origin) = when {
                app != null -> { fromApp++; app.toString() to "app" }
                excel != null -> { fromExcel++; excel.toString() to "ya venía en el Excel" }
                else -> { notTaken++; "lectura no tomada" to "" }
            }
            sb.append(r.row).append(';')
                .append(esc(r.meter)).append(';')
                .append(esc(r.nir)).append(';')
                .append(esc(r.user)).append(';')
                .append(esc(value)).append(';')
                .append(esc(origin)).append(';')
                .append(esc(if (r.hasMeterFix) r.displayMeter else "")).append(';')
                .append(order[r.row]?.toString() ?: "")
                .append("\r\n")
        }
        context.contentResolver.openOutputStream(destination, "wt").use { out ->
            requireNotNull(out) { "No se pudo crear el archivo." }
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        ReadingsListResult(list.size, fromApp, fromExcel, notTaken)
    }

    /**
     * Lista COMPLETA del orden en que entregaste las facturas, de todos los días y todas las
     * jornadas, numerada 1, 2, 3… según el momento en que oprimiste «Entregar».
     * [asText] = true: archivo de texto legible; false: CSV (se abre en Excel).
     */
    suspend fun exportDeliveryOrder(destination: Uri, asText: Boolean): Int = withContext(Dispatchers.IO) {
        val names = store.routeNames()
        val list = store.allDeliveries().sortedWith(compareBy({ it.deliveredAt }, { it.id }))
        val sb = StringBuilder()
        if (asText) {
            sb.append("ORDEN DE ENTREGA DE FACTURAS\r\n")
            sb.append("Total: ").append(list.size).append(" entregas, en el orden en que se entregaron.\r\n")
            sb.append("Factura 1 = la primera que se entregó.\r\n\r\n")
            var lastDay = ""
            for ((i, d) in list.withIndex()) {
                val day = TimeUtil.fileStamp(d.deliveredAt).substringBefore('_')
                if (day != lastDay) { sb.append("--- ").append(day).append(" ---\r\n"); lastDay = day }
                sb.append(i + 1).append(". NIR ").append(d.nir)
                    .append(" · ").append(d.user)
                if (d.address.isNotBlank()) sb.append(" · ").append(d.address)
                if (d.barrio.isNotBlank()) sb.append(" · ").append(d.barrio)
                names[d.routeId]?.takeIf { it.isNotBlank() }?.let { sb.append(" · ").append(it) }
                sb.append(" · ").append(TimeUtil.hour(d.deliveredAt))
                if (d.note.isNotBlank()) sb.append(" · ").append(d.note)
                sb.append("\r\n")
            }
        } else {
            sb.append('﻿')
            sb.append("N°;Fecha;Hora;NIR;Medidor;Usuario;Dirección;Barrio;Ruta;Dónde se dejó\r\n")
            fun esc(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
            for ((i, d) in list.withIndex()) {
                sb.append(i + 1).append(';')
                    .append(TimeUtil.fileStamp(d.deliveredAt).substringBefore('_')).append(';')
                    .append(TimeUtil.hour(d.deliveredAt)).append(';')
                    .append(esc(d.nir)).append(';')
                    .append(esc(d.meter)).append(';')
                    .append(esc(d.user)).append(';')
                    .append(esc(d.address)).append(';')
                    .append(esc(d.barrio)).append(';')
                    .append(esc(names[d.routeId].orEmpty())).append(';')
                    .append(esc(d.note))
                    .append("\r\n")
            }
        }
        context.contentResolver.openOutputStream(destination, "wt").use { out ->
            requireNotNull(out) { "No se pudo crear el archivo." }
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        list.size
    }

    // ------------------------------------------------------------------
    // LECTURAS
    // ------------------------------------------------------------------

    /** Guarda la lectura. (La ubicación del medidor se guarda aparte: ver setLocationIfBetter.) */
    suspend fun saveReading(row: Int, value: Long, note: String) =
        withContext(Dispatchers.IO) {
            val route = _route.value ?: return@withContext
            recordByRow(row) ?: return@withContext
            val now = System.currentTimeMillis()
            // Al registrar la lectura se borra el motivo de "no se pudo leer", si tenía.
            val before = recordByRow(row)
            store.saveReading(route.id, row, value, now, note, null, null)
            if (before != null && before.appReading != value) {
                runCatching { store.logReading(before.meterKey, route, value, now, if (before.appReading == null) "lectura" else "corrección") }
            }
            updateRecord(row) {
                val first = if (it.appReading == null) now else (it.firstReadAt ?: it.readAt ?: now)
                it.copy(appReading = value, readAt = now, firstReadAt = first, note = note, skipReason = null, skipAt = null)
            }
            recountUnexported()
        }

    /** Quita la lectura de la app (el Excel conserva la suya, si tenía). */
    suspend fun clearReading(row: Int) = withContext(Dispatchers.IO) {
        val route = _route.value ?: return@withContext
        val record = recordByRow(row) ?: return@withContext
        store.saveReading(route.id, row, null, null, record.note, record.skipReason, record.skipAt)
        if (record.appReading != null) {
            runCatching { store.logReading(record.meterKey, route, null, System.currentTimeMillis(), "borrada") }
        }
        updateRecord(row) { it.copy(appReading = null, readAt = null, firstReadAt = null) }
        recountUnexported()
    }

    suspend fun saveNote(row: Int, note: String) = withContext(Dispatchers.IO) {
        val route = _route.value ?: return@withContext
        val record = recordByRow(row) ?: return@withContext
        store.saveReading(route.id, row, record.appReading, record.readAt, note, record.skipReason, record.skipAt)
        updateRecord(row) { it.copy(note = note) }
    }

    /**
     * Anota por qué no se pudo leer ("Casa cerrada"...). Con [reason] = null quita el motivo.
     * No cambia el Excel ni cuenta como lectura: solo ayuda a volver a esos medidores.
     */
    suspend fun setSkipReason(row: Int, reason: String?, note: String) = withContext(Dispatchers.IO) {
        val route = _route.value ?: return@withContext
        val record = recordByRow(row) ?: return@withContext
        if (record.appReading != null) return@withContext        // ya tiene lectura: no aplica
        val now = if (reason == null) null else System.currentTimeMillis()
        store.saveReading(route.id, row, null, null, note, reason, now)
        updateRecord(row) { it.copy(note = note, skipReason = reason, skipAt = now) }
    }

    /** Historial de un medidor: meses anteriores, lectura anterior del Excel, este mes y los cambios hechos. */
    suspend fun meterHistory(row: Int): MeterHistory.View? = withContext(Dispatchers.IO) {
        val record = recordByRow(row) ?: return@withContext null
        val route = _route.value
        MeterHistory.build(
            record, route?.period.orEmpty(),
            store.meterSnapshots(record.meterKey),
            store.readingLog(record.meterKey)
        )
    }

    // ------------------------------------------------------------------
    // CORRECCIÓN DEL NÚMERO DE MEDIDOR (solo en la app)
    // ------------------------------------------------------------------

    /**
     * Anota el número REAL de un medidor que el Excel trae mal codificado. El Excel no se toca
     * ni se exporta distinto: solo la búsqueda y las tarjetas usan el número corregido.
     * Con [realMeter] vacío o null se quita la corrección.
     */
    suspend fun setMeterFix(row: Int, realMeter: String?) = withContext(Dispatchers.IO) {
        val record = recordByRow(row) ?: return@withContext
        val clean = realMeter?.trim().orEmpty()
        if (clean.isEmpty() || TextUtil.meterKey(clean) == record.meterKey) {
            store.deleteMeterFix(record.meterKey)
            updateRecord(row) { it.copy(realMeter = null) }
        } else {
            store.saveMeterFix(record.meterKey, clean, System.currentTimeMillis())
            updateRecord(row) { it.copy(realMeter = clean) }
        }
    }

    // ------------------------------------------------------------------
    // UBICACIONES
    // ------------------------------------------------------------------

    suspend fun setLocation(row: Int, lat: Double, lon: Double, accuracy: Float?, source: String) =
        withContext(Dispatchers.IO) {
            val record = recordByRow(row) ?: return@withContext
            saveLocationInternal(record, lat, lon, accuracy, source)
            // Un punto puesto a mano reemplaza todo el historial; una medición propia empieza uno nuevo.
            if (source == "manual" || accuracy == null) store.clearLocSamples(record.meterKey)
            else restartSamples(record.meterKey, lat, lon, accuracy, source)
        }

    enum class LocationUpdate {
        SAVED, IMPROVED, CORRECTED,
        /** Corregido porque la medición cuadra con los vecinos del orden y el punto viejo no. */
        CORRECTED_BY_NEIGHBORS,
        /** Un punto puesto a mano parece estar mal: se le pregunta al usuario. */
        PROPOSED,
        /** El GPS fue regular (±25 a ±40 m) y no alcanzó para cambiar nada. */
        WEAK,
        KEPT
    }

    /** Un punto "manual" que las visitas contradicen: se le pregunta al usuario si lo mueve. */
    data class LocationProposal(
        val row: Int, val nir: String, val user: String,
        val newLat: Double, val newLon: Double, val accuracy: Float, val meters: Double
    )

    private val _proposal = MutableStateFlow<LocationProposal?>(null)
    val locationProposal: StateFlow<LocationProposal?> = _proposal.asStateFlow()

    private val disputes by lazy { context.getSharedPreferences("loc_disputes", Context.MODE_PRIVATE) }

    fun acceptProposal() {
        val pr = _proposal.value ?: return
        _proposal.value = null
        scope.launch {
            val record = recordByRow(pr.row) ?: return@launch
            saveLocationInternal(record, pr.newLat, pr.newLon, pr.accuracy, "corregida")
            restartSamples(record.meterKey, pr.newLat, pr.newLon, pr.accuracy, "corregida")
            disputes.edit().remove(record.meterKey).apply()
        }
    }

    fun dismissProposal() {
        val pr = _proposal.value ?: return
        _proposal.value = null
        recordByRow(pr.row)?.let { disputes.edit().remove(it.meterKey).apply() }
    }

    /**
     * Puntos de referencia para opinar sobre una medición: los vecinos del medidor en el orden
     * de la ruta (3 antes y 3 después) y las últimas entregas hechas (30 min).
     */
    private fun anchorsFor(record: MeterRecord): List<Spot> {
        val out = ArrayList<Spot>()
        val ordered = com.lecturasgas.beta.data.repository.RoutePlanner.plan(_records.value, _learned.value)
        val i = ordered.indexOfFirst { it.row == record.row }
        if (i >= 0) {
            for (j in (i - 3)..(i + 3)) {
                if (j == i || j < 0 || j >= ordered.size) continue
                val r = ordered[j]
                if (r.meterKey == record.meterKey) continue
                val la = r.lat ?: continue
                val lo = r.lon ?: continue
                out.add(Spot(la, lo, r.locationAccuracy ?: LocationFusion.UNKNOWN_ACCURACY))
            }
        }
        val cutoff = System.currentTimeMillis() - 30 * 60_000L
        _deliveries.value.asSequence()
            .filter { it.row != record.row && it.deliveredAt >= cutoff && it.lat != null && it.lon != null }
            .sortedByDescending { it.deliveredAt }
            .take(3)
            .forEach { out.add(Spot(it.lat!!, it.lon!!, it.accuracy ?: 15f)) }
        return out
    }

    /**
     * Guarda la ubicación solo si el medidor no tenía, o si la nueva aporta algo. Reglas:
     *  - GPS bueno (±25 m o menos): se promedia con el punto guardado, o lo corrige si estaba lejos.
     *  - GPS regular (±25 a ±40 m): solo sirve para CORREGIR un punto lejano cuando además cuadra
     *    con los vecinos del orden de la ruta.
     *  - Si la medición queda lejos de todos los vecinos, no se le hace caso (¿entregaste en otro lado?).
     *  - Un punto "manual" nunca se cambia solo: si las visitas lo contradicen, se le pregunta al usuario.
     */
    suspend fun setLocationIfBetter(row: Int, lat: Double, lon: Double, accuracy: Float, source: String): LocationUpdate =
        withContext(Dispatchers.IO) {
            val record = recordByRow(row) ?: return@withContext LocationUpdate.KEPT
            val old = store.locationInfo(record.meterKey)
            val loose = accuracy > STRICT_ACCURACY
            val oldLat = record.lat
            val oldLon = record.lon
            if (old == null || oldLat == null || oldLon == null) {
                if (loose) return@withContext LocationUpdate.WEAK
                saveLocationInternal(record, lat, lon, accuracy, source)
                restartSamples(record.meterKey, lat, lon, accuracy, source)
                return@withContext LocationUpdate.SAVED
            }
            val measure = Spot(lat, lon, accuracy)
            val stored = Spot(oldLat, oldLon, old.accuracy ?: LocationFusion.UNKNOWN_ACCURACY)
            val distance = com.lecturasgas.beta.util.Geo.distanceMeters(oldLat, oldLon, lat, lon)
            // Solo se consulta a los vecinos cuando hay una diferencia seria que explicar.
            val ctx = if (distance > MANUAL_DISPUTE_NEAR_M) {
                com.lecturasgas.beta.location.NeighborContext.verdict(measure, stored, anchorsFor(record))
            } else com.lecturasgas.beta.location.NeighborContext.Verdict.UNKNOWN
            val fits = ctx == com.lecturasgas.beta.location.NeighborContext.Verdict.MEASURE_FITS

            if (old.source == "manual") return@withContext manualCase(record, measure, distance, ctx, loose)

            if (loose && !(fits && distance > LocationFusion.FAR_M)) return@withContext LocationUpdate.WEAK

            when (val r = LocationFusion.resolve(stored, measure, ctx)) {
                is LocationFusion.Result.Fused -> {
                    // El punto se recalcula con TODAS las mediciones guardadas (precisión honesta).
                    val now = System.currentTimeMillis()
                    if (store.locSamples(record.meterKey).isEmpty()) {
                        store.addLocSample(record.meterKey, stored.lat, stored.lon, maxOf(stored.accuracy, 8f), old.source, now)
                    }
                    store.addLocSample(record.meterKey, lat, lon, accuracy, source, now)
                    val est = com.lecturasgas.beta.location.PointEstimator.estimate(store.locSamples(record.meterKey)) ?: r.spot
                    saveLocationInternal(record, est.lat, est.lon, est.accuracy, "combinada")
                    LocationUpdate.IMPROVED
                }
                // El punto viejo estaba lejos: ahora que estás en el medidor, manda la medición nueva.
                is LocationFusion.Result.Corrected -> {
                    restartSamples(record.meterKey, r.spot.lat, r.spot.lon, r.spot.accuracy, "corregida")
                    saveLocationInternal(record, r.spot.lat, r.spot.lon, r.spot.accuracy, "corregida")
                    if (fits) LocationUpdate.CORRECTED_BY_NEIGHBORS else LocationUpdate.CORRECTED
                }
                LocationFusion.Result.Keep -> LocationUpdate.KEPT
            }
        }

    /** Punto puesto a mano: no se toca solo; si dos visitas buenas lo contradicen, se pregunta. */
    private fun manualCase(
        record: MeterRecord, m: Spot, distance: Double,
        ctx: com.lecturasgas.beta.location.NeighborContext.Verdict, loose: Boolean
    ): LocationUpdate {
        val key = record.meterKey
        if (distance <= MANUAL_DISPUTE_M) { disputes.edit().remove(key).apply(); return LocationUpdate.KEPT }
        if (loose || ctx == com.lecturasgas.beta.location.NeighborContext.Verdict.MEASURE_OFF) return LocationUpdate.KEPT
        // ¿Esta visita coincide con la anterior que también contradijo el punto manual?
        val prev = disputes.getString(key, null)?.split("|")
        var count = 1
        if (prev != null && prev.size == 3) {
            val pLat = prev[0].toDoubleOrNull()
            val pLon = prev[1].toDoubleOrNull()
            val pCount = prev[2].toIntOrNull() ?: 0
            if (pLat != null && pLon != null &&
                com.lecturasgas.beta.util.Geo.distanceMeters(pLat, pLon, m.lat, m.lon) <= MANUAL_DISPUTE_NEAR_M
            ) count = pCount + 1
        }
        val fits = ctx == com.lecturasgas.beta.location.NeighborContext.Verdict.MEASURE_FITS
        val needed = if (fits) 1 else 2
        if (count >= needed) {
            disputes.edit().remove(key).apply()
            _proposal.value = LocationProposal(record.row, record.nir, record.user, m.lat, m.lon, m.accuracy, distance)
            return LocationUpdate.PROPOSED
        }
        disputes.edit().putString(key, "${m.lat}|${m.lon}|$count").apply()
        return LocationUpdate.KEPT
    }

    /**
     * Una sola vez: usa el GPS de las entregas YA hechas para afinar los puntos de los medidores
     * (antes la entrega solo ayudaba si el medidor no tenía ubicación). Devuelve cuántos mejoraron.
     * Esos GPS vienen filtrados y suelen ser algo optimistas, así que se les da menos peso.
     */
    suspend fun refineFromDeliveries(): Int = withContext(Dispatchers.IO) {
        var improved = 0
        for (d in _deliveries.value.sortedBy { it.deliveredAt }) {
            val lat = d.lat ?: continue
            val lon = d.lon ?: continue
            val acc = maxOf(6f, (d.accuracy ?: 15f) * 1.5f)
            if (acc > 25f) continue
            val r = setLocationIfBetter(d.row, lat, lon, acc, "entrega")
            if (r == LocationUpdate.IMPROVED || r == LocationUpdate.CORRECTED || r == LocationUpdate.CORRECTED_BY_NEIGHBORS) improved++
        }
        improved
    }

    /**
     * "Mejorar ubicación" confirmada por el usuario (está parado frente al medidor): reemplaza
     * el punto guardado si la nueva medición es más precisa. Lo puesto a mano no se toca.
     * Devuelve true si se guardó.
     */
    suspend fun improveLocation(row: Int, lat: Double, lon: Double, accuracy: Float): Boolean =
        withContext(Dispatchers.IO) {
            val record = recordByRow(row) ?: return@withContext false
            val old = store.locationInfo(record.meterKey)
            if (old != null && old.source == "manual") return@withContext false
            val oldAcc = old?.accuracy
            if (oldAcc != null && accuracy >= oldAcc) return@withContext false
            saveLocationInternal(record, lat, lon, accuracy, "remedido")
            restartSamples(record.meterKey, lat, lon, accuracy, "remedido")
            true
        }

    suspend fun deleteLocation(row: Int) = withContext(Dispatchers.IO) {
        val record = recordByRow(row) ?: return@withContext
        store.deleteLocation(record.meterKey)
        updateByMeterKey(record.meterKey) { it.copy(lat = null, lon = null, locationSource = null, locationAccuracy = null) }
    }

    /** Empieza un historial nuevo para el medidor con esta medición (el historial viejo ya no sirve). */
    private fun restartSamples(meterKey: String, lat: Double, lon: Double, accuracy: Float, source: String) {
        store.clearLocSamples(meterKey)
        store.addLocSample(meterKey, lat, lon, accuracy, source, System.currentTimeMillis())
    }

    private fun saveLocationInternal(record: MeterRecord, lat: Double, lon: Double, accuracy: Float?, source: String) {
        store.saveLocation(record.meterKey, record.nir, lat, lon, accuracy, source, System.currentTimeMillis())
        updateByMeterKey(record.meterKey) { it.copy(lat = lat, lon = lon, locationSource = source, locationAccuracy = accuracy) }
    }

    // ------------------------------------------------------------------
    // ENTREGA
    // ------------------------------------------------------------------

    suspend fun startSession(): DeliverySession = withContext(Dispatchers.IO) {
        _session.value?.let { return@withContext it }
        val route = _route.value ?: error("Primero importa un Excel.")
        val s = store.startSession(route.id, System.currentTimeMillis())
        trackMutex.withLock { _track.value = emptyList() }
        _session.value = s
        s
    }

    suspend fun endSession() = withContext(Dispatchers.IO) {
        val s = _session.value ?: return@withContext
        val distance = trackMutex.withLock { _session.value?.distanceMeters ?: s.distanceMeters }
        store.endSession(s.id, System.currentTimeMillis(), distance)
        _session.value = null
        startedFromReadings = false
        _skippedForNow.value = emptySet()
        _learned.value = RoutePlanner.learnRanks(store.allDeliveries())
    }

    /** Para el servicio: true si hay una jornada activa guardada. */
    suspend fun ensureActiveSessionLoaded(): Boolean = withContext(Dispatchers.IO) {
        if (_session.value == null) {
            val s = store.activeSession()
            _session.value = s
            if (s != null) _track.value = store.trackPoints(s.id)
        }
        _session.value != null
    }

    fun isDelivered(row: Int): Boolean = _deliveries.value.any { it.row == row }

    /** Registra la entrega con hora, GPS y orden real. Devuelve false si ya estaba entregada. */
    suspend fun deliver(row: Int, fix: GeoFix?, note: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (isDelivered(row)) return@withContext false
        val record = recordByRow(row) ?: return@withContext false
        val session = _session.value ?: startSession()
        val good = fix != null && (fix.accuracy ?: 999f) <= 50f
        val delivery = Delivery(
            id = 0,
            sessionId = session.id,
            routeId = session.routeId,
            row = row,
            meterKey = record.meterKey,
            nir = record.nir,
            meter = record.meter,
            user = record.user,
            address = record.address,
            barrio = record.barrio,
            deliveredAt = System.currentTimeMillis(),
            lat = if (good) fix?.lat else null,
            lon = if (good) fix?.lon else null,
            accuracy = if (good) fix?.accuracy else null,
            order = (_deliveries.value.maxOfOrNull { it.order } ?: 0) + 1,
            note = DeliveryNotes.clean(note)
        )
        val id = store.insertDelivery(delivery)
        _deliveries.update { it + delivery.copy(id = id) }
        if (delivery.note.isNotBlank()) refreshNotes()
        true
    }

    /**
     * Entrega por PAQUETE (buzón general de un condominio o barrio): registra de una vez todas
     * las facturas de [rows] como entregadas. Quedan seguidas en el orden de entrega, sin GPS
     * (el buzón no es la casa) y marcadas como paquete para poder deshacerlas juntas.
     * Devuelve cuántas se registraron.
     */
    suspend fun deliverBatch(rows: List<Int>, note: String): Int = withContext(Dispatchers.IO) {
        val route = _route.value ?: return@withContext 0
        val session = _session.value ?: startSession()
        val cleanNote = DeliveryNotes.clean(note)
        val stamp = System.currentTimeMillis()
        var order = _deliveries.value.maxOfOrNull { it.order } ?: 0
        val already = _deliveries.value.map { it.row }.toHashSet()
        val added = mutableListOf<Delivery>()
        for (row in rows) {
            if (row in already) continue
            val record = recordByRow(row) ?: continue
            order++
            val d = Delivery(
                id = 0, sessionId = session.id, routeId = route.id, row = row,
                meterKey = record.meterKey, nir = record.nir, meter = record.meter,
                user = record.user, address = record.address, barrio = record.barrio,
                deliveredAt = stamp, lat = null, lon = null, accuracy = null,
                order = order, note = cleanNote, bulk = stamp
            )
            val id = store.insertDelivery(d)
            added.add(d.copy(id = id))
        }
        if (added.isNotEmpty()) {
            _deliveries.update { it + added }
            if (cleanNote.isNotBlank()) refreshNotes()
        }
        added.size
    }

    /** Deshace un paquete completo (todas sus facturas vuelven a pendientes). */
    suspend fun undoBatch(stamp: Long): Int = withContext(Dispatchers.IO) {
        val n = _deliveries.value.count { it.bulk == stamp }
        store.deleteDeliveryBatch(stamp)
        _deliveries.update { list -> list.filterNot { it.bulk == stamp } }
        refreshNotes()
        n
    }

    /** Cambia (o borra, con texto vacío) la nota de una entrega. */
    suspend fun setDeliveryNote(id: Long, note: String) = withContext(Dispatchers.IO) {
        val clean = DeliveryNotes.clean(note)
        store.updateDeliveryNote(id, clean)
        _deliveries.update { list -> list.map { if (it.id == id) it.copy(note = clean) else it } }
        refreshNotes()
    }

    private fun refreshNotes() {
        _notes.value = DeliveryNotes.from(store.allDeliveries())
    }

    suspend fun undoDelivery(id: Long) = withContext(Dispatchers.IO) {
        store.deleteDelivery(id)
        _deliveries.update { list -> list.filterNot { it.id == id } }
    }

    /** Llega cada nueva ubicación mientras la jornada está activa. */
    fun onTrackFix(fix: GeoFix) {
        if (_session.value == null) return
        val accuracy = fix.accuracy ?: 999f
        if (accuracy > 35f) return
        scope.launch {
            trackMutex.withLock {
                val session = _session.value ?: return@withLock
                val last = _track.value.lastOrNull()
                var add = 0.0
                if (last != null) {
                    val d = Geo.distanceMeters(last.lat, last.lon, fix.lat, fix.lon)
                    val dt = fix.time - last.time
                    // Se guarda un punto cada 5 m (o cada minuto si se movió algo).
                    if (d < 5.0 && !(dt > 60_000L && d >= 2.0)) return@withLock
                    // Saltos imposibles (> 50 m/s) se ignoran: son errores del GPS.
                    if (dt > 0 && d / (dt / 1000.0) > 50.0) return@withLock
                    add = d
                }
                val p = TrackPoint(fix.lat, fix.lon, fix.accuracy, fix.time)
                store.addTrackPoint(session.id, p)
                _track.value = _track.value + p
                val updated = session.copy(distanceMeters = session.distanceMeters + add)
                _session.value = updated
                if (_track.value.size % 10 == 0) store.updateSessionDistance(updated.id, updated.distanceMeters)
            }
        }
    }

    // ------------------------------------------------------------------
    // HISTORIAL
    // ------------------------------------------------------------------

    suspend fun sessionSummaries(): List<SessionSummary> = withContext(Dispatchers.IO) { store.sessionSummaries() }
    suspend fun sessionDeliveries(id: Long): List<Delivery> = withContext(Dispatchers.IO) { store.deliveriesForSession(id) }
    /** Rastro de un día completo (todas las jornadas que empezaron ese día), en orden. */
    suspend fun dayTrack(dayStart: Long): List<TrackPoint> = withContext(Dispatchers.IO) {
        val end = dayStart + 24L * 3600_000L
        store.sessionSummaries()
            .filter { it.session.startedAt in dayStart until end }
            .sortedBy { it.session.startedAt }
            .flatMap { store.trackPoints(it.session.id) }
    }

    suspend fun sessionTrack(id: Long): List<TrackPoint> = withContext(Dispatchers.IO) { store.trackPoints(id) }

    // ------------------------------------------------------------------
    // MIGRACIÓN DESDE LA VERSIÓN 0.2 (SharedPreferences "lecturas_gas")
    // ------------------------------------------------------------------

    /**
     * Rescata los puntos GPS y entregas que la versión anterior guardaba,
     * para no perder el trabajo ya hecho al instalar esta versión.
     */
    private fun migrateLegacyIfNeeded() {
        if (prefs.legacyMigrated) return
        val old = context.getSharedPreferences("lecturas_gas", Context.MODE_PRIVATE)
        old.getString("route_gps_points", null)?.let { raw ->
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val meter = o.optString("meter")
                val lat = o.optDouble("latitude", Double.NaN)
                val lon = o.optDouble("longitude", Double.NaN)
                if (meter.isBlank() || lat.isNaN() || lon.isNaN() || lat == 0.0) continue
                val acc = o.optDouble("accuracy", Double.NaN).let { if (it.isNaN()) null else it.toFloat() }
                store.saveLocation(
                    TextUtil.meterKey(meter), o.optString("nir"), lat, lon, acc, "lectura",
                    o.optLong("timestamp", System.currentTimeMillis())
                )
            }
        }
        old.getString("delivery_points", null)?.let { raw ->
            val array = JSONArray(raw)
            if (array.length() > 0) {
                val first = array.optJSONObject(0)?.optLong("timestamp") ?: System.currentTimeMillis()
                val session = store.startSession(0L, first)
                var last = first
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val lat = o.optDouble("lat", Double.NaN)
                    val lon = o.optDouble("lon", Double.NaN)
                    last = maxOf(last, o.optLong("timestamp", last))
                    store.insertDelivery(
                        Delivery(
                            id = 0, sessionId = session.id, routeId = 0L,
                            row = o.optInt("row"), meterKey = TextUtil.meterKey(o.optString("meter")),
                            nir = o.optString("nir"), meter = o.optString("meter"), user = o.optString("user"),
                            address = o.optString("address"), barrio = o.optString("neighborhood"),
                            deliveredAt = o.optLong("timestamp", last),
                            lat = if (lat.isNaN()) null else lat, lon = if (lon.isNaN()) null else lon,
                            accuracy = null, order = o.optInt("sequence", i + 1)
                        )
                    )
                }
                store.endSession(session.id, last, 0.0)
            }
        }
        prefs.legacyMigrated = true
    }

    private companion object {
        /** Hasta este error (m) el GPS se considera "bueno" para afinar puntos. */
        const val STRICT_ACCURACY = 25f
        /** Un punto manual solo se cuestiona si la visita cae a más de esto. */
        const val MANUAL_DISPUTE_M = 100.0
        /** Dos mediciones a esta distancia o menos cuentan como "el mismo lugar". */
        const val MANUAL_DISPUTE_NEAR_M = 40.0
    }
}
