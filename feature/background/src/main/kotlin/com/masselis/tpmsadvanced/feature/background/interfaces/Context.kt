package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.Context
import android.widget.Toast

// Toasts are owned by the system rather than by an Activity, so calling this with the application
// context is safe even right before (or after) the calling Activity finishes. Must run on the main
// thread.
internal fun Context.flash(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
