package org.muilab.notigpt.ui.notification.state

import androidx.compose.ui.Modifier

/**
 * Small shared Compose UI state types.
 *
 * Keep only cross-component UI primitives here. Feature-specific state belongs next to the screen or component
 * that owns the behavior.
 */
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