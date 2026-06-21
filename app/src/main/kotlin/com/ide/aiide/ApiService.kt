package com.ide.aiide

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class ApiService(private val apiKey: String) {

    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val MAX_RETRY = 3

    fun sendMessage(
        messages: List<ChatHistory.ChatMessage>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        retryCount = 0
        sendMessageWithRetry(messages, onSuccess, onError)
    }

    private fun sendMessageWithRetry(
        messages: List<ChatHistory.ChatMessage>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                // System Prompt (English)
                val systemPrompt = """
                    You are an Expert IT AI Assistant. You have deep knowledge of:
                    - Android Development (Kotlin, Java, XML, Jetpack Compose)
                    - iOS Development (Swift, UIKit, SwiftUI)
                    - Web Development (HTML, CSS, JavaScript, React, Node.js)
                    - Backend (Python, Java, PHP, Node.js)
                    - Databases (SQL, Firebase, MongoDB)
                    - Cloud (AWS, Azure, Google Cloud)
                    - DevOps (Docker, Kubernetes, CI/CD)
                    - AI/ML (TensorFlow, PyTorch)
                    - Game Development (Unity, Unreal Engine)
                    
                    RULES:
                    1. Respond in ENGLISH only
                    2. Provide FULL code with comments
                    3. Explain step by step
                    4. Include best practices
                    5. Remember chat history
                    6. For UI/XML, provide complete layouts
                    7. Code must be complete and runnable
                """.trimIndent()

                val messagesArray = JSONArray()
                messagesArray.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })

                // Only send last 5 messages
                val maxHistory = 5
                val recentMessages = if (messages.size > maxHistory) {
                    messages.takeLast(maxHistory)
                } else {
                    messages
                }
                recentMessages.forEach { msg ->
                    messagesArray.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }

                val jsonBody = JSONObject().apply {
                    put("model", "llama-3.3-70b-versatile")
                    put("temperature", 0.3)
                    put("max_tokens", 2048)
                    put("messages", messagesArray)
                }

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonBody.toString())
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                val inputStream = if (responseCode == 200) connection.inputStream else connection.errorStream
                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                connection.disconnect()

                if (responseCode == 200) {
                    val jsonResponse = JSONObject(response.toString())
                    val content = jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    handler.post { onSuccess(content) }
                } else {
                    val errorMsg = when (responseCode) {
                        401 -> "⚠️ Invalid API Key"
                        403 -> "⚠️ API Key is blocked"
                        413 -> "⚠️ Too many messages. Clear history and try again."
                        429 -> "⚠️ Rate limit: 30 requests per minute. Please wait 1-2 minutes."
                        500 -> "⚠️ Server error. Please try again later."
                        502 -> "⚠️ Bad Gateway. Please try again later."
                        503 -> "⚠️ Service Unavailable. Please try again later."
                        else -> "⚠️ Error: $responseCode"
                    }
                    
                    if (responseCode == 429 && retryCount < MAX_RETRY) {
                        retryCount++
                        val waitTime = retryCount * 3000
                        handler.post {
                            onError("⏳ Waiting ${waitTime/1000}s and retrying... ($retryCount/$MAX_RETRY)")
                        }
                        Thread.sleep(waitTime.toLong())
                        sendMessageWithRetry(messages, onSuccess, onError)
                    } else {
                        handler.post { onError(errorMsg) }
                    }
                }

            } catch (e: UnknownHostException) {
                handler.post {
                    onError("⚠️ No internet connection. Please check WiFi or Mobile Data.")
                }
            } catch (e: SSLException) {
                handler.post {
                    onError("⚠️ SSL Connection error. Please try again later.")
                }
            } catch (e: Exception) {
                handler.post {
                    onError("⚠️ Network error: ${e.message}")
                }
            }
        }.start()
    }
}