package com.amplitude.experiment

class ExperimentException(
    override val message: String? = null,
    override val cause: Throwable? = null
) : Exception()
