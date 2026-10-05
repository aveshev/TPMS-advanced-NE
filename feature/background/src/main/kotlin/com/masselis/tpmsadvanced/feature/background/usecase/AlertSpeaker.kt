package com.masselis.tpmsadvanced.feature.background.usecase

import android.media.AudioAttributes
import android.media.AudioAttributes.USAGE_ALARM
import android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
@OptIn(ExperimentalCoroutinesApi::class)
internal class AlertSpeaker(
    private val appPreferences: AppPreferences,
    isScanningTyres: Flow<Boolean>,
    scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val logger = Logger.withTag("AlertSpeaker")

    private data class Queue(
        val red: List<String> = emptyList(),
        /** The crimson phrases by the tag of their notification */
        val crimson: Map<String, Crimson> = emptyMap(),
        /** When the crimson phrases are said next, null to say them right away */
        val nextCrimson: TimeMark? = null,
    ) {
        val isEmpty get() = red.isEmpty() && crimson.isEmpty()
    }

    private data class Crimson(val phrase: String, val end: TimeMark)

    private val queue = MutableStateFlow(Queue())

    /** Says [phrase] twice, once */
    fun red(phrase: String) {
        if (appPreferences.spokenAlerts.value) queue.update { it.copy(red = it.red + phrase) }
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
            // The engine is only kept while there's something to say
            combine(appPreferences.spokenAlerts, queue) { enabled, queue -> enabled && queue.isEmpty.not() }
                .distinctUntilChanged()
                .flatMapLatest { if (it) textToSpeech() else emptyFlow() }
                .collectLatest { tts -> tts.speakQueue() }
        }
    }

    private suspend fun TextToSpeech.speakQueue() {
        while (true) {
            // The crimson alerts which ended are forgotten
            queue.update { queue -> queue.copy(crimson = queue.crimson.filterValues { it.end.hasNotPassedNow() }) }
            val current = queue.value
            when {
                current.red.isNotEmpty() -> current.red.first().let { phrase ->
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
                        say("$phrases. $phrases.", USAGE_ALARM)
                        queue.update { it.copy(nextCrimson = timeSource.markNow() + CRIMSON_PERIOD) }
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

    /** Speaks [text] with [usage], returning once it's said */
    private suspend fun TextToSpeech.say(text: String, usage: Int) = suspendCancellableCoroutine { continuation ->
        val id = UUID.randomUUID().toString()
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
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
    }
}
