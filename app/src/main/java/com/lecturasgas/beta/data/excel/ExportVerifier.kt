package com.lecturasgas.beta.data.excel

import com.lecturasgas.beta.data.model.MeterRecord

/**
 * Comprueba un Excel YA EXPORTADO contra lo que la app tiene guardado: cada lectura debe quedar en la
 * fila de SU medidor (mismo NIR y mismo número de medidor en esa fila) y las filas sin lectura nueva
 * deben seguir exactamente como venían. Es código puro (sin Android).
 */
object ExportVerifier {

    data class Result(
        val problems: List<String>,
        val rowsChecked: Int,
        val readingsChecked: Int
    ) {
        val ok: Boolean get() = problems.isEmpty()
    }

    fun verify(
        exported: ExcelImporter.Parsed,
        records: List<MeterRecord>,
        updates: Map<Int, Long>,
        formulaRows: Set<Int> = emptySet()
    ): Result {
        val problems = mutableListOf<String>()
        var extra = 0
        fun problem(text: String) { if (problems.size < MAX) problems.add(text) else extra++ }

        if (exported.rows.size != records.size) {
            problem("El archivo exportado tiene ${exported.rows.size} medidores y la app tiene ${records.size}.")
        }
        val byRow = exported.rows.associateBy { it.row }
        var readings = 0
        for (rec in records) {
            val out = byRow[rec.row]
            if (out == null) { problem("Falta la fila ${rec.row} (medidor ${rec.meter}) en el archivo exportado."); continue }
            if (out.meter != rec.meter) problem("Fila ${rec.row}: el medidor es «${out.meter}» y debía ser «${rec.meter}».")
            if (out.nir != rec.nir) problem("Fila ${rec.row}: el NIR es «${out.nir}» y debía ser «${rec.nir}».")
            val wanted = updates[rec.row]
            if (wanted != null && rec.row !in formulaRows) {
                readings++
                if (out.reading != wanted) problem("Fila ${rec.row} (medidor ${rec.meter}): debía quedar $wanted y quedó ${out.reading ?: "vacía"}.")
            } else if (rec.row !in formulaRows && out.reading != rec.excelReading) {
                problem("Fila ${rec.row} (medidor ${rec.meter}): no tenía lectura nueva, pero cambió de ${rec.excelReading ?: "vacía"} a ${out.reading ?: "vacía"}.")
            }
        }
        val known = records.mapTo(HashSet()) { it.row }
        for (r in exported.rows) if (r.row !in known) problem("El archivo tiene una fila ${r.row} que la app no conoce.")
        if (extra > 0) problems.add("… y $extra problemas más.")
        return Result(problems, records.size, readings)
    }

    private const val MAX = 8
}
