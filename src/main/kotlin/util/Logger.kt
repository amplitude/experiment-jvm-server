package com.amplitude.experiment.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal interface ILogger {
    fun v(msg: String)
    fun d(msg: String)
    fun i(msg: String)
    fun w(msg: String, e: Throwable? = null)
    fun e(msg: String, e: Throwable? = null)
}

internal object Logger : ILogger {

    internal var implementation: ILogger? = null

    override fun v(msg: String) {
        implementation?.v(msg)
    }

    override fun d(msg: String) {
        implementation?.d(msg)
    }

    override fun i(msg: String) {
        implementation?.i(msg)
    }

    override fun w(msg: String, e: Throwable?) {
        implementation?.w(msg, e)
    }

    override fun e(msg: String, e: Throwable?) {
        implementation?.e(msg, e)
    }
}

internal val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

internal fun timestamp(): String {
    return format.format(Date())
}

// For Testing
internal class SystemLogger(private val debug: Boolean) : ILogger {

    override fun v(msg: String) {
        if (debug) {
            println("[${timestamp()}] VERBOSE: $msg")
        }
    }

    override fun d(msg: String) {
        if (debug) {
            println("[${timestamp()}] DEBUG: $msg")
        }
    }

    override fun i(msg: String) {
        if (debug) {
            println("[${timestamp()}] INFO: $msg")
        }
    }

    override fun w(msg: String, e: Throwable?) {
        if (e == null) {
            println("[${timestamp()}] WARN: $msg")
        } else {
            println("[${timestamp()}] WARN: $msg - $e")
        }
    }

    override fun e(msg: String, e: Throwable?) {
        if (e == null) {
            println("[${timestamp()}] ERROR: $msg")
        } else {
            println("[${timestamp()}] ERROR: $msg - $e")
        }
    }
}
