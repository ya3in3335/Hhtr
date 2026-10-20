package com.ultron.chat.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GitHubApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Validates a personal token against api.github.com and returns the login. */
    fun validate(token: String): Result<String> {
        return try {
            val req = Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return Result.failure(RuntimeException("HTTP ${resp.code}"))
                Result.success(JSONObject(text).optString("login", "?"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
