package com.amplitude.experiment.exposure


import com.amplitude.experiment.util.Cache

internal interface ExposureFilter {
    fun shouldTrack(exposure: Exposure): Boolean
}

internal class InMemoryExposureFilter(size: Int, ttlMillis: Long = DAY_MILLIS) : ExposureFilter {

    // Cache of canonical exposure to the last sent timestamp.
    private val cache = Cache<String, Unit>(size, ttlMillis)

    override fun shouldTrack(exposure: Exposure): Boolean {
        val canonicalExposure = exposure.canonicalize()
        val track = cache[canonicalExposure] == null
        if (track) {
            cache[canonicalExposure] = Unit
        }
        return track
    }
}
