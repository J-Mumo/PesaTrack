// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // AGP 8.9.x is the first release with stable support for compileSdk 36 (Android 16),
    // required by Google Play from 31 Aug 2026.
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("com.google.dagger.hilt.android") version "2.53" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    // Firebase (Analytics) — applied conditionally in app/build.gradle.kts when
    // google-services.json is present so local/CI builds without the config file
    // still succeed (Phase 1 telemetry — see plans/telemetry-phase1-plan.md).
    id("com.google.gms.google-services") version "4.4.2" apply false
}
