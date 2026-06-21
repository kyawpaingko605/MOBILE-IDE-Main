package com.ide.aiide

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ChatHistory(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ChatHistory", Context.MODE_PRIVATE)

    data class ChatMessage(
        val role: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Chat session တစ်ခုလုံးကို သိမ်းမယ်
    data class ChatSession(
        val id: String,
        val title: String,
        val messages: List<ChatMessage>,
        val timestamp: Long
    )

    // Session အကုန်ကို သိမ်းမယ်
    fun saveSessions(sessions: List<ChatSession>) {
        val jsonArray = JSONArray()
        sessions.forEach { session ->
            val messagesArray = JSONArray()
            session.messages.forEach { msg ->
                val msgObj = JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                    put("timestamp", msg.timestamp)
                }
                messagesArray.put(msgObj)
            }
            
            val sessionObj = JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("messages", messagesArray)
                put("timestamp", session.timestamp)
            }
            jsonArray.put(sessionObj)
        }
        prefs.edit().putString("chat_sessions", jsonArray.toString()).apply()
    }

    // Session အကုန်ကို ပြန်ဖတ်မယ်
    fun loadSessions(): List<ChatSession> {
        val jsonString = prefs.getString("chat_sessions", "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val sessions = mutableListOf<ChatSession>()
        
        for (i in 0 until jsonArray.length()) {
            val sessionObj = jsonArray.getJSONObject(i)
            val id = sessionObj.getString("id")
            val title = sessionObj.getString("title")
            val timestamp = sessionObj.getLong("timestamp")
            
            val messagesArray = sessionObj.getJSONArray("messages")
            val messages = mutableListOf<ChatMessage>()
            for (j in 0 until messagesArray.length()) {
                val msgObj = messagesArray.getJSONObject(j)
                val role = msgObj.getString("role")
                val content = msgObj.getString("content")
                val msgTime = msgObj.getLong("timestamp")
                messages.add(ChatMessage(role, content, msgTime))
            }
            
            sessions.add(ChatSession(id, title, messages, timestamp))
        }
        return sessions
    }

    // Session အသစ်ထည့်မယ်
    fun addSession(title: String, messages: List<ChatMessage>): ChatSession {
        val sessions = loadSessions().toMutableList()
        val id = System.currentTimeMillis().toString()
        val session = ChatSession(id, title, messages, System.currentTimeMillis())
        sessions.add(0, session)  // အသစ်ကို အပေါ်ဆုံးထား
        saveSessions(sessions)
        return session
    }

    // Session ကို update လုပ်မယ်
    fun updateSession(sessionId: String, messages: List<ChatMessage>) {
        val sessions = loadSessions().toMutableList()
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index != -1) {
            val oldSession = sessions[index]
            val title = if (messages.isNotEmpty()) {
                messages.first().content.take(30) + "..."
            } else {
                "New Chat"
            }
            sessions[index] = oldSession.copy(
                title = title,
                messages = messages,
                timestamp = System.currentTimeMillis()
            )
            saveSessions(sessions)
        }
    }

    // Session ကိုဖျက်မယ်
    fun deleteSession(sessionId: String) {
        val sessions = loadSessions().toMutableList()
        sessions.removeAll { it.id == sessionId }
        saveSessions(sessions)
    }

    // အကုန်ဖျက်မယ်
    fun clearAll() {
        prefs.edit().remove("chat_sessions").apply()
    }

    // Session တစ်ခုရဲ့ messages တွေကို ရှင်းမယ်
    fun clearSessionMessages(sessionId: String) {
        val sessions = loadSessions().toMutableList()
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index != -1) {
            sessions[index] = sessions[index].copy(messages = emptyList())
            saveSessions(sessions)
        }
    }
}