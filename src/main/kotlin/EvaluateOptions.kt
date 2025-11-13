package com.amplitude.experiment

data class EvaluateOptions(
  @JvmField var flagKeys: Set<String>? = setOf(),
  @JvmField val tracksExposure: Boolean? = null,
) {
  companion object {
    @JvmStatic
    fun builder(): Builder {
      return Builder()
    }
  }

  class Builder {
    private var flagKeys: Set<String>? = null
    private var tracksExposure: Boolean? = null

    fun setFlagKeys(flagKeys: Set<String>?) = apply {
      this.flagKeys = flagKeys
    }

    fun setTracksExposure(tracksExposure: Boolean) = apply {
      this.tracksExposure = tracksExposure
    }

    fun build(): EvaluateOptions {
      return EvaluateOptions(
        flagKeys = flagKeys,
        tracksExposure = tracksExposure,
      )
    }
  }
}