package com.fynn.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class WakeWordService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var overlay: TextView? = null
    private var listening = false
    private var lastWakeAt = 0L
    private val channelId = "jarvis_wake_listener"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }.setContentTitle("JARVIS / ULTRON listening")
            .setContentText("Say “Hey Jarvis” or “Hey Ultron” to wake an assistant")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
        startForeground(41, notification)
        if (SpeechRecognizer.isRecognitionAvailable(this)) startListening()
        else Toast.makeText(this, "Speech recognition is unavailable on this device.", Toast.LENGTH_LONG).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!listening) startListening()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Background wake words", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Visible notification while wake-word listening is active"
                }
            )
        }
    }

    private fun startListening() {
        if (listening || !SpeechRecognizer.isRecognitionAvailable(this)) return
        listening = true
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { speech ->
                speech.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { listening = false; restartSoon(250) }
                    override fun onError(error: Int) { listening = false; restartSoon(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200 else 500) }
                    override fun onResults(results: android.os.Bundle?) {
                        listening = false
                        val lines = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        val phrase = lines.firstOrNull().orEmpty()
                        detectWakeWord(phrase)
                        restartSoon(350)
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        val phrase = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        detectWakeWord(phrase)
                    }
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
                val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                speech.startListening(request)
            }
        } catch (_: Exception) {
            listening = false
            restartSoon(1500)
        }
    }

    private fun restartSoon(delay: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE).getBoolean("background_wake_enabled", false)) startListening() else stopSelf() }, delay)
    }

    private fun detectWakeWord(raw: String) {
        val phrase = raw.lowercase(Locale.ROOT)
        val assistant = when {
            Regex("\\b(hey\\s+)?jarvis\\b").containsMatchIn(phrase) -> "Jarvis"
            Regex("\\b(hey\\s+)?ultron\\b").containsMatchIn(phrase) -> "Ultron"
            else -> return
        }
        val now = System.currentTimeMillis()
        if (now - lastWakeAt < 4500) return
        lastWakeAt = now
        val cleaned = phrase
            .replace(Regex("\\bhey\\s+jarvis\\b|\\bjarvis\\b|\\bhey\\s+ultron\\b|\\bultron\\b", RegexOption.IGNORE_CASE), "")
            .trim(' ', ',', '.', '?', '!', ':', ';')
        showPopup(assistant)
        val open = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_ASSISTANT, assistant)
            putExtra(EXTRA_WAKE_TEXT, cleaned)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // Keep the popup visible; tapping it opens the assistant with any words spoken after the wake name.
        overlay?.setOnClickListener { startActivity(open); removePopup() }
        val nm = getSystemService(NotificationManager::class.java)
        val openIntent = PendingIntent.getActivity(this, 2, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, channelId) else @Suppress("DEPRECATION") Notification.Builder(this)
        nm.notify(41, notification.setContentTitle("$assistant is awake")
            .setContentText("Tap to open and talk. Listening remains active.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent).setOngoing(true).build())
    }

    private fun showPopup(assistant: String) {
        if (!Settings.canDrawOverlays(this)) {
            handler.post { Toast.makeText(this, "Wake word detected: $assistant. Allow “Display over other apps” for the popup.", Toast.LENGTH_LONG).show() }
            return
        }
        handler.post {
            removePopup()
            val bubble = TextView(this).apply {
                text = "  $assistant is awake  ·  Tap to talk  "
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(22, 18, 22, 18)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(7, 28, 49))
                    cornerRadius = 48f
                    setStroke(2, if (assistant == "Jarvis") Color.rgb(75, 190, 255) else Color.rgb(190, 80, 255))
                }
                elevation = 12f
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 28 }
            try {
                getSystemService(WindowManager::class.java).addView(bubble, params)
                overlay = bubble
                handler.postDelayed({ removePopup() }, 12000)
            } catch (_: Exception) { overlay = null }
        }
    }

    private fun removePopup() {
        handler.removeCallbacksAndMessages(null)
        val current = overlay ?: return
        try { getSystemService(WindowManager::class.java).removeView(current) } catch (_: Exception) {}
        overlay = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        listening = false
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        removePopup()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.fynn.jarvis.STOP_WAKE_LISTENER"
        const val EXTRA_ASSISTANT = "wake_assistant"
        const val EXTRA_WAKE_TEXT = "wake_text"
    }
}
