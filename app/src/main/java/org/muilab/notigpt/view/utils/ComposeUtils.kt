package org.muilab.notigpt.view.utils

import androidx.compose.ui.Modifier

enum class NotiExpandState {
    Collapsed,
    Opened,
}

enum class NotiSwipeState {
    Left,
    Center,
    Right
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