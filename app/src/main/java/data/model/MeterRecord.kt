package com.lecturasgas.beta.data.model

data class MeterRecord(
    val rowNumber: Int,
    val nir: String,
    val address: String,
    val meter: String,
    val previousReading: Long?,
    val neighborhood: String,
    val user: String,
    val observation: String,
    val currentReading: Long?
)