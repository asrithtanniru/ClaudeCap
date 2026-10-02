package dev.me.claudeusage

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val label = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 20f
            setTextColor(0xFFFAF9F5.toInt())
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF1F1E1D.toInt())
            addView(label, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
        }
        setContentView(root)
    }
}
