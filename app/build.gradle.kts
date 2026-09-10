import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val mapTilerProperties = Properties().apply {
    val propertiesFile = rootProject.file("maptiler.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}
val mapTilerKey = (mapTilerProperties.getProperty("MAPTILER_KEY")
    ?: providers.gradleProperty("MAPTILER_KEY").orNull
    ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
val apiBaseUrl = (providers.gradleProperty("API_BASE_URL").orNull ?: "https://locusgps.pro")
    .trimEnd('/')
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val apiTokenProperties = Properties().apply {
    val propertiesFile = rootProject.file("api.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use(::load)
}
val apiToken = (apiTokenProperties.getProperty("API_TOKEN")
    ?: providers.gradleProperty("API_TOKEN").orNull
    ?: "").replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.locusgps"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.locusgps"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "MAPTILER_KEY", "\"$mapTilerKey\"")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "API_TOKEN", "\"$apiToken\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.maplibre)
}
