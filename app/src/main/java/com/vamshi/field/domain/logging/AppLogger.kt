package com.vamshi.field.domain.logging

/**
 * Diagnostic logging seam for the domain layer.
 *
 * `domain/` is pure Kotlin (see CLAUDE.md) so it cannot touch `android.util.Log`
 * directly — a JVM unit test has no Android framework, and every `Log` call the
 * use cases made threw "Method w in android.util.Log not mocked" rather than
 * exercising the logic under test.
 *
 * Use cases depend on this interface instead. Production binds
 * `AndroidAppLogger` in `di/LoggingModule`; tests get [NoOpAppLogger] for free
 * via the constructor default.
 *
 * Only log what a coach's bug report would need. Per-call success traces belong
 * in a debugger, not here.
 */
interface AppLogger {
    fun debug(tag: String, message: String)
    fun warn(tag: String, message: String)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}

/** Default for unit tests and any caller that has no sink to write to. */
object NoOpAppLogger : AppLogger {
    override fun debug(tag: String, message: String) = Unit
    override fun warn(tag: String, message: String) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}
