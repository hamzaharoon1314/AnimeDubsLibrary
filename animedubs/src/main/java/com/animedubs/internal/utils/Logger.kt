package com.animedubs.internal.utils

import android.util.Log

internal object Logger {
    var isEnabled = false
    private const val TAG = "AnimeDubs"

    fun d(message: String) {
        if (isEnabled) Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (isEnabled) Log.e(TAG, message, throwable)
    }
}
