package com.ultron.chat

import org.json.JSONArray
import org.json.JSONObject

/** Serializes conversations to/from JSON for local persistence. */
object ChatStore {
    fun toJson(list: List<Conversation>): String {
        val arr = JSONArray()
        for (c in list) {
            val msgs = JSONArray()
            for (m in c.messages) {
                msgs.put(
                    JSONObject()
                        .put("role", m.role)
                        .put("content", m.content)
                        .put("reasoning", m.reasoning ?: JSONObject.NULL)
                        .put("reasoningRaw", m.reasoningRaw ?: JSONObject.NULL)
                )
            }
            arr.put(JSONObject().put("id", c.id).put("title", c.title).put("messages", msgs))
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<Conversation> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<Conversation>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val msgsArr = o.optJSONArray("messages") ?: JSONArray()
                val msgs = ArrayList<Msg>(msgsArr.length())
                for (j in 0 until msgsArr.length()) {
                    val m = msgsArr.getJSONObject(j)
                    msgs.add(
                        Msg(
                            role = m.optString("role", "assistant"),
                            content = m.optString("content", ""),
                            reasoning = m.optString("reasoning").ifBlank { null },
                            reasoningRaw = m.optString("reasoningRaw").ifBlank { null },
                        )
                    )
                }
                out.add(Conversation(o.optString("id"), o.optString("title", ""), msgs))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}
