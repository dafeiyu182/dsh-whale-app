package com.dsh.whale

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class FloatingService : Service(), TextToSpeech.OnInitListener {

    private lateinit var wm: WindowManager
    private var view: View? = null
    private var web: WebView? = null
    private lateinit var params: WindowManager.LayoutParams
    private val main = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    private val history = mutableListOf<Pair<String, String>>() // role to content
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        startForegroundSafely()
        // 无界面情况下也要能静默初始化识别器
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(listener)
            }
        }
    }

    private fun startForegroundSafely() {
        val chId = "whale"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(chId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(chId, "大肥鱼助手", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, chId)
                .setContentTitle("大肥鱼在待命")
                .setContentText("点一下鱼说话；长按拖动位置")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .setContentIntent(pi)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("大肥鱼在待命")
                .setContentText("点一下鱼说话")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .setContentIntent(pi)
                .build()
        }
        startForeground(1, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (view == null) showFloating()
        return START_STICKY
    }

    private fun showFloating() {
        // 把 assets 映射到 https://appassets.androidplatform.net/assets/
        // file:// 下 XHR 会被拦，导致 Live2D 模型加载失败（Network error）
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(0x00000000)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }
            }
            addJavascriptInterface(Bridge(), "WhaleNative")
            loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }

        val size = (resources.displayMetrics.widthPixels * 0.2138f).toInt()  // 231dp@1080，对齐 DSH 页内桌宠宽度

        params = WindowManager.LayoutParams(
            size,
            (size * 1.35f).toInt(),   // 与 dsh-pet 一致：宽:高 = 1:1.35
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 8
            y = 240
        }

        val root = webView
        root.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var moved = false
            private var downAt = 0L

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        startX = params.x; startY = params.y
                        moved = false; downAt = System.currentTimeMillis()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (downX - e.rawX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) moved = true
                        if (moved) {
                            params.x = startX + dx
                            params.y = startY + dy
                            wm.updateViewLayout(v, params)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved && System.currentTimeMillis() - downAt < 500) {
                            // 短按：说一句
                            startListening()
                        }
                    }
                }
                return true
            }
        })

        wm.addView(root, params)
        view = root
        web = webView
    }

    // ---------- 语音 ----------

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            web?.post { bubble("听着呢…", 0) }
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            listening = false
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "没听清"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听到声音"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络不给力"
                else -> "识别出错($error)"
            }
            web?.post { bubble(msg, 2500) }
        }
        override fun onPartialResults(partialResults: android.os.Bundle?) {}
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

        override fun onResults(results: android.os.Bundle?) {
            listening = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isBlank()) {
                web?.post { bubble("没听清", 2000) }
                return
            }
            web?.post { bubble("你：$text", 4000) }
            ask(text)
        }
    }

    private fun startListening() {
        if (listening) return
        if (recognizer == null) {
            web?.post { bubble("这台手机没有语音识别服务", 3000) }
            return
        }
        listening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        main.post {
            try {
                recognizer?.cancel()
                recognizer?.startListening(intent)
            } catch (t: Throwable) {
                listening = false
                web?.post { bubble("启动识别失败：${t.message}", 3000) }
            }
        }
    }

    // ---------- 对话 ----------

    private fun apiKey(): String =
        getSharedPreferences("whale", MODE_PRIVATE).getString("apiKey", "").orEmpty()

    private fun persona(): String =
        getSharedPreferences("whale", MODE_PRIVATE)
            .getString("persona", MainActivity.DEFAULT_PERSONA).orEmpty()

    private fun ask(question: String) {
        val key = apiKey()
        if (key.isBlank()) {
            bubble("还没填 API Key", 3000); return
        }
        web?.post { bubble("…", 0) }

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", persona()))
        // 只带最近 6 轮，省 token
        history.takeLast(12).forEach { (role, content) ->
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        messages.put(JSONObject().put("role", "user").put("content", question))

        val body = JSONObject()
            .put("model", "deepseek-chat")
            .put("messages", messages)
            .put("temperature", 1.0)
            .put("max_tokens", 400)
            .put("stream", false)

        val req = okhttp3.Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                web?.post { bubble("网络出错：${e.message}", 4000) }
            }

            override fun onResponse(call: okhttp3.Call, resp: okhttp3.Response) {
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    web?.post { bubble("接口报错 ${resp.code}", 4000) }
                    return
                }
                try {
                    val json = JSONObject(raw)
                    val reply = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()
                    history.add("user" to question)
                    history.add("assistant" to reply)
                    if (history.size > 20) history.subList(0, history.size - 20).clear()
                    speak(reply)
                } catch (t: Throwable) {
                    web?.post { bubble("解析失败", 4000) }
                }
            }
        })
    }

    // ---------- 说话 ----------

    private fun speak(text: String) {
        web?.post {
            bubble(text, 0)
            web?.evaluateJavascript("window.startTalk && window.startTalk()", null)
        }
        if (!ttsReady) {
            web?.post { bubble("(TTS 未就绪)", 3000) }
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "whale-${System.currentTimeMillis()}")
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.SIMPLIFIED_CHINESE
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    web?.post { web?.evaluateJavascript("window.setMouth(true)", null) }
                }
                override fun onDone(utteranceId: String?) {
                    web?.post {
                        web?.evaluateJavascript("window.setMouth(false)", null)
                        web?.evaluateJavascript("window.stopTalk && window.stopTalk()", null)
                    }
                }
                @Deprecated("deprecated")
                override fun onError(utteranceId: String?) {
                    web?.post { web?.evaluateJavascript("window.setMouth(false)", null) }
                }
            })
        }
    }

    private fun bubble(text: String, autoHideMs: Int) {
        val safe = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
        web?.evaluateJavascript("window.showBubble && window.showBubble('$safe', $autoHideMs)", null)
    }

    /** 给网页用：让它能主动问我 */
    inner class Bridge {
        @JavascriptInterface
        fun ask(text: String) {
            if (text.isNotBlank()) ask(text)
        }
        @JavascriptInterface
        fun listen() {
            startListening()
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop(); tts?.shutdown()
        view?.let { runCatching { wm.removeView(it) } }
        view = null; web = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
