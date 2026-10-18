package com.lecturasgas.beta.data.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.lecturasgas.beta.data.db.LocalStore
import com.lecturasgas.beta.data.repository.LecturasRepository
import com.lecturasgas.beta.data.repository.UiPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Respaldo y traspaso de TODO lo que la app guarda: lecturas, ubicaciones GPS, correcciones
 * de medidor, motivos, jornadas de entrega, recorridos y el Excel original de la ruta.
 *
 * Un respaldo es UN solo archivo ".lgbak" (por dentro es un zip):
 *   manifest.json     -> datos para mostrar y validar antes de restaurar
 *   lecturasgas.db    -> copia consistente de la base de datos
 *   routes/<archivo>  -> el Excel original (se necesita para exportar)
 *
 * Sirve para dos cosas: tener una copia de seguridad (automática) y pasar todo el trabajo
 * a otro celular (compartir el archivo y restaurarlo allá).
 */
class BackupManager(
    private val context: Context,
    private val store: LocalStore,
    private val prefs: UiPrefs
) {
    data class Info(
        val createdAt: Long,
        val appVersion: String,
        val schema: Int,
        val routeName: String,
        val meters: Int,
        val readings: Int,
        val locations: Int,
        val deliveries: Int
    )

    data class Status(
        val folderName: String?,
        val lastAutoAt: Long,
        val lastError: String?,
        val busy: Boolean
    )

    private val lock = Any()
    @Volatile private var restoring = false
    private var lastAutoRun = 0L

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<Status> = _status.asStateFlow()

    private fun readStatus(busy: Boolean = false) = Status(
        folderName = prefs.backupTree?.let { folderLabel(Uri.parse(it)) },
        lastAutoAt = prefs.lastAutoBackupAt,
        lastError = prefs.lastBackupError,
        busy = busy
    )

    private fun folderLabel(tree: Uri): String =
        runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifBlank { "Almacenamiento" } }
            .getOrDefault("Carpeta elegida")

    private fun dbFile(): File = context.getDatabasePath(LocalStore.DB_NAME)

    // ------------------------------------------------------------------
    // CREAR
    // ------------------------------------------------------------------

    /** Copia consistente de la base: VACUUM INTO (Android 11+) o, si no, vaciar el WAL y copiar. */
    private fun snapshotDb(): File {
        val tmp = File(context.cacheDir, "backup_db.tmp")
        tmp.delete()
        val db = store.writableDatabase
        var done = false
        if (Build.VERSION.SDK_INT >= 30) {
            done = runCatching {
                db.execSQL("VACUUM INTO '${tmp.absolutePath.replace("'", "''")}'")
                tmp.length() > 0
            }.getOrDefault(false)
        }
        if (!done) {
            tmp.delete()
            runCatching { db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() } }
            dbFile().inputStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        }
        return tmp
    }

    private fun count(db: SQLiteDatabase, sql: String): Int =
        runCatching { db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 } }.getOrDefault(0)

    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    /** Escribe el respaldo completo en [out]. Devuelve lo que contiene. */
    fun write(out: OutputStream): Info = synchronized(lock) {
        val snap = snapshotDb()
        try {
            val db = store.readableDatabase
            val routePaths = runCatching {
                db.rawQuery("SELECT original_path FROM route", null).use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                }
            }.getOrDefault(emptyList())
            val routeName = runCatching {
                db.rawQuery("SELECT name FROM route ORDER BY active DESC, id DESC LIMIT 1", null).use { if (it.moveToFirst()) it.getString(0) else "" }
            }.getOrDefault("")
            val info = Info(
                createdAt = System.currentTimeMillis(),
                appVersion = appVersion(),
                schema = LocalStore.VERSION,
                routeName = routeName,
                meters = count(db, "SELECT COUNT(*) FROM meter"),
                readings = count(db, "SELECT COUNT(*) FROM reading WHERE value IS NOT NULL"),
                locations = count(db, "SELECT COUNT(*) FROM location"),
                deliveries = count(db, "SELECT COUNT(*) FROM delivery")
            )
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifestJson(info).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("lecturasgas.db"))
                snap.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()

                for (path in routePaths.distinct()) {
                    val f = File(path)
                    if (!f.exists()) continue
                    zip.putNextEntry(ZipEntry("routes/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            info
        } finally {
            snap.delete()
        }
    }

    private fun manifestJson(i: Info) = JSONObject()
        .put("format", FORMAT)
        .put("createdAt", i.createdAt)
        .put("appVersion", i.appVersion)
        .put("schema", i.schema)
        .put("routeName", i.routeName)
        .put("meters", i.meters)
        .put("readings", i.readings)
        .put("locations", i.locations)
        .put("deliveries", i.deliveries)
        .toString()

    private fun parseManifest(text: String): Info {
        val j = JSONObject(text)
        require(j.optInt("format", 0) == FORMAT) { "Este archivo no es un respaldo de Lecturas Gas." }
        return Info(
            createdAt = j.optLong("createdAt"),
            appVersion = j.optString("appVersion"),
            schema = j.optInt("schema"),
            routeName = j.optString("routeName"),
            meters = j.optInt("meters"),
            readings = j.optInt("readings"),
            locations = j.optInt("locations"),
            deliveries = j.optInt("deliveries")
        )
    }

    fun writeTo(uri: Uri): Info {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("No se pudo escribir el archivo.")
        return write(out)
    }

    /** Crea el archivo temporal que se envía por WhatsApp, correo, Drive, etc. */
    fun createShareIntent(): Intent {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, suggestedName())
        file.outputStream().use { write(it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Respaldo Lecturas Gas")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun suggestedName(): String =
        "LecturasGas_respaldo_" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date()) + ".lgbak"

    // ------------------------------------------------------------------
    // RESTAURAR
    // ------------------------------------------------------------------

    private class Unpacked(val dir: File, val info: Info, val db: File, val routes: List<File>)

    private fun unpack(input: InputStream): Unpacked {
        val dir = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val routesDir = File(dir, "routes").apply { mkdirs() }
        var manifest: String? = null
        var db: File? = null
        val routes = mutableListOf<File>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.name == "manifest.json" -> manifest = zip.readBytes().toString(Charsets.UTF_8)
                    e.name == "lecturasgas.db" -> {
                        val f = File(dir, "lecturasgas.db")
                        f.outputStream().use { zip.copyTo(it) }
                        db = f
                    }
                    e.name.startsWith("routes/") && !e.isDirectory -> {
                        // Solo el nombre del archivo: evita rutas raras dentro del zip.
                        val f = File(routesDir, File(e.name).name)
                        f.outputStream().use { zip.copyTo(it) }
                        routes += f
                    }
                }
                zip.closeEntry()
            }
        }
        val m = requireNotNull(manifest) { "Este archivo no es un respaldo de Lecturas Gas." }
        val dbFile = requireNotNull(db) { "El respaldo está incompleto (no trae la base de datos)." }
        val info = parseManifest(m)
        require(info.schema <= LocalStore.VERSION) {
            "Este respaldo es de una versión más nueva de la app. Actualiza Lecturas Gas y vuelve a intentarlo."
        }
        validateDb(dbFile)
        return Unpacked(dir, info, dbFile, routes)
    }

    private fun validateDb(file: File) {
        val header = ByteArray(16)
        file.inputStream().use { it.read(header) }
        require(String(header, Charsets.ISO_8859_1).startsWith("SQLite format 3")) { "El respaldo está dañado." }
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val ok = db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
            require(ok) { "El respaldo está dañado." }
            val tables = db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('route','meter','reading','location')", null
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            require(tables == 4) { "El respaldo no tiene los datos esperados." }
        } finally {
            db.close()
        }
    }

    /** Lee el respaldo SIN restaurarlo (para mostrar qué contiene y pedir confirmación). */
    fun inspect(uri: Uri): Info {
        val input = context.contentResolver.openInputStream(uri) ?: error("No se pudo abrir el archivo.")
        val u = input.use { unpack(it) }
        u.dir.deleteRecursively()
        return u.info
    }

    /**
     * Reemplaza TODOS los datos de la app por los del respaldo. Antes guarda una copia de lo
     * que había (por si te arrepientes). Después hay que reiniciar la app.
     */
    fun restore(uri: Uri): Info = synchronized(lock) {
        val input = context.contentResolver.openInputStream(uri) ?: error("No se pudo abrir el archivo.")
        val u = input.use { unpack(it) }
        try {
            restoring = true

            // Copia de seguridad de lo actual, por si algo sale mal o te arrepientes.
            runCatching {
                val dir = File(context.filesDir, "respaldos").apply { mkdirs() }
                File(dir, "antes_de_restaurar.lgbak").outputStream().use { write(it) }
            }

            store.close()
            val target = dbFile()
            listOf("", "-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
            u.db.inputStream().use { i -> target.outputStream().use { o -> i.copyTo(o) } }

            val routesDir = File(context.filesDir, "routes").apply { mkdirs() }
            routesDir.listFiles()?.forEach { it.delete() }
            u.routes.forEach { it.copyTo(File(routesDir, it.name), overwrite = true) }

            // El Excel original ahora vive en otra carpeta (otro celular): se corrige la ruta guardada.
            val db = SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                val rows = db.rawQuery("SELECT id, original_path FROM route", null).use { c ->
                    buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
                }
                for ((id, path) in rows) {
                    val name = File(path).name
                    db.execSQL("UPDATE route SET original_path = ? WHERE id = ?", arrayOf(File(routesDir, name).absolutePath, id))
                }
            } finally {
                db.close()
            }
            u.info
        } catch (e: Exception) {
            restoring = false
            throw e
        } finally {
            u.dir.deleteRecursively()
        }
    }

    /** Vuelve a abrir la app desde cero (necesario después de restaurar). */
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    // ------------------------------------------------------------------
    // AUTOMÁTICO
    // ------------------------------------------------------------------

    /** Elige (o quita con null) la carpeta donde se copian los respaldos automáticos. */
    fun setFolder(uri: Uri?) {
        prefs.backupTree?.let { old ->
            if (uri == null || old != uri.toString()) {
                runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), FLAGS) }
            }
        }
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, FLAGS) }
        }
        prefs.backupTree = uri?.toString()
        prefs.lastBackupError = null
        _status.value = readStatus()
    }

    /** Se activa una vez al abrir la app: cada vez que cambian lecturas/GPS/entregas, guarda un respaldo. */
    @OptIn(FlowPreview::class)
    fun startAuto(scope: CoroutineScope, repo: LecturasRepository) {
        scope.launch {
            merge(repo.records, repo.deliveries).debounce(AUTO_DELAY_MS).collect {
                runAuto(repo)
            }
        }
    }

    /** Respaldo inmediato (también se usa desde el botón "Respaldar ahora"). */
    fun runAuto(repo: LecturasRepository, force: Boolean = false) {
        if (restoring || repo.route.value == null) return
        val now = System.currentTimeMillis()
        if (!force && now - lastAutoRun < MIN_GAP_MS) return
        lastAutoRun = now
        _status.value = readStatus(busy = true)
        var error: String? = null
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

        // 1) Copia interna (rápida; se pierde si desinstalas la app).
        runCatching {
            val dir = File(context.filesDir, "respaldos").apply { mkdirs() }
            File(dir, "auto_$day.lgbak").outputStream().use { write(it) }
            dir.listFiles { f -> f.name.startsWith("auto_") }
                ?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        }.onFailure { error = "Copia interna: ${it.message}" }

        // 2) Copia en la carpeta elegida (sobrevive a desinstalar o perder el celular si es Drive/SD).
        prefs.backupTree?.let { tree ->
            runCatching { writeToFolder(Uri.parse(tree), "LecturasGas_respaldo_$day.lgbak") }
                .onFailure { error = "No se pudo escribir en la carpeta elegida (${it.message}). Elígela de nuevo." }
        }

        if (error == null) prefs.lastAutoBackupAt = now
        prefs.lastBackupError = error
        _status.value = readStatus()
    }

    private fun writeToFolder(tree: Uri, fileName: String) {
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)

        val existing = mutableListOf<Pair<String, String>>()   // (id, nombre)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c -> while (c.moveToNext()) existing += c.getString(0) to c.getString(1) }

        val same = existing.firstOrNull { it.second == fileName }
        val target = if (same != null) DocumentsContract.buildDocumentUriUsingTree(tree, same.first)
        else DocumentsContract.createDocument(resolver, parent, "application/octet-stream", fileName)
            ?: error("no se pudo crear el archivo")
        val out = resolver.openOutputStream(target, "wt") ?: error("no se pudo abrir el archivo")
        write(out)

        // Deja solo los últimos días.
        existing.filter { it.second.startsWith("LecturasGas_respaldo_") && it.second.endsWith(".lgbak") }
            .sortedByDescending { it.second }
            .drop(KEEP)
            .forEach { runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it.first)) } }
    }

    private companion object {
        const val FORMAT = 1
        const val AUTO_DELAY_MS = 20_000L        // espera a que termines de escribir antes de respaldar
        const val MIN_GAP_MS = 60_000L
        const val KEEP = 7                       // se guardan los últimos 7 días
        const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
