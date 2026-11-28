import java.util.Properties

plugins { id("com.android.application") }
val artworkProperties = Properties().apply { rootProject.file("tmdb.properties").inputStream().use { load(it) } }
android {
    buildFeatures { buildConfig = true }
    namespace = "com.example.xtreamtv"
    compileSdkVersion("android-37.0")
    defaultConfig { applicationId = "com.example.xtreamtv"; minSdk = 24; targetSdk = 35; versionCode = 50090; versionName = "5.0.90"; buildConfigField("String", "TMDB_API_KEY", "\""+artworkProperties.getProperty("TMDB_API_KEY","").trim()+"\"") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(files("libs/media3-ffmpeg-1.12.0-rc01.aar"))
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("androidx.media3:media3-exoplayer:1.12.0-rc01")
    implementation("androidx.media3:media3-exoplayer-hls:1.12.0-rc01")
    implementation("androidx.media3:media3-datasource-okhttp:1.12.0-rc01")
    implementation("androidx.media3:media3-ui:1.12.0-rc01")
    implementation("androidx.media3:media3-effect:1.12.0-rc01")
}
