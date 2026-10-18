package com.lecturasgas.beta.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lecturasgas.beta.data.excel.ExcelImporter
import com.lecturasgas.beta.data.excel.ImportDiff
import com.lecturasgas.beta.data.excel.ImportDiffer
import com.lecturasgas.beta.data.excel.NewMeter
import com.lecturasgas.beta.data.excel.OldMeter
import com.lecturasgas.beta.data.model.Delivery
import com.lecturasgas.beta.data.model.DeliverySession
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.data.model.RouteInfo
import com.lecturasgas.beta.data.model.RouteSummary
import com.lecturasgas.beta.data.model.SessionSummary
import com.lecturasgas.beta.data.model.TrackPoint
import com.lecturasgas.beta.util.TextUtil

/**
 * Base de datos local (SQLite que trae Android, sin librerías extra).
 *
 * Antes todo se guardaba como un JSON gigante en SharedPreferences y se
 * reescribía completo en cada lectura: lento y con riesgo de perder datos.
 * Ahora cada lectura / entrega / punto GPS es una fila independiente.
 *
 * Tablas:
 *   route          -> cada Excel importado
 *   meter          -> filas del Excel de la ruta actual
 *   reading        -> lecturas tomadas con la app (+ nota + motivo si no se pudo leer)
 *   meter_fix      -> número REAL de medidores mal codificados en el Excel (solo en la app)
 *   location       -> GPS de cada medidor (se conserva entre meses)
 *   delivery_session, delivery, track_point -> jornadas de entrega
 */
class LocalStore(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE route(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL, period TEXT NOT NULL, file_name TEXT NOT NULL,
                imported_at INTEGER NOT NULL, reading_col TEXT NOT NULL,
                original_path TEXT NOT NULL, last_export_at INTEGER,
                route_key TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE meter(
                route_id INTEGER NOT NULL, row_num INTEGER NOT NULL,
                nir TEXT NOT NULL, nir_num INTEGER, address TEXT NOT NULL,
                meter TEXT NOT NULL, meter_key TEXT NOT NULL, previous INTEGER,
                barrio TEXT NOT NULL, user_name TEXT NOT NULL, observation TEXT NOT NULL,
                excel_reading INTEGER,
                is_new INTEGER NOT NULL DEFAULT 0, prev_nir TEXT,
                PRIMARY KEY(route_id, row_num))"""
        )
        db.execSQL(
            """CREATE TABLE reading(
                route_id INTEGER NOT NULL, row_num INTEGER NOT NULL,
                value INTEGER, taken_at INTEGER, first_at INTEGER, note TEXT NOT NULL DEFAULT '',
                skip_reason TEXT, skip_at INTEGER,
                PRIMARY KEY(route_id, row_num))"""
        )
        createMeterFix(db)
        createLocSample(db)
        createHistory(db)
        db.execSQL(
            """CREATE TABLE location(
                meter_key TEXT PRIMARY KEY, nir TEXT NOT NULL,
                lat REAL NOT NULL, lon REAL NOT NULL, accuracy REAL,
                source TEXT NOT NULL, updated_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE delivery_session(
                id INTEGER PRIMARY KEY AUTOINCREMENT, route_id INTEGER NOT NULL,
                started_at INTEGER NOT NULL, ended_at INTEGER,
                distance_m REAL NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE delivery(
                id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL,
                route_id INTEGER NOT NULL, row_num INTEGER NOT NULL, meter_key TEXT NOT NULL,
                nir TEXT NOT NULL, meter TEXT NOT NULL, user_name TEXT NOT NULL,
                address TEXT NOT NULL, barrio TEXT NOT NULL, delivered_at INTEGER NOT NULL,
                lat REAL, lon REAL, accuracy REAL, ord INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT '', bulk INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE track_point(
                id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL,
                lat REAL NOT NULL, lon REAL NOT NULL, accuracy REAL, time INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_delivery_route ON delivery(route_id)")
        db.execSQL("CREATE INDEX idx_delivery_key ON delivery(meter_key)")
        db.execSQL("CREATE INDEX idx_track_session ON track_point(session_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migraciones: siempre AGREGAR, nunca borrar tablas (para no perder lecturas ni GPS).
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE reading ADD COLUMN skip_reason TEXT")
            db.execSQL("ALTER TABLE reading ADD COLUMN skip_at INTEGER")
            createMeterFix(db)
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE delivery ADD COLUMN note TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 4) migrateToV4(db)
        if (oldVersion < 5) {
            if (!hasColumn(db, "delivery", "bulk")) db.execSQL("ALTER TABLE delivery ADD COLUMN bulk INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 6) {
            createLocSample(db)
            // Cada punto que ya tenías cuenta como UNA medición (menos los puestos a mano en el mapa).
            db.execSQL(
                """INSERT INTO loc_sample(meter_key, lat, lon, accuracy, source, taken_at)
                   SELECT meter_key, lat, lon, MAX(COALESCE(accuracy, 30.0), 8.0), source, updated_at
                   FROM location WHERE source <> 'manual'"""
            )
        }
        if (oldVersion < 7) createHistory(db)
        if (oldVersion < 8) {
            // Cuándo se tomó POR PRIMERA VEZ cada lectura (taken_at cambia si luego la corriges): sirve para el orden de lectura.
            if (!hasColumn(db, "reading", "first_at")) db.execSQL("ALTER TABLE reading ADD COLUMN first_at INTEGER")
            db.execSQL("UPDATE reading SET first_at = taken_at WHERE value IS NOT NULL AND first_at IS NULL")
        }
    }

    /**
     * v7: historial de lecturas por medidor.
     *  - meter_history: la lectura de cada medidor en meses anteriores (se guarda al importar el Excel del mes siguiente
     *    o al borrar una ruta), para ver cómo ha ido su consumo.
     *  - reading_log: cada lectura que se guarda, se corrige o se borra en la app (con su hora).
     */
    private fun createHistory(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS meter_history(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                meter_key TEXT NOT NULL, route_key TEXT NOT NULL, period TEXT NOT NULL,
                reading INTEGER NOT NULL, previous INTEGER, taken_at INTEGER, source TEXT NOT NULL)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meter_history_key ON meter_history(meter_key)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS reading_log(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                meter_key TEXT NOT NULL, route_key TEXT NOT NULL, period TEXT NOT NULL,
                value INTEGER, at INTEGER NOT NULL, kind TEXT NOT NULL)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reading_log_key ON reading_log(meter_key)")
    }

    /** v6: historial de mediciones GPS por medidor (cada visita guarda una), para recalcular el punto con todas. */
    private fun createLocSample(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS loc_sample(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                meter_key TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL,
                accuracy REAL NOT NULL, source TEXT NOT NULL, taken_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_loc_sample_key ON loc_sample(meter_key)")
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val idx = c.getColumnIndex("name")
            var found = false
            while (c.moveToNext()) if (c.getString(idx).equals(column, ignoreCase = true)) found = true
            found
        }

    /**
     * v4: varias rutas guardadas a la vez (A, B, C), marcas de medidor nuevo / NIR anterior, y las
     * correcciones de número de medidor pasan a guardarse solo por medidor (antes también por NIR,
     * y se perdían cuando el NIR se corría).
     */
    private fun migrateToV4(db: SQLiteDatabase) {
        if (!hasColumn(db, "route", "route_key")) db.execSQL("ALTER TABLE route ADD COLUMN route_key TEXT NOT NULL DEFAULT ''")
        if (!hasColumn(db, "route", "active")) db.execSQL("ALTER TABLE route ADD COLUMN active INTEGER NOT NULL DEFAULT 0")
        if (!hasColumn(db, "meter", "is_new")) db.execSQL("ALTER TABLE meter ADD COLUMN is_new INTEGER NOT NULL DEFAULT 0")
        if (!hasColumn(db, "meter", "prev_nir")) db.execSQL("ALTER TABLE meter ADD COLUMN prev_nir TEXT")
        val ids = ArrayList<Pair<Long, String>>()
        db.rawQuery("SELECT id, name FROM route", null).use { c -> while (c.moveToNext()) ids.add(c.getLong(0) to c.getString(1)) }
        for ((id, name) in ids) {
            db.execSQL("UPDATE route SET route_key=? WHERE id=?", arrayOf<Any>(ExcelImporter.routeKeyOf(name), id))
        }
        db.execSQL("UPDATE route SET active=0")
        db.execSQL("UPDATE route SET active=1 WHERE id=(SELECT MAX(id) FROM route)")
        if (hasColumn(db, "meter_fix", "nir")) {
            db.execSQL(
                """CREATE TABLE meter_fix_v4(
                    meter_key TEXT PRIMARY KEY, real_meter TEXT NOT NULL, updated_at INTEGER NOT NULL)"""
            )
            // Si hubiera dos correcciones del mismo medidor, gana la más reciente.
            db.execSQL(
                "INSERT OR REPLACE INTO meter_fix_v4(meter_key, real_meter, updated_at) " +
                        "SELECT meter_key, real_meter, updated_at FROM meter_fix ORDER BY updated_at ASC"
            )
            db.execSQL("DROP TABLE meter_fix")
            db.execSQL("ALTER TABLE meter_fix_v4 RENAME TO meter_fix")
        }
    }

    /**
     * Medidores mal codificados en el Excel. Se identifican por (medidor del Excel + NIR) y
     * se conservan entre meses: si el mes siguiente el Excel sigue con el mismo error,
     * la corrección se vuelve a aplicar sola. El Excel NUNCA se modifica.
     */
    private fun createMeterFix(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS meter_fix(
                meter_key TEXT PRIMARY KEY,
                real_meter TEXT NOT NULL, updated_at INTEGER NOT NULL)"""
        )
    }

    // ------------------------------------------------------------------
    // RUTA
    // ------------------------------------------------------------------

    private fun Cursor.routeInfo() = RouteInfo(
        id = long("id"),
        name = str("name"),
        period = str("period"),
        fileName = str("file_name"),
        importedAt = long("imported_at"),
        readingColumn = str("reading_col"),
        originalPath = str("original_path"),
        lastExportAt = longOrNull("last_export_at"),
        routeKey = str("route_key")
    )

    /** La ruta con la que estás trabajando (la marcada como activa; si no hay, la última con medidores). */
    fun currentRoute(): RouteInfo? =
        readableDatabase.rawQuery(
            """SELECT * FROM route WHERE EXISTS (SELECT 1 FROM meter m WHERE m.route_id = route.id)
               ORDER BY active DESC, id DESC LIMIT 1""", null
        ).use { c -> if (c.moveToFirst()) c.routeInfo() else null }

    /** La ruta guardada con esa identidad ("RUTA A"), si existe. */
    fun routeByKey(routeKey: String): RouteInfo? =
        readableDatabase.rawQuery(
            """SELECT * FROM route WHERE UPPER(route_key) = UPPER(?)
               AND EXISTS (SELECT 1 FROM meter m WHERE m.route_id = route.id)
               ORDER BY id DESC LIMIT 1""", arrayOf(routeKey)
        ).use { c -> if (c.moveToFirst()) c.routeInfo() else null }

    /** Todas las rutas guardadas (una por identidad, la importación más reciente) con su avance. */
    fun listRoutes(): List<RouteSummary> =
        readableDatabase.rawQuery(
            """SELECT r.id, r.name, r.route_key, r.period, r.active,
                      (SELECT COUNT(*) FROM meter m WHERE m.route_id = r.id) AS total,
                      (SELECT COUNT(*) FROM meter m
                         LEFT JOIN reading x ON x.route_id = m.route_id AND x.row_num = m.row_num
                         WHERE m.route_id = r.id AND (x.value IS NOT NULL OR m.excel_reading IS NOT NULL)) AS done,
                      (SELECT COUNT(*) FROM reading x WHERE x.route_id = r.id AND x.value IS NOT NULL) AS app_reads,
                      (SELECT COUNT(*) FROM reading x WHERE x.route_id = r.id AND x.value IS NOT NULL
                         AND COALESCE(x.taken_at, 0) > COALESCE(r.last_export_at, 0)) AS unexported,
                      (SELECT COUNT(*) FROM delivery d WHERE d.route_id = r.id) AS deliveries,
                      r.imported_at, r.last_export_at
               FROM route r
               WHERE r.id IN (SELECT MAX(r2.id) FROM route r2
                              WHERE EXISTS (SELECT 1 FROM meter m WHERE m.route_id = r2.id)
                              GROUP BY UPPER(r2.route_key))
               ORDER BY r.active DESC, r.name""", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    RouteSummary(
                        id = c.getLong(0), name = c.getString(1), routeKey = c.getString(2),
                        period = c.getString(3), active = c.getInt(4) == 1,
                        total = c.getInt(5), read = c.getInt(6),
                        appReadings = c.getInt(7), unexported = c.getInt(8), deliveries = c.getInt(9),
                        importedAt = c.getLong(10), lastExportAt = if (c.isNull(11)) null else c.getLong(11)
                    )
                )
            }
        }

    fun routeById(routeId: Long): RouteInfo? =
        readableDatabase.rawQuery("SELECT * FROM route WHERE id=?", arrayOf(routeId.toString()))
            .use { c -> if (c.moveToFirst()) c.routeInfo() else null }

    /**
     * Borra una ruta guardada con todo lo suyo: medidores, lecturas, entregas, jornadas y recorridos.
     * Los puntos GPS de los medidores se conservan (son de los medidores, no de la ruta).
     * Con [keepHistory] guarda antes la lectura de cada medidor en su historial.
     */
    fun deleteRoute(routeId: Long, keepHistory: Boolean) {
        val db = writableDatabase
        val info = routeById(routeId) ?: return
        db.beginTransaction()
        try {
            if (keepHistory) snapshotPeriod(db, info)
            val id = routeId.toString()
            db.execSQL("DELETE FROM track_point WHERE session_id IN (SELECT id FROM delivery_session WHERE route_id=?)", arrayOf<Any>(routeId))
            db.delete("delivery", "route_id=?", arrayOf(id))
            db.delete("delivery_session", "route_id=?", arrayOf(id))
            db.delete("reading", "route_id=?", arrayOf(id))
            db.delete("meter", "route_id=?", arrayOf(id))
            db.delete("route", "id=?", arrayOf(id))
            // Si era la ruta activa, pasa a ser activa la más reciente que quede.
            val anyActive = db.rawQuery("SELECT COUNT(*) FROM route WHERE active=1", null).use { it.moveToFirst() && it.getInt(0) > 0 }
            if (!anyActive) {
                db.execSQL(
                    """UPDATE route SET active=1 WHERE id=(SELECT id FROM route r WHERE EXISTS
                       (SELECT 1 FROM meter m WHERE m.route_id = r.id) ORDER BY id DESC LIMIT 1)"""
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ------------------------------------------------------------------
    // HISTORIAL POR MEDIDOR
    // ------------------------------------------------------------------

    /** Nombre del periodo de una ruta para el historial («2025 septiembre»; si el Excel no lo trae, la fecha de importación). */
    private fun periodLabel(info: RouteInfo): String =
        info.period.ifBlank {
            "Excel del " + java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("es", "CO")).format(java.util.Date(info.importedAt))
        }

    /** Guarda en el historial la lectura (la de la app, o la que traía el Excel) de cada medidor de la ruta. */
    private fun snapshotPeriod(db: SQLiteDatabase, info: RouteInfo) {
        val period = periodLabel(info)
        db.execSQL("DELETE FROM meter_history WHERE route_key=? AND period=?", arrayOf<Any>(info.routeKey, period))
        db.execSQL(
            """INSERT INTO meter_history(meter_key, route_key, period, reading, previous, taken_at, source)
               SELECT m.meter_key, ?, ?, COALESCE(r.value, m.excel_reading), m.previous, COALESCE(r.first_at, r.taken_at),
                      CASE WHEN r.value IS NOT NULL THEN 'app' ELSE 'excel' END
               FROM meter m LEFT JOIN reading r ON r.route_id = m.route_id AND r.row_num = m.row_num
               WHERE m.route_id = ? AND COALESCE(r.value, m.excel_reading) IS NOT NULL""",
            arrayOf<Any>(info.routeKey, period, info.id)
        )
    }

    /** Anota un cambio de lectura (nueva, corregida o borrada) para poder ver su historial. */
    fun logReading(meterKey: String, route: RouteInfo, value: Long?, at: Long, kind: String) {
        writableDatabase.insert("reading_log", null, ContentValues().apply {
            put("meter_key", meterKey)
            put("route_key", route.routeKey)
            put("period", periodLabel(route))
            if (value == null) putNull("value") else put("value", value)
            put("at", at)
            put("kind", kind)
        })
    }

    /** Lecturas de meses anteriores de este medidor, la más reciente primero. */
    fun meterSnapshots(meterKey: String): List<com.lecturasgas.beta.review.MeterHistory.Snapshot> =
        readableDatabase.rawQuery(
            "SELECT period, reading, previous, taken_at, source FROM meter_history WHERE meter_key=? ORDER BY id DESC LIMIT 36",
            arrayOf(meterKey)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    com.lecturasgas.beta.review.MeterHistory.Snapshot(
                        c.getString(0), c.getLong(1), if (c.isNull(2)) null else c.getLong(2),
                        if (c.isNull(3)) null else c.getLong(3), c.getString(4) == "app"
                    )
                )
            }
        }

    /** Cambios de lectura de este medidor en la app, el más reciente primero. */
    fun readingLog(meterKey: String): List<com.lecturasgas.beta.review.MeterHistory.LogEntry> =
        readableDatabase.rawQuery(
            "SELECT at, value, kind FROM reading_log WHERE meter_key=? ORDER BY id DESC LIMIT 40", arrayOf(meterKey)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    com.lecturasgas.beta.review.MeterHistory.LogEntry(c.getLong(0), if (c.isNull(1)) null else c.getLong(1), c.getString(2))
                )
            }
        }

    fun setActiveRoute(routeId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE route SET active=0")
            db.execSQL("UPDATE route SET active=1 WHERE id=?", arrayOf<Any>(routeId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Los medidores de una ruta, tal como están ahora, para compararlos con un Excel nuevo. */
    fun metersForDiff(routeId: Long): List<OldMeter> =
        readableDatabase.rawQuery(
            "SELECT meter_key, nir, user_name, barrio, is_new, prev_nir FROM meter WHERE route_id=? ORDER BY row_num",
            arrayOf(routeId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    OldMeter(
                        key = c.getString(0), nir = c.getString(1), user = c.getString(2), barrio = c.getString(3),
                        isNew = c.getInt(4) == 1, prevNir = if (c.isNull(5)) null else c.getString(5)
                    )
                )
            }
        }

    /** Excels originales que siguen en uso (los demás archivos de la carpeta se pueden borrar). */
    fun liveOriginalPaths(): Set<String> =
        readableDatabase.rawQuery(
            "SELECT original_path FROM route WHERE EXISTS (SELECT 1 FROM meter m WHERE m.route_id = route.id)", null
        ).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }

    fun routeName(routeId: Long): String =
        readableDatabase.rawQuery("SELECT name FROM route WHERE id=?", arrayOf(routeId.toString())).use {
            if (it.moveToFirst()) it.getString(0) else ""
        }

    fun markExported(routeId: Long, time: Long) {
        writableDatabase.update(
            "route", ContentValues().apply { put("last_export_at", time) },
            "id=?", arrayOf(routeId.toString())
        )
    }

    /** Misma ruta y mismo mes que el Excel nuevo (se vuelve a importar: no se pierde nada). */
    fun isSamePeriod(previous: RouteInfo?, period: String): Boolean =
        previous != null && previous.period.isNotBlank() && previous.period.equals(period, ignoreCase = true)

    /**
     * Guarda un Excel como ruta activa.
     *
     *  - Si ya tienes una ruta con la misma identidad ([routeKey], por ejemplo "RUTA A"), el Excel
     *    nuevo la ACTUALIZA: se compara por número de medidor para marcar los nuevos y los que
     *    cambiaron de NIR. Si es el mismo mes, las lecturas y entregas de la app pasan a la ruta nueva.
     *  - Si es una ruta distinta (la B), se guarda APARTE y la anterior queda intacta.
     * El GPS de los medidores siempre se conserva (se guarda por número de medidor).
     */
    fun replaceRoute(
        name: String,
        routeKey: String,
        fileName: String,
        originalPath: String,
        parsed: ExcelImporter.Parsed,
        now: Long
    ): ImportOutcome {
        val db = writableDatabase
        val previous = routeByKey(routeKey)
        val samePeriod = isSamePeriod(previous, parsed.period)
        val diff = ImportDiffer.compute(
            previous?.let { metersForDiff(it.id) },
            parsed.rows.map { NewMeter(TextUtil.meterKey(it.meter), it.nir, it.barrio) },
            samePeriod
        )
        db.beginTransaction()
        try {
            val routeId = db.insertOrThrow("route", null, ContentValues().apply {
                put("name", name)
                put("route_key", routeKey)
                put("period", parsed.period)
                put("file_name", fileName)
                put("imported_at", now)
                put("reading_col", parsed.columns.reading)
                put("original_path", originalPath)
                put("active", 0)
            })

            val insert = db.compileStatement(
                "INSERT INTO meter(route_id,row_num,nir,nir_num,address,meter,meter_key,previous," +
                        "barrio,user_name,observation,excel_reading,is_new,prev_nir) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            )
            for ((i, r) in parsed.rows.withIndex()) {
                val flag = diff.flags[i]
                insert.clearBindings()
                insert.bindLong(1, routeId)
                insert.bindLong(2, r.row.toLong())
                insert.bindString(3, r.nir)
                TextUtil.nirNumber(r.nir)?.let { insert.bindLong(4, it) } ?: insert.bindNull(4)
                insert.bindString(5, r.address)
                insert.bindString(6, r.meter)
                insert.bindString(7, TextUtil.meterKey(r.meter))
                r.previous?.let { insert.bindLong(8, it) } ?: insert.bindNull(8)
                insert.bindString(9, r.barrio)
                insert.bindString(10, r.user)
                insert.bindString(11, r.observation)
                r.reading?.let { insert.bindLong(12, it) } ?: insert.bindNull(12)
                insert.bindLong(13, if (flag.isNew) 1L else 0L)
                flag.prevNir?.let { insert.bindString(14, it) } ?: insert.bindNull(14)
                insert.executeInsert()
            }
            insert.close()

            if (previous != null) {
                if (samePeriod) carryOver(db, previous.id, routeId)
                else snapshotPeriod(db, previous)   // el mes que termina pasa al historial de cada medidor
                // De la misma ruta solo se guarda el detalle de la importación actual.
                db.delete("meter", "route_id=?", arrayOf(previous.id.toString()))
                db.delete("reading", "route_id=?", arrayOf(previous.id.toString()))
            }
            db.execSQL("UPDATE route SET active=0")
            db.execSQL("UPDATE route SET active=1 WHERE id=?", arrayOf<Any>(routeId))
            db.setTransactionSuccessful()
            return ImportOutcome(routeId, previous, samePeriod, diff)
        } finally {
            db.endTransaction()
        }
    }

    /** Resultado de guardar un Excel: qué ruta quedó y qué cambió respecto a la anterior. */
    data class ImportOutcome(
        val routeId: Long,
        /** La ruta de la que venía (null si es una ruta nueva en la app). */
        val previous: RouteInfo?,
        val samePeriod: Boolean,
        val diff: ImportDiff
    )

    /** Pasa lecturas y entregas a la ruta nueva, emparejando por clave de medidor única. */
    private fun carryOver(db: SQLiteDatabase, oldRoute: Long, newRoute: Long) {
        val newRowsByKey = HashMap<String, MutableList<Int>>()
        db.rawQuery("SELECT row_num, meter_key FROM meter WHERE route_id=?", arrayOf(newRoute.toString())).use { c ->
            while (c.moveToNext()) newRowsByKey.getOrPut(c.getString(1)) { mutableListOf() }.add(c.getInt(0))
        }
        val oldKeyByRow = HashMap<Int, String>()
        db.rawQuery("SELECT row_num, meter_key FROM meter WHERE route_id=?", arrayOf(oldRoute.toString())).use { c ->
            while (c.moveToNext()) oldKeyByRow[c.getInt(0)] = c.getString(1)
        }
        fun newRowFor(oldRow: Int): Int? {
            val key = oldKeyByRow[oldRow] ?: return null
            val list = newRowsByKey[key] ?: return null
            return if (list.size == 1) list[0] else if (list.contains(oldRow)) oldRow else null
        }

        db.rawQuery(
            "SELECT row_num, value, taken_at, note, skip_reason, skip_at, first_at FROM reading WHERE route_id=?",
            arrayOf(oldRoute.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val target = newRowFor(c.getInt(0)) ?: continue
                db.insertWithOnConflict("reading", null, ContentValues().apply {
                    put("route_id", newRoute)
                    put("row_num", target)
                    if (c.isNull(1)) putNull("value") else put("value", c.getLong(1))
                    if (c.isNull(2)) putNull("taken_at") else put("taken_at", c.getLong(2))
                    put("note", c.getString(3) ?: "")
                    if (c.isNull(4)) putNull("skip_reason") else put("skip_reason", c.getString(4))
                    if (c.isNull(5)) putNull("skip_at") else put("skip_at", c.getLong(5))
                    if (c.isNull(6)) putNull("first_at") else put("first_at", c.getLong(6))
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }

        val moves = mutableListOf<Pair<Long, Int>>()
        db.rawQuery("SELECT id, row_num FROM delivery WHERE route_id=?", arrayOf(oldRoute.toString())).use { c ->
            while (c.moveToNext()) {
                val target = newRowFor(c.getInt(1)) ?: continue
                moves.add(c.getLong(0) to target)
            }
        }
        for ((id, row) in moves) {
            db.update("delivery", ContentValues().apply {
                put("route_id", newRoute); put("row_num", row)
            }, "id=?", arrayOf(id.toString()))
        }
        db.update(
            "delivery_session", ContentValues().apply { put("route_id", newRoute) },
            "route_id=?", arrayOf(oldRoute.toString())
        )
    }

    /** Todos los medidores de la ruta con su lectura de la app y su GPS (una sola consulta). */
    fun loadRecords(routeId: Long): List<MeterRecord> {
        val sql = """
            SELECT m.row_num, m.nir, m.nir_num, m.address, m.meter, m.meter_key, m.previous,
                   m.barrio, m.user_name, m.observation, m.excel_reading,
                   r.value, r.taken_at, r.note,
                   l.lat, l.lon, l.source,
                   f.real_meter, r.skip_reason, r.skip_at,
                   l.accuracy, m.is_new, m.prev_nir, COALESCE(r.first_at, r.taken_at)
            FROM meter m
            LEFT JOIN reading r ON r.route_id = m.route_id AND r.row_num = m.row_num
            LEFT JOIN location l ON l.meter_key = m.meter_key
            LEFT JOIN meter_fix f ON f.meter_key = m.meter_key
            WHERE m.route_id = ?
            ORDER BY m.row_num
        """.trimIndent()
        val out = ArrayList<MeterRecord>(2500)
        readableDatabase.rawQuery(sql, arrayOf(routeId.toString())).use { c ->
            while (c.moveToNext()) {
                out.add(
                    MeterRecord(
                        row = c.getInt(0),
                        nir = c.getString(1),
                        nirNumber = if (c.isNull(2)) null else c.getLong(2),
                        address = c.getString(3),
                        meter = c.getString(4),
                        meterKey = c.getString(5),
                        previous = if (c.isNull(6)) null else c.getLong(6),
                        barrio = c.getString(7),
                        user = c.getString(8),
                        observation = c.getString(9),
                        excelReading = if (c.isNull(10)) null else c.getLong(10),
                        appReading = if (c.isNull(11)) null else c.getLong(11),
                        readAt = if (c.isNull(12)) null else c.getLong(12),
                        note = if (c.isNull(13)) "" else c.getString(13),
                        lat = if (c.isNull(14)) null else c.getDouble(14),
                        lon = if (c.isNull(15)) null else c.getDouble(15),
                        locationSource = if (c.isNull(16)) null else c.getString(16),
                        realMeter = if (c.isNull(17)) null else c.getString(17),
                        skipReason = if (c.isNull(18)) null else c.getString(18),
                        skipAt = if (c.isNull(19)) null else c.getLong(19),
                        locationAccuracy = if (c.isNull(20)) null else c.getFloat(20),
                        isNew = c.getInt(21) == 1,
                        prevNir = if (c.isNull(22)) null else c.getString(22),
                        firstReadAt = if (c.isNull(23)) null else c.getLong(23)
                    )
                )
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // LECTURAS
    // ------------------------------------------------------------------

    /** Guarda la fila completa de la lectura (valor, nota y motivo de no lectura). */
    fun saveReading(
        routeId: Long, row: Int, value: Long?, takenAt: Long?, note: String,
        skipReason: String?, skipAt: Long?
    ) {
        // «first_at» = cuándo se tomó por primera vez; no cambia al corregir la lectura ni al editar la nota.
        val firstAt: Long? = if (value == null) null else {
            val kept = writableDatabase.rawQuery(
                "SELECT value, first_at FROM reading WHERE route_id=? AND row_num=?", arrayOf(routeId.toString(), row.toString())
            ).use { c -> if (c.moveToFirst() && !c.isNull(0) && !c.isNull(1)) c.getLong(1) else null }
            kept ?: takenAt
        }
        writableDatabase.insertWithOnConflict("reading", null, ContentValues().apply {
            put("route_id", routeId)
            put("row_num", row)
            if (value == null) putNull("value") else put("value", value)
            if (takenAt == null) putNull("taken_at") else put("taken_at", takenAt)
            if (firstAt == null) putNull("first_at") else put("first_at", firstAt)
            put("note", note)
            if (skipReason == null) putNull("skip_reason") else put("skip_reason", skipReason)
            if (skipAt == null) putNull("skip_at") else put("skip_at", skipAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ------------------------------------------------------------------
    // CORRECCIÓN DEL NÚMERO DE MEDIDOR (solo en la app, el Excel no cambia)
    // ------------------------------------------------------------------

    fun saveMeterFix(meterKey: String, realMeter: String, time: Long) {
        writableDatabase.insertWithOnConflict("meter_fix", null, ContentValues().apply {
            put("meter_key", meterKey)
            put("real_meter", realMeter)
            put("updated_at", time)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteMeterFix(meterKey: String) {
        writableDatabase.delete("meter_fix", "meter_key=?", arrayOf(meterKey))
    }

    /** Filas con lectura tomada en la app -> lo que se escribe al exportar. */
    fun appReadings(routeId: Long): Map<Int, Long> {
        val out = HashMap<Int, Long>()
        readableDatabase.rawQuery(
            "SELECT row_num, value FROM reading WHERE route_id=? AND value IS NOT NULL",
            arrayOf(routeId.toString())
        ).use { c -> while (c.moveToNext()) out[c.getInt(0)] = c.getLong(1) }
        return out
    }

    // ------------------------------------------------------------------
    // UBICACIONES
    // ------------------------------------------------------------------

    fun saveLocation(meterKey: String, nir: String, lat: Double, lon: Double, accuracy: Float?, source: String, time: Long) {
        writableDatabase.insertWithOnConflict("location", null, ContentValues().apply {
            put("meter_key", meterKey)
            put("nir", nir)
            put("lat", lat)
            put("lon", lon)
            if (accuracy == null) putNull("accuracy") else put("accuracy", accuracy.toDouble())
            put("source", source)
            put("updated_at", time)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteLocation(meterKey: String) {
        writableDatabase.delete("location", "meter_key=?", arrayOf(meterKey))
        clearLocSamples(meterKey)
    }

    // ---- Historial de mediciones GPS (v6) ----
    fun addLocSample(meterKey: String, lat: Double, lon: Double, accuracy: Float, source: String, time: Long) {
        writableDatabase.insert("loc_sample", null, ContentValues().apply {
            put("meter_key", meterKey)
            put("lat", lat)
            put("lon", lon)
            put("accuracy", accuracy.toDouble())
            put("source", source)
            put("taken_at", time)
        })
        // Solo se guardan las 12 más recientes por medidor.
        writableDatabase.execSQL(
            "DELETE FROM loc_sample WHERE meter_key=? AND id NOT IN " +
                    "(SELECT id FROM loc_sample WHERE meter_key=? ORDER BY id DESC LIMIT 12)",
            arrayOf<Any>(meterKey, meterKey)
        )
    }

    fun locSamples(meterKey: String): List<com.lecturasgas.beta.location.LocSample> =
        readableDatabase.rawQuery(
            "SELECT lat, lon, accuracy, taken_at FROM loc_sample WHERE meter_key=? ORDER BY id", arrayOf(meterKey)
        ).use { c ->
            val out = ArrayList<com.lecturasgas.beta.location.LocSample>()
            while (c.moveToNext()) out.add(
                com.lecturasgas.beta.location.LocSample(c.getDouble(0), c.getDouble(1), c.getDouble(2).toFloat(), c.getLong(3))
            )
            out
        }

    fun clearLocSamples(meterKey: String) {
        writableDatabase.delete("loc_sample", "meter_key=?", arrayOf(meterKey))
    }

    fun hasLocation(meterKey: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM location WHERE meter_key=?", arrayOf(meterKey)).use { it.moveToFirst() }

    /** Cómo se guardó la ubicación de un medidor (para decidir si una nueva es mejor). */
    data class StoredLocation(val source: String, val accuracy: Float?)

    fun locationInfo(meterKey: String): StoredLocation? =
        readableDatabase.rawQuery(
            "SELECT source, accuracy FROM location WHERE meter_key=?", arrayOf(meterKey)
        ).use { c ->
            if (!c.moveToFirst()) null
            else StoredLocation(c.getString(0), if (c.isNull(1)) null else c.getDouble(1).toFloat())
        }

    fun locationCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM location", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ------------------------------------------------------------------
    // ENTREGAS
    // ------------------------------------------------------------------

    fun activeSession(): DeliverySession? =
        readableDatabase.rawQuery(
            "SELECT * FROM delivery_session WHERE ended_at IS NULL ORDER BY id DESC LIMIT 1", null
        ).use { if (it.moveToFirst()) it.session() else null }

    fun startSession(routeId: Long, time: Long): DeliverySession {
        val id = writableDatabase.insertOrThrow("delivery_session", null, ContentValues().apply {
            put("route_id", routeId)
            put("started_at", time)
            put("distance_m", 0.0)
        })
        return DeliverySession(id, routeId, time, null, 0.0)
    }

    fun endSession(id: Long, time: Long, distance: Double) {
        writableDatabase.update("delivery_session", ContentValues().apply {
            put("ended_at", time)
            put("distance_m", distance)
        }, "id=?", arrayOf(id.toString()))
    }

    fun updateSessionDistance(id: Long, distance: Double) {
        writableDatabase.update("delivery_session", ContentValues().apply {
            put("distance_m", distance)
        }, "id=?", arrayOf(id.toString()))
    }

    fun insertDelivery(d: Delivery): Long =
        writableDatabase.insertOrThrow("delivery", null, ContentValues().apply {
            put("session_id", d.sessionId)
            put("route_id", d.routeId)
            put("row_num", d.row)
            put("meter_key", d.meterKey)
            put("nir", d.nir)
            put("meter", d.meter)
            put("user_name", d.user)
            put("address", d.address)
            put("barrio", d.barrio)
            put("delivered_at", d.deliveredAt)
            if (d.lat == null) putNull("lat") else put("lat", d.lat)
            if (d.lon == null) putNull("lon") else put("lon", d.lon)
            if (d.accuracy == null) putNull("accuracy") else put("accuracy", d.accuracy.toDouble())
            put("ord", d.order)
            put("note", d.note)
            put("bulk", d.bulk)
        })

    fun deleteDeliveryBatch(stamp: Long) {
        writableDatabase.delete("delivery", "bulk=?", arrayOf(stamp.toString()))
    }

    fun updateDeliveryNote(id: Long, note: String) {
        writableDatabase.update("delivery", ContentValues().apply { put("note", note) }, "id=?", arrayOf(id.toString()))
    }

    fun deleteDelivery(id: Long) {
        writableDatabase.delete("delivery", "id=?", arrayOf(id.toString()))
    }

    fun deliveriesForRoute(routeId: Long): List<Delivery> =
        readableDatabase.rawQuery(
            "SELECT * FROM delivery WHERE route_id=? ORDER BY ord, id", arrayOf(routeId.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.delivery()) } }

    /** Historial de todas las jornadas: base de la "ruta aprendida". */
    fun allDeliveries(): List<Delivery> =
        readableDatabase.rawQuery("SELECT * FROM delivery ORDER BY session_id, ord", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.delivery()) } }

    /** Nombre de cada ruta guardada (incluye las importaciones viejas), por id. */
    fun routeNames(): Map<Long, String> =
        readableDatabase.rawQuery("SELECT id, name FROM route", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), c.getString(1) ?: "") }
        }

    fun addTrackPoint(sessionId: Long, p: TrackPoint) {
        writableDatabase.insert("track_point", null, ContentValues().apply {
            put("session_id", sessionId)
            put("lat", p.lat)
            put("lon", p.lon)
            if (p.accuracy == null) putNull("accuracy") else put("accuracy", p.accuracy.toDouble())
            put("time", p.time)
        })
    }

    fun trackPoints(sessionId: Long): List<TrackPoint> =
        readableDatabase.rawQuery(
            "SELECT lat, lon, accuracy, time FROM track_point WHERE session_id=? ORDER BY id",
            arrayOf(sessionId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    TrackPoint(c.getDouble(0), c.getDouble(1), if (c.isNull(2)) null else c.getFloat(2), c.getLong(3))
                )
            }
        }

    fun sessionSummaries(): List<SessionSummary> {
        val sql = """
            SELECT s.*, (SELECT COUNT(*) FROM delivery d WHERE d.session_id = s.id) AS n,
                   COALESCE((SELECT name FROM route r WHERE r.id = s.route_id), '') AS route_name
            FROM delivery_session s ORDER BY s.id DESC LIMIT 1000
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null).use { c ->
            buildList {
                while (c.moveToNext()) add(SessionSummary(c.session(), c.getInt(c.getColumnIndexOrThrow("n")), c.str("route_name")))
            }
        }
    }

    fun deliveriesForSession(sessionId: Long): List<Delivery> =
        readableDatabase.rawQuery(
            "SELECT * FROM delivery WHERE session_id=? ORDER BY ord", arrayOf(sessionId.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.delivery()) } }

    // ------------------------------------------------------------------

    private fun Cursor.str(name: String): String = getString(getColumnIndexOrThrow(name)) ?: ""
    private fun Cursor.long(name: String): Long = getLong(getColumnIndexOrThrow(name))
    private fun Cursor.longOrNull(name: String): Long? =
        getColumnIndexOrThrow(name).let { if (isNull(it)) null else getLong(it) }
    private fun Cursor.doubleOrNull(name: String): Double? =
        getColumnIndexOrThrow(name).let { if (isNull(it)) null else getDouble(it) }

    private fun Cursor.session() = DeliverySession(
        id = long("id"),
        routeId = long("route_id"),
        startedAt = long("started_at"),
        endedAt = longOrNull("ended_at"),
        distanceMeters = getDouble(getColumnIndexOrThrow("distance_m"))
    )

    private fun Cursor.delivery() = Delivery(
        id = long("id"),
        sessionId = long("session_id"),
        routeId = long("route_id"),
        row = getInt(getColumnIndexOrThrow("row_num")),
        meterKey = str("meter_key"),
        nir = str("nir"),
        meter = str("meter"),
        user = str("user_name"),
        address = str("address"),
        barrio = str("barrio"),
        deliveredAt = long("delivered_at"),
        lat = doubleOrNull("lat"),
        lon = doubleOrNull("lon"),
        accuracy = doubleOrNull("accuracy")?.toFloat(),
        order = getInt(getColumnIndexOrThrow("ord")),
        note = getColumnIndex("note").let { if (it >= 0) getString(it) ?: "" else "" },
        bulk = getColumnIndex("bulk").let { if (it >= 0) getLong(it) else 0L }
    )

    companion object {
        const val VERSION = 8
        const val DB_NAME = "lecturasgas.db"
    }
}
