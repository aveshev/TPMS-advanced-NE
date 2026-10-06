package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Dismisses an alert for a while, from its notification's buttons or from swiping it away, see
 * [com.masselis.tpmsadvanced.feature.background.usecase.AlertSnoozeUseCase]
 */
internal class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = requireNotNull(intent.getStringExtra(EXTRA_TAG))
        Bindings
            .featureBackgroundInternal
            .alertSnoozeUseCase
            .snooze(
                intent.getIntExtra(EXTRA_SENSOR_ID, 0),
                AlertClass.valueOf(requireNotNull(intent.getStringExtra(EXTRA_CLASS))),
                AlertLevel.valueOf(requireNotNull(intent.getStringExtra(EXTRA_LEVEL))),
                intent.getLongExtra(EXTRA_DURATION, 0L).milliseconds,
            )
        Bindings.featureBackgroundInternal.alertSpeaker.stop(tag, isDismissed = true)
        // Already gone when swiped away
        NotificationManagerCompat.from(context).cancel(tag, AlertNotifier.NOTIFICATION_ID)
    }

    internal companion object {
        private const val EXTRA_TAG = "TAG"
        private const val EXTRA_SENSOR_ID = "SENSOR_ID"
        private const val EXTRA_CLASS = "CLASS"
        private const val EXTRA_LEVEL = "LEVEL"
        private const val EXTRA_DURATION = "DURATION"

        fun intent(tag: String, sensorId: Int, alertClass: AlertClass, level: AlertLevel, duration: Duration) =
            Intent(appContext, AlertActionReceiver::class.java)
                // Tells the pending intents of each notification and duration apart
                .setData("tpmsadvanced-alert:$tag/${level.name}/${duration.inWholeMilliseconds}".toUri())
                .putExtra(EXTRA_TAG, tag)
                .putExtra(EXTRA_SENSOR_ID, sensorId)
                .putExtra(EXTRA_CLASS, alertClass.name)
                .putExtra(EXTRA_LEVEL, level.name)
                .putExtra(EXTRA_DURATION, duration.inWholeMilliseconds)
    }
}
