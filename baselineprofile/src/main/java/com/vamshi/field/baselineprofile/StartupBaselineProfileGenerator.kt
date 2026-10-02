package com.vamshi.field.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

/**
 * Records the classes and methods touched on the way to Field's first usable frame, so ART can
 * AOT-compile them instead of interpreting them on a cold start.
 *
 * The measured cold start this exists to cut was 13.6s on first launch and ~7.0s thereafter,
 * dominated by class loading and first-time composition rather than by any app-level work —
 * see specs/2026-09-27-app-stuck-on-loading-investigation.md.
 *
 * Regenerate with:  gradlew :app:generateBaselineProfile   (needs a connected, rootable device)
 * The output lands in app/src/main/generated/baselineProfiles/ and is committed.
 */
class StartupBaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = "com.vamshi.field",
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()

        // The nav graph shows a spinner until the auth gate resolves; waiting for idle means the
        // profile covers the real start destination (Dashboard or Onboarding), not just the splash.
        device.waitForIdle()
    }
}
