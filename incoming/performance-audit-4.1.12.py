from pathlib import Path
import re

# A separate release benchmark test APK; no instrumentation classes in the TV app.
root=Path(".")
app=root/"app/build.gradle.kts"
s=app.read_text()
assert 'media3Version = "1.11.1"' in s, "Expected matching Media3 release"
assert 'androidx.profileinstaller:profileinstaller:' in s
manifest=root/"app/src/main/AndroidManifest.xml"
m=manifest.read_text()
if '<profileable ' not in m:
    assert '</application>' in m
    m=m.replace('</application>','    <profileable android:shell="true" />\n    </application>',1)
    manifest.write_text(m)
# Standalone com.android.test module against the release variant; never ships with the OTA.
settings=root/"settings.gradle.kts"
s=settings.read_text()
if 'include(":macrobenchmark")' not in s:
    settings.write_text(s+'\ninclude(":macrobenchmark")\n')
module=root/"macrobenchmark"
module.mkdir(exist_ok=True)
(module/"build.gradle.kts").write_text('''plugins {
    id("com.android.test") version "9.1.1"
}
android {
    namespace = "com.example.xtreamtv.macrobenchmark"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            matchingFallbacks += listOf("release")
        }
    }
}
androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.enable = variant.buildType == "benchmark"
    }
}
dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.4")
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.test:runner:1.6.2")
}
''')
src=module/"src/main/java/com/example/xtreamtv/macrobenchmark"
src.mkdir(parents=True,exist_ok=True)
(src/"SharkPerformanceBenchmark.kt").write_text('''package com.example.xtreamtv.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SharkPerformanceBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    @Test fun coldStartup() = benchmark.measureRepeated(
        packageName = "com.example.xtreamtv",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }

    @Test fun remoteNavigation() = benchmark.measureRepeated(
        packageName = "com.example.xtreamtv",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        setupBlock = { startActivityAndWait(); device.waitForIdle() }
    ) {
        repeat(8) { device.pressDPadRight(); device.waitForIdle() }
        repeat(8) { device.pressDPadLeft(); device.waitForIdle() }
    }
}
''')
(module/"src/main/AndroidManifest.xml").write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <queries><package android:name="com.example.xtreamtv" /></queries>
</manifest>
''')
print("Release Macrobenchmark test module generated; physical-device measurements are still required.")
