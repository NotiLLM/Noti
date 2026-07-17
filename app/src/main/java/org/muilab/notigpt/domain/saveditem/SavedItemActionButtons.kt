package org.muilab.notigpt.domain.saveditem

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale

data class SavedItemActionButton(
    val buttonText: String,
    val intent: String,
    val type: String,
)

/** Normalizes the small copy/link action contract shared by extraction, previews, and UI. */
object SavedItemActionButtons {
    const val TYPE_COPY = "copy"
    const val TYPE_LINK = "link"

    private val genericLinkLabels = setOf(
        "link", "open", "open link", "website", "url", "click here", "visit", "more",
        "連結", "链接", "開啟", "打开", "開啟連結", "打开链接", "網址", "网址",
    )
    private val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}$")
    private val longHexPattern = Regex("^[0-9a-fA-F]{8,}$")
    private val digitsPattern = Regex("^\\d+$")

    fun parse(raw: String): List<SavedItemActionButton> {
        val array = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyList()
        }
        val seen = linkedSetOf<String>()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val type = obj.optString("type", TYPE_LINK).trim().lowercase(Locale.ROOT)
                if (type != TYPE_COPY && type != TYPE_LINK) continue
                val intent = obj.optString("intent", "").trim()
                if (intent.isBlank()) continue
                val suppliedLabel = obj.optString("buttonText", "").trim()
                val label = when (type) {
                    TYPE_COPY -> suppliedLabel.takeIf(String::isNotBlank) ?: continue
                    else -> linkDisplayText(suppliedLabel, intent)
                }
                if (!seen.add("$type\u0000$intent")) continue
                add(SavedItemActionButton(label, intent, type))
            }
        }
    }

    fun mergeJson(vararg rawArrays: String): String {
        val seen = linkedSetOf<String>()
        val merged = rawArrays.flatMap(::parse).filter { seen.add("${it.type}\u0000${it.intent}") }
        return JSONArray().apply {
            merged.forEach { button ->
                put(
                    JSONObject().apply {
                        put("buttonText", button.buttonText)
                        put("intent", button.intent)
                        put("type", button.type)
                    }
                )
            }
        }.toString()
    }

    fun linkDisplayText(suppliedLabel: String, intent: String): String {
        val normalized = suppliedLabel.trim()
        if (normalized.isNotBlank() && normalized.lowercase(Locale.ROOT) !in genericLinkLabels) {
            return normalized
        }
        return fallbackLinkLabel(intent)
    }

    /**
     * Uses a readable path only when it looks semantic. Tokens, UUIDs, map short codes, and other
     * opaque paths deliberately fall back to the host so generated chips never expose junk labels.
     */
    fun fallbackLinkLabel(intent: String): String {
        val uri = parseUri(intent)
        val host = uri?.host?.removePrefix("www.")?.takeIf(String::isNotBlank)
        if (host != null) {
            val semanticSegments = uri.path.orEmpty()
                .split('/')
                .filter(String::isNotBlank)
                .takeWhile { segment -> isSemanticPathSegment(segment) }
                .take(2)
            return if (semanticSegments.isEmpty()) host else "$host/${semanticSegments.joinToString("/")}"
        }

        return intent.trim()
            .replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
            .substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
            .take(48)
            .ifBlank { "Link" }
    }

    private fun parseUri(intent: String): URI? {
        val trimmed = intent.trim()
        return try {
            val direct = URI(trimmed)
            if (direct.host != null) direct else URI("https://$trimmed")
        } catch (_: Exception) {
            null
        }
    }

    private fun isSemanticPathSegment(segment: String): Boolean {
        val value = segment.trim().trimEnd('/')
        if (value.length !in 2..32 || '%' in value) return false
        if (digitsPattern.matches(value) || uuidPattern.matches(value) || longHexPattern.matches(value)) return false

        val hasLetter = value.any(Char::isLetter)
        if (!hasLetter) return false
        val hasUpper = value.any(Char::isUpperCase)
        val hasLower = value.any(Char::isLowerCase)
        val hasDigit = value.any(Char::isDigit)
        val tokenLikeMixedCase = value.length >= 10 && hasUpper && hasLower && hasDigit && '-' !in value && '_' !in value
        val longUnbrokenToken = value.length >= 20 && value.all { it.isLetterOrDigit() }
        return !tokenLikeMixedCase && !longUnbrokenToken
    }
}
