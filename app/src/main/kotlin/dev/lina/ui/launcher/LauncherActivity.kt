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
import dev.lina.core.audio.Earcons
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
import dev.lina.feature.document.DocumentCamera
import dev.lina.feature.reminder.ReminderManager
import dev.lina.feature.reminder.ReminderReceiver
import dev.lina.feature.onboarding.AccessibilityGuide
import dev.lina.feature.onboarding.VoiceOnboarding
import dev.lina.feature.onboarding.BatteryWhitelistGuide
import dev.lina.feature.onboarding.PermissionsGuide
import dev.lina.ui.components.LinaTheme

class LauncherActivity : ComponentActivity() {

    private var ttsEngine: TtsEngine? = null
    private var piperEngine: PiperTtsEngine? = null
    private var claude: ClaudeConversation? = null
    private var documentCamera: DocumentCamera? = null
    /** Nur während des Dokument-Folgefensters im RAM – wird danach verworfen. */
    private var lastDocumentImage: ByteArray? = null
    private val intentResolver = LocalCommandResolver()
    private var contactMatcher: FuzzyContactMatcher? = null
    private var callHandler: CallHandler? = null
    private var smsReader: SmsReader? = null
    private var smsSender: SmsSender? = null
    private var newsReader: NewsReader? = null
    private var audiobookManager: AudiobookManager? = null
    private var reminderManager: ReminderManager? = null
    private var statusText by mutableStateOf("Lina startet…")
    private var debugInput by mutableStateOf("")
    private var debugLog by mutableStateOf("")
    private var linaReady by mutableStateOf(false)
    private var sttEngine: SttEngine? = null
    private var onboarding: VoiceOnboarding? = null
    private var newsHintGesagt = false
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

    /** Eine Erinnerung ist fällig – Lina sagt sie an. */
    private val reminderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ReminderReceiver.ACTION_REMINDER_DUE) return
            val text = intent.getStringExtra(ReminderReceiver.EXTRA_TEXT) ?: return
            runOnUiThread {
                reminderManager?.announce(text)
                debugLog = "Erinnerung fällig: $text\n\n$debugLog"
            }
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
        registerReceiver(
            reminderReceiver,
            IntentFilter(ReminderReceiver.ACTION_REMINDER_DUE),
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
        // (nicht während der Ersteinrichtung – die braucht das Mikrofon exklusiv)
        if (voiceReady && onboarding == null) WakeWordService.start(this)
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
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                if (!prefs.getBoolean(PREF_ONBOARDING_DONE, false)) {
                    // Allererster Start: gesprochene Ersteinrichtung statt Weckwort
                    startOnboarding()
                } else {
                    WakeWordService.start(this)
                    statusText = "Lina bereit – sag \"$WAKE_WORD\""
                    ttsEngine?.speak(
                        "Ich höre jetzt auf das Weckwort $WAKE_WORD.",
                        TtsPriority.NORMAL,
                    )
                }
            }
        }

        val whisper = WhisperSttEngine(applicationContext)
        // Bestätigungston sobald die Aufnahme steht und die ~2s-Transkription läuft
        whisper.onSpeechCaptured = { Earcons.ack() }
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
        // Während der Ersteinrichtung ist das Weckwort aus (Mikrofon-Konflikt)
        if (onboarding != null) return
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
                        // Wenn Claude übernimmt, steuert der Gesprächsmodus das
                        // Zuhören selbst – KEIN Wake-Neustart planen (sonst Race:
                        // startForegroundService + sofortiges stopService = Crash)
                        val claudeUebernimmt = processDebugInput()
                        if (!claudeUebernimmt) resumeWakeWordListening()
                    }
                }
            }, 800)
        }
    }

    private val wakeResumeRunnable = Runnable {
        if (onboarding != null) return@Runnable
        WakeWordService.start(this)
        statusText = "Lina bereit – sag \"$WAKE_WORD\""
    }

    private fun resumeWakeWordListening() {
        // Verzögert, damit Linas eigene Antwort nicht die Weckwort-Erkennung triggert
        mainHandler.removeCallbacks(wakeResumeRunnable)
        mainHandler.postDelayed(wakeResumeRunnable, 2_000)
    }

    private fun cancelWakeResume() {
        mainHandler.removeCallbacks(wakeResumeRunnable)
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
        reminderManager = ReminderManager(this, tts)

        NewsSyncWorker.schedule(this)

        initClaude(repo)

        linaReady = true
        statusText = "Lina bereit"
        tts.speak("Lina ist bereit.", TtsPriority.HIGH)
    }

    private fun initClaude(repo: ContactRepository) {
        if (BuildConfig.CLAUDE_API_KEY.isBlank()) return
        val names = try {
            repo.loadAll().map { it.displayName }
        } catch (e: Exception) {
            emptyList()
        }
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        claude = ClaudeConversation(
            BuildConfig.CLAUDE_API_KEY,
            contactNames = names,
            interests = prefs.getString(PREF_INTERESTS, "") ?: "",
            region = prefs.getString(PREF_REGION, "") ?: "",
        )
    }

    /**
     * Gesprochene Ersteinrichtung (VoiceOnboarding): Weckwort- und Befehls-
     * Aufnahmen + Fragenkatalog. Antworten personalisieren die Claude-Persona.
     */
    private fun startOnboarding() {
        val tts = ttsEngine ?: return
        cancelWakeResume()
        stopService(Intent(this, WakeWordService::class.java))
        statusText = "Ersteinrichtung läuft…"
        val flow = VoiceOnboarding(
            tts = tts,
            stt = sttEngine as? WhisperSttEngine,
            isTtsSpeaking = { piperEngine?.isBusySpeaking() == true },
            baseDir = getExternalFilesDir("onboarding") ?: filesDir,
        )
        onboarding = flow
        flow.start { interests, name, region ->
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(PREF_ONBOARDING_DONE, true)
                .putString(PREF_INTERESTS, interests)
                .putString(PREF_USER_NAME, name)
                .putString(PREF_REGION, region)
                .apply()
            onboarding = null
            // Claude mit den frischen Interessen neu aufsetzen
            initClaude(ContactRepository(this))
            statusText = "Lina bereit – sag \"$WAKE_WORD\""
            resumeWakeWordListening()
        }
    }

    /** @return true, wenn Claude die Eingabe asynchron übernimmt (Gesprächsmodus
     *  steuert dann selbst das Zuhören und den Weckwort-Neustart). */
    private fun processDebugInput(): Boolean {
        val input = debugInput.trim()
        if (input.isEmpty()) return false

        if (handleVoiceCommand(input)) {
            debugInput = ""
            return false
        }
        if (input.lowercase().startsWith("aufnahme")) {
            startDebugRecording()
            debugInput = ""
            return false
        }
        when (input.lowercase().trim(' ', '.', '!')) {
            "einrichtung", "einrichtung starten" -> {
                startOnboarding()
                debugInput = ""
                return true // Onboarding steuert das Mikrofon selbst
            }
            "testfoto" -> {
                captureTestPhoto()
                debugInput = ""
                return true
            }
            "einrichtung zurücksetzen" -> {
                // Auch Anrede/Interessen löschen – sonst spricht Lina den neuen
                // Nutzer mit den Daten des vorherigen an
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_ONBOARDING_DONE, false)
                    .remove(PREF_INTERESTS)
                    .remove(PREF_USER_NAME)
                    .apply()
                claude?.reset()
                ttsEngine?.speak("Einrichtung zurückgesetzt. Beim nächsten Start geht es los.")
                debugInput = ""
                return false
            }
        }

        val resolved = intentResolver.resolve(input)
        if (resolved == null && claude != null) {
            // Ebene 2: kein lokaler Befehl erkannt – freie Konversation über Claude
            askClaude(input)
            debugInput = ""
            return true
        }
        val response = handleIntent(resolved ?: ResolvedIntent.Unknown(input))
        android.util.Log.d("LinaLauncher", "Eingabe=\"$input\" Intent=${formatIntent(resolved)} Antwort=\"$response\"")

        debugLog = "Eingabe: \"$input\"\n" +
            "Intent: ${formatIntent(resolved)}\n" +
            "Lina: $response\n\n$debugLog"

        debugInput = ""
        if (istNewsIntent(resolved)) {
            // Reader spricht selbst; danach Folgefenster statt Weckwort-Zwang
            if (resolved is ResolvedIntent.ReadNews && !newsHintGesagt) {
                newsHintGesagt = true
                ttsEngine?.speak("Nach jeder Meldung kannst du direkt sagen: mehr, nächste, oder stopp.")
            }
            openFollowUpWindow(newsMode = true)
            return true
        }
        if (resolved is ResolvedIntent.ReadDocument) {
            // Kamera + Vision laufen asynchron und übernehmen Ansagen/Folgefenster
            return true
        }
        ttsEngine?.speak(response)
        return false
    }

    private fun istNewsIntent(intent: ResolvedIntent?): Boolean =
        intent is ResolvedIntent.ReadNews ||
            intent is ResolvedIntent.NextNews ||
            intent is ResolvedIntent.NewsDetail

    /** Gesprächsmodus: Nach einer freien Claude-Antwort direkt weiter zuhören. */
    private fun continueConversation() = openFollowUpWindow(newsMode = false)

    /**
     * Folgefenster: wartet bis Lina ausgesprochen hat, spielt den Signalton und
     * hört direkt zu – ohne neues Weckwort. Schweigen (~5s) beendet das Fenster.
     * Im [newsMode] reichen kurze Worte ("mehr", "nächste", "stopp"), und nach
     * jeder Meldung öffnet sich das Fenster erneut.
     */
    private fun openFollowUpWindow(newsMode: Boolean) {
        val stt = sttEngine ?: return
        if (onboarding != null) return
        cancelWakeResume()
        stopService(Intent(this, WakeWordService::class.java))
        // Warten bis TTS wirklich fertig ist. Im News-Modus länger stabil still
        // (RSS-Fetch kann eine Sprechpause erzeugen, bevor die Meldungen kommen).
        val quietNeeded = if (newsMode) 6 else 1
        var quietChecks = 0
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                if (onboarding != null) return
                if (piperEngine?.isBusySpeaking() == true) {
                    quietChecks = 0
                    mainHandler.postDelayed(this, 150)
                    return
                }
                if (++quietChecks < quietNeeded) {
                    mainHandler.postDelayed(this, 200)
                    return
                }
                statusText = if (newsMode) "Nachrichten – ich höre…" else "Gespräch – ich höre…"
                Earcons.go()
                var handled = false
                val timeout = Runnable {
                    if (!handled) {
                        handled = true
                        stt.stopListening()
                        resumeWakeWordListening()
                    }
                }
                mainHandler.postDelayed(timeout, STT_TIMEOUT_MS)
                mainHandler.postDelayed({
                    if (handled) return@postDelayed
                    stt.startListening { text ->
                        runOnUiThread {
                            if (handled) return@runOnUiThread
                            handled = true
                            mainHandler.removeCallbacks(timeout)
                            handleFollowUpResult(text, newsMode)
                        }
                    }
                }, 350)
            }
        }, 200)
    }

    private fun handleFollowUpResult(text: String, newsMode: Boolean) {
        if (text.isBlank()) {
            resumeWakeWordListening()
            return
        }
        if (newsMode) {
            val cmd = mapNewsFollowUp(text)
            if (cmd != null) {
                android.util.Log.d("LinaLauncher", "News-Folge: \"$text\" → ${formatIntent(cmd)}")
                val response = handleIntent(cmd)
                if (cmd is ResolvedIntent.Stop) {
                    ttsEngine?.speak(response)
                    resumeWakeWordListening()
                } else {
                    // Meldung/Artikel wird vorgelesen – danach wieder zuhören
                    openFollowUpWindow(newsMode = true)
                }
                return
            }
            // Kein News-Schlüsselwort und kein klarer Befehl → vermutlich
            // Raumgespräch: Fenster STILL schließen, nicht an Claude schicken
            val direct = intentResolver.resolve(text)
            if (direct == null) {
                android.util.Log.d("LinaLauncher", "News-Folge ignoriert (nicht an Lina): \"$text\"")
                resumeWakeWordListening()
            } else {
                debugInput = text
                val uebernommen = processDebugInput()
                if (!uebernommen) resumeWakeWordListening()
            }
            return
        }
        // Gesprächsfenster: alles über Claude – die Regex-Ebene ist für
        // Raumgespräche zu triggerfreudig ("ich ruf dich später an" → Anruf!).
        // Claude kennt den Dialog und kann Befehle (Do) wie Raumgespräche (End)
        // unterscheiden. Nur "Stopp" bleibt lokal – muss sofort wirken.
        val resolved = intentResolver.resolve(text)
        if (resolved is ResolvedIntent.Stop) {
            ttsEngine?.speak(handleIntent(resolved))
            resumeWakeWordListening()
            return
        }
        if (claude != null) {
            askClaude(text) // Folgefrage oder Befehl – Claude entscheidet
        } else {
            debugInput = text
            val uebernommen = processDebugInput()
            if (!uebernommen) resumeWakeWordListening()
        }
    }

    /** Kurzbefehle im Nachrichten-Folgefenster. */
    private fun mapNewsFollowUp(text: String): ResolvedIntent? {
        val t = text.lowercase()
        return when {
            listOf("stopp", "stop", "reicht", "nein", "das war", "aufhören", "genug")
                .any { t.contains(it) } -> ResolvedIntent.Stop
            listOf("nächste", "naechste", "überspringen", "ueberspringen", "andere", "skip")
                .any { t.contains(it) } -> ResolvedIntent.NextNews
            listOf("mehr", "weiter", "artikel", "ganz", "vorlesen", "lies", "details")
                .any { t.contains(it) } -> ResolvedIntent.NewsDetail
            else -> null
        }
    }

    /**
     * Debug: Foto aufnehmen und speichern, damit per adb pull geprüft werden kann,
     * ob der Kreppband-Rahmen formatfüllend im Bild der Rückkamera liegt.
     * Wird nur für die Einrichtung gebraucht – reguläres Vorlesen speichert nichts.
     */
    private fun captureTestPhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ttsEngine?.speak("Die Kamera ist noch nicht freigegeben.", TtsPriority.HIGH)
            PermissionsGuide.requestMissing(this)
            resumeWakeWordListening()
            return
        }
        cancelWakeResume()
        stopService(Intent(this, WakeWordService::class.java))
        ttsEngine?.speak("Ich mache ein Testfoto.", TtsPriority.INTERRUPT)
        val camera = documentCamera ?: DocumentCamera(this).also { documentCamera = it }
        mainHandler.postDelayed({
            camera.capture { bytes ->
                if (bytes == null) {
                    ttsEngine?.speak("Das Testfoto hat nicht geklappt.", TtsPriority.HIGH)
                } else {
                    val dir = java.io.File(getExternalFilesDir(null), "docphotos")
                        .apply { mkdirs() }
                    val file = java.io.File(dir, "test_${System.currentTimeMillis()}.jpg")
                    file.writeBytes(bytes)
                    android.util.Log.d("LinaLauncher", "Testfoto: ${file.absolutePath} (${bytes.size / 1024} kB)")
                    debugLog = "Testfoto: ${file.name}\n\n$debugLog"
                    ttsEngine?.speak("Testfoto gespeichert.", TtsPriority.HIGH)
                }
                resumeWakeWordListening()
            }
        }, 1_500)
    }

    /**
     * Dokument-Vorlesen: fotografiert den Rahmen hinter dem Tablet (Rückkamera)
     * und lässt Claude vorlesen, was darauf wichtig ist.
     */
    private fun readDocumentAloud(verbatimOf: ByteArray? = null) {
        val conversation = claude
        if (conversation == null) {
            ttsEngine?.speak(
                "Zum Vorlesen von Dokumenten brauche ich eine Internetverbindung " +
                    "und den Zugang zu meinem Sprachdienst.",
                TtsPriority.HIGH,
            )
            resumeWakeWordListening()
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ttsEngine?.speak(
                "Ich darf die Kamera noch nicht benutzen. " +
                    "Bitte lass deinen Betreuer die Kamera-Berechtigung freigeben.",
                TtsPriority.HIGH,
            )
            PermissionsGuide.requestMissing(this)
            resumeWakeWordListening()
            return
        }

        cancelWakeResume()
        stopService(Intent(this, WakeWordService::class.java))

        // "Alles vorlesen" nutzt das bereits vorhandene Bild – kein neues Foto
        if (verbatimOf != null) {
            statusText = "Lese den ganzen Text…"
            Earcons.thinking()
            Thread({
                val reply = conversation.readDocument(verbatimOf, verbatim = true)
                runOnUiThread { speakDocumentReply(reply, verbatimOf, offerFullText = false) }
            }, "doc-verbatim").start()
            return
        }

        statusText = "Ich fotografiere das Dokument…"
        ttsEngine?.speak("Einen Moment, ich schaue mir das an.", TtsPriority.INTERRUPT)
        val camera = documentCamera ?: DocumentCamera(this).also { documentCamera = it }

        // Kurz warten, damit die Ansage durch ist (ruhiger Moment für die Aufnahme)
        mainHandler.postDelayed({
            camera.capture { bytes ->
                if (bytes == null) {
                    android.util.Log.w("LinaLauncher", "Dokument-Foto fehlgeschlagen")
                    ttsEngine?.speak(
                        "Ich konnte leider kein Foto machen. Versuch es bitte noch einmal.",
                        TtsPriority.HIGH,
                    )
                    resumeWakeWordListening()
                    return@capture
                }
                android.util.Log.d("LinaLauncher", "Dokument-Foto: ${bytes.size / 1024} kB")
                statusText = "Ich lese das Dokument…"
                Earcons.thinking()
                Thread({
                    val reply = conversation.readDocument(bytes)
                    runOnUiThread { speakDocumentReply(reply, bytes, offerFullText = true) }
                }, "doc-read").start()
            }
        }, 1_500)
    }

    /** Ergebnis der Dokument-Auswertung vorlesen und Folgefenster öffnen. */
    private fun speakDocumentReply(
        reply: LinaReply,
        bytes: ByteArray,
        offerFullText: Boolean,
    ) {
        val text = when (reply) {
            is LinaReply.Say -> reply.text
            is LinaReply.Error -> reply.text
            else -> ""
        }
        android.util.Log.d("LinaLauncher", "Dokument gelesen: ${text.take(80)}…")
        debugLog = "Dokument: ${text.take(200)}\n\n$debugLog"

        val erfolg = reply is LinaReply.Say
        val ansage = if (erfolg && offerFullText) {
            "$text … Soll ich den ganzen Text vorlesen?"
        } else {
            text
        }
        if (ansage.isNotBlank()) ttsEngine?.speak(ansage, TtsPriority.HIGH)

        if (erfolg) {
            lastDocumentImage = bytes
            openDocFollowUp(text)
        } else {
            lastDocumentImage = null
            resumeWakeWordListening()
        }
    }

    /**
     * Folgefenster nach dem Vorlesen: "ja/alles" → ganzer Text, "wiederhole" →
     * nochmal dasselbe, "nochmal/neu" → neues Foto. Alles andere schließt still
     * (Raumgespräch-Schutz).
     */
    private fun openDocFollowUp(lastText: String) {
        val stt = sttEngine ?: return
        if (onboarding != null) return
        cancelWakeResume()
        stopService(Intent(this, WakeWordService::class.java))
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                if (onboarding != null) return
                if (piperEngine?.isBusySpeaking() == true) {
                    mainHandler.postDelayed(this, 200)
                    return
                }
                statusText = "Dokument – ich höre…"
                Earcons.go()
                var handled = false
                val timeout = Runnable {
                    if (!handled) {
                        handled = true
                        stt.stopListening()
                        lastDocumentImage = null
                        resumeWakeWordListening()
                    }
                }
                mainHandler.postDelayed(timeout, STT_TIMEOUT_MS)
                mainHandler.postDelayed({
                    if (handled) return@postDelayed
                    stt.startListening { text ->
                        runOnUiThread {
                            if (handled) return@runOnUiThread
                            handled = true
                            mainHandler.removeCallbacks(timeout)
                            handleDocFollowUp(text, lastText)
                        }
                    }
                }, 350)
            }
        }, 200)
    }

    private fun handleDocFollowUp(text: String, lastText: String) {
        val t = text.lowercase()
        val bild = lastDocumentImage
        when {
            text.isBlank() -> {
                lastDocumentImage = null
                resumeWakeWordListening()
            }
            listOf("ja", "alles", "ganze", "vollständig", "vollstaendig", "kompletten")
                .any { t.contains(it) } && bild != null -> {
                android.util.Log.d("LinaLauncher", "Dokument-Folge: ganzer Text")
                readDocumentAloud(verbatimOf = bild)
            }
            listOf("wiederhol", "nochmal sagen", "noch mal sagen").any { t.contains(it) } -> {
                ttsEngine?.speak(lastText, TtsPriority.HIGH)
                openDocFollowUp(lastText)
            }
            listOf("nochmal", "noch mal", "neu", "nächste seite", "naechste seite", "umgeblättert")
                .any { t.contains(it) } -> {
                android.util.Log.d("LinaLauncher", "Dokument-Folge: neues Foto")
                lastDocumentImage = null
                readDocumentAloud()
            }
            else -> {
                // Befehl oder Raumgespräch – nicht im Dokument-Kontext behandeln
                lastDocumentImage = null
                val resolved = intentResolver.resolve(text)
                if (resolved != null) {
                    debugInput = text
                    val uebernommen = processDebugInput()
                    if (!uebernommen) resumeWakeWordListening()
                } else {
                    android.util.Log.d("LinaLauncher", "Dokument-Folge ignoriert: \"$text\"")
                    resumeWakeWordListening()
                }
            }
        }
    }

    /**
     * Ebene 2 des Intent-Systems: fragt Claude (blockierend, daher eigener Thread).
     * Say → vorlesen, Do → lokal ausführen, Error → Fehlermeldung vorlesen.
     */
    private fun askClaude(input: String) {
        val conversation = claude ?: return
        statusText = "Lina denkt nach…"
        Earcons.thinking()
        Thread {
            val reply = conversation.ask(input)
            runOnUiThread {
                val response = when (reply) {
                    is LinaReply.Say -> reply.text
                    is LinaReply.Do -> handleIntent(reply.intent)
                    is LinaReply.Error -> reply.text
                    is LinaReply.End -> "" // Raumgespräch – still zurück zum Weckwort
                }
                android.util.Log.d("LinaLauncher", "Eingabe=\"$input\" Intent=Claude(${reply::class.simpleName}) Antwort=\"$response\"")
                debugLog = "Eingabe: \"$input\"\n" +
                    "Intent: Claude (${reply::class.simpleName})\n" +
                    "Lina: $response\n\n$debugLog"
                val newsDo = reply is LinaReply.Do && istNewsIntent(reply.intent)
                if (response.isNotBlank() && !newsDo) ttsEngine?.speak(response, TtsPriority.HIGH)
                when {
                    // Gesprächsmodus: direkt weiter zuhören, kein neues Weckwort nötig
                    reply is LinaReply.Say -> continueConversation()
                    // Claude hat Nachrichten erkannt → Nachrichten-Folgefenster
                    newsDo -> openFollowUpWindow(newsMode = true)
                    else -> {
                        statusText = "Lina bereit – sag \"$WAKE_WORD\""
                        resumeWakeWordListening()
                    }
                }
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
            "Aufnahme startet und läuft dreißig Sekunden. Sprich nach dem Ton, " +
                "mit kurzen Pausen zwischen den Sätzen.",
            TtsPriority.INTERRUPT,
        )
        mainHandler.postDelayed({
            Earcons.go()
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
        is ResolvedIntent.NextChapter -> {
            audiobookManager?.nextChapter()
            "Nächstes Kapitel…"
        }
        is ResolvedIntent.PreviousChapter -> {
            audiobookManager?.previousChapter()
            "Vorheriges Kapitel…"
        }
        is ResolvedIntent.GoToChapter -> {
            audiobookManager?.goToChapter(intent.number)
            "Kapitel ${intent.number}…"
        }
        is ResolvedIntent.ListChapters -> {
            audiobookManager?.listChapters()
            "Kapitel werden aufgelistet…"
        }
        is ResolvedIntent.ReadDocument -> {
            readDocumentAloud()
            "" // Ansagen macht readDocumentAloud selbst
        }
        is ResolvedIntent.SetReminder -> {
            reminderManager?.createFromSpeech(intent.rawInput)
            "" // ReminderManager sagt selbst an
        }
        is ResolvedIntent.SetReminderAt -> {
            val millis = try {
                java.time.LocalDateTime.parse(intent.isoZeit)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
            } catch (e: Exception) {
                android.util.Log.w("LinaLauncher", "Ungültiger Zeitpunkt: ${intent.isoZeit}", e)
                null
            }
            if (millis != null) {
                reminderManager?.create(intent.text, millis, intent.daily)
            } else {
                ttsEngine?.speak(
                    "Ich habe den Zeitpunkt nicht verstanden. Sag zum Beispiel: " +
                        "Erinnere mich morgen um zehn an den Arzt.",
                    TtsPriority.HIGH,
                )
            }
            ""
        }
        is ResolvedIntent.ListReminders -> {
            reminderManager?.list()
            ""
        }
        is ResolvedIntent.ClearReminders -> {
            reminderManager?.clearAll()
            ""
        }
        is ResolvedIntent.Time -> {
            val now = java.util.Calendar.getInstance()
            val h = now.get(java.util.Calendar.HOUR_OF_DAY)
            val m = now.get(java.util.Calendar.MINUTE)
            if (m == 0) "Es ist $h Uhr." else "Es ist $h Uhr $m."
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
        is ResolvedIntent.NextChapter -> "NextChapter"
        is ResolvedIntent.PreviousChapter -> "PreviousChapter"
        is ResolvedIntent.GoToChapter -> "GoToChapter(${intent.number})"
        is ResolvedIntent.ListChapters -> "ListChapters"
        is ResolvedIntent.AcceptCall -> "AcceptCall"
        is ResolvedIntent.RejectCall -> "RejectCall"
        is ResolvedIntent.HangUp -> "HangUp"
        is ResolvedIntent.ReadDocument -> "ReadDocument"
        is ResolvedIntent.SetReminder -> "SetReminder"
        is ResolvedIntent.SetReminderAt -> "SetReminderAt(${intent.isoZeit})"
        is ResolvedIntent.ListReminders -> "ListReminders"
        is ResolvedIntent.ClearReminders -> "ClearReminders"
        is ResolvedIntent.Time -> "Time"
        is ResolvedIntent.Stop -> "Stop"
        is ResolvedIntent.Unknown -> "Unknown"
    }

    override fun onDestroy() {
        try { unregisterReceiver(debugReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(accessibilityReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(wakeWordReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(reminderReceiver) } catch (_: Exception) {}
        sttEngine?.destroy()
        audiobookManager?.release()
        ttsEngine?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val WAKE_WORD = "Hey Lina"
        // Whisper ist nicht-streamend: bis zu 10s Aufnahme + Transkriptionszeit
        private const val STT_TIMEOUT_MS = 30_000L
        private const val PREFS = "lina"
        private const val PREF_ONBOARDING_DONE = "onboarding_done"
        private const val PREF_INTERESTS = "user_interests"
        private const val PREF_USER_NAME = "user_name"
        private const val PREF_REGION = "user_region"
    }
}
