package com.lecturasgas.beta.data.excel

/**
 * Avance de una tarea larga: (etapa en texto, fracción de 0.0 a 1.0 del total de la tarea).
 * Código puro (sin Android) para poder usarlo también en las pruebas.
 */
typealias Progress = (stage: String, fraction: Float) -> Unit

val NoProgress: Progress = { _, _ -> }

/** Convierte el avance de una sub-tarea (0..1) en un tramo [from, to] del avance total. */
fun Progress.slice(from: Float, to: Float): Progress = { stage, f ->
    this(stage, from + (to - from) * f.coerceIn(0f, 1f))
}

/** Se lanza desde el avance cuando la persona toca «Cancelar»; corta la tarea sin dejar nada a medias. */
class TaskCancelled : RuntimeException("Cancelado por el usuario")
