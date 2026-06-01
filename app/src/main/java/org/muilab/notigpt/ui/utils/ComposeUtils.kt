package org.muilab.notigpt.ui.utils

import androidx.compose.ui.Modifier

enum class NotiExpandState {
    Collapsed,
    Opened,
}

inline fun Modifier.conditional(
    condition: Boolean,
    ifTrue: Modifier.() -> Modifier,
    ifFalse: Modifier.() -> Modifier = { this },
): Modifier = if (condition) {
    then(ifTrue(Modifier))
} else {
    then(ifFalse(Modifier))
}