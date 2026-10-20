package com.ultron.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultron.chat.net.ChatApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel : ViewModel() {
    val messages = mutableStateListOf<Msg>()
    var status by mutableStateOf(Status.READY)
        private set
    var busy by mutableStateOf(false)
        private set

    fun setStatus(s: Status) { status = s }

    fun clear() {
        messages.clear()
        status = Status.READY
    }

    /** Sends the user text, appends the reply (or a localized error), then calls onReply with spoken text. */
    fun send(apiBase: String, text: String, errText: String, onReply: (String) -> Unit) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        messages.add(Msg("user", t))
        busy = true
        status = Status.THINKING
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { ChatApi.send(apiBase, messages.toList()) }
            busy = false
            status = Status.READY
            res.onSuccess { m ->
                messages.add(m)
                onReply(m.content)
            }.onFailure {
                messages.add(Msg("assistant", errText))
            }
        }
    }
}
