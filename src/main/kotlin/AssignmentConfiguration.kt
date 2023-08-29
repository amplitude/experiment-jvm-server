package com.amplitude.experiment

data class AssignmentConfiguration(
    val apiKey: String,
    val cacheCapacity: Int = 65536,
    val eventUploadThreshold: Int = 10,
    val eventUploadPeriodMillis: Int = 10000,
    val useBatchMode: Boolean = true,
)
