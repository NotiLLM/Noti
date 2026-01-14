package org.muilab.notigpt.util

/**
 * Converts common escaped sequences (coming from JSON-ish sanitization) into user-friendly text.
 *
 * This is primarily for notification content that was sanitized for serialization/storage
 * (e.g., real newlines were converted to the two characters "\\n").
 */
fun unescapeUserText(input: String): String {
    if (input.isEmpty()) return input

    // Normalize Windows newlines first.
    var s = input.replace("\r\n", "\n")

    // Convert common escape sequences.
    // Order matters: unescape \\ first so that \\n becomes \n? Actually in our case sanitizeInput
    // turns \ into \\ and then \n remains \n, so we can safely replace \n afterwards.
    s = s
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\r", "\r")
        .replace("\\\"", "\"")

    // Finally collapse double-backslashes into a single backslash.
    s = s.replace("\\\\", "\\")

    return s
}

