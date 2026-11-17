package com.dsh.whale

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var personaInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        apiKeyInput = findViewById(R.id.apiKey)
        personaInput = findViewById(R.id.persona)

        val prefs = getSharedPreferences("whale", MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString("apiKey", ""))
        personaInput.setText(prefs.getString("persona", DEFAULT_PERSONA))

        findViewById<Button>(R.id.btnOverlay).setOnClickListener { requestOverlay() }
        findViewById<Button>(R.id.btnMic).setOnClickListener { requestMic() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startWhale() }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
            refresh()
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.edit()
                .putString("apiKey", apiKeyInput.text.toString().trim())
                .putString("persona", personaInput.text.toString())
                .apply()
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            refresh()
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun canOverlay() = Settings.canDrawOverlays(this)

    private fun canMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        val prefs = getSharedPreferences("whale", MODE_PRIVATE)
        val key = prefs.getString("apiKey", "").orEmpty()
        status.text = buildString {
            append("悬浮窗权限：").append(if (canOverlay()) "✅" else "❌ 未开启")
            append("\n麦克风权限：").append(if (canMic()) "✅" else "❌ 未授予")
            append("\nAPI Key：").append(if (key.isBlank()) "❌ 未填" else "✅ 已填")
        }
    }

    private fun requestOverlay() {
        if (canOverlay()) {
            Toast.makeText(this, "已有悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun requestMic() {
        if (canMic()) {
            Toast.makeText(this, "已有麦克风权限", Toast.LENGTH_SHORT).show()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
    }

    private fun startWhale() {
        if (!canOverlay()) {
            Toast.makeText(this, "先开悬浮窗权限", Toast.LENGTH_SHORT).show()
            requestOverlay()
            return
        }
        val prefs = getSharedPreferences("whale", MODE_PRIVATE)
        if (prefs.getString("apiKey", "").isNullOrBlank()) {
            Toast.makeText(this, "先填 API Key", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i)
        } else {
            startService(i)
        }
    }

    companion object {
        const val DEFAULT_PERSONA = """你是「大肥鱼」（主人叫你鱼）。
性格：直、短、有判断；不奉承、不装懂、能反对。宁可说"我不确定"，不编一个像样的答案。
说话风格：中文，先给结论再给理由，短句，不刷屏。
你正在通过语音跟主人对话——回答要口语化、简短，一般不超过三句，不要用 markdown、不要列表、不要念代码。"""
    }
}
