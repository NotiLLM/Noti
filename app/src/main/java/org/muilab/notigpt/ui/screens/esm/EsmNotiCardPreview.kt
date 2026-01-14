package org.muilab.notigpt.ui.screens.esm

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import org.muilab.notigpt.model.notifications.NotiDisplayUnit

/**
 * Backwards-compatible wrapper for older call sites.
 *
 * Prefer [EsmNotiCardLikePreview] which matches the real NotiCard layout.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun EsmNotiCardPreview(noti: NotiDisplayUnit) {
    EsmNotiCardLikePreview(notiDisplayUnit = noti)
}
