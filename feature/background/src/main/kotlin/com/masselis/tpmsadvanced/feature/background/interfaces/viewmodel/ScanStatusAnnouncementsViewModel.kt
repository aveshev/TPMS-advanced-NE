package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences

internal class ScanStatusAnnouncementsViewModel(appPreferences: AppPreferences) : ViewModel() {
    val announceScanStatus = appPreferences.announceScanStatus
}
