package org.muilab.notigpt.ui.common.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale

/**
 * Small app icon shared by the home previews and the notification history.
 *
 * Mostly-transparent (monochrome) icons are drawn as a tinted [Icon] so they stay visible against
 * the surface; full-color launcher icons are drawn as-is via [Image].
 */
@Composable
fun AppIconImage(bitmap: Bitmap, modifier: Modifier = Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val isMonochrome = remember(bitmap) {
        if (!bitmap.hasAlpha()) false else {
            val w = minOf(bitmap.width, 16)
            val h = minOf(bitmap.height, 16)
            val scaled = if (bitmap.width > w || bitmap.height > h) bitmap.scale(w, h) else bitmap
            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
            pixels.count { ((it ushr 24) and 0xFF) < 250 } / pixels.size.toFloat() > 0.1f
        }
    }
    if (isMonochrome) {
        Icon(bitmap = image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
    } else {
        Image(bitmap = image, contentDescription = null, modifier = modifier)
    }
}
