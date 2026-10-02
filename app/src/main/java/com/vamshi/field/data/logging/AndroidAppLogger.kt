package com.vamshi.field.data.logging

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.vamshi.field.domain.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Writes [AppLogger] output to logcat. The only place `android.util.Log` is reached from. */
@Singleton
class AndroidAppLogger @Inject constructor(
    @ApplicationContext context: Context
) : AppLogger {

    // Debug traces are for development builds only. Checked at runtime rather than left to R8's
    // -assumenosideeffects, which can miss a call site after it merges branches (it kept one of
    // SeedDataManager's Log.d calls in the first release build).
    private val debugEnabled =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    override fun debug(tag: String, message: String) {
        if (debugEnabled) Log.d(tag, message)
    }

    override fun warn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
