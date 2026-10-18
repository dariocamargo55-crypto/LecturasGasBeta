package com.lecturasgas.beta.location

import com.lecturasgas.beta.util.Geo
import kotlin.math.max
import kotlin.math.sqrt

/** Un punto con su error estimado en metros. */
data class Spot(val lat: Double, val lon: Double, val accuracy: Float)

/**
 * Afina la ubicación de un medidor con cada visita (leer, entregar...), como hace el GPS de un
 * topógrafo: promedia las mediciones dándole más peso a las más precisas. Así el punto
 * mejora con el tiempo en vez de quedarse con la primera medición, que pudo salir corrida.
 *
 * Es matemática pura (sin Android) para poder probarla en el computador.
 */
object LocationFusion {
    /** Si el punto guardado no dice con qué precisión se tomó, se supone un error de esto. */
    const val UNKNOWN_ACCURACY = 30f
    /** Ningún celular es confiable por debajo de esto: la precisión combinada no baja de aquí. */
    const val MIN_ACCURACY = 3f
    /** Solo se guarda si la precisión mejora al menos esto (evita reescribir por nada). */
    const val MIN_GAIN_M = 0.5f

    /**
     * Combina el punto guardado [old] con una medición nueva [new].
     * Devuelve el punto afinado, o null si NO conviene tocar el guardado:
     *  - la medición nueva cae muy lejos (puede haberse hecho desde otro lugar, p. ej. al
     *    entregar a un vecino), o
     *  - no aporta nada (es mucho peor que lo que ya había).
     */
    fun fuse(old: Spot, new: Spot): Spot? {
        val s1 = max(old.accuracy, MIN_ACCURACY).toDouble()
        val s2 = max(new.accuracy, MIN_ACCURACY).toDouble()
        val v1 = s1 * s1
        val v2 = s2 * s2

        val distance = Geo.distanceMeters(old.lat, old.lon, new.lat, new.lon)
        val gate = max(12.0, 3.0 * sqrt(v1 + v2))
        if (distance > gate) return null

        val w1 = 1.0 / v1
        val w2 = 1.0 / v2
        val lat = (old.lat * w1 + new.lat * w2) / (w1 + w2)
        val lon = (old.lon * w1 + new.lon * w2) / (w1 + w2)
        val acc = max(MIN_ACCURACY.toDouble(), sqrt(1.0 / (w1 + w2))).toFloat()
        if (old.accuracy - acc < MIN_GAIN_M) return null
        return Spot(lat, lon, acc)
    }

    /** A partir de esta distancia, dos puntos del mismo medidor no pueden ser ambos correctos. */
    const val FAR_M = 60.0
    /** Una medición hecha en la puerta es confiable para CORREGIR un punto lejano si tiene este error o menos. */
    const val TRUSTED_ACCURACY = 20f
    /** Con el apoyo de los vecinos del orden, basta un GPS de este error o menos para corregir. */
    const val CONTEXT_ACCURACY = 40f
    /** Si te moviste más que esto mientras se promediaba el GPS, el promedio ya no está en la puerta. */
    const val MOVED_M = 15.0

    /** Qué hacer con una medición nueva frente al punto guardado. */
    sealed interface Result {
        /** Mezcla de ambos (el punto se afina). */
        data class Fused(val spot: Spot) : Result
        /** El punto guardado estaba mal (lejos): se reemplaza por la medición nueva. */
        data class Corrected(val spot: Spot, val movedMeters: Double) : Result
        object Keep : Result
    }

    /**
     * Como [fuse], pero si el punto guardado está LEJOS de donde acabas de medir (más de [FAR_M]),
     * no lo ignora en silencio: cuando lees o entregas estás parado en el medidor, así que una
     * medición buena allí es la que manda y se corrige el punto viejo.
     */
    fun resolve(old: Spot, new: Spot, context: NeighborContext.Verdict = NeighborContext.Verdict.UNKNOWN): Result {
        val distance = Geo.distanceMeters(old.lat, old.lon, new.lat, new.lon)
        val sOld = max(old.accuracy, MIN_ACCURACY).toDouble()
        val sNew = max(new.accuracy, MIN_ACCURACY).toDouble()
        val gate = max(12.0, 3.0 * sqrt(sOld * sOld + sNew * sNew))
        if (distance <= gate) {
            val f = fuse(old, new)
            return if (f != null) Result.Fused(f) else Result.Keep
        }
        // Lejos. Si la medición nueva queda lejos de los vecinos del orden, no es de fiar.
        if (context == NeighborContext.Verdict.MEASURE_OFF) return Result.Keep
        // Si cuadra con los vecinos y el punto viejo no, se le exige menos al GPS.
        val limit = if (context == NeighborContext.Verdict.MEASURE_FITS) CONTEXT_ACCURACY else TRUSTED_ACCURACY
        // Si la medición nueva es buena y la diferencia es enorme (o ella es más precisa), gana.
        if (new.accuracy <= limit && (distance > FAR_M || new.accuracy < old.accuracy)) {
            return Result.Corrected(new, distance)
        }
        return Result.Keep
    }

    /**
     * Mientras se promediaba el GPS puede que ya te hayas ido caminando. Si el promedio quedó lejos
     * de donde estabas al tocar el botón, se usa la posición del toque; si tampoco es buena, null.
     */
    fun steady(atTap: Spot?, captured: Spot): Spot? {
        if (atTap == null) return captured
        if (Geo.distanceMeters(atTap.lat, atTap.lon, captured.lat, captured.lon) <= MOVED_M) return captured
        return if (atTap.accuracy <= 25f) atTap else null
    }
}
