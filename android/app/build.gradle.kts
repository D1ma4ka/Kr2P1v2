import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Addresses are build configuration, never credentials or invented production endpoints.
fun apiAddress(variable: String, fallback: String = "", localHttp: Boolean = false): String {
    val raw = providers.environmentVariable(variable).orElse(fallback).get()
    if (raw.isEmpty()) return "" // staging/release tasks below require explicit configuration
    val uri = try { URI(raw) } catch (_: Exception) { error("Invalid $variable") }
    require(uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
        "$variable must contain a host, without credentials, query or fragment"
    }
    val localHosts = setOf("10.0.2.2", "127.0.0.1", "localhost")
    require(uri.scheme == "https" || (localHttp && uri.scheme == "http" && uri.host in localHosts)) {
        "$variable requires HTTPS (debug permits only loopback/emulator HTTP)"
    }
    require(localHttp || (uri.host !in localHosts && !uri.host.matches(Regex("[0-9.]+")) && ':' !in uri.host)) {
        "$variable requires a DNS hostname"
    }
    require(uri.port == -1 || uri.port in 1..65535) { "Invalid $variable port" }
    require(raw.none { it == '"' || it == '\\' || it.isISOControl() }) { "Invalid $variable characters" }
    return raw.trimEnd('/') + "/"
}
val devApi = apiAddress("KRAPIVA_DEV_API_BASE_URL", "http://10.0.2.2:53000/", true)
val stagingApi = apiAddress("KRAPIVA_STAGING_API_BASE_URL")
val productionApi = apiAddress("KRAPIVA_PROD_API_BASE_URL")
fun quoted(value: String) = "\"$value\""

android {
    namespace = "ru.krapiva.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.krapiva.app"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["class"] = "ru.krapiva.app.P3AcceptanceSuite"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"; versionNameSuffix = "-p6"
            buildConfigField("String", "API_BASE_URL", quoted(devApi))
            buildConfigField("String", "API_ENVIRONMENT", quoted("development"))
            buildConfigField("boolean", "ALLOW_LOCAL_HTTP", "true")
        }
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"; versionNameSuffix = "-staging"
            isDebuggable = false
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "API_BASE_URL", quoted(stagingApi))
            buildConfigField("String", "API_ENVIRONMENT", quoted("staging"))
            buildConfigField("boolean", "ALLOW_LOCAL_HTTP", "false")
        }
        release {
            buildConfigField("String", "API_BASE_URL", quoted(productionApi))
            buildConfigField("String", "API_ENVIRONMENT", quoted("production"))
            buildConfigField("boolean", "ALLOW_LOCAL_HTTP", "false")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    buildFeatures { compose = true; buildConfig = true }
    // Correctness errors fail CI; version advisories remain visible in its lint report.
    lint { abortOnError = true; warningsAsErrors = false; checkReleaseBuilds = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Missing staging/production addresses must not silently produce unusable builds.
tasks.matching { it.name in setOf("preStagingBuild", "generateStagingBuildConfig") }.configureEach {
    doFirst { require(stagingApi.isNotEmpty()) { "Set KRAPIVA_STAGING_API_BASE_URL" } }
}
tasks.matching { it.name in setOf("preReleaseBuild", "generateReleaseBuildConfig") }.configureEach {
    doFirst { require(productionApi.isNotEmpty()) { "Set KRAPIVA_PROD_API_BASE_URL" } }
}
