package com.amplitude.experiment

data class FetchOptions(
  /**
   * Whether assignment events are tracked when fetching flag configurations.
   * Defaults to null, which uses the server default behavior (to track assignment event).
   */
  @JvmField val tracksAssignment: Boolean? = null,
  /**
   * Whether exposure events are tracked when fetching flag configurations.
   * Defaults to null, which uses the server default behavior (to not track exposure event).
   */
  @JvmField val tracksExposure: Boolean? = null,
) {
  companion object {
    @JvmStatic
    fun builder(): Builder {
      return Builder()
    }
  }

  class Builder {
    private var tracksAssignment: Boolean? = null
    private var tracksExposure: Boolean? = null

    fun setTracksAssignment(tracksAssignment: Boolean) = apply {
      this.tracksAssignment = tracksAssignment
    }

    fun setTracksExposure(tracksExposure: Boolean) = apply {
      this.tracksExposure = tracksExposure
    }

    fun build(): FetchOptions {
      return FetchOptions(
        tracksAssignment = tracksAssignment,
        tracksExposure = tracksExposure,
      )
    }
  }
}