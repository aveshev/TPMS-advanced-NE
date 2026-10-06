package com.masselis.tpmsadvanced.feature.background.usecase

import android.media.AudioAttributes
import android.media.AudioAttributes.USAGE_ALARM
import android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
import android.media.AudioAttributes.USAGE_NOTIFICATION
import android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Speaks the alerts, see docs/alerts.md: a red one twice, each time its notification sounds, the
 * crimson ones twice every [CRIMSON_PERIOD] for [CRIMSON_DURATION] after their latest reading.
 * Everything goes through a single queue, nothing talks over anything. Crimson alerts stop with the
 * tyre scans: no reading could clear them any more.
 */
internal class AlertSpeaker(
    private val appPreferences: AppPreferences,
    isScanningTyres: Flow<Boolean>,
    scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val logger = Logger.withTag("AlertSpeaker")
    private val audioManager by lazy { appContext.getSystemService<AudioManager>()!! }

    private data class Queue(
        val red: List<String> = emptyList(),
        /** The crimson phrases by the tag of their notification */
        val crimson: Map<String, Crimson> = emptyMap(),
        /** When the crimson phrases are said next, null to say them right away */
        val nextCrimson: TimeMark? = null,
        /** When the latest alert sounded, its notification's sound is let out first */
        val sounded: TimeMark? = null,
    ) {
        val isEmpty get() = red.isEmpty() && crimson.isEmpty()
    }

    private data class Crimson(val phrase: String, val end: TimeMark)

    private val queue = MutableStateFlow(Queue())

    /** Says [phrase] twice, once */
    fun red(phrase: String) {
        if (appPreferences.spokenAlerts.value) queue.update {
            it.copy(red = it.red + phrase, sounded = timeSource.markNow())
        }
    }

    /**
     * Says [phrase] twice every [CRIMSON_PERIOD] for [CRIMSON_DURATION], starting over that duration
     * if [tag] is already being said. A new one is said right away.
     */
    fun crimson(tag: String, phrase: String) {
        if (appPreferences.spokenAlerts.value) queue.update { queue ->
            queue.copy(
                crimson = queue.crimson + (tag to Crimson(phrase, timeSource.markNow() + CRIMSON_DURATION)),
                nextCrimson = queue.nextCrimson.takeIf { tag in queue.crimson },
                sounded = timeSource.markNow(),
            )
        }
    }

    /** Stops saying the crimson alert of [tag] */
    fun stop(tag: String) {
        queue.update { it.copy(crimson = it.crimson - tag) }
    }

    init {
        isScanningTyres
            .filter { it.not() }
            .onEach { queue.update { it.copy(crimson = emptyMap()) } }
            .launchIn(scope)

        appPreferences
            .spokenAlerts
            .filter { it.not() }
            .onEach { queue.value = Queue() }
            .launchIn(scope)

        scope.launch {
            // The engine is only kept while there's something to say, the queue being spoken for as
            // long as it lives
            combine(appPreferences.spokenAlerts, queue) { enabled, queue -> enabled && queue.isEmpty.not() }
                .distinctUntilChanged()
                .collectLatest { isBusy -> if (isBusy) textToSpeech().collect { tts -> tts.speakQueue() } }
        }
    }

    private suspend fun TextToSpeech.speakQueue() {
        while (true) {
            // The crimson alerts which ended are forgotten
            queue.update { queue -> queue.copy(crimson = queue.crimson.filterValues { it.end.hasNotPassedNow() }) }
            val current = queue.value
            when {
                current.red.isNotEmpty() -> current.red.first().let { phrase ->
                    awaitNotificationSound(current.sounded)
                    say("$phrase. $phrase.", USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    queue.update { it.copy(red = it.red.drop(1)) }
                }

                current.crimson.isNotEmpty() && current.nextCrimson?.hasPassedNow() != false -> current
                    .crimson
                    .values
                    .map(Crimson::phrase)
                    .distinct()
                    .joinToString(", ")
                    .let { phrases ->
                        awaitNotificationSound(current.sounded)
                        // Every period from the start of the phrases, however long they take
                        queue.update { it.copy(nextCrimson = timeSource.markNow() + CRIMSON_PERIOD) }
                        say("$phrases. $phrases.", USAGE_ALARM)
                    }

                // Until anything changes, the next crimson phrase or the end of one
                else -> withTimeoutOrNull(
                    current
                        .crimson
                        .values
                        .map(Crimson::end)
                        .plus(listOfNotNull(current.nextCrimson.takeIf { current.crimson.isNotEmpty() }))
                        .minOfOrNull { it.elapsedNow().unaryMinus() }
                        ?: Duration.INFINITE
                ) { queue.first { it != current } }
            }
        }
    }

    /**
     * Lets a notification's sound play out rather than speaking over it: the one of the alert which
     * [sounded], Android playing it about half a second after it's posted, and any other playing.
     * Doesn't wait for a silent or vibrating notification.
     */
    private suspend fun awaitNotificationSound(sounded: TimeMark?) {
        sounded
            ?.let { -(it + SOUND_START).elapsedNow() }
            ?.takeIf { it.isPositive() }
            ?.let { withTimeoutOrNull(it) { isNotificationSounding().first { sounding -> sounding } } }
        withTimeoutOrNull(MAX_SOUND) { isNotificationSounding().first { it.not() } }
    }

    /** Whether a notification's sound is playing, from any app */
    private fun isNotificationSounding(): Flow<Boolean> = callbackFlow {
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                trySend(configs.any { it.audioAttributes.usage in NOTIFICATION_USAGES })
            }
        }
        audioManager.registerAudioPlaybackCallback(callback, null)
        send(audioManager.activePlaybackConfigurations.any { it.audioAttributes.usage in NOTIFICATION_USAGES })
        awaitClose { audioManager.unregisterAudioPlaybackCallback(callback) }
    }.distinctUntilChanged()

    /**
     * Speaks [text] with [usage], returning once it's said. The audio focus ducks what's playing
     * meanwhile, as navigation prompts do: the engine doesn't ask for it by itself.
     */
    private suspend fun TextToSpeech.say(text: String, usage: Int) = AudioAttributes
        .Builder()
        .setUsage(usage)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
        .let { attributes ->
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
                .let { focus ->
                    audioManager.requestAudioFocus(focus)
                    try {
                        say(text, attributes)
                    } finally {
                        audioManager.abandonAudioFocusRequest(focus)
                    }
                }
        }

    private suspend fun TextToSpeech.say(text: String, attributes: AudioAttributes) =
        suspendCancellableCoroutine { continuation ->
            val id = UUID.randomUUID().toString()
            setAudioAttributes(attributes)
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(Unit)
                }
            })
            continuation.invokeOnCancellation { stop() }
            if (speak(text, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.ERROR && continuation.isActive)
                continuation.resume(Unit)
        }

    /** The default engine once it is ready, shut down when the collection ends */
    private fun textToSpeech(): Flow<TextToSpeech> = callbackFlow {
        val status = CompletableDeferred<Int>()
        val tts = TextToSpeech(appContext) { status.complete(it) }
        launch {
            if (status.await() == TextToSpeech.SUCCESS) send(tts)
            else logger.w { "Text-to-speech failed to initialize, alerts won't be spoken" }
        }
        awaitClose { tts.shutdown() }
    }

    private companion object {
        val CRIMSON_PERIOD = 20.seconds
        val CRIMSON_DURATION = 10.minutes

        /** How long a notification's sound can take to start after it's posted */
        val SOUND_START = 1.5.seconds

        /** Speaks anyway after that, a notification's sound being stuck or very long */
        val MAX_SOUND = 10.seconds

        val NOTIFICATION_USAGES = setOf(USAGE_NOTIFICATION, USAGE_NOTIFICATION_EVENT)
    }
}
