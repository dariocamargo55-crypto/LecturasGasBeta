package com.lecturasgas.beta.data.model

data class RouteSegment(
    val id: Int,
    val originalName: String,
    val displayName: String,
    val records: List<MeterRecord>
) {
    val total: Int
        get() = records.size

    val read: Int
        get() = records.count {
            it.currentReading != null
        }

    val pending: Int
        get() = records.count {
            it.currentReading == null
        }
}