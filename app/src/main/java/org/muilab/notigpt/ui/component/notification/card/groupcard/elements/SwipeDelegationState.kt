package org.muilab.notigpt.ui.component.notification.card.groupcard.elements

/**
 * Shared state enum for nested swipe delegation in grouped notification cards.
 *
 * This coordinates which card currently owns a horizontal gesture. Keep it ephemeral and UI-scoped;
 * persisted drawer state should be updated only after actions are committed.
 */
internal enum class SwipeDelegationState {
    Group,
    Child,
}

