package com.ultron.chat.net

import com.ultron.chat.Msg
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

/** Talks to the admin backend's /api/chat proxy (which injects the admin prompt + OpenRouter). */
object ChatApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun send(apiBase: String, history: List<Msg>): Result<Msg> {
        return try {
            val arr = JSONArray()
            for (m in history) {
                val o = JSONObject()
                    .put("role", m.role)
                    .put("content", m.content)
                if (m.role == "assistant" && !m.reasoningRaw.isNullOrBlank()) {
                    // Pass reasoning_details back unmodified.
                    o.put("reasoning_details", JSONTokener(m.reasoningRaw).nextValue())
                }
                arr.put(o)
            }
            val payload = JSONObject().put("messages", arr).toString()
            val base = apiBase.trimEnd('/')
            val req = Request.Builder()
                .url("$base/api/chat")
                .post(payload.toRequestBody(JSON))
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return Result.failure(RuntimeException("HTTP ${resp.code}"))
                val root = JSONObject(text)
                val msg = root.optJSONObject("message") ?: JSONObject()
                val content = msg.optString("content", "")
                val details = msg.opt("reasoning_details")
                val reasoningRaw = details?.toString()
                val reasoning = reasoningToText(details)
                Result.success(Msg("assistant", content, reasoning, reasoningRaw))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun reasoningToText(details: Any?): String? {
        if (details == null) return null
        return when (details) {
            is String -> details.ifBlank { null }
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until details.length()) {
                    val it = details.opt(i)
                    if (it is JSONObject) {
                        val t = it.optString("text", it.optString("summary", it.optString("content", "")))
                        if (t.isNotBlank()) { if (sb.isNotEmpty()) sb.append("\n\n"); sb.append(t) }
                    } else if (it != null) {
                        sb.append(it.toString())
                    }
                }
                sb.toString().ifBlank { null }
            }
            is JSONObject -> details.optString("text", "").ifBlank { null }
            else -> null
        }
    }
}
