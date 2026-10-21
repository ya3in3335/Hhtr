package com.ultron.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultron.chat.net.ChatApi
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
            val history = c.messages.take(idx)    // everything up to and including the user turn
            val sb = StringBuilder()
            val rb = StringBuilder()
            val res = withContext(Dispatchers.IO) {
                ChatApi.sendStream(apiBase, history) { dc, dr ->
                    if (dc != null) sb.append(dc)
                    if (dr != null) rb.append(dr)
                    if (idx < c.messages.size) {
                        c.messages[idx] = Msg("assistant", sb.toString(), rb.toString().ifBlank { null })
                    }
                }
            }
            busy = false
            status = Status.READY
            res.onSuccess { m -> if (idx < c.messages.size) c.messages[idx] = m; onReply(m.content) }
                .onFailure { if (idx < c.messages.size) c.messages[idx] = Msg("assistant", errText) }
            persist()
        }
    }
}
