package org.muilab.notigpt.ui.settings

/**
 * A selectable target language for reminder extraction.
 *
 * [code] is the BCP-47 language tag sent verbatim to n8n (e.g. "en", "zh-TW"). The sentinel
 * "original" means "keep the notification's own language" and is handled specially by the UI.
 * [englishName] and [nativeName] are both matched by the settings search box so the user can find a
 * language by its English or local name.
 */
data class ExtractionLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
) {
    /** True when [query] (already lowercased) matches the code or either name. */
    fun matches(query: String): Boolean =
        query.isBlank() ||
            code.lowercase().contains(query) ||
            englishName.lowercase().contains(query) ||
            nativeName.lowercase().contains(query)
}

/** Sentinel value stored when the user wants extraction to keep the notification's own language. */
const val EXTRACTION_LANGUAGE_ORIGINAL = "original"

/**
 * Curated list of target languages offered in settings. Codes are BCP-47 tags acceptable to n8n.
 * Chinese is split into Traditional (zh-TW) and Simplified (zh-CN) because the script matters.
 */
val EXTRACTION_LANGUAGES: List<ExtractionLanguage> = listOf(
    ExtractionLanguage("en", "English", "English"),
    ExtractionLanguage("zh-TW", "Chinese (Traditional)", "繁體中文"),
    ExtractionLanguage("zh-CN", "Chinese (Simplified)", "简体中文"),
    ExtractionLanguage("ja", "Japanese", "日本語"),
    ExtractionLanguage("ko", "Korean", "한국어"),
    ExtractionLanguage("es", "Spanish", "Español"),
    ExtractionLanguage("fr", "French", "Français"),
    ExtractionLanguage("de", "German", "Deutsch"),
    ExtractionLanguage("it", "Italian", "Italiano"),
    ExtractionLanguage("pt", "Portuguese", "Português"),
    ExtractionLanguage("pt-BR", "Portuguese (Brazil)", "Português (Brasil)"),
    ExtractionLanguage("ru", "Russian", "Русский"),
    ExtractionLanguage("ar", "Arabic", "العربية"),
    ExtractionLanguage("hi", "Hindi", "हिन्दी"),
    ExtractionLanguage("bn", "Bengali", "বাংলা"),
    ExtractionLanguage("pa", "Punjabi", "ਪੰਜਾਬੀ"),
    ExtractionLanguage("id", "Indonesian", "Bahasa Indonesia"),
    ExtractionLanguage("ms", "Malay", "Bahasa Melayu"),
    ExtractionLanguage("th", "Thai", "ไทย"),
    ExtractionLanguage("vi", "Vietnamese", "Tiếng Việt"),
    ExtractionLanguage("tr", "Turkish", "Türkçe"),
    ExtractionLanguage("nl", "Dutch", "Nederlands"),
    ExtractionLanguage("pl", "Polish", "Polski"),
    ExtractionLanguage("uk", "Ukrainian", "Українська"),
    ExtractionLanguage("ro", "Romanian", "Română"),
    ExtractionLanguage("el", "Greek", "Ελληνικά"),
    ExtractionLanguage("cs", "Czech", "Čeština"),
    ExtractionLanguage("hu", "Hungarian", "Magyar"),
    ExtractionLanguage("sv", "Swedish", "Svenska"),
    ExtractionLanguage("da", "Danish", "Dansk"),
    ExtractionLanguage("fi", "Finnish", "Suomi"),
    ExtractionLanguage("no", "Norwegian", "Norsk"),
    ExtractionLanguage("he", "Hebrew", "עברית"),
    ExtractionLanguage("fa", "Persian", "فارسی"),
    ExtractionLanguage("ur", "Urdu", "اردو"),
    ExtractionLanguage("ta", "Tamil", "தமிழ்"),
    ExtractionLanguage("te", "Telugu", "తెలుగు"),
    ExtractionLanguage("mr", "Marathi", "मराठी"),
    ExtractionLanguage("gu", "Gujarati", "ગુજરાતી"),
    ExtractionLanguage("kn", "Kannada", "ಕನ್ನಡ"),
    ExtractionLanguage("ml", "Malayalam", "മലയാളം"),
    ExtractionLanguage("fil", "Filipino", "Filipino"),
    ExtractionLanguage("sw", "Swahili", "Kiswahili"),
    ExtractionLanguage("af", "Afrikaans", "Afrikaans"),
    ExtractionLanguage("bg", "Bulgarian", "Български"),
    ExtractionLanguage("hr", "Croatian", "Hrvatski"),
    ExtractionLanguage("sr", "Serbian", "Српски"),
    ExtractionLanguage("sk", "Slovak", "Slovenčina"),
    ExtractionLanguage("sl", "Slovenian", "Slovenščina"),
    ExtractionLanguage("lt", "Lithuanian", "Lietuvių"),
    ExtractionLanguage("lv", "Latvian", "Latviešu"),
    ExtractionLanguage("et", "Estonian", "Eesti"),
    ExtractionLanguage("ca", "Catalan", "Català"),
    ExtractionLanguage("is", "Icelandic", "Íslenska"),
    ExtractionLanguage("ga", "Irish", "Gaeilge"),
)
