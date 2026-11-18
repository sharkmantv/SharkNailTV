package com.example.xtreamtv

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import kotlin.math.roundToInt

// Adapted from Adaptive Hz RefreshRateController.resolveDisplayMinMax (MIT).
// No guessed fallback modes or privileged system settings writes.
internal fun adaptiveHzRates(context: Context): List<Int> = runCatching {
    val display = context.applicationContext.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    display?.supportedModes?.map { it.refreshRate.roundToInt() }?.filter { it > 0 }?.distinct()?.sorted().orEmpty()
}.getOrDefault(emptyList())
