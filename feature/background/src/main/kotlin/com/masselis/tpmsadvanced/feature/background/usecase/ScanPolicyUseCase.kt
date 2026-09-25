package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ALWAYS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ANDROID_AUTO
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.MANUAL
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.STAY_ACTIVE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.WIRELESS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScanPolicyUseCase(
    private val appPreferences: AppPreferences,
    private val scanSuspensionUseCase: ScanSuspensionUseCase,
    private val chargingStateUseCase: ChargingStateUseCase,
    private val androidAutoUseCase: AndroidAutoUseCase,
    scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    /**
     * Each decision along with the mode (persistent scanning or not) it was made for. Shared, so
     * the service and the UI observe the same decision and a single set of system listeners is
     * registered.
     */
    private val decisionByMode: SharedFlow<Pair<Boolean, ScanDecision>> = appPreferences
        .persistentScanning
        .flatMapLatest { persistent ->
            if (persistent) automatic().map { persistent to it }
            // Without persistent scanning, monitoring is the manual start/stop button: scanning
            // for as long as the service runs
            else flowOf(persistent to ScanDecision.Active(setOf(MANUAL)))
        }
        // Several inputs can change without changing the outcome: nobody needs to hear it again
        .distinctUntilChanged()
        .shareIn(scope, WhileSubscribed(), replay = 1)

    /**
     * What background scanning should do right now. Right after the mode changed, the shared
     * replay still holds the previous mode's decision until the new mode's inputs are read: it is
     * never handed out, so that nobody scans (nor shows "Active") for a stale reason.
     */
    val decision: Flow<ScanDecision> = combine(
        appPreferences.persistentScanning,
        decisionByMode,
    ) { persistent, (mode, decision) -> decision.takeIf { mode == persistent } }
        .filterNotNull()
        .distinctUntilChanged()

    /** The latest [decision] if one is known for the current mode, without waiting for it */
    val currentDecision: ScanDecision?
        get() = decisionByMode
            .replayCache
            .lastOrNull()
            ?.takeIf { (mode) -> mode == appPreferences.persistentScanning.value }
            ?.second

    private fun automatic(): Flow<ScanDecision> = combine(
        enabledCauses(),
        appPreferences.stayActiveMinutes,
        ::Pair,
    ).flatMapLatest { (enabled, stayActiveMinutes) ->
        combine(
            fulfilledCauses(enabled, stayActiveMinutes.minutes),
            scanSuspensionUseCase.suspensionReasons,
        ) { fulfilled, reasons ->
            decide(enabled, fulfilled, reasons)
        }
    }

    private fun enabledCauses(): Flow<Set<ActivateCause>> = combine(
        appPreferences.activateConditions,
        appPreferences.activateOnCableCharging,
        appPreferences.activateOnWirelessCharging,
        appPreferences.activateOnAndroidAuto,
        appPreferences.stayActive,
    ) { conditions, cable, wireless, androidAuto, stayActive ->
        buildSet {
            // Nothing selected is the same as the conditions being off, which they are turned into
            // when leaving their page: the decision doesn't flicker through "idle" meanwhile.
            // "Stay active" only extends the others, it doesn't count.
            if (conditions.not() || listOf(cable, wireless, androidAuto).none { it }) add(ALWAYS)
            if (cable) add(CABLE)
            if (wireless) add(WIRELESS)
            if (androidAuto) add(ANDROID_AUTO)
            if (stayActive) add(STAY_ACTIVE)
        }
    }

    private fun fulfilledCauses(
        enabled: Set<ActivateCause>,
        stayActiveFor: Duration,
    ): Flow<Set<ActivateCause>> = when {
        // No need to listen to anything when always scanning overrides every other condition
        ALWAYS in enabled -> flowOf(emptySet())
        STAY_ACTIVE in enabled -> directCauses(enabled).stayingActive(enabled, stayActiveFor)

        else -> directCauses(enabled)
    }

    private fun directCauses(enabled: Set<ActivateCause>): Flow<Set<ActivateCause>> {
        val sources = buildList {
            if (CABLE in enabled || WIRELESS in enabled) {
                add(
                    chargingStateUseCase.state.map { charging ->
                        buildSet {
                            if (charging.cable) add(CABLE)
                            if (charging.wireless) add(WIRELESS)
                        }
                    }
                )
            }
            if (ANDROID_AUTO in enabled) {
                add(androidAutoUseCase.connected.map { if (it) setOf(ANDROID_AUTO) else emptySet() })
            }
        }
        return if (sources.isEmpty()) flowOf(emptySet())
        else combine(sources) { it.flatMap(Set<ActivateCause>::toList).toSet() }
    }

    /**
     * Once none of the [enabled] conditions is fulfilled any more, reports [STAY_ACTIVE] for
     * [duration], unless scanning was suspended at that very moment: then there is nothing to stay
     * active for. Starts over each time the conditions end, and nothing is held for conditions
     * that were never fulfilled. A suspension that begins or ends during the stay does not move
     * its end, it is a fixed point in time.
     */
    private fun Flow<Set<ActivateCause>>.stayingActive(
        enabled: Set<ActivateCause>,
        duration: Duration,
    ): Flow<Set<ActivateCause>> = flow {
        var wasFulfilled = false
        var stayEnd: TimeMark? = null
        emitAll(
            combine(this@stayingActive, scanSuspensionUseCase.suspensionReasons) { causes, reasons ->
                causes to reasons.isNotEmpty()
            }.transformLatest { (causes, suspended) ->
                if ((causes intersect enabled).isNotEmpty()) {
                    wasFulfilled = true
                    stayEnd = null
                    emit(causes)
                    return@transformLatest
                }
                if (wasFulfilled) {
                    wasFulfilled = false
                    stayEnd = if (suspended) null else timeSource.markNow() + duration
                }
                val remaining = stayEnd?.let { -it.elapsedNow() }?.takeIf { it.isPositive() }
                if (remaining == null) {
                    emit(causes)
                } else {
                    emit(causes + STAY_ACTIVE)
                    delay(remaining)
                    stayEnd = null
                    emit(causes)
                }
            }
        )
    }
}
