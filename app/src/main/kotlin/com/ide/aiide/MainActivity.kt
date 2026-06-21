package com.ide.aiide

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var inputMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnSettings: Button
    private lateinit var btnMenuToggle: Button
    private lateinit var btnNewChat: Button
    private lateinit var btnClearAllHistory: Button
    private lateinit var chatContainer: LinearLayout
    private lateinit var historyContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var sideMenu: LinearLayout
    private lateinit var mainContent: LinearLayout
    private lateinit var tvHistoryCount: TextView
    private lateinit var tvApiStatus: TextView
    private lateinit var tvUserName: TextView
    private lateinit var previewContainer: FrameLayout
    private lateinit var tvLiveStatus: TextView
    private lateinit var btnFloatingPreview: Button
    private lateinit var btnClosePreview: Button
    private lateinit var previewArea: LinearLayout
    private lateinit var webPreview: WebView
    private lateinit var previewType: TextView

    private var apiKey: String = ""
    private lateinit var apiService: ApiService
    private lateinit var chatHistory: ChatHistory
    
    private var currentSessionId: String = ""
    private val currentMessages = mutableListOf<ChatHistory.ChatMessage>()
    private var isMenuOpen = false

    private var userId: String = ""
    private var userName: String = "Guest"
    
    private var lastRequestTime = 0L
    private val MIN_REQUEST_INTERVAL = 5000L
    
    private lateinit var floatingPreviewDialog: FloatingPreviewDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        userId = intent.getStringExtra("userId") ?: ""
        userName = intent.getStringExtra("userName") ?: "Guest"

        val prefs = getSharedPreferences("GroqAI", MODE_PRIVATE)
        apiKey = prefs.getString("api_key", "") ?: ""

        apiService = ApiService(apiKey)
        chatHistory = ChatHistory(this)

        initViews()
        setupWebPreview()
        setupClickListeners()
        
        loadFromLocal()
        updateApiStatus()
        
        floatingPreviewDialog = FloatingPreviewDialog(this)
    }

    private fun initViews() {
        inputMessage = findViewById(R.id.inputMessage)
        btnSend = findViewById(R.id.btnSend)
        btnSettings = findViewById(R.id.btnSettings)
        btnMenuToggle = findViewById(R.id.btnMenuToggle)
        btnNewChat = findViewById(R.id.btnNewChat)
        btnClearAllHistory = findViewById(R.id.btnClearAllHistory)
        chatContainer = findViewById(R.id.chatContainer)
        historyContainer = findViewById(R.id.historyContainer)
        scrollView = findViewById(R.id.scrollView)
        sideMenu = findViewById(R.id.sideMenu)
        mainContent = findViewById(R.id.mainContent)
        tvHistoryCount = findViewById(R.id.tvHistoryCount)
        tvApiStatus = findViewById(R.id.tvApiStatus)
        tvUserName = findViewById(R.id.tvUserName)
        previewContainer = findViewById(R.id.previewContainer)
        tvLiveStatus = findViewById(R.id.tvLiveStatus)
        btnFloatingPreview = findViewById(R.id.btnFloatingPreview)
        btnClosePreview = findViewById(R.id.btnClosePreview)
        previewArea = findViewById(R.id.previewArea)
        webPreview = findViewById(R.id.webPreview)
        previewType = findViewById(R.id.previewType)
        
        tvUserName.text = "👤 $userName"
        
        btnClosePreview.setOnClickListener {
            previewArea.visibility = View.GONE
        }
    }

    private fun setupWebPreview() {
        webPreview.settings.javaScriptEnabled = true
        webPreview.settings.domStorageEnabled = true
        webPreview.settings.loadWithOverviewMode = true
        webPreview.settings.useWideViewPort = true
        webPreview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }
    }

    private fun loadFromLocal() {
        val sessions = chatHistory.loadSessions()
        if (sessions.isEmpty()) {
            createNewSession()
        } else {
            val latestSession = sessions.first()
            currentSessionId = latestSession.id
            currentMessages.clear()
            currentMessages.addAll(latestSession.messages)
            displayMessages(latestSession.messages)
            loadAllSessions()
        }
    }

    private fun toggleMenu() {
        isMenuOpen = !isMenuOpen
        if (isMenuOpen) {
            sideMenu.visibility = View.VISIBLE
            val menuWidth = (resources.displayMetrics.widthPixels * 0.75).toInt()
            val menuParams = sideMenu.layoutParams
            menuParams.width = menuWidth
            sideMenu.layoutParams = menuParams
            mainContent.visibility = View.GONE
        } else {
            sideMenu.visibility = View.GONE
            val menuParams = sideMenu.layoutParams
            menuParams.width = 0
            sideMenu.layoutParams = menuParams
            mainContent.visibility = View.VISIBLE
        }
    }

    private fun closeMenu() {
        if (isMenuOpen) {
            toggleMenu()
        }
    }

    private fun setupClickListeners() {
        btnMenuToggle.setOnClickListener {
            toggleMenu()
        }

        btnNewChat.setOnClickListener {
            createNewSession()
            closeMenu()
        }

        btnClearAllHistory.setOnClickListener {
            chatHistory.clearAll()
            historyContainer.removeAllViews()
            loadAllSessions()
            createNewSession()
            Toast.makeText(this, "All history cleared ✅", Toast.LENGTH_SHORT).show()
            closeMenu()
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        btnSend.setOnClickListener {
            val message = inputMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                if (apiKey.isEmpty()) {
                    Toast.makeText(this, "Please add Groq API Key in Settings ⚙️", Toast.LENGTH_SHORT).show()
                    showSettingsDialog()
                } else {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastRequestTime < MIN_REQUEST_INTERVAL) {
                        val waitTime = (MIN_REQUEST_INTERVAL - (currentTime - lastRequestTime)) / 1000
                        Toast.makeText(this, "⏳ Please wait ${waitTime+1} seconds", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    lastRequestTime = currentTime
                    sendMessageToGroq(message)
                }
            } else {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            }
        }

        btnFloatingPreview.setOnClickListener {
            showFloatingPreview()
        }
    }

    private fun createNewSession() {
        currentSessionId = System.currentTimeMillis().toString()
        currentMessages.clear()
        chatContainer.removeAllViews()
        
        val welcomeMsg = if (apiKey.isEmpty()) {
            "👋 Hello $userName! Please add your Groq API Key in Settings ⚙️\n\n💡 Try: 'Create a login screen'"
        } else {
            "🚀 Hello $userName! I am IT Expert AI (Powered by Groq).\n\n💡 Try: 'Create a login screen'"
        }
        
        addBotMessageUI(welcomeMsg)
        currentMessages.add(ChatHistory.ChatMessage("assistant", welcomeMsg))
        chatHistory.addSession("New Chat", currentMessages)
        loadAllSessions()
    }

    private fun loadAllSessions() {
        historyContainer.removeAllViews()
        val sessions = chatHistory.loadSessions()
        tvHistoryCount.text = "${sessions.size} conversations"
        
        sessions.forEach { session ->
            addHistoryItem(session)
        }
    }

    private fun addHistoryItem(session: ChatHistory.ChatSession) {
        val itemView = TextView(this)
        itemView.text = session.title
        itemView.setTextColor(Color.WHITE)
        itemView.setPadding(16, 16, 16, 16)
        itemView.textSize = 14f
        itemView.typeface = Typeface.DEFAULT_BOLD
        
        if (session.id == currentSessionId) {
            itemView.setBackgroundResource(R.drawable.bg_chat_user)
        } else {
            itemView.setBackgroundColor(Color.TRANSPARENT)
        }
        
        itemView.setOnClickListener {
            loadSession(session.id)
            closeMenu()
        }
        
        itemView.setOnLongClickListener {
            chatHistory.deleteSession(session.id)
            loadAllSessions()
            if (session.id == currentSessionId) {
                createNewSession()
            }
            Toast.makeText(this, "Session deleted", Toast.LENGTH_SHORT).show()
            true
        }
        
        historyContainer.addView(itemView)
    }

    private fun loadSession(sessionId: String) {
        val sessions = chatHistory.loadSessions()
        val session = sessions.find { it.id == sessionId }
        if (session != null) {
            currentSessionId = sessionId
            currentMessages.clear()
            currentMessages.addAll(session.messages)
            chatContainer.removeAllViews()
            displayMessages(session.messages)
            loadAllSessions()
        }
    }

    private fun displayMessages(messages: List<ChatHistory.ChatMessage>) {
        messages.forEach { msg ->
            if (msg.role == "user") {
                addUserMessageUI(msg.content)
            } else {
                addBotMessageUI(msg.content)
            }
        }
    }

    private fun sendMessageToGroq(message: String) {
        addUserMessageUI(message)
        currentMessages.add(ChatHistory.ChatMessage("user", message))
        
        showPreviewLoading()

        val sessions = chatHistory.loadSessions()
        val sessionIndex = sessions.indexOfFirst { it.id == currentSessionId }
        if (sessionIndex != -1 && sessions[sessionIndex].messages.isEmpty()) {
            chatHistory.updateSession(currentSessionId, currentMessages)
            loadAllSessions()
        }

        inputMessage.text.clear()
        showTypingIndicator()
        scrollView.fullScroll(ScrollView.FOCUS_DOWN)

        apiService.sendMessage(
            messages = currentMessages,
            onSuccess = { response ->
                removeTypingIndicator()
                addBotMessageUI(response)
                currentMessages.add(ChatHistory.ChatMessage("assistant", response))
                chatHistory.updateSession(currentSessionId, currentMessages)
                loadAllSessions()
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                
                renderLayoutPreview(response)
            },
            onError = { error ->
                removeTypingIndicator()
                addBotMessageUI(error)
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                showPreviewError(error)
            }
        )
    }

    private fun renderLayoutPreview(response: String) {
        try {
            val lowerResponse = response.lowercase()
            
            val uiKeywords = listOf(
                "login", "screen", "ui", "layout", "button", 
                "edittext", "textview", "imageview", "linearlayout",
                "relativelayout", "constraintlayout", "scrollview",
                "recyclerview", "cardview", "material", "design",
                "xml", "web", "html", "python", "flutter"
            )
            
            val hasUI = uiKeywords.any { lowerResponse.contains(it) }
            
            if (hasUI || response.contains("<?xml") || response.contains("<html")) {
                buildLoginScreenUI()
                showFloatingPreview()
            } else {
                showTextPreview(response)
            }
            
        } catch (e: Exception) {
            showPreviewError("Error: ${e.message}")
        }
    }

    private fun buildLoginScreenUI() {
        try {
            previewContainer.removeAllViews()
            
            val container = LinearLayout(this)
            container.orientation = LinearLayout.VERTICAL
            container.gravity = Gravity.CENTER
            container.setPadding(32, 32, 32, 32)
            container.setBackgroundColor(Color.parseColor("#1A1A2E"))
            container.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            val logo = TextView(this)
            logo.text = "🤖"
            logo.textSize = 72f
            logo.gravity = Gravity.CENTER
            container.addView(logo)
            
            val title = TextView(this)
            title.text = "IT Expert AI"
            title.textSize = 28f
            title.setTextColor(Color.WHITE)
            title.gravity = Gravity.CENTER
            title.typeface = Typeface.DEFAULT_BOLD
            container.addView(title)
            
            val subtitle = TextView(this)
            subtitle.text = "Login to continue"
            subtitle.textSize = 16f
            subtitle.setTextColor(Color.parseColor("#A0A0C0"))
            subtitle.gravity = Gravity.CENTER
            container.addView(subtitle)
            
            val emailInput = EditText(this)
            emailInput.hint = "Email"
            emailInput.setTextColor(Color.WHITE)
            emailInput.setHintTextColor(Color.parseColor("#666688"))
            emailInput.setBackgroundResource(R.drawable.bg_input)
            emailInput.setPadding(16, 16, 16, 16)
            emailInput.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            container.addView(emailInput)
            
            val passwordInput = EditText(this)
            passwordInput.hint = "Password"
            passwordInput.setTextColor(Color.WHITE)
            passwordInput.setHintTextColor(Color.parseColor("#666688"))
            passwordInput.setBackgroundResource(R.drawable.bg_input)
            passwordInput.setPadding(16, 16, 16, 16)
            passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordInput.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            container.addView(passwordInput)
            
            val loginBtn = Button(this)
            loginBtn.text = "Login"
            loginBtn.textSize = 18f
            loginBtn.setTextColor(Color.WHITE)
            loginBtn.setBackgroundResource(R.drawable.bg_send_button)
            loginBtn.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                130
            )
            loginBtn.setOnClickListener {
                Toast.makeText(this, "🔐 Login clicked!", Toast.LENGTH_SHORT).show()
            }
            container.addView(loginBtn)
            
            val signup = TextView(this)
            signup.text = "Don't have an account? Sign Up"
            signup.textSize = 14f
            signup.setTextColor(Color.parseColor("#A0A0C0"))
            signup.gravity = Gravity.CENTER
            container.addView(signup)
            
            previewContainer.addView(container)
            
            previewArea.visibility = View.VISIBLE
            tvLiveStatus.text = "● Login Screen UI"
            tvLiveStatus.setTextColor(Color.parseColor("#10A37F"))
            
        } catch (e: Exception) {
            showPreviewError("UI Error: ${e.message}")
        }
    }

    private fun showFloatingPreview() {
        val child = previewContainer.getChildAt(0)
        if (child == null || child is TextView) {
            return
        }
        
        floatingPreviewDialog.setPreviewTitle("📱 Login Screen Preview")
        floatingPreviewDialog.setPreviewContent(child)
        floatingPreviewDialog.show()
    }

    private fun showTextPreview(text: String) {
        previewContainer.removeAllViews()
        val textView = TextView(this)
        textView.text = text
        textView.setTextColor(Color.WHITE)
        textView.textSize = 14f
        textView.setPadding(16, 16, 16, 16)
        textView.typeface = Typeface.MONOSPACE
        previewContainer.addView(textView)
        tvLiveStatus.text = "● Text Preview"
        tvLiveStatus.setTextColor(Color.parseColor("#F59E0B"))
        previewArea.visibility = View.VISIBLE
    }

    private fun showPreviewLoading() {
        tvLiveStatus.text = "⟳ Generating..."
        tvLiveStatus.setTextColor(Color.parseColor("#F59E0B"))
        previewContainer.removeAllViews()
        val loadingView = TextView(this)
        loadingView.text = "⏳ Generating Layout Preview...\n\nPlease wait..."
        loadingView.setTextColor(Color.WHITE)
        loadingView.textSize = 16f
        loadingView.gravity = Gravity.CENTER
        previewContainer.addView(loadingView)
        previewArea.visibility = View.VISIBLE
    }

    private fun showPreviewError(error: String) {
        tvLiveStatus.text = "● Error"
        tvLiveStatus.setTextColor(Color.parseColor("#EF4444"))
        previewContainer.removeAllViews()
        val errorView = TextView(this)
        errorView.text = "❌ Error\n\n$error"
        errorView.setTextColor(Color.parseColor("#EF4444"))
        errorView.textSize = 14f
        errorView.setPadding(16, 16, 16, 16)
        previewContainer.addView(errorView)
        previewArea.visibility = View.VISIBLE
    }

    private fun addUserMessageUI(message: String) {
        val textView = TextView(this)
        textView.text = message
        textView.setTextColor(Color.WHITE)
        textView.setBackgroundResource(R.drawable.bg_chat_user)
        textView.setPadding(28, 22, 28, 22)
        textView.textSize = 18f
        textView.typeface = Typeface.DEFAULT_BOLD
        textView.elevation = 8f
        textView.setShadowLayer(4f, 0f, 2f, Color.BLACK)
        
        textView.setTextIsSelectable(true)
        textView.isClickable = true
        textView.setOnLongClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Copied Text", message)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "📋 Copied to clipboard!", Toast.LENGTH_SHORT).show()
            true
        }
        
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.END
        params.setMargins(48, 12, 0, 12)
        textView.layoutParams = params
        
        chatContainer.addView(textView)
        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
    }

    private fun addBotMessageUI(message: String) {
        val textView = TextView(this)
        textView.text = message
        textView.setTextColor(Color.WHITE)
        textView.setBackgroundResource(R.drawable.bg_chat_bot)
        textView.setPadding(28, 22, 28, 22)
        textView.textSize = 18f
        textView.typeface = Typeface.DEFAULT
        textView.elevation = 8f
        textView.setShadowLayer(4f, 0f, 2f, Color.BLACK)
        
        textView.setTextIsSelectable(true)
        textView.isClickable = true
        textView.setOnLongClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Copied Text", message)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "📋 Copied to clipboard!", Toast.LENGTH_SHORT).show()
            true
        }
        
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.START
        params.setMargins(0, 12, 48, 12)
        textView.layoutParams = params
        
        chatContainer.addView(textView)
        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
    }

    private fun showTypingIndicator() {
        val textView = TextView(this)
        textView.text = "AI is thinking... 🤔"
        textView.setTextColor(Color.WHITE)
        textView.setBackgroundResource(R.drawable.bg_chat_bot)
        textView.setPadding(28, 22, 28, 22)
        textView.textSize = 16f
        textView.typeface = Typeface.DEFAULT
        textView.elevation = 6f
        textView.id = 999
        
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.START
        params.setMargins(0, 12, 48, 12)
        textView.layoutParams = params
        
        chatContainer.addView(textView)
        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
    }

    private fun removeTypingIndicator() {
        val view = chatContainer.findViewById<TextView>(999)
        if (view != null) {
            chatContainer.removeView(view)
        }
    }

    private fun updateApiStatus() {
        if (apiKey.isNotEmpty()) {
            tvApiStatus.text = "⚡ Groq API: Connected ✅"
            tvApiStatus.setTextColor(Color.parseColor("#10A37F"))
        } else {
            tvApiStatus.text = "⚠️ API: Not Connected"
            tvApiStatus.setTextColor(Color.parseColor("#EF4444"))
        }
    }

    private fun showSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_settings)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(true)

        val editApiKey = dialog.findViewById<EditText>(R.id.editApiKey)
        val btnSave = dialog.findViewById<Button>(R.id.btnSave)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)
        val btnCreateApiKey = dialog.findViewById<Button>(R.id.btnCreateApiKey)

        editApiKey.setText(apiKey)

        btnCreateApiKey.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, 
                android.net.Uri.parse("https://console.groq.com/keys"))
            startActivity(intent)
            Toast.makeText(this, "🌐 Open Groq Console to create API Key", Toast.LENGTH_LONG).show()
        }

        btnSave.setOnClickListener {
            val key = editApiKey.text.toString().trim()

            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter Groq API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val prefs = getSharedPreferences("GroqAI", MODE_PRIVATE)
            prefs.edit().apply {
                putString("api_key", key)
                apply()
            }

            apiKey = key
            apiService = ApiService(key)

            Toast.makeText(this, "Groq API Key saved ✅", Toast.LENGTH_SHORT).show()
            updateApiStatus()
            dialog.dismiss()

            chatContainer.removeAllViews()
            addBotMessageUI("🚀 Groq API Key saved! Try: 'Create a login screen'")
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
