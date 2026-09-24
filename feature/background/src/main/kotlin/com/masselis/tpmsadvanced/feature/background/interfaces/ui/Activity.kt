package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import com.masselis.tpmsadvanced.core.common.appContext

// No FLAG_ACTIVITY_NEW_TASK: we always launch from a live Activity, so Settings can push onto our
// own task's back stack. Adding it let a second RootActivity instance spawn when a flow bounced to
// Settings twice in a row.
internal fun Activity.openAppSettings() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    .apply { addCategory(Intent.CATEGORY_DEFAULT) }
    .apply { data = "package:${appContext.packageName}".toUri() }
    .apply { addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY) }
    .apply { addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) }
    .also { startActivity(it) }
