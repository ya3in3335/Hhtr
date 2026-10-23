package com.ultron.chat.tools

import com.ultron.chat.ToolStep
import org.json.JSONObject

/**
 * The lightweight, provider-agnostic protocol that lets the model build and run
 * its own tools. The model emits a fenced `ultron-tool` block carrying JSON
 * `{ "tool": "run_js", "code": "…" }`; the app runs it in [JsSandbox] and feeds
 * the output back as a `TOOL RESULT`. The model loops until it answers with no
 * tool block.
 */
object ToolProtocol {

    const val MAX_STEPS = 6

    /** Injected as a system message on every request so the model knows its tools. */
    val SYSTEM_INSTRUCTIONS = """
You can BUILD AND RUN YOUR OWN TOOLS. A secure JavaScript sandbox is available.
To run code, output a fenced block EXACTLY in this form and then STOP and wait:

```ultron-tool
{ "tool": "run_js", "code": "<javascript here>" }
```

Rules:
- The sandbox is pure JavaScript: Math, JSON, String, Array, Object, Date, RegExp. No network, no files, no Java.
- The value of the LAST expression is returned to you as `TOOL RESULT:`.
- Functions and variables you define PERSIST for the rest of the chat, so you can create a tool once (e.g. `function fib(n){...}`) and reuse it in later steps.
- Use tools to compute, transform data, test algorithms, verify code, or simulate logic — whenever exactness matters more than guessing.
- After you receive the result, continue. When you are done, reply normally with NO tool block.
- Escape the code as valid JSON (use \n for newlines, \" for quotes).
""".trim()

    data class Block(val tool: String, val code: String)

    private val FENCE = Regex("```ultron-tool\\s*(\\{[\\s\\S]*?})\\s*```", RegexOption.IGNORE_CASE)

    /** Extracts every tool block from a completed assistant reply. */
    fun parse(text: String): List<Block> {
        val out = mutableListOf<Block>()
        for (m in FENCE.findAll(text)) {
            try {
                val o = JSONObject(m.groupValues[1])
                val tool = o.optString("tool", "run_js")
                val code = o.optString("code", "")
                if (code.isNotBlank()) out.add(Block(tool, code))
            } catch (_: Exception) { /* malformed block — ignore */ }
        }
        return out
    }

    /** The user-facing text of a reply with any tool blocks removed. */
    fun visibleText(text: String): String =
        FENCE.replace(text, "").replace(Regex("\\n{3,}"), "\n\n").trim()

    /** Runs a block in the sandbox and records it as a [ToolStep]. */
    fun run(block: Block): ToolStep {
        val result = when (block.tool) {
            "run_js" -> JsSandbox.run(block.code)
            else -> "ERROR: unknown tool '${block.tool}'"
        }
        return ToolStep(block.tool, block.code, result)
    }
}
