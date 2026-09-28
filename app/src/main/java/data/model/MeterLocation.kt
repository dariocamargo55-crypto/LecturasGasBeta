package com.lecturasgas.beta.data.model

data class MeterLocation(
    val meter: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val updatedAt: Long
)