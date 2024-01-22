package com.amplitude.experiment.util

class FetchException internal constructor(
    val statusCode: Int,
    message: String
) : Exception(message)
