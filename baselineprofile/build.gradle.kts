plugins {
    // Bare ids: AGP 9 puts com.android.* on the classpath itself, and the root build file
    // resolves the baselineprofile version, so re-declaring versions here conflicts.
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.vamshi.field.baselineprofile"
    compileSdk = 35

    defaultConfig {
        // Baseline profile generation requires API 28+, above the app's own minSdk of 24.
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // Generate against whatever device/emulator is attached rather than a managed device,
    // so this works with the AVDs already on the machine.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
