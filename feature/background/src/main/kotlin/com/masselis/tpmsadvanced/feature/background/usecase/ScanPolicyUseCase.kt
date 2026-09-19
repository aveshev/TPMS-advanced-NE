package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ANDROID_AUTO
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.JUST_SCAN
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScanPolicyUseCase(
    private val appPreferences: AppPreferences,
    private val scanSuspensionUseCase: ScanSuspensionUseCase,
    private val chargingStateUseCase: ChargingStateUseCase,
    private val androidAutoUseCase: AndroidAutoUseCase,
    scope: CoroutineScope,
) {
    /**
     * What background scanning should do right now. Shared, so the service and the UI observe the
     * same decision and a single set of system listeners is registered.
     */
    val decision: SharedFlow<ScanDecision> = appPreferences
        .persistentScanning
        .flatMapLatest { persistent ->
            if (persistent) automatic()
            // Without persistent scanning, monitoring is the manual start/stop button: scanning
            // for as long as the service runs
            else flowOf(ScanDecision.Active(setOf(MANUAL)))
        }
        .shareIn(scope, WhileSubscribed(), replay = 1)

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
        appPreferences.justScan,
        appPreferences.activateOnCableCharging,
        appPreferences.activateOnWirelessCharging,
        appPreferences.activateOnAndroidAuto,
        appPreferences.stayActive,
    ) { justScan, cable, wireless, androidAuto, stayActive ->
        buildSet {
            if (justScan) add(JUST_SCAN)
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
        // No need to listen to anything when "Just scan" overrides every other condition
        JUST_SCAN in enabled -> flowOf(emptySet())
        STAY_ACTIVE in enabled -> directCauses(enabled).let { direct ->
            val anyFulfilled = direct.map { (it intersect enabled).isNotEmpty() }
            combine(direct, anyFulfilled.staysTrueFor(stayActiveFor)) { causes, staying ->
                if (staying && (causes intersect enabled).isEmpty()) causes + STAY_ACTIVE else causes
            }
        }

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
}

/**
 * Keeps saying true for [duration] after the source went from true to false, and starts over each
 * time it does. A source that begins false is not held: there is nothing to stay active after.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<Boolean>.staysTrueFor(duration: Duration): Flow<Boolean> = flow {
    var wasTrue = false
    emitAll(
        distinctUntilChanged().transformLatest { value ->
            when {
                value -> {
                    wasTrue = true
                    emit(true)
                }

                wasTrue -> {
                    emit(true)
                    delay(duration)
                    wasTrue = false
                    emit(false)
                }

                else -> emit(false)
            }
        }
    )
}.distinctUntilChanged()
