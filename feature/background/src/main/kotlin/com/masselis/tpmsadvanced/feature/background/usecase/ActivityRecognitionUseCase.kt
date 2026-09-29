package com.masselis.tpmsadvanced.feature.background.usecase

import android.Manifest.permission.ACTIVITY_RECOGNITION
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.S
import androidx.core.content.ContextCompat.checkSelfPermission
import co.touchlab.kermit.Logger
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.asFlow
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

/** What the phone is doing according to Play Services: in a vehicle, walking, still... */
internal class ActivityRecognitionUseCase(scope: CoroutineScope) {

    private val logger = Logger.withTag("ActivityRecognitionUseCase")

    /** Below API 29 the permission is granted at install time */
    fun requiredPermissions(): List<String> = when {
        SDK_INT >= Q -> listOf(ACTIVITY_RECOGNITION)
        else -> emptyList()
    }

    fun isPermitted(): Boolean = requiredPermissions()
        .all { checkSelfPermission(appContext, it) == PERMISSION_GRANTED }

    /**
     * Every activity Play Services thinks the phone may be doing, the most likely first. It only
     * listens while collected, because the updates cost battery. Nothing is emitted when the
     * permission is missing or Play Services cannot be reached.
     *
     * Shared: the updates are registered with one PendingIntent, so a second collector of its own
     * would remove the registration of the first one when it stops.
     */
    // Only collected once isPermitted(), and a SecurityException from a revoked permission is
    // caught by the runCatching below: lint cannot see either
    @SuppressLint("MissingPermission")
    val probableActivities: SharedFlow<List<Activity>> = callbackFlow {
        val action = "${appContext.packageName}.ACTIVITY_RECOGNITION_UPDATE"
        // The system fills the intent in when it delivers, so the PendingIntent has to be mutable
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(action).setPackage(appContext.packageName),
            FLAG_UPDATE_CURRENT or if (SDK_INT >= S) FLAG_MUTABLE else 0,
        )
        val receiving = IntentFilter(action)
            .asFlow()
            .filter { ActivityRecognitionResult.hasResult(it) }
            .onEach { intent ->
                ActivityRecognitionResult.extractResult(intent)!!
                    .probableActivities
                    .map { Activity(it.type.asType(), it.confidence) }
                    .sortedByDescending { it.confidence }
                    .let { trySend(it) }
            }
            .launchIn(this)
        val client = ActivityRecognition.getClient(appContext)
        runCatching { client.requestActivityUpdates(UPDATE_INTERVAL_MS, pendingIntent) }
            .onFailure { logger.w(it) { "Cannot request the activity updates" } }
            .getOrNull()
            ?.addOnFailureListener { logger.w(it) { "The activity updates were refused" } }
        awaitClose {
            receiving.cancel()
            runCatching { client.removeActivityUpdates(pendingIntent) }
            pendingIntent.cancel()
        }
    }
        // A sample that is older than the last time somebody listened says nothing about now
        .shareIn(scope, WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    /** [confidence] in percent */
    data class Activity(val type: Type, val confidence: Int) {
        enum class Type { IN_VEHICLE, ON_BICYCLE, ON_FOOT, WALKING, RUNNING, STILL, TILTING, UNKNOWN }
    }

    private fun Int.asType() = when (this) {
        DetectedActivity.IN_VEHICLE -> Type.IN_VEHICLE
        DetectedActivity.ON_BICYCLE -> Type.ON_BICYCLE
        DetectedActivity.ON_FOOT -> Type.ON_FOOT
        DetectedActivity.WALKING -> Type.WALKING
        DetectedActivity.RUNNING -> Type.RUNNING
        DetectedActivity.STILL -> Type.STILL
        DetectedActivity.TILTING -> Type.TILTING
        else -> Type.UNKNOWN
    }

    private companion object {
        // Shorter than what a production use would pick: this is a debugging aid, only listened to
        // while the home screen is visible
        const val UPDATE_INTERVAL_MS = 10_000L
    }
}
