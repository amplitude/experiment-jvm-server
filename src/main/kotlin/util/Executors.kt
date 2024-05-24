package com.amplitude.experiment.util

import java.util.concurrent.ThreadFactory

internal val daemonFactory: ThreadFactory = DaemonThreadFactory()

private class DaemonThreadFactory(
    private val baseName: String = "experiment"
) : ThreadFactory {
    private var count = 0
    override fun newThread(r: Runnable): Thread {
        val t = Thread(r, baseName + "-" + (++count))
        t.isDaemon = true
        return t
    }
}
