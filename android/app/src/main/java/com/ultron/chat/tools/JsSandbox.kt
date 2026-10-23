package com.ultron.chat.tools

import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable

/**
 * A secure, pure-JavaScript sandbox the assistant uses to build and run its own
 * tools. It exposes only the standard JS library (Math, JSON, String, Array,
 * Date, RegExp …) — no Java classes, no network, no filesystem. Functions and
 * variables the model defines persist for the life of the process, so it can
 * create a tool once and reuse it later in the conversation.
 *
 * Runs in interpreted mode (required on Android, which has no runtime bytecode
 * compiler) and aborts runaway scripts via an instruction-count ceiling.
 */
object JsSandbox {

    private const val MAX_INSTRUCTIONS = 4_000_000
    private const val MAX_OUTPUT = 4000

    private class SafeFactory : ContextFactory() {
        override fun makeContext(): Context {
            val cx = super.makeContext()
            cx.optimizationLevel = -1                 // interpreted mode (Android-safe)
            cx.languageVersion = Context.VERSION_ES6
            cx.instructionObserverThreshold = 10_000
            // Deny access to every Java class: a strictly JS-only sandbox.
            cx.setClassShutter { _ -> false }
            return cx
        }

        private var count = 0
        override fun observeInstructionCount(cx: Context?, instructionCount: Int) {
            count += 10_000
            if (count > MAX_INSTRUCTIONS) throw Error("Script exceeded the instruction limit (possible infinite loop).")
        }

        override fun doTopCall(callable: org.mozilla.javascript.Callable, cx: Context, scope: Scriptable?, thisObj: Scriptable?, args: Array<out Any>?): Any? {
            count = 0
            return super.doTopCall(callable, cx, scope, thisObj, args)
        }
    }

    private val factory = SafeFactory()

    // One persistent scope so tools the model defines stay available across calls.
    private var scope: Scriptable? = null

    private fun ensureScope(cx: Context): Scriptable {
        var s = scope
        if (s == null) {
            s = cx.initStandardObjects()
            scope = s
        }
        return s
    }

    /** Runs [code] in the shared scope and returns the value of its last expression (or an error). */
    @Synchronized
    fun run(code: String): String {
        val cx = factory.enterContext()
        return try {
            val s = ensureScope(cx)
            val result = cx.evaluateString(s, code, "ultron-tool", 1, null)
            val out = when {
                result == null || result === Context.getUndefinedValue() -> "(no value)"
                else -> Context.toString(result)
            }
            if (out.length > MAX_OUTPUT) out.substring(0, MAX_OUTPUT) + "\n…(truncated)" else out
        } catch (e: Throwable) {
            "ERROR: " + (e.message ?: e.toString())
        } finally {
            Context.exit()
        }
    }

    /** Wipes defined tools/state (used when starting fresh). */
    @Synchronized
    fun reset() { scope = null }
}
