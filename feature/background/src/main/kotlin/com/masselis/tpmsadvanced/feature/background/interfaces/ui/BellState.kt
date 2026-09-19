package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision

internal enum class BellState {
    /** Red, crossed out: notifications or the battery exemption are missing */
    NeedsPermission,

    /** Green */
    Active,

    /** Orange: an activate condition holds but a suspend condition overrides it */
    Suspended,

    /** Neutral: nothing to activate */
    Idle;

    companion object {
        fun of(permissionsOk: Boolean, decision: ScanDecision): BellState = when {
            permissionsOk.not() -> NeedsPermission
            decision is ScanDecision.Active -> Active
            decision is ScanDecision.Suspended -> Suspended
            else -> Idle
        }
    }
}
