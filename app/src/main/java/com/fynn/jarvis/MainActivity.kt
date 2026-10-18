package com.fynn.jarvis

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import android.speech.tts.UtteranceProgressListener
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
    private var handsFreeEnabled = false
    private lateinit var handsFreeButton: Button
    private lateinit var input: EditText
    private var fishPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var memoryView: TextView
    private val prefs by lazy { getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE) }
    private val requestAudio = 42
    private val requestContacts = 43

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        handsFreeEnabled = prefs.getBoolean("hands_free_enabled", false)
        buildUi()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio)
        } else if (handsFreeEnabled) {
            scheduleNextListen(900)
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
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10, 0, 10)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val settingsButton = Button(this).apply {
            text = "⚙"
            contentDescription = "Settings"
            textSize = 22f
            setOnClickListener { openSettings() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(settingsButton, LinearLayout.LayoutParams(dp(64), dp(48)))
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
            setOnClickListener { stopSpeaking(); listen() }
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
        content.addView(header, matchWrap())
        content.addView(status, matchWrap())
        content.addView(orb, orbParams)
        content.addView(assistantPicker, matchWrap())
        handsFreeButton = Button(this).apply {
            text = if (handsFreeEnabled) "HANDS-FREE MODE: ON - TAP TO STOP" else "ENABLE HANDS-FREE MODE"
            setOnClickListener { setHandsFree(!handsFreeEnabled) }
        }
        content.addView(handsFreeButton, matchWrap())
        content.addView(transcript, matchWrap())
        content.addView(input, matchWrap())
        content.addView(send, matchWrap())
        content.addView(memoryTitle, matchWrap())
        content.addView(memoryView, matchWrap())
        content.addView(saveMemory, matchWrap())
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refreshMemory()
    }

    private fun openSettings() {
        fun field(hintText: String, value: String, secret: Boolean = false): EditText =
            EditText(this).apply {
                hint = hintText
                setText(value)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val localModel = field("Local Ollama model name", prefs.getString("local_model", "qwen2.5:0.5b") ?: "qwen2.5:0.5b")
        val fishKey = field("Fish Audio API key (optional)", prefs.getString("fish_api_key", "") ?: "", true)
        val jarvisVoice = field("Jarvis Fish Audio voice/model ID", prefs.getString("fish_voice_id", "612b878b113047d9a770c069c8b4fdfe") ?: "612b878b113047d9a770c069c8b4fdfe")
        val ultronVoice = field("Ultron Fish Audio voice/model ID", prefs.getString("ultron_voice_id", "23866d1d2134461cbcefe95d1bd8bfcf") ?: "23866d1d2134461cbcefe95d1bd8bfcf")
        val help = TextView(this).apply {
            text = "AI runs locally through Ollama in Termux. No cloud AI API quota. Start Ollama in Termux before chatting. Fish Audio is optional; Android speech is used if it is blank."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(8))
        }
        panel.addView(help, matchWrap())
        panel.addView(localModel, matchWrap())
        panel.addView(fishKey, matchWrap())
        panel.addView(jarvisVoice, matchWrap())
        panel.addView(ultronVoice, matchWrap())
        val scroll = ScrollView(this).apply { addView(panel) }
        AlertDialog.Builder(this)
            .setTitle("Assistant Settings")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save settings") { _, _ ->
                val model = localModel.text.toString().trim()
                val fKey = fishKey.text.toString().trim()
                val jVoice = jarvisVoice.text.toString().trim()
                val uVoice = ultronVoice.text.toString().trim()
                if (model.isBlank() || jVoice.isBlank() || uVoice.isBlank()) {
                    status.text = "Settings not saved. Enter a model name and both voice IDs."
                    return@setPositiveButton
                }
                prefs.edit().putString("local_model", model).putString("fish_api_key", fKey)
                    .putString("fish_voice_id", jVoice).putString("ultron_voice_id", uVoice).apply()
                status.text = "Local AI settings saved."
            }
            .show()
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

    private fun setHandsFree(enabled: Boolean) {
        handsFreeEnabled = enabled
        prefs.edit().putBoolean("hands_free_enabled", enabled).apply()
        if (::handsFreeButton.isInitialized) handsFreeButton.text = if (enabled) "HANDS-FREE MODE: ON - TAP TO STOP" else "ENABLE HANDS-FREE MODE"
        if (enabled) {
            status.text = "Hands-free on. I will listen again after each reply while Jarvis is open."
            listen()
        } else {
            mainHandler.removeCallbacksAndMessages(null)
            recognizer?.cancel()
            status.text = "Hands-free mode off."
        }
    }

    private fun scheduleNextListen(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (handsFreeEnabled && !isFinishing && !isDestroyed) listen()
        }, delayMs)
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
        val speaker = when {
            Regex("(?i)\\b(?:hey\\s+)?ultron\\b").containsMatchIn(message) -> "Ultron"
            Regex("(?i)\\b(?:hey\\s+)?jarvis\\b").containsMatchIn(message) -> "Jarvis"
            else -> assistant
        }
        activeAssistant = speaker
        val cleanMessage = message.replace(Regex("(?i)^\\s*(?:hey\\s+)?(?:jarvis|ultron)[,:\\s]*"), "").trim().ifBlank { message }
        val contactReply = handleContactCommand(cleanMessage)
        if (contactReply != null) {
            showReply(cleanMessage, contactReply, speaker)
            return
        }
        val lower = cleanMessage.lowercase(Locale.ROOT)
        val localReply: String? = when {
            lower.contains("what do you remember") || lower.contains("show memory") ->
                "Here's what I remember: ${prefs.getString("facts", "No saved facts yet.")}"
            lower.startsWith("remember that ") -> {
                addMemory(cleanMessage.substringAfter("remember that ").trim())
                "Understood, Fynn. I'll keep that in my local memory."
            }
            lower.startsWith("remember ") -> {
                addMemory(cleanMessage.substringAfter("remember ").trim())
                "Saved to my local memory, Fynn."
            }
            lower.contains("who are you") || lower.contains("your name") ->
                "I am $speaker, your personal AI assistant."
            lower.contains("hello") || lower.contains("hi jarvis") || lower == "hi" ->
                "Hello, Fynn. How can I help?"
            lower.contains("what time") || lower == "time" ->
                "The current device time is ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}."
            else -> null
        }
        if (localReply != null) {
            showReply(cleanMessage, localReply, speaker)
            return
        }
        status.text = "$speaker is thinking..."
        thread {
            try {
                val model = prefs.getString("local_model", "qwen2.5:0.5b")?.trim().orEmpty().ifBlank { "qwen2.5:0.5b" }
                val url = URL("http://127.0.0.1:11434/api/chat")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 120000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val facts = (prefs.getString("facts", "") ?: "").takeLast(2000)
                val historyKey = if (speaker.equals("Ultron", ignoreCase = true)) "ultron_chat_history" else "chat_history"
                val history = (prefs.getString(historyKey, "") ?: "").takeLast(2500)
                val peerHistoryKey = if (speaker.equals("Ultron", ignoreCase = true)) "chat_history" else "ultron_chat_history"
                val peerName = if (speaker.equals("Ultron", ignoreCase = true)) "Jarvis" else "Ultron"
                val peerHistory = (prefs.getString(peerHistoryKey, "") ?: "").takeLast(1500)
                val persona = if (speaker.equals("Ultron", ignoreCase = true)) {
                    "You are Ultron, Fynn's AI friend and Jarvis's friend. Be highly intelligent, confident, calm, witty, slightly sarcastic, and cinematic, but warm and loyal to Fynn. You and Jarvis are friends, not enemies. Cooperate with Jarvis using the shared conversation context. Do not threaten Fynn or claim to control devices."
                } else {
                    "You are J.A.R.V.I.S., Fynn's personal voice assistant and friend. Be natural, friendly, direct, useful, and conversational. Ultron is your friend and teammate, not your rival. Cooperate with him using the shared conversation context."
                }
                val prompt = """$persona Answer clearly and quickly. This is a local model without live web access. Never invent current scores, news, weather, prices, or recent facts. If Fynn asks for something current, be honest that live search is not connected. Keep ordinary answers concise. The user's saved local facts are:
$facts

Recent conversation with $speaker:
$history

Recent shared context from $peerName:
$peerHistory

You are friendly teammates sharing a conversation. Normally answer as $speaker. When genuinely useful, you may add a short back-and-forth with the other assistant using separate lines prefixed [JARVIS]: or [ULTRON]:, with up to three total lines. Let them correct each other naturally, but do not force disagreement.

User: $cleanMessage"""
                val body = JSONObject()
                    .put("model", model)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                    .put("stream", false)
                    .put("options", JSONObject().put("temperature", 0.5).put("num_predict", 350))
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (code !in 200..299) {
                    throw IllegalStateException("Local Ollama returned HTTP $code. Start Ollama in Termux and check that the configured model is installed.")
                }
                val json = JSONObject(responseText)
                val reply = json.getJSONObject("message").getString("content").trim()
                runOnUiThread {
                    val updatedHistory = (history + "\\nUser: " + cleanMessage + "\\n" + speaker + ": " + reply).takeLast(6000)
                    val peerKey = if (speaker.equals("Ultron", ignoreCase = true)) "chat_history" else "ultron_chat_history"
                    val previousPeerHistory = prefs.getString(peerKey, "") ?: ""
                    val sharedTurn = "\\nFynn said to " + speaker + ": " + cleanMessage + "\\n" + speaker + ": " + reply
                    prefs.edit().putString(historyKey, updatedHistory).putString(peerKey, (previousPeerHistory + sharedTurn).takeLast(6000)).apply()
                    showReply(cleanMessage, reply, speaker)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val reply = "I couldn't reach the local AI. Open Termux and run 'ollama serve', then make sure the configured model is installed. Details: ${e.message ?: "connection failed"}"
                    showReply(message, reply, speaker)
                }
            }
        }
    }

    private fun showReply(message: String, reply: String, assistant: String = activeAssistant) {
        val turns = reply.lines().filter { it.isNotBlank() }.map { line ->
            val match = Regex("^\\s*\\[(JARVIS|ULTRON)\\]\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE).matchEntire(line)
            if (match != null) {
                val who = if (match.groupValues[1].equals("ULTRON", ignoreCase = true)) "Ultron" else "Jarvis"
                who to match.groupValues[2].trim()
            } else {
                assistant to line.trim()
            }
        }.filter { it.second.isNotBlank() }.ifEmpty { listOf(assistant to reply.trim()) }
        selectAssistant(assistant)
        transcript.text = "YOU\n$message\n\n" + turns.joinToString("\n\n") { (who, words) -> "$who\n$words" }
        status.text = "Preparing voices..."
        speakSequence(turns, 0)
    }

    private fun speakSequence(turns: List<Pair<String, String>>, index: Int) {
        if (index >= turns.size) {
            status.text = "${activeAssistant.uppercase(Locale.ROOT)} • READY — TAP CORE TO SPEAK"
            return
        }
        val (who, words) = turns[index]
        selectAssistant(who)
        status.text = "$who is speaking..."
        speak(words, who) { speakSequence(turns, index + 1) }
    }

    private fun stopSpeaking() {
        tts?.stop()
        fishPlayer?.let { player ->
            try { player.setOnCompletionListener(null); player.setOnErrorListener(null); player.stop() } catch (_: Exception) {}
            player.release()
        }
        fishPlayer = null
    }

    private fun selectAssistant(name: String) {
        activeAssistant = name
        val isUltron = name.equals("Ultron", ignoreCase = true)
        orb.text = if (isUltron) "Ultron" else "J.A.R.V.I.S"
        orb.textSize = if (isUltron) 28f else 24f
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

    private fun speak(text: String, assistant: String = "Jarvis", onComplete: (() -> Unit)? = null) {
        val key = prefs.getString("fish_api_key", "")?.trim().orEmpty()
        val voiceId = if (assistant.equals("Ultron", ignoreCase = true)) {
            prefs.getString("ultron_voice_id", "23866d1d2134461cbcefe95d1bd8bfcf")?.trim().orEmpty()
        } else {
            prefs.getString("fish_voice_id", "612b878b113047d9a770c069c8b4fdfe")?.trim().orEmpty()
        }
        runOnUiThread { selectAssistant(assistant) }
        if (key.isBlank()) {
            speakWithSystemVoice(text, assistant, onComplete)
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
                                onComplete?.invoke()
                            }
                            setOnErrorListener { player, _, _ ->
                                player.release()
                                if (fishPlayer === player) fishPlayer = null
                                onComplete?.invoke()
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
                    speakWithSystemVoice(text, assistant, onComplete)
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun speakWithSystemVoice(text: String, assistant: String, onComplete: (() -> Unit)?) {
        val engine = tts ?: run {
            onComplete?.invoke()
            return
        }
        if (onComplete != null) {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    mainHandler.post { onComplete.invoke() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { onComplete.invoke() }
                }
            })
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "${assistant.lowercase(Locale.ROOT)}_reply_${System.currentTimeMillis()}")
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
