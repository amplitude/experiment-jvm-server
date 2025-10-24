package com.amplitude.experiment

data class FetchOptions(
  @JvmField val tracksAssignment: Boolean? = null,
  @JvmField val tracksExposure: Boolean? = null,
) {
  companion object {
    @JvmStatic
    fun builder(): Builder {
      return Builder()
    }
  }

  class Builder {
    private var tracksAssignment: Boolean? = null;
    private var tracksExposure: Boolean? = null;

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