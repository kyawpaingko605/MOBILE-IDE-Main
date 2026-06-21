package com.ide.aiide

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button

class LoginActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val btnGuestSignIn = findViewById<Button>(R.id.btnGuestSignIn)

        btnGuestSignIn.setOnClickListener {
            // Button animation effect
            btnGuestSignIn.isEnabled = false
            
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("userId", "guest_${System.currentTimeMillis()}")
                intent.putExtra("userName", "Guest")
                startActivity(intent)
                finish()
            }, 300)
        }
    }
}