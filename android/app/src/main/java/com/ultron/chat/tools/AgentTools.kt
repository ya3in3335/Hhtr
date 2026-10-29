package com.ultron.chat.tools

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The agent's real-world tools, executed natively. Each returns a short text
 * result for the model to read. Everything here is safe-by-design: public web
 * access, local memory, device info the user already granted — no control of
 * other apps and no system commands.
 */
object AgentTools {

    private const val MAX_OUT = 6000

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun cap(s: String) = if (s.length > MAX_OUT) s.substring(0, MAX_OUT) + "\n…(truncated)" else s

    /** Strips HTML tags so fetched pages are readable as text. */
    private fun stripHtml(html: String): String =
        html.replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?s)<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&").replace(Regex("&lt;"), "<").replace(Regex("&gt;"), ">")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    fun httpGet(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "ERROR: url must start with http(s)://"
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) Ultron/1.0")
                .build()
            http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                val ct = r.header("Content-Type", "").orEmpty()
                val text = if (ct.contains("html", true)) stripHtml(body) else body
                "HTTP ${r.code}\n" + cap(text)
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /** DuckDuckGo Instant Answer (free, no key). Good for quick facts. */
    fun webSearch(query: String): String {
        if (query.isBlank()) return "ERROR: empty query"
        return try {
            val url = "https://api.duckduckgo.com/?q=" +
                Uri.encode(query) + "&format=json&no_html=1&skip_disambig=1"
            val req = Request.Builder().url(url).header("User-Agent", "Ultron/1.0").build()
            http.newCall(req).execute().use { r ->
                val o = JSONObject(r.body?.string().orEmpty())
                val sb = StringBuilder()
                o.optString("Heading").takeIf { it.isNotBlank() }?.let { sb.append(it).append("\n") }
                o.optString("AbstractText").takeIf { it.isNotBlank() }?.let { sb.append(it).append("\n") }
                o.optString("AbstractURL").takeIf { it.isNotBlank() }?.let { sb.append("Source: ").append(it).append("\n") }
                val related = o.optJSONArray("RelatedTopics")
                if (related != null) {
                    var n = 0
                    var i = 0
                    while (i < related.length() && n < 5) {
                        val t = related.optJSONObject(i)?.optString("Text").orEmpty()
                        if (t.isNotBlank()) { sb.append("• ").append(t).append("\n"); n++ }
                        i++
                    }
                }
                if (sb.isBlank()) "No instant answer. Use http_get on a specific URL for details."
                else cap(sb.toString().trim())
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun now(): String {
        val f = SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm:ss zzz", Locale.getDefault())
        return f.format(Date())
    }

    // ---- Persistent agent memory (local key/value) ----
    private fun memPrefs(ctx: Context) = ctx.getSharedPreferences("ultron_agent_mem", Context.MODE_PRIVATE)
    private fun memData(ctx: Context): JSONObject =
        try { JSONObject(memPrefs(ctx).getString("data", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }

    fun memSet(ctx: Context, key: String, value: String): String {
        if (key.isBlank()) return "ERROR: empty key"
        val d = memData(ctx)
        d.put(key, value)
        memPrefs(ctx).edit().putString("data", d.toString()).apply()
        return "saved '$key'"
    }

    fun memGet(ctx: Context, key: String): String {
        val d = memData(ctx)
        return if (d.has(key)) d.optString(key) else "(no value for '$key')"
    }

    fun memList(ctx: Context): String {
        val d = memData(ctx)
        if (d.length() == 0) return "(memory empty)"
        val sb = StringBuilder()
        for (k in d.keys()) sb.append(k).append(": ").append(d.optString(k).take(120)).append("\n")
        return cap(sb.toString().trim())
    }

    fun location(ctx: Context): String {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return "ERROR: location permission not granted (enable it from the shield)"
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) "lat=${loc.latitude}, lon=${loc.longitude}" else "(no last known location available)"
        } catch (e: SecurityException) {
            "ERROR: ${e.message}"
        }
    }

    fun openUrl(ctx: Context, url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "ERROR: url must start with http(s)://"
        return try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            "opened $url"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun clipboardSet(ctx: Context, text: String): String {
        return try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ultron", text))
            "copied to clipboard"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    // ---- GitHub (uses the token saved in "Link GitHub") ----
    private fun ghToken(ctx: Context): String =
        ctx.getSharedPreferences("ultron", Context.MODE_PRIVATE).getString("gh", "").orEmpty().trim()

    private fun ghReq(token: String, url: String): Request.Builder =
        Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Ultron")

    fun ghWhoami(ctx: Context): String {
        val token = ghToken(ctx)
        if (token.isBlank()) return "ERROR: link GitHub first (menu → Link GitHub)"
        return try {
            http.newCall(ghReq(token, "https://api.github.com/user").build()).execute().use { r ->
                val o = JSONObject(r.body?.string().orEmpty())
                if (!r.isSuccessful) "ERROR: HTTP ${r.code} ${o.optString("message")}"
                else "login=${o.optString("login")}, name=${o.optString("name")}, repos=${o.optInt("public_repos")}"
            }
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    fun ghListRepos(ctx: Context): String {
        val token = ghToken(ctx)
        if (token.isBlank()) return "ERROR: link GitHub first"
        return try {
            http.newCall(ghReq(token, "https://api.github.com/user/repos?per_page=40&sort=updated").build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) return "ERROR: HTTP ${r.code}"
                val arr = org.json.JSONArray(body)
                val sb = StringBuilder()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    sb.append(o.optString("full_name")).append(if (o.optBoolean("private")) " (private)" else "").append("\n")
                }
                cap(sb.toString().trim().ifBlank { "(no repos)" })
            }
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    fun ghGetFile(ctx: Context, repo: String, path: String): String {
        val token = ghToken(ctx)
        if (token.isBlank()) return "ERROR: link GitHub first"
        if (repo.isBlank() || path.isBlank()) return "ERROR: need repo (owner/name) and path"
        return try {
            http.newCall(ghReq(token, "https://api.github.com/repos/$repo/contents/$path").build()).execute().use { r ->
                val o = JSONObject(r.body?.string().orEmpty())
                if (!r.isSuccessful) return "ERROR: HTTP ${r.code} ${o.optString("message")}"
                val content = o.optString("content").replace("\n", "")
                val decoded = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
                cap(decoded)
            }
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    fun ghPutFile(ctx: Context, repo: String, path: String, content: String, message: String): String {
        val token = ghToken(ctx)
        if (token.isBlank()) return "ERROR: link GitHub first"
        if (repo.isBlank() || path.isBlank()) return "ERROR: need repo (owner/name) and path"
        return try {
            // Look up existing sha (required to update an existing file).
            var sha: String? = null
            http.newCall(ghReq(token, "https://api.github.com/repos/$repo/contents/$path").build()).execute().use { r ->
                if (r.isSuccessful) sha = JSONObject(r.body?.string().orEmpty()).optString("sha").ifBlank { null }
            }
            val b64 = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
            val payload = JSONObject()
                .put("message", message.ifBlank { "Update $path via Ultron" })
                .put("content", b64)
            if (sha != null) payload.put("sha", sha)
            val req = ghReq(token, "https://api.github.com/repos/$repo/contents/$path")
                .put(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { r ->
                val o = JSONObject(r.body?.string().orEmpty())
                if (!r.isSuccessful) "ERROR: HTTP ${r.code} ${o.optString("message")}"
                else "committed: " + o.optJSONObject("content")?.optString("html_url").orEmpty()
            }
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    fun ghCreateGist(ctx: Context, filename: String, content: String, description: String, public: Boolean): String {
        val token = ghToken(ctx)
        if (token.isBlank()) return "ERROR: link GitHub first"
        return try {
            val files = JSONObject().put(filename.ifBlank { "ultron.txt" }, JSONObject().put("content", content))
            val payload = JSONObject().put("description", description).put("public", public).put("files", files)
            val req = ghReq(token, "https://api.github.com/gists")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { r ->
                val o = JSONObject(r.body?.string().orEmpty())
                if (!r.isSuccessful) "ERROR: HTTP ${r.code} ${o.optString("message")}"
                else "gist: " + o.optString("html_url")
            }
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }
}
