package org.muilab.notigpt.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * App-wide "Screenshot mode" (dummy data) toggle.
 *
 * - Persisted in local SharedPreferences so it survives restarts.
 * - Exposed as a hot [StateFlow] so Compose can react immediately.
 */
object ScreenshotMode {

    private const val KEY_SCREENSHOT_MODE_ENABLED = "screenshotModeEnabled"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Call once (e.g. from [org.muilab.notigpt.MainActivity]) after [SharedPreferencesManager.init]. */
    fun init() {
        _enabled.value = SharedPreferencesManager.get("local", KEY_SCREENSHOT_MODE_ENABLED, false)
    }

    fun setEnabled(enabled: Boolean) {
        SharedPreferencesManager.put("local", KEY_SCREENSHOT_MODE_ENABLED, enabled)
        _enabled.value = enabled
    }

    fun toggle() {
        setEnabled(!_enabled.value)
    }
}
