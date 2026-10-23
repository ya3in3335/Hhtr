package com.ultron.chat

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Persisted settings. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ultron", Context.MODE_PRIVATE)

    var apiBase: String
        get() = sp.getString("api", "https://ultron-proxy.yacincianai.workers.dev") ?: "https://ultron-proxy.yacincianai.workers.dev"
        set(v) { sp.edit().putString("api", v).apply() }

    var ghToken: String
        get() = sp.getString("gh", "") ?: ""
        set(v) { sp.edit().putString("gh", v).apply() }

    var lang: String
        get() = sp.getString("lang", "ar") ?: "ar"
        set(v) { sp.edit().putString("lang", v).apply() }

    /** All conversations serialized as JSON (see ChatStore). */
    var chatsJson: String
        get() = sp.getString("chats", "") ?: ""
        set(v) { sp.edit().putString("chats", v).apply() }
}

/** Applies the chosen app language by wrapping the base context. */
object LocaleHelper {
    fun wrap(ctx: Context, lang: String): Context {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(ctx.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return ctx.createConfigurationContext(config)
    }
}

enum class Status { READY, THINKING, LISTENING, TOOL }

/** One tool the model built and ran during a turn (code + sandbox output). */
data class ToolStep(val tool: String, val code: String, val result: String)

/**
 * One chat turn.
 * [reasoning] is the human-readable thinking text shown in the UI.
 * [reasoningRaw] is the untouched reasoning_details JSON, resent verbatim to the model.
 * [tools] are the sandboxed tools the assistant created and executed this turn
 * (shown in the UI; not persisted or resent).
 */
data class Msg(
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val reasoningRaw: String? = null,
    val tools: List<ToolStep>? = null,
)
