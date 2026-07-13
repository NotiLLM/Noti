package org.muilab.notigpt.domain.reminder

/**
 * Pure rollback logic for rejecting a pending LLM update, kept free of Android/Room dependencies so
 * the delicate string surgery and field guards can be unit-tested in isolation. The repository owns
 * the DB reads/writes and calls into here.
 */
object SavedItemRevertLogic {

    /**
     * Removes the dated section a rejected update appended to [content].
     *
     * [exactSection] is what the repository's `buildUpdateSection` reproduces for this fragment and
     * timestamp; stripping it is preferred. It can fail to match when the language preference or
     * timezone changed since the append, so we fall back to locating [fragment] and stripping back to
     * its `"\n\n["` header. Returns null when the fragment is gone entirely (the user edited it away),
     * signalling the caller to leave content untouched.
     */
    fun stripUpdateSection(content: String, fragment: String, exactSection: String?): String? {
        if (fragment.isBlank()) return content
        if (exactSection != null) {
            val idx = content.lastIndexOf(exactSection)
            if (idx >= 0) return content.removeRange(idx, idx + exactSection.length)
        }
        val fragIdx = content.lastIndexOf(fragment)
        if (fragIdx < 0) return null
        val headerIdx = content.lastIndexOf("\n\n[", fragIdx)
        val start = if (headerIdx in 0..fragIdx) headerIdx else fragIdx
        return content.removeRange(start, fragIdx + fragment.length)
    }

    /**
     * Restores [old] only when [current] still equals what the LLM set ([newValue]). If a later user
     * edit (or a hallucinated `old` echo) left a different current value, it is preserved untouched.
     */
    fun <T> revertField(current: T, newValue: T, old: T): T = if (current == newValue) old else current
}
