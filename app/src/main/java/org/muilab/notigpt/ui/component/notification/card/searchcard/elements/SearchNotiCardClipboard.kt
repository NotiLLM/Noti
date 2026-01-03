package org.muilab.notigpt.ui.component.notification.card.searchcard.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.muilab.notigpt.platform.AndroidClipboardController
import org.muilab.notigpt.platform.ClipboardController

@Composable
internal fun rememberClipboardController(): ClipboardController {
    val localContext = LocalContext.current
    return remember(localContext) { AndroidClipboardController(localContext) }
}

