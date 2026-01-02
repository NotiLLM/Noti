package org.muilab.notigpt.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Simple wrapper around system clipboard (helps keep UI code tidy and testable). */
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

