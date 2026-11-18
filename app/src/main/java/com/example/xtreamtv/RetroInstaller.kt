package com.example.xtreamtv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal object RetroInstaller {
    private const val base = "https://buildbot.libretro.com/stable/1.22.1/android/"
    private val packages = setOf("com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32")
    fun permitted(context: Context) = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
    fun settings(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
    }
    suspend fun prepare(context: Context): File = withContext(Dispatchers.IO) {
        val name = when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "RetroArch_aarch64.apk"
            Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "RetroArch_ra32.apk"
            else -> "RetroArch.apk"
        }
        val dir = File(context.cacheDir, "install").apply { mkdirs() }
        val apk = File(dir, name)
        val temp = File(dir, "$name.part")
        try {
            val connection = URL(base + name).openConnection() as HttpsURLConnection
            connection.connectTimeout = 20000
            connection.readTimeout = 45000
            connection.instanceFollowRedirects = false
            try {
                check(connection.responseCode == 200) { "Official server returned ${connection.responseCode}" }
                connection.inputStream.use { input -> temp.outputStream().use { output ->
                    val buf = ByteArray(65536); var total = 0L
                    while (true) { val n = input.read(buf); if (n < 0) break; total += n; check(total <= 350L * 1024 * 1024) { "Download too large" }; output.write(buf, 0, n) }
                    check(total > 0 && (connection.contentLengthLong < 0 || total == connection.contentLengthLong)) { "Incomplete download" }
                } }
            } finally { connection.disconnect() }
            val info = context.packageManager.getPackageArchiveInfo(temp.absolutePath, 0)
            check(info != null && info.packageName in packages) { "Unexpected APK package" }
            apk.delete(); check(temp.renameTo(apk)) { "Cannot save APK" }; apk
        } finally { temp.delete() }
    }
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
