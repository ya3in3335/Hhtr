package com.ultron.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultron.chat.net.ChatApi
import com.ultron.chat.tools.ToolProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** A single conversation (observable title + message list). */
class Conversation(val id: String, title: String, initial: List<Msg> = emptyList()) {
    var title by mutableStateOf(title)
    val messages = mutableStateListOf<Msg>().also { it.addAll(initial) }
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    val conversations = mutableStateListOf<Conversation>()
    var currentId by mutableStateOf("")
        private set
    var status by mutableStateOf(Status.READY)
        private set
    var busy by mutableStateOf(false)
        private set

    init {
        conversations.addAll(ChatStore.fromJson(prefs.chatsJson))
        if (conversations.isEmpty()) newChat() else currentId = conversations.first().id
    }

    val current: Conversation
        get() = conversations.firstOrNull { it.id == currentId } ?: conversations.first()

    val messages get() = current.messages

    fun updateStatus(s: Status) { status = s }

    private fun persist() { prefs.chatsJson = ChatStore.toJson(conversations) }

    fun newChat() {
        // Reuse an existing empty conversation instead of piling up blank ones.
        val existingEmpty = conversations.firstOrNull { it.messages.isEmpty() }
        if (existingEmpty != null) { currentId = existingEmpty.id; return }
        val c = Conversation(UUID.randomUUID().toString().take(8), "")
        conversations.add(0, c)
        currentId = c.id
        persist()
    }

    fun selectChat(id: String) { if (conversations.any { it.id == id }) currentId = id }

    fun deleteChat(id: String) {
        conversations.removeAll { it.id == id }
        if (conversations.isEmpty()) newChat()
        else if (currentId == id) currentId = conversations.first().id
        persist()
    }

    /** "New chat" button. */
    fun clear() = newChat()

    /**
     * Sends a turn and runs the tool-use agent loop: it streams a reply, and if
     * the model emitted a tool block it runs the tool in the sandbox, feeds the
     * result back, and streams again — until the model answers with no tool
     * block (or [ToolProtocol.MAX_STEPS] is reached).
     */
    fun send(apiBase: String, text: String, errText: String, onReply: (String) -> Unit) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        val c = current
        c.messages.add(Msg("user", t))
        if (c.title.isBlank()) c.title = t.take(40)
        val idx = c.messages.size                 // index for the streaming assistant placeholder
        c.messages.add(Msg("assistant", ""))
        busy = true
        status = Status.THINKING
        persist()
        viewModelScope.launch {
            val working = c.messages.take(idx).toMutableList()   // up to & including the user turn
            val steps = mutableListOf<ToolStep>()
            var finalMsg: Msg? = null
            var failed = false

            loop@ for (step in 0 until ToolProtocol.MAX_STEPS) {
                status = if (step == 0) Status.THINKING else Status.TOOL
                val sb = StringBuilder()
                val rb = StringBuilder()
                val res = withContext(Dispatchers.IO) {
                    ChatApi.sendStream(apiBase, working) { dc, dr ->
                        if (dc != null) sb.append(dc)
                        if (dr != null) rb.append(dr)
                        if (idx < c.messages.size) {
                            c.messages[idx] = Msg(
                                "assistant",
                                ToolProtocol.visibleText(sb.toString()),
                                rb.toString().ifBlank { null },
                                tools = steps.toList().ifEmpty { null },
                            )
                        }
                    }
                }
                if (res.isFailure) { failed = true; break@loop }
                val m = res.getOrNull()!!
                val full = m.content
                val blocks = ToolProtocol.parse(full)

                if (blocks.isEmpty()) {
                    finalMsg = Msg("assistant", ToolProtocol.visibleText(full), m.reasoning, m.reasoningRaw, steps.toList().ifEmpty { null })
                    break@loop
                }

                // The model built one or more tools — run them in the sandbox.
                val results = StringBuilder()
                withContext(Dispatchers.Default) {
                    blocks.forEachIndexed { i, b ->
                        val st = ToolProtocol.run(b)
                        steps.add(st)
                        results.append("[").append(i + 1).append("] ").append(st.result).append("\n")
                    }
                }
                if (idx < c.messages.size) {
                    c.messages[idx] = Msg("assistant", ToolProtocol.visibleText(full), m.reasoning, m.reasoningRaw, steps.toList())
                }
                working.add(Msg("assistant", full))
                working.add(Msg("user", "TOOL RESULT:\n" + results.toString().trim()))
                // Fallback if we hit the step ceiling without a clean final answer.
                finalMsg = Msg("assistant", ToolProtocol.visibleText(full), m.reasoning, m.reasoningRaw, steps.toList())
            }

            busy = false
            status = Status.READY
            if (failed) {
                if (idx < c.messages.size) c.messages[idx] = Msg("assistant", errText)
            } else {
                val fm = finalMsg ?: Msg("assistant", "")
                if (idx < c.messages.size) c.messages[idx] = fm
                onReply(fm.content)
            }
            persist()
        }
    }
}
