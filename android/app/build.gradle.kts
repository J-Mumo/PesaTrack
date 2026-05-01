import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Load local.properties for signing credentials
val localProperties = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProperties.load(localPropsFile.inputStream())
}

android {
    namespace = "com.pesatrack"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pesatrack"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

    }

    signingConfigs {
        create("release") {
            // Upload key for Google Play App Signing
            // Google re-signs with their app signing key before delivery to users
            storeFile = file("../pesatrack-upload.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: localProperties.getProperty("KEYSTORE_PASSWORD") ?: ""
            keyAlias = "pesatrack-upload"
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: localProperties.getProperty("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Exclude duplicate files from Apache POI dependencies
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/LICENSE.txt"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.53")
    ksp("com.google.dagger:hilt-android-compiler:2.53")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Apache POI for Excel (.xlsx) file parsing
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    // Note: Removed exclusions and manual commons-compress to avoid NoClassDefFoundError

    // Apache PDFBox for M-PESA statement PDF parsing (text extraction from password-protected PDFs)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Vico charting library (Compose + Material 3)
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-beta.3")

    // Biometric authentication (fingerprint / face unlock)
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // ProcessLifecycleOwner for app background/foreground detection
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    // WorkManager for periodic background tasks (recurring expense reminders)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Google Play In-App Review API (Stage 1B)
    implementation("com.google.android.play:review-ktx:2.0.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
