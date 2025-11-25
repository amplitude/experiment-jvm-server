package com.amplitude.experiment

data class EvaluateOptions(
    @JvmField val tracksExposure: Boolean? = null,
) {
    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }

    class Builder {
        private var tracksExposure: Boolean? = null

        fun setTracksExposure(tracksExposure: Boolean) = apply {
            this.tracksExposure = tracksExposure
        }

        fun build(): EvaluateOptions {
            return EvaluateOptions(
                tracksExposure = tracksExposure,
            )
        }
    }
}
