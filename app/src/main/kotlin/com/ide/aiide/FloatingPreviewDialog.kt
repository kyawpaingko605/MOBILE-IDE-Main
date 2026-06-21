package com.ide.aiide

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

class FloatingPreviewDialog(context: Context) : Dialog(context) {

    private lateinit var previewContainer: FrameLayout
    private lateinit var tvPreviewTitle: TextView
    private lateinit var btnClose: Button
    private lateinit var btnMinimize: Button

    init {
        setContentView(R.layout.dialog_floating_preview)
        window?.setGravity(Gravity.CENTER)
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        setCancelable(true)

        previewContainer = findViewById(R.id.floatingPreviewContainer)
        tvPreviewTitle = findViewById(R.id.tvPreviewTitle)
        btnClose = findViewById(R.id.btnCloseFloatingPreview)
        btnMinimize = findViewById(R.id.btnMinimizeFloatingPreview)

        btnClose.setOnClickListener {
            dismiss()
        }

        btnMinimize.setOnClickListener {
            window?.setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            tvPreviewTitle.text = "📱 Preview (Minimized)"
        }

        tvPreviewTitle.setOnClickListener {
            window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            tvPreviewTitle.text = "📱 Live Preview"
        }
    }

    fun setPreviewContent(view: View) {
        previewContainer.removeAllViews()
        previewContainer.addView(view)
    }

    fun setPreviewTitle(title: String) {
        tvPreviewTitle.text = title
    }

    fun updatePreview(view: View) {
        previewContainer.removeAllViews()
        previewContainer.addView(view)
    }
}