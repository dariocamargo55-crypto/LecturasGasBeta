package com.lecturasgas.beta.data.model

data class RoutePoint(
    val recordRowNumber: Int,
    val nir: String,
    val meter: String,
    val user: String,
    val address: String,
    val neighborhood: String,
    val reading: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestamp: Long
)