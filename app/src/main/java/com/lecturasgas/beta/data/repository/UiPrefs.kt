package com.lecturasgas.beta.data.repository

import android.content.Context
import com.lecturasgas.beta.data.model.SearchField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Qué hace la app justo después de guardar una lectura. */
enum class AfterSave(val label: String) {
    /** Cierra la tarjeta, limpia el buscador y deja el teclado listo para escribir el siguiente medidor. */
    SEARCH("Buscar otro"),
    /** Abre la tarjeta del siguiente medidor pendiente (en orden de NIR). */
    NEXT("Siguiente en ruta"),
    /** Solo cierra la tarjeta. */
    CLOSE("Solo guardar")
}

/** Ajustes que el usuario cambia desde la pantalla «Ajustes». */
data class AppSettings(
    /** Avisar «Estás frente a NIR…» cuando hay un medidor cerca. */
    val nearEnabled: Boolean = true,
    /** Distancia (m) a la que se avisa que un medidor está cerca. */
    val nearRadius: Int = 25,
    val nearVibrate: Boolean = true,
    /** Ofrecer «Mejorar ubicación» cuando el GPS guardado es dudoso. */
    val nearWeakHint: Boolean = true,
    val vibrateSave: Boolean = true,
    val vibrateDeliver: Boolean = true,
    /** Avisar si una lectura es menor que la anterior o el consumo es muy alto. */
    val consumptionAlert: Boolean = true,
    /** Al entregar, afinar la ubicación del medidor con el GPS. */
    val refineOnDeliver: Boolean = true,
    /** Al entregar, preguntar de una vez dónde se dejó la factura. */
    val askNoteOnDeliver: Boolean = false,
    /** Mostrar la sugerencia del siguiente medidor en Entrega (y si es la versión mínima de una fila). */
    val showNextCard: Boolean = true,
    /** Al leer, grabar tu recorrido (rastro) como en Entrega. */
    val trackReadings: Boolean = true,
    val nextCompact: Boolean = true,
    val keepScreenOn: Boolean = false,
    val saveGpsWithReading: Boolean = true,
    val afterSave: AfterSave = AfterSave.SEARCH,
    /** Apariencia: SYSTEM / LIGHT / DARK. */
    val themeMode: String = "SYSTEM",
    /** En el mapa que gira, usar la brújula del celular cuando estás quieto. */
    val useCompass: Boolean = true,
    /** Al exportar, volver a leer el archivo y compararlo celda por celda con el original (más lento, más seguro). */
    val verifyOnExport: Boolean = true,
    /** Mostrar «Revisión antes de exportar» al tocar Exportar. */
    val reviewBeforeExport: Boolean = true,
    /** Al leer: mostrar el semáforo del GPS y solo guardar el punto del medidor con el GPS en verde. Apagado = rápido. */
    val calibrateGps: Boolean = true,
    /** Modo ligero (menos animaciones, más fluido en celulares viejos): AUTO / ON / OFF. */
    val liteMode: String = "AUTO",
    /** Tamaño de la interfaz: AUTO / NORMAL / COMPACT / TINY. */
    val uiScale: String = "AUTO"
)

/** Preferencias pequeñas de la interfaz (se recuerdan al cerrar la app). */
class UiPrefs(context: Context) {
    private val p = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    private fun readSettings() = AppSettings(
        nearEnabled = p.getBoolean("near_enabled", true),
        nearRadius = p.getInt("near_radius", 25),
        nearVibrate = p.getBoolean("near_vibrate", true),
        nearWeakHint = p.getBoolean("near_weak_hint", true),
        vibrateSave = p.getBoolean("vibrate_save", true),
        vibrateDeliver = p.getBoolean("vibrate_deliver", true),
        consumptionAlert = p.getBoolean("consumption_alert", true),
        refineOnDeliver = p.getBoolean("refine_on_deliver", true),
        askNoteOnDeliver = p.getBoolean("ask_note_on_deliver", false),
        showNextCard = p.getBoolean("show_next_card", true),
        trackReadings = p.getBoolean("track_readings", true),
        nextCompact = p.getBoolean("next_compact", true),
        keepScreenOn = p.getBoolean("keep_screen_on", false),
        verifyOnExport = p.getBoolean("verify_on_export", true),
        reviewBeforeExport = p.getBoolean("review_before_export", true),
        calibrateGps = p.getBoolean("calibrate_gps", true),
        saveGpsWithReading = p.getBoolean("gps_with_reading", true),
        afterSave = runCatching { AfterSave.valueOf(p.getString("after_save", null) ?: "") }
            .getOrDefault(AfterSave.SEARCH),
        themeMode = p.getString("theme_mode", "SYSTEM") ?: "SYSTEM",
        useCompass = p.getBoolean("use_compass", true),
        liteMode = p.getString("lite_mode", "AUTO") ?: "AUTO",
        uiScale = p.getString("ui_scale", "AUTO") ?: "AUTO"
    )

    private val _settings = MutableStateFlow(readSettings())
    /** Ajustes actuales; las pantallas se actualizan solas al cambiarlos. */
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val n = transform(_settings.value)
        p.edit()
            .putBoolean("near_enabled", n.nearEnabled)
            .putInt("near_radius", n.nearRadius)
            .putBoolean("near_vibrate", n.nearVibrate)
            .putBoolean("near_weak_hint", n.nearWeakHint)
            .putBoolean("vibrate_save", n.vibrateSave)
            .putBoolean("vibrate_deliver", n.vibrateDeliver)
            .putBoolean("consumption_alert", n.consumptionAlert)
            .putBoolean("refine_on_deliver", n.refineOnDeliver)
            .putBoolean("ask_note_on_deliver", n.askNoteOnDeliver)
            .putBoolean("show_next_card", n.showNextCard)
            .putBoolean("track_readings", n.trackReadings)
            .putBoolean("next_compact", n.nextCompact)
            .putBoolean("keep_screen_on", n.keepScreenOn)
            .putBoolean("verify_on_export", n.verifyOnExport)
            .putBoolean("review_before_export", n.reviewBeforeExport)
            .putBoolean("calibrate_gps", n.calibrateGps)
            .putBoolean("gps_with_reading", n.saveGpsWithReading)
            .putString("after_save", n.afterSave.name)
            .putString("theme_mode", n.themeMode)
            .putBoolean("use_compass", n.useCompass)
            .putString("lite_mode", n.liteMode)
            .putString("ui_scale", n.uiScale)
            .apply()
        _settings.value = n
    }

    fun searchField(scope: String): SearchField =
        runCatching { SearchField.valueOf(p.getString("field_$scope", null) ?: "") }
            .getOrDefault(SearchField.METER)

    fun setSearchField(scope: String, field: SearchField) =
        p.edit().putString("field_$scope", field.name).apply()

    /** Campos de búsqueda elegidos (uno o varios). Si solo existe el ajuste viejo, se usa ese. */
    fun searchFields(scope: String): Set<SearchField> {
        val saved = p.getString("fields_$scope", null)
        val parsed = saved?.split(',')
            ?.mapNotNull { n -> runCatching { SearchField.valueOf(n.trim()) }.getOrNull() }
            ?.toSet().orEmpty()
        return parsed.ifEmpty { setOf(searchField(scope)) }
    }

    fun setSearchFields(scope: String, fields: Set<SearchField>) =
        p.edit().putString("fields_$scope", fields.joinToString(",") { it.name }).apply()

    fun numericKeyboard(scope: String): Boolean = p.getBoolean("numeric_$scope", true)
    fun setNumericKeyboard(scope: String, value: Boolean) = p.edit().putBoolean("numeric_$scope", value).apply()

    var readingsFilter: String
        get() = p.getString("readings_filter", "PENDING") ?: "PENDING"
        set(v) = p.edit().putString("readings_filter", v).apply()

    var satellite: Boolean
        get() = p.getBoolean("satellite", false)
        set(v) = p.edit().putBoolean("satellite", v).apply()

    /** Al seguirte, el mapa gira para que «arriba» sea hacia donde avanzas (como Waze). */
    var mapHeadingUp: Boolean
        get() = p.getBoolean("map_heading_up", true)
        set(v) = p.edit().putBoolean("map_heading_up", v).apply()

    var showPlan: Boolean
        get() = p.getBoolean("show_plan", true)
        set(v) = p.edit().putBoolean("show_plan", v).apply()

    var showTrack: Boolean
        get() = p.getBoolean("show_track", true)
        set(v) = p.edit().putBoolean("show_track", v).apply()

    var saveGpsWithReading: Boolean
        get() = p.getBoolean("gps_with_reading", true)
        set(v) { updateSettings { it.copy(saveGpsWithReading = v) } }

    var afterSave: AfterSave
        get() = runCatching { AfterSave.valueOf(p.getString("after_save", null) ?: "") }
            .getOrDefault(AfterSave.SEARCH)
        set(v) { updateSettings { it.copy(afterSave = v) } }

    var continueWithNext: Boolean
        get() = p.getBoolean("continue_next", true)
        set(v) = p.edit().putBoolean("continue_next", v).apply()

    /** Carpeta elegida para los respaldos automáticos (permiso persistente de Android). */
    var backupTree: String?
        get() = p.getString("backup_tree", null)
        set(v) = p.edit().putString("backup_tree", v).apply()

    var lastAutoBackupAt: Long
        get() = p.getLong("last_auto_backup", 0L)
        set(v) = p.edit().putLong("last_auto_backup", v).apply()

    var lastBackupError: String?
        get() = p.getString("last_backup_error", null)
        set(v) = p.edit().putString("last_backup_error", v).apply()

    /** Ya se usaron las entregas anteriores para afinar los puntos de los medidores (se hace una sola vez). */
    var deliveriesRefined: Boolean
        get() = p.getBoolean("deliveries_refined", false)
        set(v) = p.edit().putBoolean("deliveries_refined", v).apply()

    /** Segunda pasada: las entregas anteriores ahora también CORRIGEN puntos que estaban lejos. */
    var deliveriesRefined2: Boolean
        get() = p.getBoolean("deliveries_refined_v2", false)
        set(v) = p.edit().putBoolean("deliveries_refined_v2", v).apply()

    var legacyMigrated: Boolean
        get() = p.getBoolean("legacy_migrated", false)
        set(v) = p.edit().putBoolean("legacy_migrated", v).apply()
}
