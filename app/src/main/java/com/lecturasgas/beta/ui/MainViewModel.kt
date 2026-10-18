package com.lecturasgas.beta.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lecturasgas.beta.appContainer
import com.lecturasgas.beta.data.backup.BackupManager
import com.lecturasgas.beta.data.model.Delivery
import com.lecturasgas.beta.data.model.SessionSummary
import com.lecturasgas.beta.data.repository.AfterSave
import com.lecturasgas.beta.data.repository.LecturasRepository
import com.lecturasgas.beta.data.repository.RoutePlanner
import com.lecturasgas.beta.util.TextUtil
import com.lecturasgas.beta.location.Near
import com.lecturasgas.beta.location.LocationFusion
import com.lecturasgas.beta.location.LocationOutliers
import com.lecturasgas.beta.location.Spot
import com.lecturasgas.beta.location.Suspect
import com.lecturasgas.beta.data.repository.AppSettings
import com.lecturasgas.beta.data.model.RouteSummary
import com.lecturasgas.beta.review.MeterHistory
import com.lecturasgas.beta.location.Proximity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.lecturasgas.beta.data.model.GeoFix
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado compartido por los tres módulos (Mapa, Lecturas, Entrega):
 * importar/exportar, el diálogo de lectura y los saltos al mapa.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.appContainer
    val repo = container.repository
    val tracker = container.tracker
    val prefs = container.prefs

    val route = repo.route
    val loaded = repo.loaded
    val unexported = repo.unexported
    val fix = tracker.fix
    /** Semáforo del GPS (para la tarjeta de lectura). */
    val gpsQuality = tracker.quality
    val gpsEnabled = tracker.gpsEnabled

    // ---------------- Mensajes ----------------
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun showMessage(text: String) { _message.value = text }
    fun consumeMessage() { _message.value = null }

    // ---------------- Progreso (importar/exportar) ----------------
    data class Busy(
        val title: String,
        val done: Int = 0,
        val total: Int = 0,
        /** Avance total 0..1 de tareas con etapas (comparar, comprobar, exportar); -1 = no aplica. */
        val fraction: Float = -1f,
        /** Etapa actual en texto, p. ej. «Comparando celda por celda…». */
        val stage: String = "",
        /** Cuándo empezó la tarea (ms), para mostrar el tiempo transcurrido y estimar lo que falta. */
        val startedAt: Long = 0L,
        /** false cuando ya se está guardando el archivo final: ahí cancelar dejaría todo a medias. */
        val cancelable: Boolean = true,
        /** true después de tocar «Cancelar», mientras la tarea llega a un punto donde puede detenerse. */
        val cancelling: Boolean = false
    )

    @Volatile private var cancelRequested = false

    /** «Cancelar» en la ventana de progreso. La tarea se detiene en su siguiente aviso de avance (menos de un segundo). */
    fun cancelBusy() {
        val b = _busy.value ?: return
        if (!b.cancelable || b.cancelling) return
        cancelRequested = true
        _busy.value = b.copy(cancelling = true, stage = "Cancelando…")
    }

    /**
     * Avance con etapas: solo publica cuando cambia el porcentaje o la etapa (no satura la pantalla) y corta la
     * tarea si la persona canceló. [lockedStages] son etapas finales (guardar el archivo) donde ya no se puede cancelar.
     */
    private fun stagedProgress(title: String, lockedStages: Set<String> = emptySet()): com.lecturasgas.beta.data.excel.Progress {
        val start = System.currentTimeMillis()
        var lastPct = -1
        var lastStage = ""
        cancelRequested = false
        return { stage, f ->
            val locked = stage in lockedStages
            if (cancelRequested && !locked) throw com.lecturasgas.beta.data.excel.TaskCancelled()
            val pct = (f.coerceIn(0f, 1f) * 100).toInt()
            if (pct != lastPct || stage != lastStage) {
                lastPct = pct; lastStage = stage
                _busy.value = Busy(title, fraction = pct / 100f, stage = stage, startedAt = start, cancelable = !locked)
            }
        }
    }

    private val _busy = MutableStateFlow<Busy?>(null)
    val busy: StateFlow<Busy?> = _busy.asStateFlow()

    /** Resultado detallado para mostrar en un diálogo. */
    data class Report(val title: String, val lines: List<String>)

    private val _report = MutableStateFlow<Report?>(null)
    val report: StateFlow<Report?> = _report.asStateFlow()
    fun closeReport() { _report.value = null }

    // ---------------- Importar Excel (en dos pasos) ----------------

    /** El Excel que se acaba de leer, esperando que confirmes a qué ruta pertenece. */
    data class ImportState(
        val preview: LecturasRepository.ImportPreview,
        val key: String,
        val plan: LecturasRepository.ImportPlan?
    )

    private val _importState = MutableStateFlow<ImportState?>(null)
    val importState: StateFlow<ImportState?> = _importState.asStateFlow()

    /** Paso 1: lee el Excel y abre la ventana para confirmar el nombre de la ruta. */
    fun importExcel(uri: Uri) {
        if (_busy.value != null) return
        _busy.value = Busy("Leyendo Excel…", fraction = 0f, stage = "Preparando…", startedAt = System.currentTimeMillis())
        viewModelScope.launch {
            try {
                val preview = repo.previewImport(uri, stagedProgress("Leyendo Excel…"))
                _importState.value = ImportState(preview, preview.suggestedKey, repo.planImport(preview.suggestedKey))
            } catch (e: com.lecturasgas.beta.data.excel.TaskCancelled) {
                showMessage("Importación cancelada. No se cambió nada.")
            } catch (e: Exception) {
                _report.value = Report("No se pudo importar", listOf(e.message ?: "Error desconocido"))
            } finally {
                _busy.value = null
            }
        }
    }

    /** El usuario cambió el nombre de la ruta: se recalcula qué pasaría. */
    fun selectImportKey(key: String) {
        val cur = _importState.value ?: return
        _importState.value = cur.copy(key = key)
        viewModelScope.launch {
            val plan = repo.planImport(key)
            _importState.update { st -> if (st != null && st.key == key) st.copy(plan = plan) else st }
        }
    }

    fun cancelImport() {
        repo.cancelImport()
        _importState.value = null
    }

    /** Paso 2: guarda el Excel como la ruta elegida y muestra el resumen. */
    fun confirmImportRoute() {
        val st = _importState.value ?: return
        if (_busy.value != null) return
        _importState.value = null
        _busy.value = Busy("Importando Excel…")
        viewModelScope.launch {
            try {
                val r = repo.commitImport(st.key)
                _report.value = Report(
                    if (r.updatedExisting) "Ruta actualizada" else "Ruta nueva guardada",
                    buildList {
                        add(r.routeName)
                        add("${r.total} medidores en ${r.barrios} barrios")
                        add("${r.withReading} ya tienen lectura · ${r.pending} pendientes")
                        add("${r.withGps} con ubicación GPS guardada")
                        if (r.carriedReadings > 0) add("${r.carriedReadings} lecturas de la app conservadas (mismo periodo)")
                        if (r.updatedExisting) {
                            add("")
                            add("Cambios frente al Excel anterior de esta ruta:")
                            add("• ${r.newMeters} medidores NUEVOS (llevan la etiqueta NUEVO hasta que los leas)")
                            add("• ${r.renumbered} cambiaron de NIR (la tarjeta muestra el NIR de antes)")
                            add("• ${r.removedCount} ya no aparecen en el Excel (no se borró su GPS)")
                            r.removed.forEach { add("    – $it") }
                            if (r.removedCount > r.removed.size) add("    … y ${r.removedCount - r.removed.size} más")
                            if (r.newBarrios.isNotEmpty()) add("• Barrios nuevos: " + r.newBarrios.joinToString(", "))
                        } else {
                            add("")
                            add("Es una ruta nueva en la app. Las otras rutas siguen guardadas: puedes volver a ellas desde el menú ▸ Rutas.")
                        }
                        addAll(r.warnings)
                    }
                )
            } catch (e: Exception) {
                _report.value = Report("No se pudo importar", listOf(e.message ?: "Error desconocido"))
            } finally {
                _busy.value = null
            }
        }
    }

    // ---------------- Historial por medidor ----------------
    private val _meterHistory = MutableStateFlow<Pair<Int, MeterHistory.View>?>(null)
    val meterHistory: StateFlow<Pair<Int, MeterHistory.View>?> = _meterHistory.asStateFlow()

    fun openMeterHistory(row: Int) {
        viewModelScope.launch {
            val view = repo.meterHistory(row)
            if (view != null) _meterHistory.value = row to view
        }
    }
    fun closeMeterHistory() { _meterHistory.value = null }

    // ---------------- Rutas guardadas (A, B, C…) ----------------
    private val _routesOpen = MutableStateFlow(false)
    val routesOpen: StateFlow<Boolean> = _routesOpen.asStateFlow()
    private val _routes = MutableStateFlow<List<RouteSummary>>(emptyList())
    val routes: StateFlow<List<RouteSummary>> = _routes.asStateFlow()

    fun openRoutes() {
        _routesOpen.value = true
        viewModelScope.launch { _routes.value = repo.routeList() }
    }
    fun closeRoutes() { _routesOpen.value = false }

    /** Borra una ruta guardada (con confirmación previa en la pantalla). Los puntos GPS se conservan. */
    fun deleteRoute(id: Long, keepHistory: Boolean) {
        viewModelScope.launch {
            val problem = repo.deleteRoute(id, keepHistory)
            _routes.value = repo.routeList()
            showMessage(problem ?: "Ruta borrada")
        }
    }

    fun switchRoute(id: Long) {
        viewModelScope.launch {
            val problem = repo.switchRoute(id)
            if (problem != null) showMessage(problem)
            else { _routesOpen.value = false; showMessage("Ruta cambiada") }
        }
    }

    // ---------------- Revisión antes de exportar ----------------
    private val _reviewOpen = MutableStateFlow(false)
    val reviewOpen: StateFlow<Boolean> = _reviewOpen.asStateFlow()

    /** Abre «Revisión antes de exportar» (la lista se calcula en la pantalla y se actualiza sola al corregir lecturas). */
    fun openExportReview() {
        if (route.value == null) { showMessage("Primero importa un Excel."); return }
        _reviewOpen.value = true
    }
    fun closeExportReview() { _reviewOpen.value = false }

    // ---------------- Orden de lectura ----------------
    private val _orderOpen = MutableStateFlow(false)
    val readingOrderOpen: StateFlow<Boolean> = _orderOpen.asStateFlow()
    fun openReadingOrder() {
        if (route.value == null) { showMessage("Primero importa un Excel."); return }
        _orderOpen.value = true
    }
    fun closeReadingOrder() { _orderOpen.value = false }

    /** «Comprobar exportación»: genera el Excel y lo verifica, pero NO guarda ningún archivo. */
    fun checkExport() {
        if (_busy.value != null) return
        if (route.value == null) { showMessage("Primero importa un Excel."); return }
        _busy.value = Busy("Comprobando la exportación…", fraction = 0f, stage = "Preparando…", startedAt = System.currentTimeMillis())
        viewModelScope.launch {
            try {
                val c = repo.checkExport(stagedProgress("Comprobando la exportación…"))
                _report.value = if (c.ok) Report(
                    "Exportación correcta ✔",
                    buildList {
                        add("Así saldría el Excel si lo exportaras ahora mismo (no se guardó nada):")
                        add("• Se escribirían ${c.written} lecturas (${c.unchanged} ya estaban iguales en el archivo).")
                        add("• Se revisaron ${c.rowsChecked} filas: cada una tiene el mismo NIR y número de medidor que la app.")
                        add("• Las ${c.readingsChecked} lecturas nuevas quedaron en la fila de SU medidor.")
                        add("• Comparado celda por celda con el original: solo cambiarían ${c.changedCells} celdas de la columna de lectura. Todo lo demás es idéntico.")
                        if (c.skippedFormula > 0) add("• ${c.skippedFormula} filas se omiten (la celda de lectura tiene fórmula o un formato poco común).")
                    }
                ) else Report(
                    "La comprobación encontró problemas",
                    buildList {
                        add("No exportes todavía. Esto es lo que no cuadra:")
                        addAll(c.problems.map { "• $it" })
                    }
                )
            } catch (e: com.lecturasgas.beta.data.excel.TaskCancelled) {
                showMessage("Comprobación cancelada. Puedes repetirla cuando tengas tiempo (menú ☰ → Comprobar exportación).")
            } catch (e: Exception) {
                _report.value = Report("No se pudo comprobar", listOf(e.message ?: "Error desconocido"))
            } finally {
                _busy.value = null
            }
        }
    }

    fun exportExcel(uri: Uri) {
        if (_busy.value != null) return
        val verify = prefs.settings.value.verifyOnExport
        _busy.value = Busy(if (verify) "Exportando y verificando…" else "Exportando…", fraction = 0f, stage = "Preparando…", startedAt = System.currentTimeMillis())
        viewModelScope.launch {
            try {
                val r = repo.exportExcel(uri, verify, stagedProgress(if (verify) "Exportando y verificando…" else "Exportando…", setOf("Guardando el archivo…", "Comprobando el archivo guardado…")))
                _report.value = if (r.verified) Report(
                    "Excel exportado y verificado",
                    buildList {
                        add("Lecturas escritas desde la app: ${r.written}")
                        add("Total de medidores con lectura en el archivo: ${r.totalWithReading}")
                        add("Se comprobó fila por fila (${r.rowsChecked} filas): mismo NIR y mismo medidor, y cada lectura en la fila de su medidor ✔")
                        add("Comparado celda por celda con el original: solo cambiaron ${r.changedVsOriginal} celdas de la columna de lectura. Todo lo demás es idéntico ✔")
                        when (r.readBackOk) {
                            true -> add("Se volvió a leer el archivo guardado y es idéntico al verificado ✔")
                            else -> add("No se pudo volver a leer el archivo guardado para compararlo (el archivo sí se escribió).")
                        }
                        if (r.skippedFormula > 0) add("Filas omitidas (fórmula o formato poco común): ${r.skippedFormula}")
                    }
                ) else Report(
                    "Excel exportado (sin verificar)",
                    buildList {
                        add("Lecturas escritas desde la app: ${r.written}")
                        add("No se comparó con el original para que sea más rápido. Solo se escribe la columna de lectura; el resto del archivo no se toca.")
                        add("Si quieres revisarlo después: menú ☰ → Comprobar exportación.")
                        if (r.skippedFormula > 0) add("Filas omitidas (fórmula o formato poco común): ${r.skippedFormula}")
                    }
                )
            } catch (e: com.lecturasgas.beta.data.excel.TaskCancelled) {
                showMessage("Exportación cancelada. No se guardó ningún archivo; tus lecturas siguen intactas en la app.")
            } catch (e: Exception) {
                _report.value = Report("No se pudo exportar", listOf(e.message ?: "Error desconocido"))
            } finally {
                _busy.value = null
            }
        }
    }

    fun exportDeliveriesCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val n = repo.exportDeliveriesCsv(uri)
                showMessage("Recorrido exportado: $n entregas")
            } catch (e: Exception) {
                showMessage("No se pudo exportar: ${e.message}")
            }
        }
    }

    fun exportReadingsText(uri: Uri) {
        viewModelScope.launch {
            try {
                val n = repo.exportReadingsText(uri)
                showMessage("Respaldo de lecturas guardado: $n lecturas")
            } catch (e: Exception) {
                showMessage("No se pudo guardar el respaldo: ${e.message}")
            }
        }
    }

    fun exportReadingOrderText(uri: Uri) {
        viewModelScope.launch {
            try {
                val n = repo.exportReadingOrderText(uri)
                showMessage("Orden de lectura guardado: $n lecturas")
            } catch (e: Exception) {
                showMessage("No se pudo guardar el orden de lectura: ${e.message}")
            }
        }
    }

    /** Compara un Excel exportado (que el usuario elige) con el original de la ruta. */
    fun compareExportedWithOriginal(uri: Uri) {
        if (_busy.value != null) return
        _busy.value = Busy("Comparando con el original…", fraction = 0f, stage = "Preparando…", startedAt = System.currentTimeMillis())
        viewModelScope.launch {
            try {
                val c = repo.compareWithOriginal(uri, stagedProgress("Comparando con el original…"))
                _report.value = if (c.ok) Report(
                    "Idéntico al original ✔",
                    listOf(
                        "Lo único distinto son ${c.changedCells} celdas de la columna de lectura actual.",
                        "Estilos, fórmulas, protección y todos los demás datos son iguales al original."
                    )
                ) else Report(
                    "El archivo NO es idéntico al original",
                    buildList {
                        add("Se encontraron diferencias fuera de la columna de lectura:")
                        addAll(c.problems.map { "• $it" })
                    }
                )
            } catch (e: com.lecturasgas.beta.data.excel.TaskCancelled) {
                showMessage("Comparación cancelada. Puedes repetirla cuando quieras.")
            } catch (e: Exception) {
                _report.value = Report("No se pudo comparar", listOf(e.message ?: "Error desconocido"))
            } finally {
                _busy.value = null
            }
        }
    }

    fun exportReadingsList(uri: Uri) {
        viewModelScope.launch {
            try {
                val r = repo.exportReadingsList(uri)
                _report.value = Report(
                    "Lista de lecturas guardada",
                    listOf(
                        "Medidores en la lista: ${r.total}",
                        "Lecturas tomadas con la app: ${r.fromApp}",
                        "Lecturas que ya venían en el Excel: ${r.fromExcel}",
                        "Sin lectura (\"lectura no tomada\"): ${r.notTaken}",
                        "Revisa que ${r.fromApp} + ${r.fromExcel} + ${r.notTaken} sea igual a ${r.total}."
                    )
                )
            } catch (e: Exception) {
                _report.value = Report("No se pudo guardar la lista", listOf(e.message ?: "Error desconocido"))
            }
        }
    }

    fun exportDeliveryOrder(uri: Uri, asText: Boolean) {
        viewModelScope.launch {
            try {
                val n = repo.exportDeliveryOrder(uri, asText)
                showMessage("Lista de orden de entrega guardada: $n facturas")
            } catch (e: Exception) {
                showMessage("No se pudo guardar la lista: ${e.message}")
            }
        }
    }

    init {
        // Una sola vez: las entregas que ya hiciste afinan los puntos de los medidores.
        viewModelScope.launch {
            repo.loaded.first { it }
            if (!prefs.deliveriesRefined2) {
                val n = repo.refineFromDeliveries()
                prefs.deliveriesRefined = true
                prefs.deliveriesRefined2 = true
                if (n > 0) showMessage("Corregí/afiné la ubicación de $n medidores con tus entregas anteriores")
            }
        }
    }

    // ---------------- "Estás frente a…" y mejorar ubicación ----------------
    private val dismissedNear = MutableStateFlow<Set<Int>>(emptySet())

    /** El medidor más cercano que necesita algo (leerlo o mejorar su GPS). Se actualiza con tu posición. */
    val near: StateFlow<Near?> =
        combine(tracker.fix, repo.records, dismissedNear, prefs.settings) { fix, records, dismissed, cfg ->
            if (!cfg.nearEnabled) null
            else Proximity.nearest(fix, records, dismissed, nearM = cfg.nearRadius.toDouble(), weakHint = cfg.nearWeakHint)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---------------- Ubicaciones sospechosas ----------------
    /** Medidores cuyo punto cae lejos de su barrio (probable error de GPS). */
    val suspects: StateFlow<List<Suspect>> =
        repo.records.map { LocationOutliers.find(it) }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _suspectsOpen = MutableStateFlow(false)
    val suspectsOpen: StateFlow<Boolean> = _suspectsOpen.asStateFlow()
    fun openSuspects() { _suspectsOpen.value = true }
    fun closeSuspects() { _suspectsOpen.value = false }

    /** Borra el punto malo: el medidor queda «sin GPS» y se vuelve a medir bien cuando lo leas o entregues. */
    fun clearSuspect(row: Int) {
        viewModelScope.launch {
            repo.deleteLocation(row)
            showMessage("Punto borrado. Se medirá de nuevo cuando lo leas o entregues.")
        }
    }

    fun clearAllSuspects() {
        viewModelScope.launch {
            val rows = suspects.value.map { it.record.row }
            rows.forEach { repo.deleteLocation(it) }
            showMessage("${rows.size} puntos borrados. Se medirán de nuevo cuando leas o entregues esos medidores.")
            _suspectsOpen.value = false
        }
    }

    // ---------------- Ajustes ----------------
    val settings: StateFlow<AppSettings> = prefs.settings
    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()
    fun openSettings() { _settingsOpen.value = true }
    fun closeSettings() { _settingsOpen.value = false }
    fun updateSettings(transform: (AppSettings) -> AppSettings) = prefs.updateSettings(transform)

    fun dismissNear(row: Int) { dismissedNear.update { it + row } }

    private val _improving = MutableStateFlow<Int?>(null)
    /** Fila del medidor cuya ubicación se está midiendo ahora (para mostrar "Midiendo…"). */
    val improving: StateFlow<Int?> = _improving.asStateFlow()

    /**
     * "Mejorar ubicación": el usuario está parado frente al medidor. Se promedian varios
     * segundos de GPS y solo se reemplaza el punto guardado si queda mejor.
     */
    fun improveLocation(row: Int) {
        if (_improving.value != null) return
        val before = repo.recordByRow(row)
        _improving.value = row
        viewModelScope.launch {
            try {
                showMessage("Midiendo… quédate quieto unos segundos")
                val capture = tracker.captureBest(maxWaitMs = 8_000L, goodAccuracy = 8f, minSamples = 5)
                if (capture == null) {
                    showMessage("Sin señal GPS. Prueba otra vez a cielo abierto.")
                    return@launch
                }
                val acc = capture.fix.accuracy ?: 999f
                if (acc > Proximity.IMPROVE_FIX_ACCURACY) {
                    showMessage("El GPS sigue impreciso (±${acc.toInt()} m). Espera un momento a cielo abierto y toca de nuevo.")
                    return@launch
                }
                val saved = repo.improveLocation(row, capture.fix.lat, capture.fix.lon, acc)
                val was = before?.locationAccuracy?.let { "de ±${it.toInt()} m " } ?: ""
                showMessage(
                    if (saved) "Ubicación mejorada $was" + "a ±${acc.toInt()} m"
                    else "La ubicación guardada ya era igual o mejor"
                )
                dismissedNear.update { it + row }
            } finally {
                _improving.value = null
            }
        }
    }

    // ---------------- Respaldo y traspaso ----------------
    private val backupManager = container.backup
    val backupStatus = backupManager.status

    private val _backupDialog = MutableStateFlow(false)
    val backupDialog: StateFlow<Boolean> = _backupDialog.asStateFlow()
    fun openBackup() { _backupDialog.value = true }
    fun closeBackup() { _backupDialog.value = false }

    fun suggestedBackupName(): String = backupManager.suggestedName()

    /** Guarda el respaldo en el archivo que el usuario eligió (Descargas, Drive, USB…). */
    fun saveBackupTo(uri: Uri) {
        if (_busy.value != null) return
        _busy.value = Busy("Guardando respaldo…")
        viewModelScope.launch {
            try {
                val i = withContext(Dispatchers.IO) { backupManager.writeTo(uri) }
                _report.value = Report("Respaldo guardado", backupLines(i))
            } catch (e: Exception) {
                _report.value = Report("No se pudo guardar el respaldo", listOf(e.message ?: "Error desconocido"))
            } finally {
                _busy.value = null
            }
        }
    }

    /** Prepara el respaldo y abre el menú de compartir (WhatsApp, correo, Drive…). */
    fun shareBackup(onReady: (Intent) -> Unit) {
        if (_busy.value != null) return
        _busy.value = Busy("Preparando respaldo…")
        viewModelScope.launch {
            try {
                val intent = withContext(Dispatchers.IO) { backupManager.createShareIntent() }
                onReady(intent)
            } catch (e: Exception) {
                _report.value = Report("No se pudo preparar el respaldo", listOf(e.message ?: "Error desconocido"))
            } finally {
                _busy.value = null
            }
        }
    }

    data class RestorePrompt(val uri: Uri, val info: BackupManager.Info)

    private val _restorePrompt = MutableStateFlow<RestorePrompt?>(null)
    val restorePrompt: StateFlow<RestorePrompt?> = _restorePrompt.asStateFlow()
    fun cancelRestore() { _restorePrompt.value = null }

    /** Paso 1 de restaurar: lee el archivo y muestra qué contiene antes de reemplazar nada. */
    fun inspectBackup(uri: Uri) {
        if (_busy.value != null) return
        _busy.value = Busy("Leyendo respaldo…")
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { backupManager.inspect(uri) }
                _restorePrompt.value = RestorePrompt(uri, info)
            } catch (e: Exception) {
                _report.value = Report("No se pudo leer el respaldo", listOf(e.message ?: "Error desconocido"))
            } finally {
                _busy.value = null
            }
        }
    }

    private val _restored = MutableStateFlow<BackupManager.Info?>(null)
    val restored: StateFlow<BackupManager.Info?> = _restored.asStateFlow()

    /** Paso 2: reemplaza todos los datos por los del respaldo. */
    fun confirmRestore() {
        val prompt = _restorePrompt.value ?: return
        _restorePrompt.value = null
        _busy.value = Busy("Restaurando…")
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { backupManager.restore(prompt.uri) }
                _restored.value = info
            } catch (e: Exception) {
                _report.value = Report("No se pudo restaurar", listOf(e.message ?: "Error desconocido"))
            } finally {
                _busy.value = null
            }
        }
    }

    fun restartApp() = backupManager.restartApp()

    fun setBackupFolder(uri: Uri?) {
        backupManager.setFolder(uri)
        if (uri != null) backupNow()
    }

    fun backupNow() {
        viewModelScope.launch(Dispatchers.IO) { backupManager.runAuto(repo, force = true) }
    }

    fun backupLines(i: BackupManager.Info): List<String> = buildList {
        if (i.routeName.isNotBlank()) add(i.routeName)
        add("${i.meters} medidores · ${i.readings} lecturas tomadas")
        add("${i.locations} ubicaciones GPS · ${i.deliveries} entregas")
        add("Hecho: ${com.lecturasgas.beta.util.TimeUtil.dateTime(i.createdAt)}")
    }

    // ---------------- Diálogo de lectura (se abre desde cualquier módulo) ----------------
    private val _readingRow = MutableStateFlow<Int?>(null)
    val readingRow: StateFlow<Int?> = _readingRow.asStateFlow()

    fun openReading(row: Int) { _readingRow.value = row }
    fun closeReading() { _readingRow.value = null }

    /** Página que se está viendo (la actualiza AppRoot). */
    var currentPage: Int = PAGE_READINGS

    /** Cada vez que sube, el buscador de Lecturas se limpia y abre el teclado. */
    private val _searchFocus = MutableStateFlow(0)
    val searchFocus: StateFlow<Int> = _searchFocus.asStateFlow()
    fun requestSearchFocus() { _searchFocus.value = _searchFocus.value + 1 }

    // ---------------- Jornada / recorrido desde Lecturas ----------------
    /** ▶ de la tarjeta de Lecturas: empieza a grabar tu recorrido. */
    fun startRoute(onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.startedFromReadings = repo.session.value == null
                repo.startSession()
                com.lecturasgas.beta.location.DeliveryTrackingService.start(getApplication())
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo iniciar")
            }
        }
    }

    /** ■ de la tarjeta: termina y guarda el recorrido. */
    fun endRoute() {
        viewModelScope.launch {
            repo.endSession()
            com.lecturasgas.beta.location.DeliveryTrackingService.stop(getApplication())
        }
    }

    /** Con «Grabar mi recorrido al leer» activo, la primera lectura del día empieza el recorrido. */
    private suspend fun autoStartRoute() {
        if (!prefs.settings.value.trackReadings) return
        val now = System.currentTimeMillis()
        val current = repo.session.value
        if (current != null) {
            // Una jornada olvidada de otro día se cierra y se empieza una nueva.
            if (current.startedAt >= com.lecturasgas.beta.readings.Dashboard.startOfDay(now)) return
            repo.endSession()
            com.lecturasgas.beta.location.DeliveryTrackingService.stop(getApplication())
        }
        runCatching {
            repo.startedFromReadings = true
            repo.startSession()
            com.lecturasgas.beta.location.DeliveryTrackingService.start(getApplication())
        }
    }

    fun saveReading(row: Int, value: Long, note: String, saveGps: Boolean, after: AfterSave) {
        prefs.saveGpsWithReading = saveGps
        prefs.afterSave = after
        val atTap = tracker.fix.value   // dónde estabas al guardar (por si luego caminas mientras se promedia el GPS)
        val quality = tracker.quality.value
        val calibrate = prefs.settings.value.calibrateGps
        viewModelScope.launch {
            autoStartRoute()
            repo.saveReading(row, value, note)
            if (saveGps) {
                // Con la calibración encendida, el punto del medidor solo se guarda con el GPS en verde.
                val green = quality.light == com.lecturasgas.beta.location.Light.GREEN &&
                        System.currentTimeMillis() - quality.time <= com.lecturasgas.beta.location.GpsQualityRules.STALE_MS
                if (calibrate && !green) skipLocation(quality)
                else captureLocation(row, atTap)
            }
            afterAction(row, after)
            showMessage("Lectura $value guardada")
        }
    }

    /** Qué hacer con la pantalla después de guardar una lectura o un motivo: buscar otro, siguiente o cerrar. */
    private fun afterAction(row: Int, after: AfterSave) {
        when (after) {
            AfterSave.NEXT -> {
                val next = nextPendingAfter(row)
                _readingRow.value = next
                if (next == null) showMessage("¡Ruta completa! No quedan lecturas pendientes.")
            }
            AfterSave.SEARCH -> {
                _readingRow.value = null
                // Solo si estás en Lecturas: limpia el buscador y abre el teclado.
                if (currentPage == PAGE_READINGS) requestSearchFocus()
            }
            AfterSave.CLOSE -> _readingRow.value = null
        }
    }

    /** "No se pudo leer": guarda el motivo (casa cerrada, etc.) y sigue como después de una lectura. */
    fun saveSkip(row: Int, reason: String, note: String, after: AfterSave) {
        prefs.afterSave = after
        viewModelScope.launch {
            autoStartRoute()
            repo.setSkipReason(row, reason, note)
            afterAction(row, after)
            showMessage("Marcado: $reason")
        }
    }

    fun clearSkip(row: Int) {
        viewModelScope.launch {
            val note = repo.recordByRow(row)?.note ?: ""
            repo.setSkipReason(row, null, note)
            showMessage("Motivo quitado")
        }
    }

    /** Corrige el número del medidor SOLO en la app (el Excel no cambia). Vacío = quitar corrección. */
    fun setMeterFix(row: Int, realMeter: String?) {
        viewModelScope.launch {
            repo.setMeterFix(row, realMeter)
            val updated = repo.recordByRow(row)
            showMessage(
                if (updated?.hasMeterFix == true) "Medidor corregido a ${updated.realMeter}. El Excel no cambia."
                else "Corrección quitada"
            )
        }
    }

    /** Si el número real que escribes ya existe en OTRA fila del Excel, avisa (puede ser un error). */
    fun meterFixWarning(row: Int, text: String): String? {
        val key = TextUtil.meterKey(text)
        if (key.isEmpty()) return null
        val other = repo.records.value.firstOrNull { it.row != row && (it.meterKey == key || it.realMeterKey == key) }
            ?: return null
        return "Ojo: ya hay otro medidor ${other.displayMeter} (NIR ${other.nir}, ${other.user.ifBlank { "sin usuario" }})."
    }

    /**
     * Guarda (o mejora) la ubicación del medidor promediando las lecturas GPS de los últimos
     * segundos. Va en segundo plano para no hacerte esperar. Solo acepta puntos de ±20 m o
     * mejor: un punto malo es peor que ninguno.
     */
    /** Calibración encendida y GPS sin estar en verde: la lectura se guarda, el punto del medidor no. */
    private fun skipLocation(q: com.lecturasgas.beta.location.GpsQuality) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1_600L)   // deja ver primero "Lectura guardada"
            showMessage("Lectura guardada. El GPS no estaba en verde (${q.reason.lowercase()}): no guardé el punto del medidor.")
        }
    }

    private fun captureLocation(row: Int, atTap: GeoFix?) {
        viewModelScope.launch {
            val capture = tracker.captureBest(maxWaitMs = 6_000L, goodAccuracy = 10f, minSamples = 5)
            kotlinx.coroutines.delay(1_600L)   // deja ver primero "Lectura guardada"
            if (capture == null) {
                showMessage("Sin señal GPS: no se guardó la ubicación del medidor")
                return@launch
            }
            val acc = capture.fix.accuracy ?: 999f
            if (acc > SAVE_MAX_ACCURACY) {
                showMessage("GPS impreciso (±${acc.toInt()} m): no guardé la ubicación. Sal a cielo abierto o usa «Ubicar en mapa».")
                return@launch
            }
            val tap = atTap?.accuracy?.let { Spot(atTap.lat, atTap.lon, it) }
            val spot = LocationFusion.steady(tap, Spot(capture.fix.lat, capture.fix.lon, acc))
            if (spot == null) {
                showMessage("Te moviste mientras se medía el GPS: no guardé la ubicación (para no marcarla lejos).")
                return@launch
            }
            val before = repo.recordByRow(row)
            when (repo.setLocationIfBetter(row, spot.lat, spot.lon, spot.accuracy, "lectura")) {
                LecturasRepository.LocationUpdate.SAVED -> showMessage("Ubicación guardada (±${spot.accuracy.toInt()} m)")
                LecturasRepository.LocationUpdate.IMPROVED -> showMessage("Ubicación mejorada (±${spot.accuracy.toInt()} m)")
                LecturasRepository.LocationUpdate.CORRECTED -> {
                    val d = movedText(before, spot)
                    showMessage("Ubicación corregida$d")
                }
                LecturasRepository.LocationUpdate.CORRECTED_BY_NEIGHBORS -> {
                    val d = movedText(before, spot)
                    showMessage("Ubicación corregida$d · ahora coincide con los medidores vecinos")
                }
                LecturasRepository.LocationUpdate.PROPOSED ->
                    showMessage("El punto que pusiste a mano parece estar lejos. Te pregunto qué hacer.")
                LecturasRepository.LocationUpdate.WEAK ->
                    showMessage("GPS regular (±${spot.accuracy.toInt()} m): no cambié la ubicación guardada")
                LecturasRepository.LocationUpdate.KEPT -> Unit
            }
        }
    }

    private fun movedText(before: com.lecturasgas.beta.data.model.MeterRecord?, spot: Spot): String {
        val lat = before?.lat ?: return ""
        val lon = before.lon ?: return ""
        return " (estaba a ${com.lecturasgas.beta.util.Geo.formatDistance(com.lecturasgas.beta.util.Geo.distanceMeters(lat, lon, spot.lat, spot.lon))})"
    }

    fun clearReading(row: Int) {
        viewModelScope.launch {
            repo.clearReading(row)
            showMessage("Lectura de la app borrada")
        }
    }

    fun saveNote(row: Int, note: String) {
        viewModelScope.launch { repo.saveNote(row, note) }
    }

    /**
     * Siguiente pendiente por orden de NIR después de [row]. Los que ya tienen motivo
     * ("casa cerrada"...) se dejan para el final: primero van los que sí puedes leer.
     */
    fun nextPendingAfter(row: Int?): Int? {
        val ordered = RoutePlanner.byNir(repo.records.value)
        val start = if (row == null) -1 else ordered.indexOfFirst { it.row == row }
        for (includeSkipped in listOf(false, true)) {
            fun eligible(r: com.lecturasgas.beta.data.model.MeterRecord) =
                !r.isRead && (includeSkipped || r.skipReason == null)
            for (i in (start + 1) until ordered.size) if (eligible(ordered[i])) return ordered[i].row
            ordered.firstOrNull { eligible(it) }?.let { return it.row }
        }
        return null
    }

    // ---------------- Navegación entre módulos ----------------
    private val _pageRequest = MutableStateFlow<Int?>(null)
    val pageRequest: StateFlow<Int?> = _pageRequest.asStateFlow()
    fun goToPage(page: Int) { _pageRequest.value = page }
    fun consumePageRequest() { _pageRequest.value = null }

    /** Pedido al mapa: centrar un medidor o entrar en modo "ajustar ubicación". */
    data class MapRequest(val row: Int, val adjust: Boolean, val stamp: Long = System.nanoTime())

    private val _mapRequest = MutableStateFlow<MapRequest?>(null)
    val mapRequest: StateFlow<MapRequest?> = _mapRequest.asStateFlow()

    fun showOnMap(row: Int) {
        _mapRequest.value = MapRequest(row, adjust = false)
        goToPage(PAGE_MAP)
    }

    fun adjustOnMap(row: Int) {
        _mapRequest.value = MapRequest(row, adjust = true)
        goToPage(PAGE_MAP)
    }

    fun consumeMapRequest() { _mapRequest.value = null }

    // ---------------- Historial ----------------
    private val _history = MutableStateFlow<List<SessionSummary>?>(null)
    val history: StateFlow<List<SessionSummary>?> = _history.asStateFlow()

    fun openHistory() {
        viewModelScope.launch { _history.value = repo.sessionSummaries() }
    }

    fun closeHistory() { _history.value = null }

    suspend fun sessionDeliveries(id: Long): List<Delivery> = repo.sessionDeliveries(id)

    fun suggestedExportName(): String = repo.suggestedExportName()

    fun refreshLocation() = tracker.restart()

    companion object {
        /** Un medidor solo guarda su ubicación si el GPS promedió ±20 m o mejor. */
        const val SAVE_MAX_ACCURACY = 40f
        const val PAGE_DASHBOARD = 0
        const val PAGE_READINGS = 1
        const val PAGE_MAP = 2
        const val PAGE_DELIVERY = 3
    }
}
