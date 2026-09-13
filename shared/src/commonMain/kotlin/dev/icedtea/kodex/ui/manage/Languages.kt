package dev.icedtea.kodex.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Display name for a source language code (BCP-47 as the Mihon/LNReader indexes use them) — the
 * codes that appear in Keiyoushi's index plus the LNReader set; anything else shows as its code.
 * Kotlin common has no `Locale.getDisplayLanguage`, hence the table.
 */
fun languageLabel(code: String): String = when (code) {
    "all" -> "Multi-language"
    else -> LANGUAGE_NAMES[code] ?: LANGUAGE_NAMES[code.substringBefore('-')]?.let { "$it (${code.substringAfter('-').uppercase()})" } ?: code.uppercase()
}

private val LANGUAGE_NAMES = mapOf(
    "en" to "English", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese", "zh-Hans" to "Chinese (Simplified)",
    "zh-Hant" to "Chinese (Traditional)", "vi" to "Vietnamese", "th" to "Thai", "id" to "Indonesian", "ms" to "Malay",
    "fil" to "Filipino", "tl" to "Tagalog", "my" to "Burmese", "km" to "Khmer", "lo" to "Lao", "hi" to "Hindi", "bn" to "Bengali",
    "ta" to "Tamil", "te" to "Telugu", "ml" to "Malayalam", "ur" to "Urdu", "ne" to "Nepali", "si" to "Sinhala",
    "ar" to "Arabic", "fa" to "Persian", "he" to "Hebrew", "tr" to "Turkish", "ku" to "Kurdish", "az" to "Azerbaijani",
    "ka" to "Georgian", "hy" to "Armenian", "kk" to "Kazakh", "uz" to "Uzbek", "mn" to "Mongolian",
    "ru" to "Russian", "uk" to "Ukrainian", "be" to "Belarusian", "pl" to "Polish", "cs" to "Czech", "sk" to "Slovak",
    "hu" to "Hungarian", "ro" to "Romanian", "bg" to "Bulgarian", "sr" to "Serbian", "hr" to "Croatian", "bs" to "Bosnian",
    "sl" to "Slovenian", "mk" to "Macedonian", "sq" to "Albanian", "el" to "Greek", "lt" to "Lithuanian", "lv" to "Latvian",
    "et" to "Estonian", "fi" to "Finnish", "sv" to "Swedish", "no" to "Norwegian", "nb" to "Norwegian Bokmål", "da" to "Danish",
    "is" to "Icelandic", "de" to "German", "nl" to "Dutch", "fr" to "French", "it" to "Italian", "es" to "Spanish",
    "es-419" to "Spanish (Latin America)", "pt" to "Portuguese", "pt-BR" to "Portuguese (Brazil)", "ca" to "Catalan",
    "eu" to "Basque", "gl" to "Galician", "eo" to "Esperanto", "la" to "Latin", "ga" to "Irish", "cy" to "Welsh",
    "af" to "Afrikaans", "sw" to "Swahili", "am" to "Amharic", "ha" to "Hausa", "yo" to "Yoruba", "zu" to "Zulu",
    "jv" to "Javanese", "su" to "Sundanese", "ceb" to "Cebuano", "other" to "Other",
)

/**
 * A section band in the extension/plugin lists: the language (or "Installed"), how many rows it holds,
 * and a chevron — tapping it collapses or expands the section.
 */
@Composable
fun LanguageSectionHeader(label: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            "${label.uppercase()} · $count",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
