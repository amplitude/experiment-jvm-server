package com.amplitude.experiment.cohort

data class Cohort(
    val id: String,
    val groupType: String,
    val size: Int,
    val lastModified: Long,
    val members: Set<String>,
)
