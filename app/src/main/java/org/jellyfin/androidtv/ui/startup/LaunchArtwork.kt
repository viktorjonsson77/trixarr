package org.jellyfin.androidtv.ui.startup

import android.content.Context
import android.provider.Settings

fun launchMotionEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
