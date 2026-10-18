package com.lecturasgas.beta.map

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturasgas.beta.data.model.MeterRecord
import com.lecturasgas.beta.ui.theme.AppColors
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * MAPA SIN INTERNET
 *
 * Con WiFi descargas UNA vez las calles de tu zona de trabajo (el rectángulo que
 * cubre todos tus medidores con GPS). Después el mapa se ve igual sin datos.
 * Ocupa pocos MB. Los puntos, el recorrido y la ruta siempre funcionan sin internet.
 *
 * El satélite no se descarga completo (son demasiadas fotos); aun así, las zonas que
 * ya viste con internet quedan guardadas en un caché grande (200 MB) y se ven sin conexión.
 */
sealed interface OfflineState {
    data object Checking : OfflineState
    data object None : OfflineState
    data class Downloading(val percent: Int) : OfflineState
    /** Descarga a medias (por ejemplo se cortó el internet). */
    data class Incomplete(val percent: Int) : OfflineState
    data class Ready(val megabytes: Double) : OfflineState
    data class Failed(val message: String) : OfflineState
}

class OfflineMapController(context: Context) {

    private val app = context.applicationContext

    var state: OfflineState by mutableStateOf(OfflineState.Checking)
        private set

    private var region: OfflineRegion? = null

    /** Caché general más grande: lo que ves con internet (incluido el satélite) se guarda para después. */
    fun enlargeCache() {
        runCatching { OfflineGlue.enlargeCache(app, AMBIENT_CACHE_BYTES) }
    }

    /** Busca si ya hay una zona descargada. */
    fun refresh() {
        state = OfflineState.Checking
        runCatching {
            OfflineGlue.findRegion(app, REGION_NAME, object : OfflineGlue.Callback<OfflineRegion> {
                override fun ok(value: OfflineRegion?) {
                    region = value
                    if (value == null) {
                        state = OfflineState.None
                        return
                    }
                    OfflineGlue.status(value, object : OfflineGlue.Callback<OfflineRegionStatus> {
                        override fun ok(value: OfflineRegionStatus?) {
                            state = when {
                                value == null -> OfflineState.None
                                value.isComplete -> OfflineState.Ready(megabytes(value))
                                else -> OfflineState.Incomplete(percent(value))
                            }
                        }

                        override fun fail(message: String?) {
                            state = OfflineState.Failed(message ?: "No se pudo leer la zona descargada")
                        }
                    })
                }

                override fun fail(message: String?) {
                    state = OfflineState.Failed(message ?: "Error")
                }
            })
        }.onFailure { state = OfflineState.Failed(it.message ?: "Error") }
    }

    /** Descarga (o vuelve a descargar) la zona. Necesita internet. */
    fun download(bounds: LatLngBounds) {
        val old = region
        if (old == null) {
            create(bounds)
            return
        }
        OfflineGlue.setActive(old, false)
        OfflineGlue.delete(old, object : OfflineGlue.Callback<Any> {
            override fun ok(value: Any?) {
                region = null
                create(bounds)
            }

            override fun fail(message: String?) {
                create(bounds)
            }
        })
    }

    /** Sigue una descarga que quedó a medias. */
    fun resume() {
        val r = region ?: return
        state = OfflineState.Downloading(0)
        watch(r)
        OfflineGlue.setActive(r, true)
    }

    fun cancel() {
        region?.let { OfflineGlue.setActive(it, false) }
        refresh()
    }

    fun delete() {
        val r = region ?: return
        OfflineGlue.setActive(r, false)
        OfflineGlue.delete(r, object : OfflineGlue.Callback<Any> {
            override fun ok(value: Any?) {
                region = null
                state = OfflineState.None
            }

            override fun fail(message: String?) {
                state = OfflineState.Failed(message ?: "No se pudo borrar")
            }
        })
    }

    private fun create(bounds: LatLngBounds) {
        state = OfflineState.Downloading(0)
        runCatching {
            OfflineGlue.setTileLimit(app, TILE_LIMIT * 4)
            val definition = OfflineTilePyramidRegionDefinition(
                MapLayers.STREETS_STYLE,
                bounds,
                MIN_ZOOM,
                chooseMaxZoom(bounds),
                app.resources.displayMetrics.density
            )
            val metadata = """{"name":"$REGION_NAME"}""".toByteArray()
            OfflineGlue.create(app, definition, metadata, object : OfflineGlue.Callback<OfflineRegion> {
                override fun ok(value: OfflineRegion?) {
                    if (value == null) {
                        state = OfflineState.Failed("No se pudo crear la zona")
                        return
                    }
                    region = value
                    watch(value)
                    OfflineGlue.setActive(value, true)
                }

                override fun fail(message: String?) {
                    state = OfflineState.Failed(message ?: "Error")
                }
            })
        }.onFailure { state = OfflineState.Failed(it.message ?: "Error") }
    }

    private fun watch(r: OfflineRegion) {
        OfflineGlue.observe(r, object : OfflineGlue.Progress {
            override fun status(status: OfflineRegionStatus?) {
                if (status == null) return
                state = if (status.isComplete) {
                    OfflineGlue.setActive(r, false)
                    OfflineState.Ready(megabytes(status))
                } else OfflineState.Downloading(percent(status))
            }

            override fun error() {
                // Sin internet a mitad de la descarga: queda guardado lo que alcanzó a bajar.
                OfflineGlue.setActive(r, false)
                state = OfflineState.Incomplete((state as? OfflineState.Downloading)?.percent ?: 0)
            }

            override fun limitExceeded() {
                OfflineGlue.setActive(r, false)
                state = OfflineState.Failed("La zona es demasiado grande para descargarla")
            }
        })
    }

    private fun percent(s: OfflineRegionStatus): Int {
        val required = s.requiredResourceCount
        if (required <= 0L) return 0
        return (s.completedResourceCount * 100L / required).toInt().coerceIn(0, 100)
    }

    private fun megabytes(s: OfflineRegionStatus): Double = s.completedResourceSize / 1_048_576.0

    companion object {
        private const val REGION_NAME = "lecturas-zona"
        private const val MIN_ZOOM = 11.0
        private const val TILE_LIMIT = 8_000L
        private const val AMBIENT_CACHE_BYTES = 200L * 1024 * 1024

        /** El zoom más cercano que se descarga, bajándolo si la zona es muy grande. */
        private fun chooseMaxZoom(b: LatLngBounds): Double {
            for (z in 17 downTo 13) {
                if (tileCount(b, MIN_ZOOM.toInt(), z) <= TILE_LIMIT) return z.toDouble()
            }
            return 13.0
        }

        private fun tileCount(b: LatLngBounds, minZ: Int, maxZ: Int): Long {
            var total = 0L
            for (z in minZ..maxZ) {
                val x0 = lonToTile(b.getLonWest(), z)
                val x1 = lonToTile(b.getLonEast(), z)
                val y0 = latToTile(b.getLatNorth(), z)
                val y1 = latToTile(b.getLatSouth(), z)
                total += (x1 - x0 + 1L) * (y1 - y0 + 1L)
            }
            return total
        }

        private fun lonToTile(lon: Double, z: Int): Int = floor((lon + 180.0) / 360.0 * 2.0.pow(z)).toInt()

        private fun latToTile(lat: Double, z: Int): Int {
            val rad = Math.toRadians(lat)
            return floor((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * 2.0.pow(z)).toInt()
        }
    }
}

/**
 * Zona de trabajo = rectángulo que cubre todos los medidores con GPS (+ ~1 km de margen).
 * Si todavía no hay ninguno, se usa lo que se ve en pantalla ([fallback]).
 */
fun workZoneBounds(records: List<MeterRecord>, fallback: LatLngBounds?): LatLngBounds? {
    var north = -90.0
    var south = 90.0
    var east = -180.0
    var west = 180.0
    var any = false
    for (r in records) {
        val lat = r.lat ?: continue
        val lon = r.lon ?: continue
        any = true
        if (lat > north) north = lat
        if (lat < south) south = lat
        if (lon > east) east = lon
        if (lon < west) west = lon
    }
    if (!any) return fallback
    val margin = 0.009
    return LatLngBounds.from(north + margin, east + margin, south - margin, west - margin)
}

@Composable
fun OfflineMapDialog(
    controller: OfflineMapController,
    zone: LatLngBounds?,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { controller.refresh() }
    val state = controller.state

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mapa sin internet", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Descarga las calles de tu zona de trabajo una sola vez (con WiFi) " +
                        "y el mapa se verá igual sin internet.",
                    fontSize = 14.sp
                )
                when (state) {
                    is OfflineState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("  Revisando…", fontSize = 14.sp)
                    }
                    is OfflineState.None -> Text(
                        if (zone == null) "Aún no hay medidores con GPS. Acerca el mapa a tu zona y vuelve a abrir esta ventana."
                        else "Zona lista para descargar: cubre todos tus medidores con GPS.",
                        fontSize = 14.sp
                    )
                    is OfflineState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Descargando… ${state.percent} %", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("No cierres la app hasta que termine.", fontSize = 12.sp, color = AppColors.Muted)
                    }
                    is OfflineState.Incomplete -> Text(
                        "La descarga quedó a medias (${state.percent} %). Conéctate a internet y pulsa Continuar.",
                        fontSize = 14.sp, color = AppColors.Warning
                    )
                    is OfflineState.Ready -> Text(
                        "✓ Mapa descargado (${"%.0f".format(state.megabytes)} MB). Ya puedes trabajar sin internet.",
                        fontSize = 14.sp, color = AppColors.Done, fontWeight = FontWeight.Bold
                    )
                    is OfflineState.Failed -> Text(
                        "No se pudo: ${state.message}", fontSize = 14.sp, color = AppColors.Delivered
                    )
                }
                Text(
                    "El satélite no se descarga completo: solo se guardan las zonas que ya viste con internet.",
                    fontSize = 12.sp, color = AppColors.Muted
                )
            }
        },
        confirmButton = {
            Row(Modifier.padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is OfflineState.None, is OfflineState.Failed ->
                        Button(onClick = { zone?.let(controller::download) }, enabled = zone != null) {
                            Text("Descargar")
                        }
                    is OfflineState.Incomplete -> Button(onClick = controller::resume) { Text("Continuar") }
                    is OfflineState.Ready ->
                        OutlinedButton(onClick = { zone?.let(controller::download) }, enabled = zone != null) {
                            Text("Actualizar")
                        }
                    is OfflineState.Downloading -> OutlinedButton(onClick = controller::cancel) { Text("Pausar") }
                    is OfflineState.Checking -> Unit
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state is OfflineState.Ready || state is OfflineState.Incomplete) {
                    TextButton(onClick = controller::delete) { Text("Borrar", color = AppColors.Delivered) }
                }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    )
}
