package com.lecturasgas.beta.data.model

import com.lecturasgas.beta.util.TextUtil

/**
 * Un medidor de la ruta, tal como lo ve la interfaz.
 *
 * Combina tres fuentes:
 *  - lo que trae el Excel (nir, dirección, medidor, lectura anterior, etc.);
 *  - la lectura registrada en la app (appReading);
 *  - la ubicación GPS guardada para ese medidor (se conserva mes a mes).
 *
 * Además guarda dos cosas SOLO de la app (nunca van al Excel):
 *  - realMeter: el número REAL del medidor, cuando el Excel lo trae mal;
 *  - skipReason: por qué no se pudo leer ("Casa cerrada", etc.).
 */
data class MeterRecord(
    val row: Int,                 // fila real en el Excel (1-based)
    val nir: String,
    val nirNumber: Long?,
    val address: String,
    val meter: String,
    val meterKey: String,
    val previous: Long?,
    val barrio: String,
    val user: String,
    val observation: String,
    val excelReading: Long?,      // lectura que ya venía en el Excel
    val appReading: Long?,        // lectura tomada con la app (la que se exporta)
    val readAt: Long?,
    val note: String,
    val lat: Double?,
    val lon: Double?,
    val locationSource: String?,
    val realMeter: String? = null,
    val skipReason: String? = null,
    val skipAt: Long? = null,
    /** Precisión (±m) con la que se guardó el GPS de este medidor. Null = no se sabe. */
    val locationAccuracy: Float? = null,
    /** Medidor que no estaba en el Excel anterior de esta ruta. */
    val isNew: Boolean = false,
    /** NIR que tenía antes de que se corriera la numeración (null si no cambió). */
    val prevNir: String? = null,
    /** Cuándo se tomó por primera vez la lectura de la app (no cambia si luego se corrige). Define el orden de lectura. */
    val firstReadAt: Long? = null
) {
    /** Número que se muestra: el corregido si existe, si no el del Excel. */
    val displayMeter: String get() = realMeter?.takeIf { it.isNotBlank() } ?: meter
    val hasMeterFix: Boolean get() = !realMeter.isNullOrBlank()
    val realMeterKey: String? get() = realMeter?.takeIf { it.isNotBlank() }?.let { TextUtil.meterKey(it) }
    /** Pendiente con un motivo anotado (casa cerrada, medidor tapado...). */
    val isSkipped: Boolean get() = skipReason != null && !isRead
    val reading: Long? get() = appReading ?: excelReading
    val isRead: Boolean get() = reading != null
    val hasLocation: Boolean get() = lat != null && lon != null
    /**
     * GPS dudoso: se guardó con mala señal (más de 15 m de error) o no se sabe con qué precisión.
     * Las ubicaciones puestas a mano en el mapa nunca se consideran dudosas.
     */
    val hasWeakLocation: Boolean
        get() = hasLocation && locationSource != "manual" &&
                (locationAccuracy == null || locationAccuracy > WEAK_ACCURACY_M)
    val consumption: Long?
        get() {
            val r = reading ?: return null
            val p = previous ?: return null
            return r - p
        }

    companion object {
        const val WEAK_ACCURACY_M = 15f
    }
}

data class RouteInfo(
    val id: Long,
    val name: String,
    val period: String,
    val fileName: String,
    val importedAt: Long,
    val readingColumn: String,
    val originalPath: String,
    val lastExportAt: Long?,
    /** Identidad de la ruta ("RUTA A"): igual todos los meses, aunque cambie el nombre del archivo. */
    val routeKey: String = ""
)

/** Una ruta guardada en la app (A, B, C…), con su avance. */
data class RouteSummary(
    val id: Long,
    val name: String,
    val routeKey: String,
    val period: String,
    val total: Int,
    val read: Int,
    val active: Boolean,
    /** Lecturas tomadas con la app. */
    val appReadings: Int = 0,
    /** Lecturas de la app que aún no se han exportado. */
    val unexported: Int = 0,
    val deliveries: Int = 0,
    val importedAt: Long = 0L,
    val lastExportAt: Long? = null
)

data class DeliverySession(
    val id: Long,
    val routeId: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceMeters: Double
) {
    val isActive: Boolean get() = endedAt == null
}

data class Delivery(
    val id: Long,
    val sessionId: Long,
    val routeId: Long,
    val row: Int,
    val meterKey: String,
    val nir: String,
    val meter: String,
    val user: String,
    val address: String,
    val barrio: String,
    val deliveredAt: Long,
    val lat: Double?,
    val lon: Double?,
    val accuracy: Float?,
    val order: Int,
    /** Dónde se dejó la factura ("puerta blanca", "parte de atrás"…). Solo para ti: no va en el Excel. */
    val note: String = "",
    /** 0 = entrega normal; si no, hora del «paquete» (entrega por buzón general) a la que pertenece. */
    val bulk: Long = 0L
)

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val accuracy: Float?,
    val time: Long
)

/** Una lectura del GPS del teléfono. */
data class GeoFix(
    val lat: Double,
    val lon: Double,
    val accuracy: Float?,
    val time: Long,
    val speed: Float?,
    val bearing: Float?,
    val provider: String
)

data class SessionSummary(
    val session: DeliverySession,
    val deliveries: Int,
    val routeName: String
)

/** Campos por los que se puede buscar. */
enum class SearchField(val label: String) {
    METER("Medidor"),
    NIR("NIR"),
    USER("Usuario"),
    ADDRESS("Dirección"),
    BARRIO("Barrio"),
    READING("Lectura"),
    OBSERVATION("Observación"),
    ALL("Todos");

    companion object {
        /**
         * Al tocar una ficha: se enciende o se apaga. «Todos» va solo (apaga las demás) y, si las
         * demás se apagan todas, se queda al menos una.
         */
        fun toggle(current: Set<SearchField>, f: SearchField): Set<SearchField> {
            if (f == ALL) return setOf(ALL)
            val base = current - ALL
            val next = if (f in base) base - f else base + f
            return next.ifEmpty { setOf(f) }
        }
    }
}

/** Motivos por los que no se pudo tomar una lectura. */
object SkipReasons {
    val all = listOf(
        "Casa cerrada",
        "Medidor tapado o sin acceso",
        "Perro o zona peligrosa",
        "Medidor dañado",
        "No existe el medidor",
        "Otro motivo"
    )
}
