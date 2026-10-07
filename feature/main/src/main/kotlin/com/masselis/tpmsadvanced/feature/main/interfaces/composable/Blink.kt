package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A phase of a red alert's blinking, shown or hidden */
internal val BLINK = 400.milliseconds

/** A phase of a crimson alert's blinking */
internal val CRITICAL_BLINK = 200.milliseconds

/** A phase of a crimson reading's alternation with "CRITICAL" */
internal val CRITICAL_LABEL_BLINK = 800.milliseconds

// Every blink phase is a multiple of it
private val TICK = 200.milliseconds

// The count of ticks since the epoch, shared by every blinking element of every screen so they all
// switch together, in the same frame. Phases start on multiples of their length since the epoch, so
// a faster phase always switches along with the slower ones.
// Nothing is replayed once no screen blinks, so a screen opened later starts from the current tick.
private val ticks: Flow<Long> = flow {
    while (true) {
        emit(currentTick())
        delay(TICK.inWholeMilliseconds - System.currentTimeMillis() % TICK.inWholeMilliseconds)
    }
}.shareIn(CoroutineScope(Dispatchers.Main.immediate), WhileSubscribed(replayExpirationMillis = 0), replay = 1)

private fun currentTick() = System.currentTimeMillis() / TICK.inWholeMilliseconds

/**
 * Whether a blinking element is in the first of its two alternating [phase]s, in sync with all the
 * other blinking elements. Always the first one under [LocalInspectionMode], as in previews and
 * the screenshot tests providing it, which would otherwise depend on the time they're drawn at.
 */
@Composable
internal fun isFirstBlinkPhase(phase: Duration): Boolean {
    require(phase.inWholeMilliseconds % TICK.inWholeMilliseconds == 0L) { "$phase isn't a multiple of $TICK" }
    if (LocalInspectionMode.current)
        return true
    val tick by ticks.collectAsState(currentTick())
    val isFirst by remember(phase) { derivedStateOf { tick / (phase / TICK).toLong() % 2 == 0L } }
    return isFirst
}
