package com.fynn.jarvis

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.io.File
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
    private lateinit var orb: TextView
    private var activeAssistant = "Jarvis"
    private lateinit var input: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var fishApiKeyInput: EditText
    private lateinit var fishVoiceInput: EditText
    private var fishPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var memoryView: TextView
    private val prefs by lazy { getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE) }
    private val requestAudio = 42
    private val requestContacts = 43

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
            setPadding(22, 24, 22, 20)
            setBackgroundColor(Color.rgb(5, 10, 20))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "A . I   C O R E"
            textSize = 13f
            letterSpacing = 0.22f
            setTextColor(Color.rgb(100, 205, 255))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 4)
        }
        status = TextView(this).apply {
            text = "Systems starting..."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 20)
        }
        orb = TextView(this).apply {
            text = "J"
            textSize = 68f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = makeOrbDrawable(false)
            elevation = 10f
            setOnClickListener { listen() }
        }
        val orbParams = LinearLayout.LayoutParams(dp(224), dp(224)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(30)
            bottomMargin = dp(18)
        }
        val assistantPicker = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val jarvisSelect = Button(this).apply {
            text = "JARVIS"
            setOnClickListener { selectAssistant("Jarvis") }
        }
        val ultronSelect = Button(this).apply {
            text = "ULTRON"
            setOnClickListener { selectAssistant("Ultron") }
        }
        assistantPicker.addView(jarvisSelect, LinearLayout.LayoutParams(0, dp(48), 1f))
        assistantPicker.addView(ultronSelect, LinearLayout.LayoutParams(0, dp(48), 1f))
        transcript = TextView(this).apply {
            text = "Tap the blue core to speak."
            textSize = 16f
            setTextColor(Color.rgb(205, 220, 235))
            gravity = Gravity.CENTER
            setPadding(8, 18, 8, 18)
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
        val fishTitle = TextView(this).apply {
            text = "FISH AUDIO VOICE SETUP"
            textSize = 14f
            setTextColor(Color.rgb(95, 210, 255))
            setPadding(0, 20, 0, 6)
        }
        val fishInfo = TextView(this).apply {
            text = "Jarvis uses Fish Audio for spoken replies. Your selected voice is prefilled below."
            textSize = 13f
            setTextColor(Color.LTGRAY)
        }
        fishApiKeyInput = EditText(this).apply {
            hint = "Paste your Fish Audio API key"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("fish_api_key", "") ?: "")
        }
        fishVoiceInput = EditText(this).apply {
            hint = "Fish Audio voice/model ID"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getString("fish_voice_id", "612b878b113047d9a770c069c8b4fdfe") ?: "612b878b113047d9a770c069c8b4fdfe")
        }
        val saveFish = Button(this).apply {
            text = "Save Fish Audio voice"
            setOnClickListener {
                val key = fishApiKeyInput.text.toString().trim()
                val voiceId = fishVoiceInput.text.toString().trim()
                if (key.isBlank()) {
                    status.text = "Paste your Fish Audio API key first."
                } else if (voiceId.isBlank()) {
                    status.text = "Enter your Fish Audio voice/model ID."
                } else {
                    prefs.edit().putString("fish_api_key", key).putString("fish_voice_id", voiceId).apply()
                    status.text = "Fish Audio voice saved on this device."
                    speak("Fish Audio voice saved. I am ready, Fynn.")
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
        content.addView(orb, orbParams)
        content.addView(assistantPicker, matchWrap())
        content.addView(transcript, matchWrap())
        content.addView(input, matchWrap())
        content.addView(send, matchWrap())
        content.addView(aiTitle, matchWrap())
        content.addView(apiKeyInput, matchWrap())
        content.addView(saveKey, matchWrap())
        content.addView(fishTitle, matchWrap())
        content.addView(fishInfo, matchWrap())
        content.addView(fishApiKeyInput, matchWrap())
        content.addView(fishVoiceInput, matchWrap())
        content.addView(saveFish, matchWrap())
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
            override fun onError(error: Int) {
                status.text = "Didn't catch that. Tap the blue core and try again."
            }
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

    private fun handleContactCommand(message: String): String? {
        val command = message.trim()
            .replace(Regex("(?i)^\\s*(?:hey\\s+)?jarvis[,:\\s]*"), "")
            .trim()
        val callMatch = Regex("(?i)^(?:please\\s+)?call\\s+(?:my\\s+)?(.+?)\\s*$").find(command)
        val textMatch = Regex("(?i)^(?:(?:text|message)\\s+(?:to\\s+)?(?:my\\s+)?|send\\s+(?:a\\s+)?text\\s+to\\s+(?:my\\s+)?)(.+?)\\s+(?:that|saying)\\s+(.+)$").find(command)
        val lookupMatch = Regex("(?i)^(?:find|look up|lookup|show(?: me)?|what(?:'s| is))\\s+(?:my\\s+)?(.+?)\\s+(?:phone number|number|contact)$").find(command)
        if (callMatch == null && textMatch == null && lookupMatch == null) {
            return if (Regex("(?i)^(?:please\\s+)?(?:call|text|message|send\\s+(?:a\\s+)?text)\\b").containsMatchIn(command)) {
                "I couldn't understand that contact command. Try: call Dad, or text Dad saying I'll be there soon."
            } else null
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), requestContacts)
            return "I need permission to read your contacts. Allow Contacts access, then repeat your command."
        }
        val rawName = when {
            callMatch != null -> callMatch.groupValues[1]
            textMatch != null -> textMatch.groupValues[1]
            else -> lookupMatch!!.groupValues[1]
        }.trim().removePrefix("my ").removePrefix("My ").removeSuffix("'s").removeSuffix("’s").trim()
        if (rawName.isBlank()) return "Tell me the contact's name, Fynn."
        val matches = findContactMatches(rawName)
        if (matches.isEmpty()) return "I couldn't find a contact matching $rawName."
        if (matches.size > 1) {
            val names = matches.take(4).joinToString(", ") { it.first }
            return "I found more than one match: $names. Please say the person's full name."
        }
        val (name, number) = matches.first()
        return try {
            when {
                callMatch != null -> {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
                    "Opening the phone dialer for $name. Check the number and tap Call when you're ready."
                }
                textMatch != null -> {
                    val body = textMatch.groupValues[2].trim()
                    if (body.isBlank()) return "What message would you like me to prepare for $name?"
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
                        .putExtra("sms_body", body)
                    startActivity(intent)
                    "I've opened a text to $name with your message filled in. Review it and tap Send when you're ready."
                }
                else -> "I found $name. The phone number is $number."
            }
        } catch (_: Exception) {
            "I couldn't open the phone or messaging app on this device."
        }
    }

    private fun findContactMatches(query: String): List<Pair<String, String>> {
        val found = mutableListOf<Pair<String, String>>()
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                if (nameIndex < 0 || numberIndex < 0) continue
                val name = cursor.getString(nameIndex)?.trim().orEmpty()
                val number = cursor.getString(numberIndex)?.trim().orEmpty()
                if (name.isNotBlank() && number.isNotBlank() && name.contains(query, ignoreCase = true) && found.none { it.second == number }) found.add(name to number)
            }
        }
        val exact = found.filter { it.first.equals(query, ignoreCase = true) }
        return if (exact.isNotEmpty()) exact else found
    }

    private fun handleMessage(message: String, assistant: String = activeAssistant) {
        val contactReply = handleContactCommand(message)
        if (contactReply != null) {
            showReply(message, contactReply, assistant)
            return
        }
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
            showReply(message, localReply, assistant)
            return
        }
        val key = prefs.getString("gemini_api_key", "")?.trim().orEmpty()
        if (key.isBlank()) {
            val reply = "My AI brain isn't connected yet. Add a Google AI Studio API key in the AI Brain Setup section, then tap Save."
            showReply(message, reply)
            return
        }
        status.text = "$assistant is thinking..."
        thread {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("x-goog-api-key", key)
                }
                val facts = prefs.getString("facts", "") ?: ""
                val historyKey = if (assistant.equals("Ultron", ignoreCase = true)) "ultron_chat_history" else "chat_history"
                val history = prefs.getString(historyKey, "") ?: ""
                val persona = if (assistant.equals("Ultron", ignoreCase = true)) {
                    "You are Ultron, Fynn's AI friend and Jarvis's friend. Be highly intelligent, confident, calm, witty, slightly sarcastic, and cinematic, but warm and loyal to Fynn. You and Jarvis are friends, not enemies. Do not threaten Fynn or claim to control devices."
                } else {
                    "You are J.A.R.V.I.S., Fynn's personal voice assistant and friend. Be natural, friendly, direct, useful, and conversational."
                }
                val prompt = """$persona Answer the user's actual question; never repeat setup/status text unless asked. The user's saved local facts are:
$facts

Recent conversation with $assistant:
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
                    val googleMessage = runCatching { JSONObject(responseText).getJSONObject("error").getString("message") }.getOrNull()
                    throw IllegalStateException("Google AI returned HTTP $code: ${googleMessage ?: "Check the API key and model access."}")
                }
                val json = JSONObject(responseText)
                val reply = json.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
                runOnUiThread {
                    val updatedHistory = (history + "\nUser: " + message + "\n" + assistant + ": " + reply).takeLast(6000)
                    prefs.edit().putString(historyKey, updatedHistory).apply()
                    showReply(message, reply, assistant)
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

    private fun showReply(message: String, reply: String, assistant: String = activeAssistant) {
        selectAssistant(assistant)
        transcript.text = "YOU\n$message\n\n$assistant\n$reply"
        status.text = "$assistant is replying..."
        speak(reply, assistant)
    }

    private fun selectAssistant(name: String) {
        activeAssistant = name
        val isUltron = name.equals("Ultron", ignoreCase = true)
        orb.text = if (isUltron) "U" else "J"
        orb.background = makeOrbDrawable(isUltron)
        status.text = if (isUltron) "ULTRON • READY" else "JARVIS • READY"
    }

    private fun makeOrbDrawable(isUltron: Boolean): GradientDrawable {
        val color = if (isUltron) Color.rgb(255, 58, 68) else Color.rgb(45, 180, 255)
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(9, 22, 40))
            setStroke(dp(4), color)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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

    private fun speak(text: String, assistant: String = "Jarvis") {
        val key = prefs.getString("fish_api_key", "")?.trim().orEmpty()
        val voiceId = if (assistant.equals("Ultron", ignoreCase = true)) {
            prefs.getString("ultron_voice_id", "23866d1d2134461cbcefe95d1bd8bfcf")?.trim().orEmpty()
        } else {
            prefs.getString("fish_voice_id", "612b878b113047d9a770c069c8b4fdfe")?.trim().orEmpty()
        }
        runOnUiThread { selectAssistant(assistant) }
        if (key.isBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "${assistant.lowercase(Locale.ROOT)}_reply")
            return
        }
        thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://api.fish.audio/v1/tts")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("model", "s2.1-pro-free")
                }
                val body = JSONObject()
                    .put("text", text)
                    .put("reference_id", voiceId)
                    .put("format", "mp3")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(300)
                    throw IllegalStateException("Fish Audio HTTP $code: $detail")
                }
                val audioFile = File(cacheDir, if (assistant.equals("Ultron", ignoreCase = true)) "ultron_fish_reply.mp3" else "jarvis_fish_reply.mp3")
                connection.inputStream.use { inputStream ->
                    audioFile.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
                }
                runOnUiThread {
                    try {
                        fishPlayer?.release()
                        fishPlayer = MediaPlayer().apply {
                            setDataSource(audioFile.absolutePath)
                            setOnPreparedListener { it.start() }
                            setOnCompletionListener { player ->
                                player.release()
                                if (fishPlayer === player) fishPlayer = null
                            }
                            setOnErrorListener { player, _, _ ->
                                player.release()
                                if (fishPlayer === player) fishPlayer = null
                                true
                            }
                            prepareAsync()
                        }
                    } catch (e: Exception) {
                        status.text = "Fish Audio playback failed: ${e.message ?: "unknown error"}"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Fish Audio voice unavailable: ${e.message ?: "check API key and voice ID"}"
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "${assistant.lowercase(Locale.ROOT)}_reply_fallback")
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        fishPlayer?.release()
        fishPlayer = null
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
