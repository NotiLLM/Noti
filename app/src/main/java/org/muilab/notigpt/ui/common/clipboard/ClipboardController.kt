package org.muilab.notigpt.ui.common.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Platform boundary for copying text to the Android clipboard.
 *
 * UI components depend on this interface so clipboard behavior can be tested or replaced without importing
 * Android clipboard APIs throughout Compose code.
 */
interface ClipboardController {
    fun copyPlainText(label: String, text: String)
}

class AndroidClipboardController(private val context: Context) : ClipboardController {
    override fun copyPlainText(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }
}

