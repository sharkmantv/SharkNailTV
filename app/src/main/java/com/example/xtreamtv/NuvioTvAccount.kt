package com.example.xtreamtv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Nuvio device-link protocol, adapted from the supplied NuvioMobile 1.1.0 beta source.
 * The backend URL and public anon key must be configured at build time.
 * No secret/service-role key should ever be packaged in an APK.
 */
object NuvioTvAccount {
    private const val PREFS = "nuvio_tv_account_206"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val base get() = BuildConfig.NUVIO_BACKEND_URL.trimEnd('/')
    private val key get() = BuildConfig.NUVIO_PUBLIC_KEY
    val configured get() = base.startsWith("https://") && key.isNotBlank()
    fun linked(c: Context) = prefs(c).getString("refresh", null) != null
    fun clear(c: Context) { prefs(c).edit().clear().apply() }
    data class Link(val code: String, val verificationUrl: String, val deviceCode: String, val nonce: String, val interval: Int)
    private fun request(path: String, body: JSONObject? = null, bearer: String? = null): String {
        check(configured) { "Nuvio public backend configuration is missing from this build." }
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12000; conn.readTimeout = 15000
            conn.setRequestProperty("apikey", key)
            conn.setRequestProperty("Authorization", "Bearer " + (bearer ?: key))
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val result = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("Nuvio service HTTP $status: ${result.take(150)}")
            return result
        } finally { conn.disconnect() }
    }
    private fun first(s: String): JSONObject {
        val trimmed = s.trim()
        return if (trimmed.startsWith("[")) JSONArray(trimmed).getJSONObject(0) else JSONObject(trimmed)
    }
    suspend fun start(): Link = withContext(Dispatchers.IO) {
        val nonce = UUID.randomUUID().toString()
        val row = first(request("/rest/v1/rpc/start_device_login_session",
            JSONObject().put("p_device_nonce", nonce).put("p_redirect_base_url", "https://nuvio.tv/link")
                .put("p_device_name", "SharkNailTV").put("p_device_type", "tv")))
        Link(row.getString("user_code"), row.getString("verification_uri_complete"),
            row.getString("device_code"), nonce, row.optInt("poll_interval_seconds", 3).coerceIn(2,10))
    }
    suspend fun awaitApproval(c: Context, link: Link, onStatus: (String) -> Unit) = withContext(Dispatchers.IO) {
        repeat(120) {
            delay(link.interval * 1000L)
            val poll = first(request("/rest/v1/rpc/poll_tv_login_session",
                JSONObject().put("p_code", link.deviceCode).put("p_device_nonce", link.nonce)))
            when (poll.optString("status").lowercase()) {
                "pending" -> onStatus("Waiting for approval on your phone…")
                "approved" -> {
                    val session = JSONObject(request("/functions/v1/tv-logins-exchange",
                        JSONObject().put("code", link.deviceCode).put("device_nonce", link.nonce)))
                    val access = session.getString("access_token")
                    val refresh = session.getString("refresh_token")
                    prefs(c).edit().putString("access",access).putString("refresh",refresh).apply()
                    return@withContext
                }
                else -> error("Nuvio link expired or was denied.")
            }
        }
        error("Nuvio link timed out.")
    }
    private fun access(c: Context) = prefs(c).getString("access", null) ?: error("Connect Nuvio account first.")
    private fun refresh(c: Context): String {
        val token = prefs(c).getString("refresh", null) ?: error("Not linked.")
        val result = JSONObject(request("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", token)))
        val next = result.getString("access_token")
        prefs(c).edit().putString("access", next).putString("refresh", result.getString("refresh_token")).apply()
        return next
    }
    suspend fun sync(c: Context): List<SyncedNuvioAddon> = withContext(Dispatchers.IO) {
        val query = "/rest/v1/addons?select=url,name,enabled,sort_order&profile_id=eq.1&order=sort_order.asc"
        val rows = try { JSONArray(request(query, bearer = access(c))) }
            catch (e: Exception) { JSONArray(request(query, bearer = refresh(c))) }
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val url = row.optString("url")
                if (url.startsWith("https://")) add(SyncedNuvioAddon(
                    url, row.optString("name"), row.optBoolean("enabled",true), row.optInt("sort_order",i)))
            }
        }
    }
}
