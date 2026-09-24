package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.JUST_SCAN
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason

internal sealed interface ScanDecision {

    enum class ActivateCause { MANUAL, CABLE, WIRELESS, ANDROID_AUTO, JUST_SCAN }

    /** Background scanning is running, for at least one of [causes]. */
    data class Active(val causes: Set<ActivateCause>) : ScanDecision

    /** An activate condition holds but a suspend condition overrides it. */
    data class Suspended(val reasons: Set<Reason>) : ScanDecision

    /** No enabled activate condition is fulfilled, there is nothing to scan for. */
    data object Idle : ScanDecision
}

/**
 * Persistent scanning is active if ANY enabled activate condition is fulfilled and NONE of the
 * suspend conditions is, "Just scan" being an activate condition that is always fulfilled and
 * makes every other one irrelevant.
 */
internal fun decide(
    enabled: Set<ScanDecision.ActivateCause>,
    fulfilled: Set<ScanDecision.ActivateCause>,
    suspendReasons: Set<Reason>,
): ScanDecision {
    val causes = if (JUST_SCAN in enabled) setOf(JUST_SCAN) else enabled intersect fulfilled
    return when {
        causes.isEmpty() -> ScanDecision.Idle
        suspendReasons.isNotEmpty() -> ScanDecision.Suspended(suspendReasons)
        else -> ScanDecision.Active(causes)
    }
}

internal fun ScanDecision.explanation(): String = when (this) {
    is ScanDecision.Active ->
        "Background scanning is active due to ${causes.joinToString(", ") { it.label }}"

    is ScanDecision.Suspended ->
        "Background scanning is suspended due to ${
            reasons.joinToString(", ") {
                when (it) {
                    Reason.DOZE -> "the phone being idle (Doze)"
                    Reason.WIFI -> "WiFi being connected"
                }
            }
        }"

    ScanDecision.Idle -> "Background scanning is idle: no activate condition is currently fulfilled"
}

/** What the user reads when tapping the bell. */
internal fun ScanDecision.rationale(): String = when (this) {
    is ScanDecision.Active -> explanation()
    // The foreground UI scans by itself, whatever the decision is
    is ScanDecision.Suspended, ScanDecision.Idle ->
        "${explanation()}. (Scanning is still active while the app is opened!)"
}

private val ScanDecision.ActivateCause.label
    get() = when (this) {
        ScanDecision.ActivateCause.MANUAL -> "monitoring being started manually"
        ScanDecision.ActivateCause.CABLE -> "charging with a cable"
        ScanDecision.ActivateCause.WIRELESS -> "charging wirelessly"
        ScanDecision.ActivateCause.ANDROID_AUTO -> "Android Auto being connected"
        JUST_SCAN -> "Just scan being enabled"
    }
