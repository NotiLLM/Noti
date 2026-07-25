package org.muilab.notigpt.ui.preference.model

/** Existing item actions that can invite explicit personalization feedback. */
enum class PreferenceEntryPoint(val wire: String) {
    EDIT("EDIT"),
    DELETE("DELETE"),
    MANUAL_EXTRACT("MANUAL_EXTRACT"),
    MERGE("MERGE"),
    SPLIT("SPLIT"),
    REGENERATE("REGENERATE"),
}
