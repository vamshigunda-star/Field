import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
}

kotlin {
    jvmToolchain(11)
}

// Room schema JSONs, one per DB version, committed to the repo. These are the
// inputs MigrationTestHelper needs to assert a migration produces the schema Room
// expects — without them, migrations are only ever verified by running the app.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}


android {
    namespace = "com.vamshi.field"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vamshi.field"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.vamshi.field.HiltTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    // Ship the exported schema JSONs into the androidTest APK so MigrationTestHelper
    // can read the "before" and "after" schemas for each migration under test.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    constraints {
        // room3-testing's MigrationTestHelper deserializes the exported schema JSON with
        // kotlinx-serialization-json 1.8.1, but AGP's consistent resolution pins the
        // androidTest classpath to whatever the app runtime classpath resolved — and a
        // production transitive was holding serialization-core at 1.6.3. The 1.8.1
        // generated serializers then call into the 1.6.3 core and every migration test
        // dies with AbstractMethodError on GeneratedSerializer.typeParametersSerializers().
        //
        // This is a constraint, not a dependency: it raises a version already on the
        // classpath rather than adding anything new. Remove it once a prod dependency
        // pulls serialization >= 1.8.1 on its own.
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1") {
            because("align serialization-core with the json 1.8.1 that room3-testing requires")
        }
    }

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation("androidx.sqlite:sqlite-bundled:2.5.0")
    implementation("androidx.sqlite:sqlite-framework:2.5.0")
    implementation(libs.hilt.android)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.12")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.room.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.aicore)
    implementation(libs.kotlinx.coroutines.guava)
    
    // Backup & Sync dependencies
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}

tasks.register("generateColorsFromDesign") {
    val designFile = rootProject.file("design.md")
    val outputDir = file("src/main/java/com/vamshi/field/ui/theme")
    val outputFile = file("$outputDir/Color.kt")
    
    inputs.file(designFile)
    outputs.file(outputFile)
    
    doLast {
        if (!designFile.exists()) return@doLast
        val lines = designFile.readLines()
        var inColorSection = false
        val colors = mutableMapOf<String, String>()
        
        for (line in lines) {
            if (line.startsWith("## 12. Color Palette")) {
                inColorSection = true
                continue
            }
            if (inColorSection && line.startsWith("## ") && !line.startsWith("## 12. Color Palette")) {
                break
            }
            if (inColorSection) {
                // Parse markdown table: | ColorName | #HEX |
                val parts = line.split("|").map { it.trim() }
                if (parts.size >= 3) {
                    val name = parts[1]
                    val hex = parts[2]
                    if (name != "Name" && name.isNotBlank() && !name.contains("-")) {
                        colors[name] = hex
                    }
                }
            }
        }
        
        val sb = StringBuilder()
        sb.append("package com.vamshi.field.ui.theme\n\n")
        sb.append("import androidx.compose.ui.graphics.Color\n\n")
        sb.append("// AUTO-GENERATED from design.md - DO NOT EDIT MANUALLY\n\n")
        for ((name, hexRaw) in colors) {
            val cleanHex = hexRaw.removePrefix("#")
            val hexVal = if (cleanHex.length == 6) "0xFF$cleanHex" else "0x$cleanHex"
            sb.append("val $name = Color($hexVal)\n")
        }
        
        outputDir.mkdirs()
        outputFile.writeText(sb.toString())
    }
}

tasks.named("preBuild") {
    dependsOn("generateColorsFromDesign")
}