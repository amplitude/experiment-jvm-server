package com.amplitude.experiment.util

class Once {
    var done = false
    fun once(block: () -> Unit) {
        synchronized(this) {
            if (done) return@once
            done = true
        }
        try {
            block.invoke()
        } catch (t: Throwable) {
            synchronized(this) {
                done = false
            }
            throw t
        }
    }
}
