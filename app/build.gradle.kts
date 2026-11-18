import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val updateManifest = providers.gradleProperty("SHARK_UPDATE_MANIFEST").orElse("https://api.github.com/repos/sharkmantv/SharkNailTV/releases/latest").get()

val nuvioProperties = Properties().apply {
    val file = rootProject.file("nuvio.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val sportsProperties = Properties().apply {
    val file = rootProject.file("sports.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.example.xtreamtv"
    compileSdk = 36
    defaultConfig { applicationId = "com.example.xtreamtv"; minSdk = 23; targetSdk = 35; versionCode = providers.gradleProperty("SHARK_VERSION_CODE").orElse("30000").get().toInt(); versionName = providers.gradleProperty("SHARK_VERSION_NAME").orElse("3.0").get() }
    defaultConfig { buildConfigField("String", "UPDATE_MANIFEST_URL", "\"${updateManifest.replace("\\", "\\\\").replace("\"", "\\\"")}\"") }
    buildFeatures { compose = true; buildConfig = true }
    packaging { jniLibs { useLegacyPackaging = true } }
    defaultConfig { buildConfigField("String", "TMDB_API_KEY", "\"2c67bb8f745b1db06a58acfffff805f4\"") }
    defaultConfig { buildConfigField("String", "SPORTS_API_KEY", "\"${sportsProperties.getProperty("SPORTS_API_KEY", "")}\"") }
    defaultConfig {
        buildConfigField("String", "NUVIO_BACKEND_URL", "\"${nuvioProperties.getProperty("NUVIO_BACKEND_URL", "https://api.nuvio.tv").replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "NUVIO_PUBLIC_KEY", "\"${nuvioProperties.getProperty("NUVIO_PUBLIC_KEY", "sb_publishable_1Clq8rlTVACkdcZuqr6_AD__xUUC_EN").replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }
    signingConfigs {
        create("updates") {
            storeFile = file(System.getenv("SHARK_KEYSTORE_PATH") ?: "missing-update-keystore")
            storePassword = System.getenv("SHARK_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SHARK_KEY_ALIAS")
            keyPassword = System.getenv("SHARK_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("updates")
            isMinifyEnabled = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
dependencies {
    implementation(fileTree("libs") { include("*.aar", "*.jar") })
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:1.11.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
