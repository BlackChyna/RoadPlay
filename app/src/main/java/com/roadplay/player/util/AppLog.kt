package com.roadplay.player.util

import android.util.Log

/** Single place for logging. Nothing here ever leaves the device. */
object AppLog {
    private const val TAG = "RoadPlay"
    fun d(msg: String) { Log.d(TAG, msg) }
    fun w(msg: String, t: Throwable? = null) { Log.w(TAG, msg, t) }
    fun e(msg: String, t: Throwable? = null) { Log.e(TAG, msg, t) }
}
