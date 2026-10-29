package com.ultron.chat.tools

import android.content.Context
import com.ultron.chat.ToolStep
import org.json.JSONObject

/**
 * The provider-agnostic protocol that turns the model into a real agent: it
 * emits a fenced `ultron-tool` block with JSON `{ "tool": "...", ...args }`,
 * the app runs the tool natively (sandbox, web, memory, device), feeds the
 * output back as a `TOOL RESULT`, and the model loops until it answers with no
 * tool block.
 */
object ToolProtocol {

    const val MAX_STEPS = 8

    /** Injected as a system message on every request so the model knows its tools. */
    val SYSTEM_INSTRUCTIONS = """
You are an autonomous AGENT. You can BUILD AND RUN TOOLS to act, not just talk.
To use a tool, output a fenced block EXACTLY in this form and then STOP and wait
for the TOOL RESULT:

```ultron-tool
{ "tool": "<name>", ...args }
```

Available tools:
- run_js: { "tool": "run_js", "code": "<javascript>" } — a secure JS sandbox (Math, JSON, String, Array, Date). The LAST expression is returned. Functions you define persist across the chat. Use it to compute, transform data, or test logic.
- http_get: { "tool": "http_get", "url": "https://..." } — fetch a public web page/API and read it as text.
- web_search: { "tool": "web_search", "query": "..." } — quick factual lookup (DuckDuckGo).
- now: { "tool": "now" } — current date and time.
- get_location: { "tool": "get_location" } — the device's last known location.
- open_url: { "tool": "open_url", "url": "https://..." } — open a link in the browser for the user.
- clipboard_set: { "tool": "clipboard_set", "text": "..." } — copy text to the clipboard.
- memory_set: { "tool": "memory_set", "key": "...", "value": "..." } — remember something permanently.
- memory_get: { "tool": "memory_get", "key": "..." } — recall it later.
- memory_list: { "tool": "memory_list" } — list everything you remember.

Rules:
- Plan, then act. Chain tools across steps to reach the goal (e.g. web_search → http_get → run_js).
- Use tools when they help you be exact, current, or actually do something — NOT for greetings or simple chat.
- Escape code/strings as valid JSON (\n for newlines, \" for quotes).
- After each TOOL RESULT, continue. When finished, reply normally with NO tool block.
""".trim()

    data class Block(val tool: String, val args: JSONObject)

    // The closing brace is escaped (\\}) — Android's regex engine rejects a bare '}'.
    private val fence: Regex? by lazy {
        runCatching { Regex("```ultron-tool\\s*(\\{[\\s\\S]*?\\})\\s*```", RegexOption.IGNORE_CASE) }.getOrNull()
    }

    /** Extracts every tool block from a completed assistant reply. Never throws. */
    fun parse(text: String): List<Block> {
        val re = fence ?: return emptyList()
        val out = mutableListOf<Block>()
        try {
            for (m in re.findAll(text)) {
                try {
                    val o = JSONObject(m.groupValues[1])
                    val tool = o.optString("tool", "run_js")
                    if (tool.isNotBlank()) out.add(Block(tool, o))
                } catch (_: Exception) { /* malformed block — ignore */ }
            }
        } catch (_: Throwable) { /* defensive */ }
        return out
    }

    /** The user-facing text of a reply with any tool blocks removed. Never throws. */
    fun visibleText(text: String): String {
        val re = fence ?: return text
        return try {
            re.replace(text, "").replace(Regex("\\n{3,}"), "\n\n").trim()
        } catch (_: Throwable) { text }
    }

    /** A short human-readable label of what a tool call did, for the UI. */
    private fun label(b: Block): String = when (b.tool) {
        "run_js" -> b.args.optString("code")
        "http_get", "open_url" -> b.tool + " " + b.args.optString("url")
        "web_search" -> "web_search \"" + b.args.optString("query") + "\""
        "memory_set" -> "memory_set " + b.args.optString("key") + " = " + b.args.optString("value")
        "memory_get" -> "memory_get " + b.args.optString("key")
        "clipboard_set" -> "clipboard_set …"
        else -> b.tool
    }

    /** Runs a block natively and records it as a [ToolStep]. Never throws. */
    fun run(block: Block, ctx: Context): ToolStep {
        val a = block.args
        val result = try {
            when (block.tool) {
                "run_js" -> JsSandbox.run(a.optString("code"))
                "http_get" -> AgentTools.httpGet(a.optString("url"))
                "web_search" -> AgentTools.webSearch(a.optString("query"))
                "now" -> AgentTools.now()
                "get_location" -> AgentTools.location(ctx)
                "open_url" -> AgentTools.openUrl(ctx, a.optString("url"))
                "clipboard_set" -> AgentTools.clipboardSet(ctx, a.optString("text"))
                "memory_set" -> AgentTools.memSet(ctx, a.optString("key"), a.optString("value"))
                "memory_get" -> AgentTools.memGet(ctx, a.optString("key"))
                "memory_list" -> AgentTools.memList(ctx)
                else -> "ERROR: unknown tool '${block.tool}'"
            }
        } catch (t: Throwable) {
            "ERROR: tool failed (${t.javaClass.simpleName}: ${t.message})"
        }
        return ToolStep(block.tool, label(block), result)
    }
}
