package com.fynn.jarvis

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var input: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var memoryView: TextView
    private val prefs by lazy { getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE) }
    private val requestAudio = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildUi()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(Color.rgb(10, 17, 30))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 30f
            setTextColor(Color.rgb(95, 210, 255))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 8)
        }
        status = TextView(this).apply {
            text = "Systems starting..."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 20)
        }
        transcript = TextView(this).apply {
            text = "Tap the microphone and speak to Jarvis."
            textSize = 17f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12, 0, 16)
        }
        val mic = Button(this).apply {
            text = "TALK TO JARVIS"
            setOnClickListener { listen() }
        }
        input = EditText(this).apply {
            hint = "Type a message or a fact to remember..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val send = Button(this).apply {
            text = "Send message"
            setOnClickListener {
                val message = input.text.toString().trim()
                if (message.isNotEmpty()) {
                    input.setText("")
                    handleMessage(message)
                }
            }
        }
        val aiTitle = TextView(this).apply {
            text = "AI BRAIN SETUP"
            textSize = 14f
            setTextColor(Color.rgb(95, 210, 255))
            setPadding(0, 20, 0, 6)
        }
        apiKeyInput = EditText(this).apply {
            hint = "Paste your Google AI Studio API key"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("gemini_api_key", "") ?: "")
        }
        val saveKey = Button(this).apply {
            text = "Save AI key on this device"
            setOnClickListener {
                val key = apiKeyInput.text.toString().trim()
                if (key.isBlank()) {
                    status.text = "Paste your Google AI Studio API key first."
                } else {
                    prefs.edit().putString("gemini_api_key", key).apply()
                    status.text = "AI key saved on this device."
                    speak("AI key saved. I am ready to think.")
                }
            }
        }
        val memoryTitle = TextView(this).apply {
            text = "LOCAL MEMORY"
            textSize = 14f
            setTextColor(Color.rgb(95, 210, 255))
            setPadding(0, 20, 0, 6)
        }
        memoryView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 4, 0, 12)
        }
        val saveMemory = Button(this).apply {
            text = "Remember typed fact"
            setOnClickListener { rememberFromInput() }
        }
        content.addView(title, matchWrap())
        content.addView(status, matchWrap())
        content.addView(transcript, matchWrap())
        content.addView(mic, matchWrap())
        content.addView(input, matchWrap())
        content.addView(send, matchWrap())
        content.addView(aiTitle, matchWrap())
        content.addView(apiKeyInput, matchWrap())
        content.addView(saveKey, matchWrap())
        content.addView(memoryTitle, matchWrap())
        content.addView(memoryView, matchWrap())
        content.addView(saveMemory, matchWrap())
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refreshMemory()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            val languageResult = tts?.setLanguage(Locale.US)
            status.text = if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                "Ready. Your device's speech voice may need setup."
            } else {
                "Systems online. Welcome back, Fynn."
            }
            speak("Access granted. Welcome back, Fynn. Jarvis is online.")
        } else {
            status.text = "Speech output could not start. You can still type."
        }
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio)
            status.text = "Allow microphone access, then tap the microphone again."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition is unavailable. You can type instead."
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "Listening..." }
            override fun onBeginningOfSpeech() { status.text = "I'm listening, Fynn." }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "Thinking..." }
            override fun onError(error: Int) { status.text = "Didn't catch that. Tap the microphone to try again." }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!words.isNullOrBlank()) handleMessage(words) else status.text = "I didn't hear any words."
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Jarvis")
        }
        recognizer?.startListening(intent)
    }

    private fun handleMessage(message: String) {
        val lower = message.lowercase(Locale.ROOT)
        val localReply: String? = when {
            lower.contains("what do you remember") || lower.contains("show memory") ->
                "Here's what I remember: ${prefs.getString("facts", "No saved facts yet.")}"
            lower.startsWith("remember that ") -> {
                addMemory(message.substringAfter("remember that ").trim())
                "Understood, Fynn. I'll keep that in my local memory."
            }
            lower.startsWith("remember ") -> {
                addMemory(message.substringAfter("remember ").trim())
                "Saved to my local memory, Fynn."
            }
            lower.contains("who are you") || lower.contains("your name") ->
                "I am Jarvis, your personal voice assistant."
            lower.contains("hello") || lower.contains("hi jarvis") || lower == "hi" ->
                "Hello, Fynn. How can I help?"
            lower.contains("what time") || lower == "time" ->
                "The current device time is ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}."
            else -> null
        }
        if (localReply != null) {
            showReply(message, localReply)
            return
        }
        val key = prefs.getString("gemini_api_key", "")?.trim().orEmpty()
        if (key.isBlank()) {
            val reply = "My AI brain isn't connected yet. Add a Google AI Studio API key in the AI Brain Setup section, then tap Save."
            showReply(message, reply)
            return
        }
        status.text = "Jarvis is thinking..."
        thread {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("x-goog-api-key", key)
                }
                val facts = prefs.getString("facts", "") ?: ""
                val history = prefs.getString("chat_history", "") ?: ""
                val prompt = """You are J.A.R.V.I.S., Fynn's personal voice assistant. Be natural, friendly, direct, useful, and conversational. Answer the user's actual question; never repeat setup/status text unless asked. The user's saved local facts are:
$facts

Recent conversation:
$history

User: $message"""
                val body = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                    .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 500))
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (code !in 200..299) {
                    throw IllegalStateException("Google AI returned HTTP $code. Check the API key and API access.")
                }
                val json = JSONObject(responseText)
                val reply = json.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
                runOnUiThread {
                    val updatedHistory = (history + "
User: " + message + "
Jarvis: " + reply).takeLast(6000)
                    prefs.edit().putString("chat_history", updatedHistory).apply()
                    showReply(message, reply)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val reason = e.message ?: "Network or API error."
                    val reply = "I couldn't reach my AI brain. $reason"
                    showReply(message, reply)
                }
            }
        }
    }

    private fun showReply(message: String, reply: String) {
        transcript.text = "You: $message\n\nJarvis: $reply"
        status.text = "Ready, Fynn."
        speak(reply)
    }

    private fun rememberFromInput() {
        val fact = input.text.toString().trim()
        if (fact.isEmpty()) {
            status.text = "Type the fact you want me to remember first."
            return
        }
        addMemory(fact)
        input.setText("")
        transcript.text = "Jarvis: Saved that fact to this device's local memory."
        speak("Saved that to my local memory, Fynn.")
    }

    private fun addMemory(fact: String) {
        if (fact.isBlank()) return
        val old = prefs.getString("facts", "") ?: ""
        val lines = old.split("\n").filter { it.isNotBlank() }.toMutableList()
        if (!lines.any { it.equals(fact, ignoreCase = true) }) lines.add(fact)
        prefs.edit().putString("facts", lines.takeLast(100).joinToString("\n")).apply()
        refreshMemory()
    }

    private fun refreshMemory() {
        if (::memoryView.isInitialized) {
            memoryView.text = prefs.getString("facts", "")?.takeIf { it.isNotBlank() }
                ?: "No saved facts yet. Facts stay on this device."
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
