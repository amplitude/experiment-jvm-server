package com.amplitude.experiment.util

class Once {
    private var done = false
    fun once(block: () -> Unit) {
        synchronized(this) {
            if (done) return@once
            done = true
        }
        block.invoke()
    }
}
