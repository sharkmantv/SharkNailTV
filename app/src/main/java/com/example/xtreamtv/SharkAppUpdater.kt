package com.example.xtreamtv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

/** Checks the public GitHub release feed once per day, with user-approved APK installation. */
object SharkAppUpdater {
    private const val API = "https://api.github.com/repos/sharkmantv/SharkNailTV/releases/latest"
    private const val PREF = "shark_app_updates"
    private const val DAY = 24L * 60 * 60 * 1000
    private const val CACHE_LIFETIME = 7L * DAY
    fun check(activity: Activity, manual: Boolean = false) {
        val prefs = activity.getSharedPreferences(PREF, 0)
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong("last_check", 0) < DAY) return
        if (!manual) prefs.edit().putLong("last_check", now).apply()
        thread {
            try {
                val release = loadRelease(activity)
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@thread
                val tag = release.optString("tag_name").removePrefix("v")
                val latestCode = versionCode(tag) ?: error("Unsupported release tag: $tag. Use a numeric version such as v3.1.0")
                val installedCode = versionCode(BuildConfig.VERSION_NAME.removePrefix("v")) ?: BuildConfig.VERSION_CODE
                if (latestCode <= installedCode) {
                    if (manual) activity.runOnUiThread { AlertDialog.Builder(activity).setTitle("SharkNailTV is up to date").setMessage("Installed version: ${BuildConfig.VERSION_NAME}").setPositiveButton("OK", null).show() }
                    return@thread
                }
                val assets = release.getJSONArray("assets")
                var url: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true) && a.optString("browser_download_url").isNotBlank()) {
                        url = a.optString("browser_download_url"); break
                    }
                }
                val apkUrl = url ?: error("Latest release has no APK asset. Publish a signed APK in GitHub Releases.")
                if (!apkUrl.startsWith("https://github.com/sharkmantv/SharkNailTV/releases/download/")) error("Unexpected APK download URL")
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) AlertDialog.Builder(activity)
                        .setTitle("SharkNailTV update available")
                        .setMessage("Version $tag is available. Download and install the update? Android will ask you to approve installation.")
                        .setNegativeButton("Later", null)
                        .setPositiveButton("Update") { _, _ -> download(activity, apkUrl) }
                        .show()
                }
            } catch (e: Exception) {
                if (manual) activity.runOnUiThread { AlertDialog.Builder(activity).setTitle("Update check failed").setMessage("Could not check GitHub Releases: ${e.message ?: "Unknown error"}. Verify internet access and that the repository has a public release with an APK.").setPositiveButton("OK", null).show() }
            }
        }
    }
    /** Preserve a known-good release so a transient GitHub API limit cannot hide its Update button. */
    private fun loadRelease(activity: Activity): JSONObject {
        val prefs = activity.getSharedPreferences(PREF, 0)
        try {
            val response = readText(API)
            val release = JSONObject(response)
            // Only cache a complete public stable release containing its APK asset.
            if (!release.optBoolean("draft") && !release.optBoolean("prerelease") &&
                release.optJSONArray("assets") != null) {
                prefs.edit().putString("cached_release", response)
                    .putLong("cached_release_at", System.currentTimeMillis()).apply()
            }
            return release
        } catch (e: GithubRateLimitException) {
            val cached = prefs.getString("cached_release", null)
            val age = System.currentTimeMillis() - prefs.getLong("cached_release_at", 0L)
            if (cached != null && age in 0..CACHE_LIFETIME) {
                return JSONObject(cached)
            }
            throw e
        }
    }
    private class GithubRateLimitException(message: String) : Exception(message)

    private fun versionCode(version: String): Int? {
        val parts = version.split('.')
        if (parts.size !in 1..3) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        if (nums.any { it !in 0..99 }) return null
        return nums[0] * 10000 + nums[1] * 100 + (nums.getOrNull(2) ?: 0)
    }
    private fun readText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000; connection.readTimeout = 12000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "SharkNailTV-Android")
        try { if (connection.responseCode != 200) {
                val status = connection.responseCode
                if (status == 404) error("No public stable GitHub release found (HTTP 404). Publish a non-draft, non-prerelease release with an APK.")
                if (status == 403 || status == 429) {
                    val remaining = connection.getHeaderField("X-RateLimit-Remaining")
                    val retryAfter = connection.getHeaderField("Retry-After")
                    val detail = if (remaining == "0") "GitHub API rate limit reached" else "GitHub temporarily rejected the release check"
                    val wait = retryAfter?.toLongOrNull()?.let { " Retry after $it seconds." } ?: ""
                    throw GithubRateLimitException("$detail (HTTP $status).$wait Try again later.")
                }
                error("GitHub release check returned HTTP $status")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
    private val downloadInProgress = java.util.concurrent.atomic.AtomicBoolean()

    private fun download(activity: Activity, url: String) {
        if (activity.isFinishing || activity.isDestroyed || !downloadInProgress.compareAndSet(false, true)) return
        val progress = AlertDialog.Builder(activity).setTitle("Downloading SharkNailTV")
            .setMessage("Please wait while the update downloads…").setCancelable(false).show()
        thread {
            val dir = File(activity.cacheDir, "install")
            val temp = File(dir, "SharkNailTV.apk.part")
            try {
                if (!dir.isDirectory && !dir.mkdirs()) error("Could not create update cache")
                val target = File(dir, "SharkNailTV.apk")
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000; connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                try {
                    if (connection.responseCode != 200) error("Download failed")
                    val size = connection.contentLengthLong
                    if (size > 300L * 1024 * 1024) error("APK is too large")
                    connection.inputStream.use { input -> temp.outputStream().use { output ->
                        val buffer = ByteArray(8192); var total = 0L
                        while (true) { val n = input.read(buffer); if (n < 0) break
                            total += n; if (total > 300L * 1024 * 1024) error("APK is too large")
                            output.write(buffer, 0, n)
                        }
                    } }
                } finally { connection.disconnect() }
                if (temp.length() < 1024 || !temp.renameTo(target)) error("Invalid APK download")
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        progress.dismiss()
                        install(activity, target)
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        progress.dismiss()
                        AlertDialog.Builder(activity).setTitle("Update failed")
                            .setMessage(e.message ?: "Please try again later")
                            .setPositiveButton("OK", null).show()
                    }
                }
            } finally {
                temp.delete()
                downloadInProgress.set(false)
            }
        }
    }
    private fun install(activity: Activity, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity).setTitle("Allow app updates")
                .setMessage("Allow SharkNailTV to install apps in Android settings, then reopen SharkNailTV to retry the update.")
                .setPositiveButton("Settings") { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")))
                }.setNegativeButton("Cancel", null).show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { activity.startActivity(intent) } catch (_: Exception) {
            AlertDialog.Builder(activity).setMessage("No APK installer found on this device")
                .setPositiveButton("OK", null).show()
        }
    }
}
