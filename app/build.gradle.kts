plugins { id("com.android.application") }
android {
    namespace = "com.example.xtreamtv"
    compileSdkVersion("android-37.0")
    defaultConfig { applicationId = "com.example.xtreamtv"; minSdk = 24; targetSdk = 35; versionCode = 50000; versionName = "5.0.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
}
