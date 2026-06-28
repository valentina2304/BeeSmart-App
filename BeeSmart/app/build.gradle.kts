import java.util.Properties

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

val deepLinkHost = (project.findProperty("BEE_DEEP_LINK_HOST") as? String).orEmpty().ifBlank { "app.beesmart.ro" }
val deepLinkScheme = (project.findProperty("BEE_DEEP_LINK_SCHEME") as? String).orEmpty().ifBlank { "https" }
val customScheme = (project.findProperty("BEE_CUSTOM_SCHEME") as? String).orEmpty().ifBlank { "beesmart" }

// OpenWeatherMap API key — read from local.properties so it stays out of git.
val openWeatherApiKey: String = localProperties.getProperty("openweather_api_key", "")

// Presentation safety net for DeepBee. Keep disabled for normal builds.
val aiDemoFallbackEnabled = (
    (project.findProperty("BEE_AI_DEMO_FALLBACK") as? String)
        ?: localProperties.getProperty("ai_demo_fallback", "false")
).toBooleanStrictOrNull() ?: false

val aiDemoFallbackTimeoutMs = (
    (project.findProperty("BEE_AI_DEMO_TIMEOUT_MS") as? String)
        ?: localProperties.getProperty("ai_demo_timeout_ms", "12000")
).toLongOrNull()
    ?.coerceIn(3_000L, 60_000L)
    ?: 12_000L

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.example.beesmart"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.beesmart"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DEEP_LINK_HOST", "\"$deepLinkHost\"")
        buildConfigField("String", "DEEP_LINK_SCHEME", "\"$deepLinkScheme\"")
        buildConfigField("String", "CUSTOM_SCHEME", "\"$customScheme\"")
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"$openWeatherApiKey\"")
        buildConfigField("boolean", "AI_DEMO_FALLBACK_ENABLED", aiDemoFallbackEnabled.toString())
        buildConfigField("long", "AI_DEMO_FALLBACK_TIMEOUT_MS", "${aiDemoFallbackTimeoutMs}L")
        manifestPlaceholders["beeDeepLinkHost"] = deepLinkHost
        resValue("string", "bee_hive_deeplink_https", "${deepLinkScheme}://${deepLinkHost}/hive/{hiveId}")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
       // freeCompilerArgs += "-Xuse-k1"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.splashscreen)
    
    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    // Integration test deps (JVM via Robolectric)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.retrofit)
    testImplementation(libs.converter.moshi)
    testImplementation(libs.okhttp)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.coil.compose)
    implementation(libs.compose.ui.text.google.fonts)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // WorkManager + Hilt integration
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    kapt(libs.hilt.work.compiler)

    // Camera & QR scanning
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.exifinterface)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.zxing.core)
}
