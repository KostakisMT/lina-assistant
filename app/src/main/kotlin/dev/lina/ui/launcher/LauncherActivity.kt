package dev.lina.ui.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.lina.core.accessibility.LinaAccessibilityService
import dev.lina.core.contacts.ContactMatchResult
import dev.lina.core.contacts.ContactRepository
import dev.lina.core.contacts.FuzzyContactMatcher
import dev.lina.BuildConfig
import dev.lina.core.intent.LocalCommandResolver
import dev.lina.core.intent.ResolvedIntent
import dev.lina.core.llm.ClaudeConversation
import dev.lina.core.llm.LinaReply
import dev.lina.core.stt.SttEngine
import dev.lina.core.stt.VoskSttEngine
import dev.lina.core.stt.WhisperSttEngine
import dev.lina.core.tts.AndroidTtsEngine
import dev.lina.core.tts.PiperTtsEngine
import dev.lina.core.tts.TtsEngine
import dev.lina.core.tts.TtsPriority
import dev.lina.core.wakeword.WakeWordService
import dev.lina.feature.audiobook.AudiobookManager
import dev.lina.feature.calls.CallHandler
import dev.lina.feature.news.NewsReader
import dev.lina.feature.news.NewsSyncWorker
import dev.lina.feature.sms.SmsReader
import dev.lina.feature.sms.SmsSender
import dev.lina.feature.onboarding.AccessibilityGuide
import dev.lina.feature.onboarding.BatteryWhitelistGuide
import dev.lina.feature.onboarding.PermissionsGuide
import dev.lina.ui.components.LinaTheme

class LauncherActivity : ComponentActivity() {

    private var ttsEngine: TtsEngine? = null
    private var piperEngine: PiperTtsEngine? = null
    private var claude: ClaudeConversation? = null
    private val intentResolver = LocalCommandResolver()
    private var contactMatcher: FuzzyContactMatcher? = null
    private var callHandler: CallHandler? = null
    private var smsReader: SmsReader? = null
    private var smsSender: SmsSender? = null
    private var newsReader: NewsReader? = null
    private var audiobookManager: AudiobookManager? = null
    private var statusText by mutableStateOf("Lina startet…")
    private var debugInput by mutableStateOf("")
    private var debugLog by mutableStateOf("")
    private var linaReady by mutableStateOf(false)
    private var sttEngine: SttEngine? = null
    private var voicePipelineStarted = false
    private var voiceReady = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
                onWakeWordDetected()
            }
        }
    }

    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("text") ?: return
            debugInput = text
            runOnUiThread { processDebugInput() }
        }
    }

    private val accessibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra(LinaAccessibilityService.EXTRA_TEXT) ?: return
            val tts = ttsEngine ?: return
            when (intent.action) {
                LinaAccessibilityService.EVENT_INCOMING_CALL -> {
                    tts.speak("Eingehender Anruf: $text", TtsPriority.INTERRUPT)
                    runOnUiThread {
                        debugLog = "Eingehender Anruf: $text\n\n$debugLog"
                    }
                }
                LinaAccessibilityService.EVENT_INCOMING_SMS -> {
                    tts.speak("Neue Nachricht: $text", TtsPriority.HIGH)
                    runOnUiThread {
                        debugLog = "Neue SMS: $text\n\n$debugLog"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LinaTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleLarge,
                    )

                    if (linaReady) {
                        Spacer(modifier = Modifier.height(32.dp))
                        OutlinedTextField(
                            value = debugInput,
                            onValueChange = { debugInput = it },
                            label = { Text("Sprachbefehl simulieren") },
                            placeholder = { Text("z.B. \"Ruf Boris an\"") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onBackground,
                                cursorColor = MaterialTheme.colorScheme.primary,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { processDebugInput() }),
                            singleLine = true,
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { processDebugInput() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .sizeIn(minHeight = 72.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("Senden", style = MaterialTheme.typography.labelLarge)
                        }

                        if (debugLog.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = debugLog,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        registerReceiver(
            wakeWordReceiver,
            IntentFilter(WakeWordService.ACTION_WAKE_WORD_DETECTED),
            RECEIVER_NOT_EXPORTED,
        )
        registerReceiver(
            debugReceiver,
            IntentFilter("dev.lina.DEBUG_INPUT"),
            RECEIVER_EXPORTED,
        )
        registerReceiver(
            accessibilityReceiver,
            IntentFilter().apply {
                addAction(LinaAccessibilityService.EVENT_INCOMING_CALL)
                addAction(LinaAccessibilityService.EVENT_INCOMING_SMS)
            },
            RECEIVER_NOT_EXPORTED,
        )

        if (!PermissionsGuide.allGranted(this)) {
            PermissionsGuide.requestMissing(this)
        }

        statusText = "Linas Stimme wird geladen…"
        val piper = PiperTtsEngine(applicationContext)
        piperEngine = piper
        piper.initialize(
            onReady = {
                runOnUiThread {
                    initializeLina(piper)
                    startVoicePipeline()
                }
            },
            onError = {
                runOnUiThread {
                    // Fallback: System-TTS, damit Lina nie stumm bleibt
                    initializeLina(AndroidTtsEngine(this))
                    startVoicePipeline()
                }
            },
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startVoicePipeline()
    }

    override fun onResume() {
        super.onResume()
        // Falls der Service im Hintergrund abgelehnt/beendet wurde: neu starten
        if (voiceReady) WakeWordService.start(this)
    }

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startVoicePipeline() {
        if (voicePipelineStarted || !hasMicPermission()) return
        voicePipelineStarted = true
        statusText = "Spracherkennung wird geladen…"

        val onSttReady: () -> Unit = {
            runOnUiThread {
                voiceReady = true
                WakeWordService.start(this)
                statusText = "Lina bereit – sag \"$WAKE_WORD\""
                ttsEngine?.speak(
                    "Ich höre jetzt auf das Weckwort $WAKE_WORD.",
                    TtsPriority.NORMAL,
                )
            }
        }

        val whisper = WhisperSttEngine(applicationContext)
        sttEngine = whisper
        whisper.initialize(
            onReady = onSttReady,
            onError = {
                // Fallback auf Vosk, damit die Sprachsteuerung nie ganz ausfällt
                runOnUiThread { statusText = "Whisper fehlgeschlagen – lade Vosk…" }
                val vosk = VoskSttEngine(applicationContext)
                sttEngine = vosk
                vosk.initialize(
                    onReady = onSttReady,
                    onError = { e ->
                        runOnUiThread {
                            voicePipelineStarted = false
                            statusText = "Spracherkennung fehlgeschlagen: ${e.message}"
                            ttsEngine?.speak(
                                "Die Spracherkennung konnte nicht geladen werden.",
                                TtsPriority.HIGH,
                            )
                        }
                    },
                )
            },
        )
    }

    private fun onWakeWordDetected() {
        // Echo-Unterdrückung: nicht auf die eigene Stimme reagieren
        if (piperEngine?.isSpeaking() == true) {
            android.util.Log.d("LinaLauncher", "Weckwort ignoriert (Lina spricht gerade)")
            return
        }
        android.util.Log.d("LinaLauncher", "Weckwort erkannt – starte STT")
        runOnUiThread {
            val stt = sttEngine ?: return@runOnUiThread
            // Wake-Word-Engine stoppen, damit Vosk das Mikrofon exklusiv bekommt
            stopService(Intent(this, WakeWordService::class.java))
            statusText = "Ich höre…"
            ttsEngine?.speak("Ja?", TtsPriority.INTERRUPT)

            var handled = false
            val timeout = Runnable {
                if (!handled) {
                    handled = true
                    stt.stopListening()
                    resumeWakeWordListening()
                }
            }
            mainHandler.postDelayed(timeout, STT_TIMEOUT_MS)

            // Kurz warten, damit das "Ja?" nicht in die Erkennung läuft
            mainHandler.postDelayed({
                if (handled) return@postDelayed
                stt.startListening { text ->
                    runOnUiThread {
                        if (handled) return@runOnUiThread
                        handled = true
                        mainHandler.removeCallbacks(timeout)
                        debugInput = text
                        processDebugInput()
                        resumeWakeWordListening()
                    }
                }
            }, 800)
        }
    }

    private fun resumeWakeWordListening() {
        // Verzögert, damit Linas eigene Antwort nicht die Weckwort-Erkennung triggert
        mainHandler.postDelayed({
            WakeWordService.start(this)
            statusText = "Lina bereit – sag \"$WAKE_WORD\""
        }, 2_000)
    }

    private fun initializeLina(tts: TtsEngine) {
        ttsEngine = tts

        val repo = ContactRepository(this)
        val matcher = FuzzyContactMatcher(repo)
        contactMatcher = matcher

        callHandler = CallHandler(this, tts, matcher)
        val reader = SmsReader(this, tts)
        smsReader = reader
        smsSender = SmsSender(tts, matcher, reader)
        newsReader = NewsReader(this, tts)
        audiobookManager = AudiobookManager(this, tts)

        NewsSyncWorker.schedule(this)

        if (BuildConfig.CLAUDE_API_KEY.isNotBlank()) {
            val names = try {
                repo.loadAll().map { it.displayName }
            } catch (e: Exception) {
                emptyList()
            }
            claude = ClaudeConversation(BuildConfig.CLAUDE_API_KEY, contactNames = names)
        }

        linaReady = true
        statusText = "Lina bereit"
        tts.speak("Lina ist bereit.", TtsPriority.HIGH)
    }

    private fun processDebugInput() {
        val input = debugInput.trim()
        if (input.isEmpty()) return

        if (handleVoiceCommand(input)) {
            debugInput = ""
            return
        }
        if (input.lowercase().startsWith("aufnahme")) {
            startDebugRecording()
            debugInput = ""
            return
        }

        val resolved = intentResolver.resolve(input)
        if (resolved == null && claude != null) {
            // Ebene 2: kein lokaler Befehl erkannt – freie Konversation über Claude
            askClaude(input)
            debugInput = ""
            return
        }
        val response = handleIntent(resolved ?: ResolvedIntent.Unknown(input))
        android.util.Log.d("LinaLauncher", "Eingabe=\"$input\" Intent=${formatIntent(resolved)} Antwort=\"$response\"")

        debugLog = "Eingabe: \"$input\"\n" +
            "Intent: ${formatIntent(resolved)}\n" +
            "Lina: $response\n\n$debugLog"

        debugInput = ""
        ttsEngine?.speak(response)
    }

    /**
     * Ebene 2 des Intent-Systems: fragt Claude (blockierend, daher eigener Thread).
     * Say → vorlesen, Do → lokal ausführen, Error → Fehlermeldung vorlesen.
     */
    private fun askClaude(input: String) {
        val conversation = claude ?: return
        statusText = "Lina denkt nach…"
        Thread {
            val reply = conversation.ask(input)
            runOnUiThread {
                val response = when (reply) {
                    is LinaReply.Say -> reply.text
                    is LinaReply.Do -> handleIntent(reply.intent)
                    is LinaReply.Error -> reply.text
                }
                statusText = "Lina bereit – sag \"$WAKE_WORD\""
                android.util.Log.d("LinaLauncher", "Eingabe=\"$input\" Intent=Claude(${reply::class.simpleName}) Antwort=\"$response\"")
                debugLog = "Eingabe: \"$input\"\n" +
                    "Intent: Claude (${reply::class.simpleName})\n" +
                    "Lina: $response\n\n$debugLog"
                if (response.isNotBlank()) ttsEngine?.speak(response)
            }
        }.start()
    }

    /**
     * Debug: nimmt 30s vom Mikrofon auf (16kHz WAV) für das Wake-Word-Training.
     * Datei landet in getExternalFilesDir() und ist per adb pull erreichbar.
     */
    private fun startDebugRecording() {
        stopService(Intent(this, WakeWordService::class.java))
        statusText = "Aufnahme läuft…"
        ttsEngine?.speak(
            "Aufnahme startet. Sag jetzt mehrmals Hey Lina, mit kurzen Pausen.",
            TtsPriority.INTERRUPT,
        )
        mainHandler.postDelayed({
            Thread({
                val sr = 16000
                val bufSize = maxOf(
                    android.media.AudioRecord.getMinBufferSize(
                        sr,
                        android.media.AudioFormat.CHANNEL_IN_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT,
                    ),
                    sr,
                )
                val rec = android.media.AudioRecord(
                    android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sr,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    bufSize,
                )
                val seconds = 30
                val pcm = ByteArray(sr * 2 * seconds)
                rec.startRecording()
                var pos = 0
                while (pos < pcm.size) {
                    val n = rec.read(pcm, pos, minOf(sr, pcm.size - pos))
                    if (n <= 0) break
                    pos += n
                }
                rec.stop()
                rec.release()

                val file = java.io.File(
                    getExternalFilesDir(null),
                    "lina_rec_${System.currentTimeMillis()}.wav",
                )
                java.io.FileOutputStream(file).use { out ->
                    val dataLen = pos
                    val header = java.nio.ByteBuffer.allocate(44)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    header.put("RIFF".toByteArray())
                    header.putInt(36 + dataLen)
                    header.put("WAVEfmt ".toByteArray())
                    header.putInt(16)
                    header.putShort(1)          // PCM
                    header.putShort(1)          // mono
                    header.putInt(sr)
                    header.putInt(sr * 2)       // byte rate
                    header.putShort(2)          // block align
                    header.putShort(16)         // bits
                    header.put("data".toByteArray())
                    header.putInt(dataLen)
                    out.write(header.array())
                    out.write(pcm, 0, dataLen)
                }
                android.util.Log.d("LinaLauncher", "Aufnahme gespeichert: ${file.absolutePath}")
                runOnUiThread {
                    statusText = "Aufnahme gespeichert"
                    ttsEngine?.speak("Aufnahme beendet, danke.", TtsPriority.HIGH)
                    resumeWakeWordListening()
                }
            }, "debug-rec").start()
        }, 4_000)
    }

    /** Debug/Test: "Stimme <1-4>" oder "nächste Stimme" wechselt die Piper-Stimme. */
    private fun handleVoiceCommand(input: String): Boolean {
        val piper = piperEngine ?: return false
        val normalized = input.lowercase()
            .replace("eins", "1").replace("zwei", "2")
            .replace("drei", "3").replace("vier", "4")
            .trim(' ', '.', '!', ',')

        val selector = when {
            normalized.startsWith("nächste stimme") || normalized.startsWith("naechste stimme") -> {
                val voices = PiperTtsEngine.AVAILABLE_VOICES
                val next = (voices.indexOf(piper.currentVoice) + 1) % voices.size
                (next + 1).toString()
            }
            normalized.startsWith("stimme ") -> normalized.removePrefix("stimme ").trim()
            else -> return false
        }
        if (selector.isEmpty()) return false

        statusText = "Stimme wird gewechselt…"
        piper.switchVoice(
            selector,
            onDone = { voice ->
                runOnUiThread {
                    statusText = "Stimme: $voice"
                    debugLog = "Stimme gewechselt: $voice\n\n$debugLog"
                    piper.speak(
                        "Hallo, ich bin Lina. So klingt meine Stimme. " +
                            "Heute ist ein schöner Tag zum Segeln.",
                        TtsPriority.INTERRUPT,
                    )
                }
            },
            onError = {
                runOnUiThread {
                    statusText = "Stimmwechsel fehlgeschlagen"
                    ttsEngine?.speak("Diese Stimme kenne ich nicht.", TtsPriority.HIGH)
                }
            },
        )
        return true
    }

    private fun resolveContactWithDisambiguation(query: String, action: String): String {
        val matcher = contactMatcher ?: return "$action $query."
        return when (val result = matcher.findMatches(query)) {
            is ContactMatchResult.SingleMatch ->
                "$action ${result.contact.displayName}."
            is ContactMatchResult.MultipleMatches -> {
                val names = result.contacts.map { it.displayName }
                val list = names.mapIndexed { i, n -> "${i + 1}. $n" }.joinToString(", ")
                "Welchen ${result.query} meinst du? $list"
            }
            is ContactMatchResult.NoMatch ->
                "Ich habe keinen Kontakt mit dem Namen ${result.query} gefunden."
        }
    }

    private fun handleIntent(intent: ResolvedIntent): String = when (intent) {
        is ResolvedIntent.Call -> {
            val handler = callHandler
            if (handler != null) {
                val result = handler.startCall(intent.contactQuery)
                result.displayMessage
            } else {
                resolveContactWithDisambiguation(intent.contactQuery, "Ich rufe")
            }
        }
        is ResolvedIntent.AcceptCall -> {
            callHandler?.acceptCall()
            "Anruf angenommen."
        }
        is ResolvedIntent.RejectCall -> {
            callHandler?.rejectCall()
            "Anruf abgelehnt."
        }
        is ResolvedIntent.HangUp -> {
            callHandler?.hangUp()
            "Aufgelegt."
        }
        is ResolvedIntent.SendSms -> {
            val sender = smsSender
            if (sender != null) {
                sender.sendTo(intent.contactQuery, intent.message).displayMessage
            } else {
                "SMS-Funktion nicht verfügbar."
            }
        }
        is ResolvedIntent.ReadSms -> {
            smsReader?.readLatest()
            "Nachrichten werden geladen…"
        }
        is ResolvedIntent.ReplySms -> {
            val result = smsSender?.replyToLast(intent.message)
            result?.displayMessage ?: "SMS-Funktion nicht verfügbar."
        }
        is ResolvedIntent.ReadNews -> {
            newsReader?.readNews()
            "Nachrichten werden geladen…"
        }
        is ResolvedIntent.NextNews -> {
            newsReader?.nextNews()
            "Nächste Meldung."
        }
        is ResolvedIntent.NewsDetail -> {
            newsReader?.readDetail()
            "Detail wird vorgelesen."
        }
        is ResolvedIntent.PlayAudiobook -> {
            audiobookManager?.play()
            "Hörbuch wird gestartet…"
        }
        is ResolvedIntent.PauseAudiobook -> {
            audiobookManager?.pause()
            "Pausiert."
        }
        is ResolvedIntent.ResumeAudiobook -> {
            audiobookManager?.resume()
            "Weiter."
        }
        is ResolvedIntent.RewindAudiobook -> {
            audiobookManager?.rewind(intent.seconds)
            "${intent.seconds} Sekunden zurück."
        }
        is ResolvedIntent.AudiobookInfo -> {
            audiobookManager?.info()
            ""
        }
        is ResolvedIntent.ListAudiobooks -> {
            audiobookManager?.listBooks()
            "Hörbücher werden aufgelistet…"
        }
        is ResolvedIntent.SearchAudiobook -> {
            audiobookManager?.searchAndPlay(intent.query)
            "Suche nach ${intent.query}…"
        }
        is ResolvedIntent.SleepTimer -> {
            audiobookManager?.startSleepTimer(intent.minutes)
            "Schlaf-Timer: ${intent.minutes} Minuten."
        }
        is ResolvedIntent.Stop -> {
            ttsEngine?.stop()
            newsReader?.stop()
            "Gestoppt."
        }
        is ResolvedIntent.Unknown ->
            "Das habe ich nicht verstanden: \"${intent.rawInput}\""
    }

    private fun formatIntent(intent: ResolvedIntent?): String = when (intent) {
        null -> "Nicht erkannt"
        is ResolvedIntent.Call -> "Call(${intent.contactQuery})"
        is ResolvedIntent.SendSms -> "SendSms(${intent.contactQuery}, ${intent.message})"
        is ResolvedIntent.ReadSms -> "ReadSms"
        is ResolvedIntent.ReplySms -> "ReplySms(${intent.message})"
        is ResolvedIntent.ReadNews -> "ReadNews"
        is ResolvedIntent.NextNews -> "NextNews"
        is ResolvedIntent.NewsDetail -> "NewsDetail"
        is ResolvedIntent.PlayAudiobook -> "PlayAudiobook"
        is ResolvedIntent.PauseAudiobook -> "PauseAudiobook"
        is ResolvedIntent.ResumeAudiobook -> "ResumeAudiobook"
        is ResolvedIntent.RewindAudiobook -> "RewindAudiobook(${intent.seconds}s)"
        is ResolvedIntent.AudiobookInfo -> "AudiobookInfo"
        is ResolvedIntent.ListAudiobooks -> "ListAudiobooks"
        is ResolvedIntent.SearchAudiobook -> "SearchAudiobook(${intent.query})"
        is ResolvedIntent.SleepTimer -> "SleepTimer(${intent.minutes}min)"
        is ResolvedIntent.AcceptCall -> "AcceptCall"
        is ResolvedIntent.RejectCall -> "RejectCall"
        is ResolvedIntent.HangUp -> "HangUp"
        is ResolvedIntent.Stop -> "Stop"
        is ResolvedIntent.Unknown -> "Unknown"
    }

    override fun onDestroy() {
        try { unregisterReceiver(debugReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(accessibilityReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(wakeWordReceiver) } catch (_: Exception) {}
        sttEngine?.destroy()
        audiobookManager?.release()
        ttsEngine?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val WAKE_WORD = "Hey Lina"
        // Whisper ist nicht-streamend: bis zu 10s Aufnahme + Transkriptionszeit
        private const val STT_TIMEOUT_MS = 30_000L
    }
}
