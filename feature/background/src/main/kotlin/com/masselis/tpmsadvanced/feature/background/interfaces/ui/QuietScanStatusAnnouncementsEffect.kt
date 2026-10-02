package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleStartEffect
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import kotlinx.coroutines.flow.update

/**
 * Keeps the status announcements of the debug settings quiet while the caller's lifecycle is
 * started: put on the main screen, where the bell shows the status. Within a navigation graph, the
 * lifecycle is the destination's, which stops when another screen is opened on top of it.
 */
@Composable
public fun QuietScanStatusAnnouncementsEffect() {
    val announcer = remember { Bindings.featureBackgroundInternal.scanStatusAnnouncer() }
    LifecycleStartEffect(announcer) {
        announcer.mainScreensStarted.update { it + 1 }
        onStopOrDispose { announcer.mainScreensStarted.update { it - 1 } }
    }
}
