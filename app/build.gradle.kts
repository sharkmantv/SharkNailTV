plugins { id("com.android.application") }
val artworkProperties = java.util.Properties().apply { rootProject.file("tmdb.properties").inputStream().use { load(it) } }
android {
    buildFeatures { buildConfig = true }
    namespace = "com.example.xtreamtv"
    compileSdkVersion("android-37.0")
    defaultConfig { applicationId = "com.example.xtreamtv"; minSdk = 24; targetSdk = 35; versionCode = 50003; versionName = "5.0.3"; buildConfigField("String", "TMDB_API_KEY", "\""+artworkProperties.getProperty("TMDB_API_KEY","").trim()+"\"") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
}
