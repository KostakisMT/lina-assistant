package dev.lina.ui.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import kotlin.math.roundToInt
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.lina.core.accessibility.LinaAccessibilityService
import dev.lina.core.audio.Earcons
import dev.lina.core.contacts.Contact
import dev.lina.core.contacts.ContactMatchResult
import dev.lina.core.contacts.ContactRepository
import dev.lina.core.contacts.FuzzyContactMatcher
import dev.lina.BuildConfig
import dev.lina.core.intent.LocalCommandResolver
import dev.lina.core.intent.ResolvedIntent
import dev.lina.core.llm.ConversationEngine
import dev.lina.core.llm.ConversationEngineProvider
import dev.lina.core.llm.DocumentReadResult
import dev.lina.core.llm.LinaReply
import dev.lina.core.llm.SuggestedCalendarEvent
import dev.lina.core.sim.SimChangeDetector
import dev.lina.core.sim.SimChangeResult
import dev.lina.core.sim.SimIdentityReader
import dev.lina.core.stt.SttEngine
import dev.lina.core.stt.TranscriptPlausibility
import dev.lina.core.stt.VoskSttEngine
import dev.lina.core.stt.WhisperSttEngine
import dev.lina.core.tts.AndroidTtsEngine
import dev.lina.core.tts.PiperTtsEngine
import dev.lina.core.tts.TtsEngine
import dev.lina.core.tts.TtsPriority
import dev.lina.core.wakeword.WakeWordService
import dev.lina.feature.audiobook.AudiobookManager
import dev.lina.feature.calls.CallHandler
import dev.lina.feature.calls.CallResult
import dev.lina.feature.contactimport.ContactImportManager
import dev.lina.feature.contactimport.ContactImportStore
import dev.lina.feature.contactimport.ImportResult
import dev.lina.feature.news.NewsReader
import dev.lina.feature.news.NewsSyncWorker
import dev.lina.feature.sms.SmsReader
import dev.lina.feature.sms.SmsSender
import dev.lina.feature.document.DocumentCamera
import dev.lina.feature.helper.HelperCallLauncher
import dev.lina.core.text.GermanCalendarNames
import dev.lina.feature.calendar.CalendarManager
import dev.lina.feature.reminder.ReminderManager
import dev.lina.feature.reminder.ReminderReceiver
import dev.lina.feature.onboarding.AccessibilityGuide
import dev.lina.feature.onboarding.VoiceOnboarding
import dev.lina.feature.onboarding.BatteryWhitelistGuide
import dev.lina.feature.onboarding.PermissionsGuide
import dev.lina.ui.components.AudiobookPlayerPanel
import dev.lina.ui.components.CalendarPanel
import dev.lina.ui.components.LinaOrb
import dev.lina.ui.components.LinaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class LauncherActivity : ComponentActivity() {

    private var ttsEngine: TtsEngine? = null
    private var piperEngine: PiperTtsEngine? = null
    private var claude: ConversationEngine? = null
    private var documentCamera: DocumentCamera? = null
    private var helperCallLauncher: HelperCallLauncher? = null
    /** Nur während des Dokument-Folgefensters im RAM – wird danach verworfen. */
    private var lastDocumentImage: ByteArray? = null
    /** Im Dokument erkannter Termin, während auf die Ja/Nein-Antwort gewartet wird. */
    private var pendingCalendarSuggestion: SuggestedCalendarEvent? = null
    /** Ansagetext, um nach der Termin-Rückfrage normal in openDocFollowUp() weiterzumachen. */
    private var pendingDocumentFollowUpText: String? = null
    private val intentResolver = LocalCommandResolver()
    private var contactMatcher: FuzzyContactMatcher? = null
    private var callHandler: CallHandler? = null
    private var smsReader: SmsReader? = null
    private var smsSender: SmsSender? = null
    private var newsReader: NewsReader? = null
    private var audiobookManager: AudiobookManager? = null
    private var reminderManager: ReminderManager? = null
    private var calendarManager: CalendarManager? = null
    private var contactImportManager: ContactImportManager? = null
    /** Fingerabdruck der SIM, für die gerade eine Import-Nachfrage offen ist. */
    /** Kontakt, dessen Sondernummer gerade zur Bestätigung aussteht. */
    private var pendingRiskyCall: Contact? = null

    private var pendingSimImportIdentity: String? = null
    /** true, wenn `listBooks()` gerade den LibriVox-Vorschlags-Dialog geöffnet hat (steuert Weckwort-Neustart). */
    private var librivoxSuggestionOpened = false
    private var statusText by mutableStateOf("Lina startet…")
    /** Treibt die Statuskugel (LinaOrb) für Angehörige/Besucher – rein additiv neben [statusText]. */
    private var linaActivity by mutableStateOf<LinaActivity>(LinaActivity.Loading)
    private var debugInput by mutableStateOf("")
    private var debugLog by mutableStateOf("")
    private var linaReady by mutableStateOf(false)
    /** Steuert, ob der rechte Panel-Slot den Kalender statt des Hörbuch-Players zeigt. */
    private var calendarVisible by mutableStateOf(false)
    private var sttEngine: SttEngine? = null
    private var onboarding: VoiceOnboarding? = null
    private var newsHintGesagt = false
    /** true, wenn das aktuelle Zuhörfenster ein laufendes Hörbuch lautlos pausiert hat. */
    private var duckedAudiobook = false
    /** true, wenn der Nutzer während des Fensters explizit Pause/Stopp wollte – dann beim Aufwachen nicht automatisch weiterspielen. */
    private var explicitAudiobookPause = false
    /** true, wenn die nächste Claude-Anfrage direkt aus einem echten Weckwort-Trigger stammt (nicht Folgefenster/Debug). */
    private var freshWakeWordTurn = false
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

    /**
     * Live-Erkennung während des Betriebs (App-Start deckt den häufigeren Fall
     * ab – das hier fängt einen SIM-Wechsel ohne Geräte-Neustart). Manche OEMs
     * verweigern die Registrierung ohne Sonderrechte; dann bleibt der
     * App-bereit-Check in [startVoicePipeline] die verlässliche Schiene.
     */
    private val simStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // SIM-Init feuert die Broadcasts oft mehrfach kurz hintereinander.
            mainHandler.postDelayed({ checkSimChangeAndMaybePrompt() }, 1_000)
        }
    }

    private val vcardPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            handleVcardPicked(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LinaTheme {
                // "Spricht gerade" lässt sich nicht aus linaActivity ableiten (kein
                // Aufrufpunkt setzt das) – separat gepollt, hat Vorrang vor jedem
                // anderen Zustand, solange es zutrifft.
                var isSpeaking by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    while (isActive) {
                        isSpeaking = ttsEngine?.isSpeaking() == true
                        delay(250)
                    }
                }
                val resolvedActivity = if (isSpeaking) LinaActivity.Speaking else linaActivity

                // Gerät liegt fast immer im Querformat (Lenovo-Tablet, Ständer) –
                // Kugel+Status und Player nebeneinander statt untereinander, damit
                // die Breite genutzt wird statt nur ein schmaler Mittelstreifen.
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val currentAudiobookManager = audiobookManager
                    val currentCalendarManager = calendarManager
                    val showCalendar = linaReady && calendarVisible && currentCalendarManager != null
                    val showPlayer = !showCalendar && linaReady && currentAudiobookManager != null &&
                        currentAudiobookManager.currentStatus() != null

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        LinaOrb(activity = resolvedActivity)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (showCalendar) {
                        Spacer(modifier = Modifier.width(32.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CalendarPanel(calendarManager = currentCalendarManager!!)
                        }
                    } else if (showPlayer) {
                        Spacer(modifier = Modifier.width(32.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AudiobookPlayerPanel(audiobookManager = currentAudiobookManager!!)
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
        // Nur in Debug-Builds registriert: erlaubt Text-Injektion ohne echtes
        // Mikrofon (adb shell am broadcast -a dev.lina.DEBUG_INPUT --es text
        // "..."). War zuvor unconditional exportiert (RECEIVER_EXPORTED, keine
        // Permission) – jede App auf dem Gerät konnte darüber echte Anrufe/SMS
        // auslösen (CallHandler/SmsSender haben keinen eigenen Schutz). Gefunden
        // 2026-08-22, siehe TODO.md „Risiken & Showstopper".
        if (BuildConfig.DEBUG) {
            registerReceiver(
                debugReceiver,
                IntentFilter("dev.lina.DEBUG_INPUT"),
                RECEIVER_EXPORTED,
            )
        }
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
        try {
            registerReceiver(
                simStateReceiver,
                // Beide Konstanten (ACTION_SIM_CARD_STATE_CHANGED/ACTION_SIM_STATE_CHANGED)
                // sind @SystemApi/versteckt und im öffentlichen SDK-Stub nicht erreichbar –
                // die Broadcast-Action als Literal funktioniert für einen zur Laufzeit
                // registrierten Empfänger trotzdem (nur manifest-deklarierte implizite
                // Empfänger sind seit Android 8 eingeschränkt).
                IntentFilter("android.intent.action.SIM_STATE_CHANGED"),
                RECEIVER_NOT_EXPORTED,
            )
        } catch (e: Exception) {
            android.util.Log.w("LinaLauncher", "SIM-Status-Empfänger nicht registrierbar", e)
        }

        if (!PermissionsGuide.allGranted(this)) {
            PermissionsGuide.requestMissing(this)
        }

        Thread({ cleanupOldDebugFiles() }, "debug-cleanup").start()

        statusText = "Linas Stimme wird geladen…"
        linaActivity = LinaActivity.Loading
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
        linaActivity = LinaActivity.Loading

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
                    linaActivity = LinaActivity.Idle
                    ttsEngine?.speak(
                        "Ich höre jetzt auf das Weckwort $WAKE_WORD.",
                        TtsPriority.NORMAL,
                    )
                    checkSimChangeAndMaybePrompt()
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
                runOnUiThread {
                    statusText = "Whisper fehlgeschlagen – lade Vosk…"
                    linaActivity = LinaActivity.Loading
                }
                val vosk = VoskSttEngine(applicationContext)
                sttEngine = vosk
                vosk.initialize(
                    onReady = onSttReady,
                    onError = { e ->
                        runOnUiThread {
                            voicePipelineStarted = false
                            statusText = "Spracherkennung fehlgeschlagen: ${e.message}"
                            linaActivity = LinaActivity.Error
                            mainHandler.postDelayed({ linaActivity = LinaActivity.Idle }, ERROR_DISPLAY_MS)
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
            WakeWordService.pauseListening(this)
            // Hörbuch lautlos pausieren: sonst hört das Mikrofon der eigenen
            // Erzählstimme zu und schickt Buchtext als vermeintlichen Befehl weiter
            duckedAudiobook = audiobookManager?.duckForListening() == true
            statusText = "Ich höre…"
            linaActivity = LinaActivity.Listening
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
                        // Diese Eingabe kam direkt nach einem echten Weckwort-
                        // Trigger – die App weiß das sicher (anders als Claudes
                        // eigene Vermutung), verhindert fälschliches
                        // gespraech_beenden bei Einladungen wie "Lass uns reden"
                        freshWakeWordTurn = true
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
        WakeWordService.resumeListening(this)
        statusText = "Lina bereit – sag \"$WAKE_WORD\""
                    linaActivity = LinaActivity.Idle
    }

    private fun resumeWakeWordListening() {
        // Ein zuvor lautlos geducktes Hörbuch wieder anstellen – außer der Nutzer
        // wollte in diesem Zuhörfenster tatsächlich pausieren/stoppen. Zentrale
        // Stelle, weil hierher aus jedem Fenster (Befehl/Gespräch/Nachrichten/
        // Dokument) am Ende der Weg zurückführt.
        if (duckedAudiobook && !explicitAudiobookPause) {
            audiobookManager?.resumeAfterListening()
        }
        duckedAudiobook = false
        explicitAudiobookPause = false
        // Verzögert, damit Linas eigene Antwort nicht die Weckwort-Erkennung triggert
        mainHandler.removeCallbacks(wakeResumeRunnable)
        mainHandler.postDelayed(wakeResumeRunnable, 2_000)
    }

    private fun cancelWakeResume() {
        mainHandler.removeCallbacks(wakeResumeRunnable)
    }

    /**
     * Wartet, bis Lina nicht mehr spricht (Piper-Warteschlange leer), und ruft
     * dann [onReady] auf. Sicherheitsnetz: bricht nach [maxWaitMs] ab und ruft
     * stattdessen [onTimeout] auf, statt endlos zu warten – die eigentlichen
     * Auslöser dafür (Piper blockierte bei langen Texten; ExoPlayer reaktivierte
     * sich selbst) sind am 2026-07-25 behoben, das hier ist zusätzliche Härtung
     * gegen einen künftigen, unbekannten Hänger.
     */
    private fun waitForSilenceThenRun(
        quietNeeded: Int = 1,
        maxWaitMs: Long = 45_000L,
        onReady: () -> Unit,
        onTimeout: () -> Unit,
    ) {
        var quietChecks = 0
        val deadline = SystemClock.uptimeMillis() + maxWaitMs
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                if (onboarding != null) return
                if (SystemClock.uptimeMillis() > deadline) {
                    android.util.Log.w(
                        "LinaLauncher",
                        "waitForSilenceThenRun: Timeout nach ${maxWaitMs}ms – TTS reagiert nicht, breche ab",
                    )
                    onTimeout()
                    return
                }
                if (piperEngine?.isBusySpeaking() == true) {
                    quietChecks = 0
                    mainHandler.postDelayed(this, 150)
                    return
                }
                if (++quietChecks < quietNeeded) {
                    mainHandler.postDelayed(this, 200)
                    return
                }
                onReady()
            }
        }, 200)
    }

    private fun initializeLina(tts: TtsEngine) {
        ttsEngine = tts

        val repo = ContactRepository(this)
        val matcher = FuzzyContactMatcher(repo)
        contactMatcher = matcher

        callHandler = CallHandler(this, tts, matcher)
        val reader = SmsReader(this, tts)
        smsReader = reader
        smsSender = SmsSender(this, tts, matcher, reader)
        newsReader = NewsReader(this, tts)
        audiobookManager = AudiobookManager(this, tts)
        reminderManager = ReminderManager(this, tts)
        calendarManager = CalendarManager(this, tts)
        contactImportManager = ContactImportManager(this)

        NewsSyncWorker.schedule(this)

        initClaude(repo)

        linaReady = true
        statusText = "Lina bereit"
        linaActivity = LinaActivity.Idle
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
        claude = ConversationEngineProvider.create(
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
        WakeWordService.pauseListening(this)
        statusText = "Ersteinrichtung läuft…"
        linaActivity = LinaActivity.Loading
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
                    linaActivity = LinaActivity.Idle
            resumeWakeWordListening()
        }
    }

    /** @return true, wenn Claude die Eingabe asynchron übernimmt (Gesprächsmodus
     *  steuert dann selbst das Zuhören und den Weckwort-Neustart). */
    private fun processDebugInput(): Boolean {
        val input = debugInput.trim()
        if (input.isEmpty()) return false
        // Nur beim tatsächlichen Verbrauch (Claude-Zweig unten) relevant – hier
        // schon abgreifen, damit das Flag nicht in einen späteren, unabhängigen
        // Aufruf durchsickert (z.B. wenn diese Eingabe stattdessen lokal matcht).
        val freshWakeWord = freshWakeWordTurn
        freshWakeWordTurn = false

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
            askClaude(input, freshWakeWord = freshWakeWord)
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
        if (resolved is ResolvedIntent.ImportSimContacts || resolved is ResolvedIntent.ImportVcardContacts) {
            // Import/Dateipicker läuft asynchron und übernimmt Ansage/Weckwort-Neustart selbst
            return true
        }
        if (resolved is ResolvedIntent.ListAudiobooks && librivoxSuggestionOpened) {
            // Nur bei dünner Bibliothek: das Folgefenster übernimmt Weckwort-Neustart selbst
            librivoxSuggestionOpened = false
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
        WakeWordService.pauseListening(this)
        // Im News-Modus länger stabil still warten (RSS-Fetch kann eine
        // Sprechpause erzeugen, bevor die Meldungen kommen).
        waitForSilenceThenRun(
            quietNeeded = if (newsMode) 6 else 1,
            onReady = {
                statusText = if (newsMode) "Nachrichten – ich höre…" else "Gespräch – ich höre…"
                linaActivity = LinaActivity.Listening
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
            },
            onTimeout = { resumeWakeWordListening() },
        )
    }

    private fun handleFollowUpResult(text: String, newsMode: Boolean) {
        // Zweite Reihe hinter dem Filter in WhisperSttEngine: greift auch für
        // den Vosk-Fallback und schützt vor allem das GESPRÄCHS-Fenster, das
        // – anders als das News-Fenster weiter unten – sonst jeden Text
        // ungeprüft an die Claude-API weiterreicht.
        if (!TranscriptPlausibility.isPlausible(text)) {
            if (text.isNotBlank()) {
                android.util.Log.d(
                    "LinaLauncher",
                    "Folgefenster: unplausibles Transkript verworfen: \"$text\"",
                )
            }
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
        // unterscheiden. Nur "Stopp" und der Schlafmodus bleiben lokal – müssen
        // sofort wirken, unabhängig vom Gesprächsverlauf (sonst plaudert Claude
        // nur "Gute Nacht" zurück, ohne dass Bildschirm/Lautstärke reagieren).
        val resolved = intentResolver.resolve(text)
        if (resolved is ResolvedIntent.Stop ||
            resolved is ResolvedIntent.SleepMode ||
            resolved is ResolvedIntent.SleepModeOff
        ) {
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
     * Räumt alte Debug-Dateien auf, die sonst unbegrenzt liegen bleiben:
     * VoiceOnboarding-Sitzungsordner (mit Sprachaufnahmen + Antworten) und
     * "testfoto"-Bilder. Läuft im Hintergrund bei jedem App-Start, löscht
     * alles älter als DEBUG_FILE_RETENTION_DAYS Tage.
     */
    private fun cleanupOldDebugFiles() {
        val cutoff = System.currentTimeMillis() - DEBUG_FILE_RETENTION_DAYS * 24 * 60 * 60 * 1000L
        listOfNotNull(
            getExternalFilesDir("onboarding"),
            java.io.File(getExternalFilesDir(null), "docphotos"),
        ).forEach { dir ->
            if (!dir.exists()) return@forEach
            dir.listFiles()?.forEach { entry ->
                if (entry.lastModified() < cutoff) {
                    val deleted = entry.deleteRecursively()
                    val status = if (deleted) "gelöscht" else "Löschen fehlgeschlagen"
                    android.util.Log.d(
                        "LinaLauncher",
                        "Aufräumen: ${entry.name} (Alter über $DEBUG_FILE_RETENTION_DAYS Tage) $status",
                    )
                }
            }
        }
    }

    /**
     * "Lauter"/"leiser" außerhalb der Hörbuch-Wiedergabe: passt die
     * Systemlautstärke (Musik-Stream, über den Piper und ExoPlayer beide
     * ausgeben) an. Die Bestätigung wird bewusst über [ttsEngine] gesprochen,
     * nicht über die System-Lautstärkeanzeige – die hilft einem blinden Nutzer
     * nicht.
     */
    private fun adjustSystemVolume(raise: Boolean) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0,
        )
        announceSystemVolume(am)
    }

    /** Direkter Sollwert, z.B. aus "Lautstärke fünf" (=50) oder "... 70 Prozent". */
    private fun setSystemVolume(percent: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * percent.coerceIn(0, 100) / 100f).roundToInt().coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        announceSystemVolume(am)
    }

    private fun announceSystemVolume(am: AudioManager) {
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percent = if (max > 0) current * 100 / max else 0
        val hinweis = when {
            current >= max -> "Lautstärke $percent Prozent, geht nicht lauter."
            current <= 0 -> "Lautstärke aus."
            else -> "Lautstärke $percent Prozent."
        }
        ttsEngine?.speak(hinweis, TtsPriority.NORMAL)
    }

    /**
     * Schlafmodus: dimmt nur die Helligkeit des eigenen Fensters (kein
     * WRITE_SETTINGS nötig, da keine Systemeinstellung verändert wird – Lina
     * läuft ohnehin dauerhaft im Vordergrund als Home-App) und senkt die
     * Lautstärke auf einen ruhigen Pegel. Trifft Hörbuch oder System, je
     * nachdem was gerade läuft – dieselbe Weiche wie bei "lauter"/"leiser".
     */
    private fun enterSleepMode() {
        window.attributes = window.attributes.apply { screenBrightness = SLEEP_MODE_BRIGHTNESS }
        if (audiobookManager?.isPlaying == true) {
            audiobookManager?.setVolume(SLEEP_MODE_VOLUME_PERCENT)
        } else {
            setSystemVolume(SLEEP_MODE_VOLUME_PERCENT)
        }
    }

    private fun exitSleepMode() {
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
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
        WakeWordService.pauseListening(this)
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
        WakeWordService.pauseListening(this)

        // "Alles vorlesen" nutzt das bereits vorhandene Bild – kein neues Foto
        if (verbatimOf != null) {
            statusText = "Lese den ganzen Text…"
            linaActivity = LinaActivity.Thinking
            Earcons.thinking()
            Thread({
                val result = conversation.readDocument(verbatimOf, verbatim = true)
                runOnUiThread { speakDocumentReply(result, verbatimOf, offerFullText = false) }
            }, "doc-verbatim").start()
            return
        }

        statusText = "Ich fotografiere das Dokument…"
        linaActivity = LinaActivity.Thinking
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
                linaActivity = LinaActivity.Thinking
                Earcons.thinking()
                Thread({
                    val result = conversation.readDocument(bytes)
                    runOnUiThread { speakDocumentReply(result, bytes, offerFullText = true) }
                }, "doc-read").start()
            }
        }, 1_500)
    }

    /** Ergebnis der Dokument-Auswertung vorlesen und Folgefenster öffnen. */
    private fun speakDocumentReply(
        result: DocumentReadResult,
        bytes: ByteArray,
        offerFullText: Boolean,
    ) {
        val reply = result.reply
        val text = when (reply) {
            is LinaReply.Say -> reply.text
            is LinaReply.Error -> reply.text
            else -> ""
        }
        android.util.Log.d("LinaLauncher", "Dokument gelesen: ${text.take(80)}…")
        debugLog = "Dokument: ${text.take(200)}\n\n$debugLog"

        val erfolg = reply is LinaReply.Say
        val suggestion = result.suggestedEvent.takeIf { erfolg }
        val ansage = buildString {
            append(if (erfolg && offerFullText) "$text … Soll ich den ganzen Text vorlesen?" else text)
            if (suggestion != null) {
                append(" Übrigens, im Dokument steht ein Termin: ${suggestion.title} am ${suggestion.date}.")
                append(" Soll ich den eintragen?")
            }
        }
        if (ansage.isNotBlank()) ttsEngine?.speak(ansage, TtsPriority.HIGH)

        if (erfolg) {
            lastDocumentImage = bytes
            if (suggestion != null) {
                pendingCalendarSuggestion = suggestion
                pendingDocumentFollowUpText = text
                openDocCalendarFollowUp()
            } else {
                openDocFollowUp(text)
            }
        } else {
            lastDocumentImage = null
            resumeWakeWordListening()
        }
    }

    /**
     * Eigener, komplett neuer Ja/Nein-Dialog für einen im Dokument erkannten
     * Termin – bewusst NICHT in handleDocFollowUp() gemischt, damit "ja"/
     * "alles" dort weiterhin ausschließlich "ganzen Text vorlesen" bedeutet.
     * Nach der Antwort geht es normal in openDocFollowUp() weiter (das
     * "alles vorlesen?"-Fenster bleibt erreichbar).
     */
    private fun openDocCalendarFollowUp() {
        val stt = sttEngine ?: return
        if (onboarding != null) return
        cancelWakeResume()
        WakeWordService.pauseListening(this)
        waitForSilenceThenRun(
            onReady = {
                statusText = "Termin eintragen? – ich höre…"
                linaActivity = LinaActivity.Listening
                Earcons.go()
                var handled = false
                val timeout = Runnable {
                    if (!handled) {
                        handled = true
                        stt.stopListening()
                        openDocFollowUp(pendingDocumentFollowUpText ?: "")
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
                            handleDocCalendarFollowUp(text)
                        }
                    }
                }, 350)
            },
            onTimeout = { openDocFollowUp(pendingDocumentFollowUpText ?: "") },
        )
    }

    private fun handleDocCalendarFollowUp(text: String) {
        val lower = text.lowercase()
        val suggestion = pendingCalendarSuggestion
        pendingCalendarSuggestion = null
        if (suggestion != null &&
            listOf("ja", "klar", "gerne", "mach", "bitte").any { lower.contains(it) }
        ) {
            calendarManager?.create(suggestion.title, suggestion.date, suggestion.time, source = "document")
        }
        openDocFollowUp(pendingDocumentFollowUpText ?: "")
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
        WakeWordService.pauseListening(this)
        waitForSilenceThenRun(
            onReady = {
                statusText = "Dokument – ich höre…"
                linaActivity = LinaActivity.Listening
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
            },
            onTimeout = {
                lastDocumentImage = null
                resumeWakeWordListening()
            },
        )
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
     * App-bereit-Check: vergleicht den aktuellen SIM-Fingerabdruck gegen den
     * zuletzt gesehenen. `FirstSeen` (auch beim allerersten Start mit bereits
     * eingelegter SIM) und `Changed` lösen beide die Nachfrage aus – das ist
     * genau das Hauptszenario ("Nutzer legt seine SIM mit Kontakten ein").
     */
    private fun checkSimChangeAndMaybePrompt() {
        val detector = SimChangeDetector(SimIdentityReader(this), ContactImportStore(this))
        when (val result = detector.evaluate()) {
            is SimChangeResult.FirstSeen -> {
                ContactImportStore(this).recordSeen(result.identity)
                pendingSimImportIdentity = result.identity
                openSimImportFollowUp()
            }
            is SimChangeResult.Changed -> {
                val store = ContactImportStore(this)
                store.recordSeen(result.identity)
                if (result.identity != store.declinedIdentity()) {
                    pendingSimImportIdentity = result.identity
                    openSimImportFollowUp()
                }
            }
            SimChangeResult.Unchanged, SimChangeResult.NoSim -> Unit
        }
    }

    /**
     * Rückfrage vor dem Wählen einer Sondernummer (Premium, Service,
     * Anbieter-Kurzwahl). Siehe [dev.lina.core.contacts.PhoneNumberRisk] –
     * der Nutzer sieht nicht, wen Lina anruft, deshalb muss die Entscheidung
     * gesprochen bei ihm liegen.
     *
     * Bei ausbleibender oder unverstandener Antwort wird NICHT gewählt: bei
     * einer teuren Nummer ist Nichtstun die richtige Voreinstellung.
     */
    private fun openRiskyCallConfirm(contact: Contact, prompt: String) {
        val stt = sttEngine
        if (stt == null || onboarding != null) {
            // Ohne Spracherkennung oder mitten in der Einrichtung lässt sich
            // nicht nachfragen. Dann NICHT wählen – aber auch nicht still
            // verschlucken: der Nutzer hat um einen Anruf gebeten und muss
            // hören, dass er nicht zustande kommt. Am 2026-08-30 am Gerät
            // beobachtet: während der laufenden Einrichtung verschwand der
            // Anrufwunsch spurlos, ohne Wählen und ohne jede Rückmeldung.
            android.util.Log.w(
                "LinaLauncher",
                "Sondernummer ${contact.displayName}: Rückfrage nicht möglich " +
                    "(stt=${stt != null}, onboarding=${onboarding != null}) – kein Anruf",
            )
            ttsEngine?.speak(
                "${contact.displayName} ist eine Sondernummer. Ich kann gerade nicht " +
                    "nachfragen und rufe deshalb nicht an.",
                TtsPriority.HIGH,
            )
            return
        }
        pendingRiskyCall = contact
        android.util.Log.d(
            "LinaLauncher",
            "Sondernummer erkannt: ${contact.displayName} (${contact.phoneNumber}) – " +
                "nicht gewählt, frage nach",
        )
        cancelWakeResume()
        WakeWordService.pauseListening(this)
        ttsEngine?.speak(prompt, TtsPriority.HIGH)
        waitForSilenceThenRun(
            onReady = {
                statusText = "Sondernummer – ich höre…"
                linaActivity = LinaActivity.Listening
                Earcons.go()
                var handled = false
                val timeout = Runnable {
                    if (!handled) {
                        handled = true
                        stt.stopListening()
                        pendingRiskyCall = null
                        android.util.Log.d("LinaLauncher", "Sondernummer: keine Antwort – kein Anruf")
                        ttsEngine?.speak("Ich habe nicht angerufen.", TtsPriority.NORMAL)
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
                            handleRiskyCallConfirm(text)
                        }
                    }
                }, 350)
            },
            onTimeout = {
                pendingRiskyCall = null
                resumeWakeWordListening()
            },
        )
    }

    private fun handleRiskyCallConfirm(text: String) {
        val contact = pendingRiskyCall
        pendingRiskyCall = null
        val t = text.lowercase()
        when {
            contact == null -> resumeWakeWordListening()
            listOf("ja", "klar", "genau", "trotzdem", "mach", "bitte").any { t.contains(it) } -> {
                android.util.Log.d("LinaLauncher", "Sondernummer bestätigt: \"$text\" – wähle ${contact.displayName}")
                callHandler?.dialContact(contact)
            }
            // Alles andere gilt als Nein – auch Unverstandenes. Eine teure
            // Nummer im Zweifel NICHT zu wählen ist die sichere Richtung.
            else -> {
                android.util.Log.d("LinaLauncher", "Sondernummer abgelehnt: \"$text\" – kein Anruf")
                ttsEngine?.speak("Alles klar, ich rufe nicht an.", TtsPriority.NORMAL)
                resumeWakeWordListening()
            }
        }
    }

    /** Sprach-Nachfrage bei automatisch erkannter neuer/anderer SIM-Karte. */
    private fun openSimImportFollowUp() {
        val stt = sttEngine ?: return
        if (onboarding != null) return
        cancelWakeResume()
        WakeWordService.pauseListening(this)
        ttsEngine?.speak(
            "Ich habe eine neue SIM-Karte erkannt. Soll ich die Kontakte übernehmen?",
            TtsPriority.HIGH,
        )
        waitForSilenceThenRun(
            onReady = {
                statusText = "SIM-Import – ich höre…"
                linaActivity = LinaActivity.Listening
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
                            handleSimImportFollowUp(text)
                        }
                    }
                }, 350)
            },
            onTimeout = { resumeWakeWordListening() },
        )
    }

    private fun handleSimImportFollowUp(text: String) {
        val t = text.lowercase()
        val identity = pendingSimImportIdentity
        pendingSimImportIdentity = null
        when {
            listOf("ja", "klar", "gerne", "mach", "bitte").any { t.contains(it) } -> {
                runSimImport()
            }
            listOf("nein", "nicht", "später", "spaeter").any { t.contains(it) } -> {
                if (identity != null) ContactImportStore(this).recordDeclined(identity)
                ttsEngine?.speak("Alles klar, ich lasse es.", TtsPriority.NORMAL)
                resumeWakeWordListening()
            }
            // Weder Ja noch Nein erkannt – nicht als abgelehnt vermerken, der
            // manuelle Befehl ("Kontakte von der SIM importieren") bleibt so
            // als Rückweg nutzbar.
            else -> resumeWakeWordListening()
        }
    }

    /**
     * Ja/Nein-Rückfrage, wenn `AudiobookManager.listBooks()` eine leere/sehr
     * kleine Bibliothek meldet – gleiches Muster wie
     * `openSimImportFollowUp()`/`handleSimImportFollowUp()`. Die eigentliche
     * Frage hat `listBooks()` schon gesprochen, hier wird nur zugehört.
     */
    private fun openLibrivoxSuggestionFollowUp() {
        val stt = sttEngine ?: return
        if (onboarding != null) return
        cancelWakeResume()
        WakeWordService.pauseListening(this)
        waitForSilenceThenRun(
            onReady = {
                statusText = "Hörbuch-Vorschlag – ich höre…"
                linaActivity = LinaActivity.Listening
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
                            handleLibrivoxSuggestionFollowUp(text)
                        }
                    }
                }, 350)
            },
            onTimeout = { resumeWakeWordListening() },
        )
    }

    private fun handleLibrivoxSuggestionFollowUp(text: String) {
        val t = text.lowercase()
        when {
            listOf("ja", "klar", "gerne", "mach", "bitte").any { t.contains(it) } ->
                openLibrivoxTopicFollowUp()
            listOf("nein", "nicht", "später", "spaeter").any { t.contains(it) } -> {
                ttsEngine?.speak("Alles klar.", TtsPriority.NORMAL)
                resumeWakeWordListening()
            }
            else -> resumeWakeWordListening()
        }
    }

    /** Fragt nach dem Thema, nachdem der Nutzer der LibriVox-Suche zugestimmt hat. */
    private fun openLibrivoxTopicFollowUp() {
        val stt = sttEngine ?: return
        if (onboarding != null) return
        ttsEngine?.speak("Zu welchem Thema?", TtsPriority.HIGH)
        waitForSilenceThenRun(
            onReady = {
                statusText = "Thema – ich höre…"
                linaActivity = LinaActivity.Listening
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
                    stt.startListening { topic ->
                        runOnUiThread {
                            if (handled) return@runOnUiThread
                            handled = true
                            mainHandler.removeCallbacks(timeout)
                            if (topic.isNotBlank()) {
                                audiobookManager?.searchByTopic(topic)
                            }
                            resumeWakeWordListening()
                        }
                    }
                }, 350)
            },
            onTimeout = { resumeWakeWordListening() },
        )
    }

    /** Für den manuellen Sprachbefehl – keine Rückfrage nötig, der Befehl ist bereits die Bestätigung. */
    private fun runSimImportNow() {
        cancelWakeResume()
        WakeWordService.pauseListening(this)
        runSimImport()
    }

    private fun runSimImport() {
        val manager = contactImportManager ?: return
        if (!hasContactWritePermission()) return
        statusText = "Kontakte werden übernommen…"
        linaActivity = LinaActivity.Thinking
        Earcons.thinking()
        Thread({
            val result = manager.importFromSim()
            runOnUiThread {
                ttsEngine?.speak(importSummary(result, "von der SIM-Karte"), TtsPriority.HIGH)
                resumeWakeWordListening()
            }
        }, "sim-import").start()
    }

    /** Öffnet Androids Dateipicker für eine vCard-Datei – die Auswahl selbst ist die Bestätigung. */
    private fun launchVcardPicker() {
        if (!hasContactWritePermission()) return
        cancelWakeResume()
        WakeWordService.pauseListening(this)
        ttsEngine?.speak("Bitte wähle die Kontaktdatei aus.", TtsPriority.HIGH)
        vcardPickerLauncher.launch(arrayOf("text/vcard", "text/x-vcard", "text/directory", "*/*"))
    }

    private fun handleVcardPicked(uri: Uri?) {
        if (uri == null) {
            resumeWakeWordListening()
            return
        }
        val manager = contactImportManager
        if (manager == null) {
            resumeWakeWordListening()
            return
        }
        statusText = "Kontaktdatei wird gelesen…"
        linaActivity = LinaActivity.Thinking
        Earcons.thinking()
        Thread({
            val text = try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            } catch (e: Exception) {
                android.util.Log.w("LinaLauncher", "Kontaktdatei nicht lesbar", e)
                null
            }
            runOnUiThread {
                if (text == null) {
                    ttsEngine?.speak("Ich konnte die Datei leider nicht lesen.", TtsPriority.HIGH)
                } else {
                    val result = contactImportManager?.importFromVcardText(text)
                    if (result != null) {
                        ttsEngine?.speak(importSummary(result, "aus der Datei"), TtsPriority.HIGH)
                    }
                }
                resumeWakeWordListening()
            }
        }, "vcard-import").start()
    }

    private fun hasContactWritePermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        ttsEngine?.speak(
            "Ich darf noch keine Kontakte speichern. Bitte lass deinen Betreuer die Kontakte-Berechtigung freigeben.",
            TtsPriority.HIGH,
        )
        PermissionsGuide.requestMissing(this)
        resumeWakeWordListening()
        return false
    }

    private fun importSummary(result: ImportResult, source: String): String {
        if (result.imported == 0 && result.duplicates == 0) {
            return "Ich habe keine Kontakte $source gefunden."
        }
        val teile = mutableListOf<String>()
        if (result.imported > 0) teile.add("${result.imported} neue Kontakte übernommen")
        if (result.duplicates > 0) teile.add("${result.duplicates} gab es schon")
        if (result.failed > 0) teile.add("${result.failed} konnten nicht gespeichert werden")
        return "Ich habe " + teile.joinToString(", ") + "."
    }

    /**
     * Ebene 2 des Intent-Systems: fragt Claude (blockierend, daher eigener Thread).
     * Say → vorlesen, Do → lokal ausführen, Error → Fehlermeldung vorlesen.
     */
    private fun askClaude(input: String, freshWakeWord: Boolean = false) {
        val conversation = claude ?: return
        statusText = "Lina denkt nach…"
        linaActivity = LinaActivity.Thinking
        Earcons.thinking()

        // Claude kann serverseitig mehrere Websuch-Runden fahren. Am
        // 2026-08-30 am Testtablet gemessen: 118 Sekunden zwischen Frage und
        // Antwort, in denen Lina kein Wort sagte. Für einen blinden Nutzer ist
        // das nicht von "Gerät ist tot" zu unterscheiden – und Leitprinzip 6
        // verlangt für jede Aktion akustische Rückmeldung. Deshalb: regelmäßig
        // vertrösten und nach einer harten Grenze aufgeben.
        var settled = false
        val reassure = object : Runnable {
            var round = 0
            override fun run() {
                if (settled) return
                round++
                ttsEngine?.speak(
                    if (round == 1) "Einen Moment, ich suche noch."
                    else "Ich bin noch dran.",
                    TtsPriority.LOW,
                )
                mainHandler.postDelayed(this, CLAUDE_REASSURE_REPEAT_MS)
            }
        }
        val giveUp = Runnable {
            if (settled) return@Runnable
            settled = true
            mainHandler.removeCallbacks(reassure)
            android.util.Log.w("LinaLauncher", "Claude-Antwort abgebrochen nach ${CLAUDE_HARD_TIMEOUT_MS}ms: \"$input\"")
            ttsEngine?.speak(
                "Das dauert mir zu lange. Frag mich das gern gleich noch einmal.",
                TtsPriority.HIGH,
            )
            statusText = "Lina bereit – sag \"$WAKE_WORD\""
            linaActivity = LinaActivity.Idle
            resumeWakeWordListening()
        }
        mainHandler.postDelayed(reassure, CLAUDE_REASSURE_AFTER_MS)
        mainHandler.postDelayed(giveUp, CLAUDE_HARD_TIMEOUT_MS)

        Thread {
            val reply = conversation.ask(input, freshWakeWord)
            runOnUiThread {
                // Nach dem Aufgeben darf die verspätete Antwort NICHT mehr
                // gesprochen werden – sonst redet Lina los, nachdem der Nutzer
                // die Sache längst abgehakt (oder "stopp" gesagt) hat.
                if (settled) {
                    android.util.Log.d("LinaLauncher", "Verspätete Claude-Antwort verworfen: \"$input\"")
                    return@runOnUiThread
                }
                settled = true
                mainHandler.removeCallbacks(reassure)
                mainHandler.removeCallbacks(giveUp)

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
                    linaActivity = LinaActivity.Idle
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
        WakeWordService.pauseListening(this)
        statusText = "Aufnahme läuft…"
        linaActivity = LinaActivity.Listening
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
                    linaActivity = LinaActivity.Idle
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
        linaActivity = LinaActivity.Loading
        piper.switchVoice(
            selector,
            onDone = { voice ->
                runOnUiThread {
                    statusText = "Stimme: $voice"
                    linaActivity = LinaActivity.Idle
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
                    linaActivity = LinaActivity.Error
                    mainHandler.postDelayed({ linaActivity = LinaActivity.Idle }, ERROR_DISPLAY_MS)
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
                when (val result = handler.startCall(intent.contactQuery)) {
                    is CallResult.Confirm -> {
                        openRiskyCallConfirm(result.contact, result.message)
                        "" // openRiskyCallConfirm spricht die Rückfrage selbst
                    }
                    else -> result.displayMessage
                }
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
            calendarVisible = false
            audiobookManager?.play()
            "Hörbuch wird gestartet…"
        }
        is ResolvedIntent.PauseAudiobook -> {
            audiobookManager?.pause()
            explicitAudiobookPause = true
            "Pausiert."
        }
        is ResolvedIntent.ResumeAudiobook -> {
            calendarVisible = false
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
            librivoxSuggestionOpened = audiobookManager?.listBooks() == true
            if (librivoxSuggestionOpened) openLibrivoxSuggestionFollowUp()
            "" // listBooks() spricht bereits alles Nötige selbst
        }
        is ResolvedIntent.AskAudiobookTopic -> {
            // Fragt "Zu welchem Thema?" und sucht dann bei LibriVox. Der Flow
            // existierte bereits, war aber nur über den Vorschlag bei kleiner
            // Bibliothek erreichbar – jetzt auch direkt per Sprachbefehl.
            openLibrivoxTopicFollowUp()
            "" // openLibrivoxTopicFollowUp() spricht selbst
        }
        is ResolvedIntent.SearchAudiobook -> {
            audiobookManager?.searchAndPlay(intent.query)
            "Suche nach ${intent.query}…"
        }
        is ResolvedIntent.SearchAudiobookByGenre -> {
            audiobookManager?.searchByTopic(intent.topic)
            "Suche zum Thema ${intent.topic}…"
        }
        is ResolvedIntent.SleepTimer -> {
            audiobookManager?.startSleepTimer(intent.minutes)
            "Schlaf-Timer: ${intent.minutes} Minuten."
        }
        is ResolvedIntent.VolumeUp -> {
            if (audiobookManager?.isPlaying == true) {
                audiobookManager?.increaseVolume()
            } else {
                adjustSystemVolume(raise = true)
            }
            "" // Lautstärke-Ansage macht die jeweilige Stelle selbst
        }
        is ResolvedIntent.VolumeDown -> {
            if (audiobookManager?.isPlaying == true) {
                audiobookManager?.decreaseVolume()
            } else {
                adjustSystemVolume(raise = false)
            }
            ""
        }
        is ResolvedIntent.SetVolume -> {
            if (audiobookManager?.isPlaying == true) {
                audiobookManager?.setVolume(intent.percent)
            } else {
                setSystemVolume(intent.percent)
            }
            ""
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
        is ResolvedIntent.CallHelper -> {
            val launcher = helperCallLauncher ?: HelperCallLauncher(this).also { helperCallLauncher = it }
            launcher.open().spokenMessage
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
        is ResolvedIntent.Date -> {
            val now = java.util.Calendar.getInstance()
            val wochentag = GermanCalendarNames.weekdayName(now)
            val monat = GermanCalendarNames.monthName(now)
            "Heute ist $wochentag, der ${now.get(java.util.Calendar.DAY_OF_MONTH)}. $monat."
        }
        is ResolvedIntent.SetCalendarEvent -> {
            calendarManager?.createFromSpeech(intent.rawInput)
            "" // CalendarManager sagt selbst an
        }
        is ResolvedIntent.SetCalendarEventAt -> {
            calendarManager?.create(intent.title, intent.isoDatum, intent.isoZeit, source = "manual")
            ""
        }
        is ResolvedIntent.ShowCalendar -> {
            calendarVisible = true
            calendarManager?.list()
            ""
        }
        is ResolvedIntent.HideCalendar -> {
            calendarVisible = false
            "Kalender ausgeblendet."
        }
        is ResolvedIntent.ClearCalendarEvents -> {
            calendarManager?.clearAll()
            ""
        }
        is ResolvedIntent.SleepMode -> {
            enterSleepMode()
            "Gute Nacht. Schlafmodus aktiviert."
        }
        is ResolvedIntent.SleepModeOff -> {
            exitSleepMode()
            "Schlafmodus beendet."
        }
        is ResolvedIntent.ImportSimContacts -> {
            runSimImportNow()
            ""
        }
        is ResolvedIntent.ImportVcardContacts -> {
            launchVcardPicker()
            ""
        }
        is ResolvedIntent.Stop -> {
            ttsEngine?.stop()
            newsReader?.stop()
            if (audiobookManager?.duckForListening() == true) {
                explicitAudiobookPause = true
            }
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
        is ResolvedIntent.AskAudiobookTopic -> "AskAudiobookTopic"
        is ResolvedIntent.SearchAudiobook -> "SearchAudiobook(${intent.query})"
        is ResolvedIntent.SearchAudiobookByGenre -> "SearchAudiobookByGenre(${intent.topic})"
        is ResolvedIntent.SleepTimer -> "SleepTimer(${intent.minutes}min)"
        is ResolvedIntent.VolumeUp -> "VolumeUp"
        is ResolvedIntent.VolumeDown -> "VolumeDown"
        is ResolvedIntent.SetVolume -> "SetVolume(${intent.percent}%)"
        is ResolvedIntent.NextChapter -> "NextChapter"
        is ResolvedIntent.PreviousChapter -> "PreviousChapter"
        is ResolvedIntent.GoToChapter -> "GoToChapter(${intent.number})"
        is ResolvedIntent.ListChapters -> "ListChapters"
        is ResolvedIntent.AcceptCall -> "AcceptCall"
        is ResolvedIntent.RejectCall -> "RejectCall"
        is ResolvedIntent.HangUp -> "HangUp"
        is ResolvedIntent.CallHelper -> "CallHelper"
        is ResolvedIntent.ReadDocument -> "ReadDocument"
        is ResolvedIntent.SetReminder -> "SetReminder"
        is ResolvedIntent.SetReminderAt -> "SetReminderAt(${intent.isoZeit})"
        is ResolvedIntent.ListReminders -> "ListReminders"
        is ResolvedIntent.ClearReminders -> "ClearReminders"
        is ResolvedIntent.SleepMode -> "SleepMode"
        is ResolvedIntent.SleepModeOff -> "SleepModeOff"
        is ResolvedIntent.ImportSimContacts -> "ImportSimContacts"
        is ResolvedIntent.ImportVcardContacts -> "ImportVcardContacts"
        is ResolvedIntent.Time -> "Time"
        is ResolvedIntent.Date -> "Date"
        is ResolvedIntent.SetCalendarEvent -> "SetCalendarEvent"
        is ResolvedIntent.SetCalendarEventAt -> "SetCalendarEventAt(${intent.isoDatum})"
        is ResolvedIntent.ShowCalendar -> "ShowCalendar"
        is ResolvedIntent.HideCalendar -> "HideCalendar"
        is ResolvedIntent.ClearCalendarEvents -> "ClearCalendarEvents"
        is ResolvedIntent.Stop -> "Stop"
        is ResolvedIntent.Unknown -> "Unknown"
    }

    override fun onDestroy() {
        try { unregisterReceiver(debugReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(accessibilityReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(wakeWordReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(reminderReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(simStateReceiver) } catch (_: Exception) {}
        sttEngine?.destroy()
        audiobookManager?.release()
        ttsEngine?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val WAKE_WORD = "Hey Lina"
        // Whisper ist nicht-streamend: bis zu 10s Aufnahme + Transkriptionszeit
        private const val STT_TIMEOUT_MS = 30_000L

        // Vertröstung und harte Grenze für Claude-Antworten. Die Werte sind an
        // der Messung vom 2026-08-30 orientiert: unauffällige Antworten kamen
        // in ~9s, die entgleiste Websuche brauchte 118s.
        private const val CLAUDE_REASSURE_AFTER_MS = 12_000L
        private const val CLAUDE_REASSURE_REPEAT_MS = 20_000L
        private const val CLAUDE_HARD_TIMEOUT_MS = 90_000L
        private const val DEBUG_FILE_RETENTION_DAYS = 7L
        // Transiente Fehleranzeige der Statuskugel – danach zurück zu Idle
        private const val ERROR_DISPLAY_MS = 4_000L
        // Fenster-Helligkeit im Schlafmodus (0f wäre komplett schwarz/unlesbar
        // für Angehörige, die kurz nachsehen – ein schwacher Rest bleibt sichtbar)
        private const val SLEEP_MODE_BRIGHTNESS = 0.04f
        private const val SLEEP_MODE_VOLUME_PERCENT = 30
        private const val PREFS = "lina"
        private const val PREF_ONBOARDING_DONE = "onboarding_done"
        private const val PREF_INTERESTS = "user_interests"
        private const val PREF_USER_NAME = "user_name"
        private const val PREF_REGION = "user_region"
    }
}
