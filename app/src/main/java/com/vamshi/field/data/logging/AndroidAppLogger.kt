package com.vamshi.field.data.logging

import android.util.Log
import com.vamshi.field.domain.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/** Writes [AppLogger] output to logcat. The only place `android.util.Log` is reached from. */
@Singleton
class AndroidAppLogger @Inject constructor() : AppLogger {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun warn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
