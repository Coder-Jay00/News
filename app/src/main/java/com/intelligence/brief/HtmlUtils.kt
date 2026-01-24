package com.intelligence.brief

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.util.regex.Pattern

object HtmlTextMapper {
    // Basic regex to strip HTML tags
    private val TAG_PATTERN = Pattern.compile("<[^>]*>")

    fun fromHtml(html: String): String {
        // Quick & dirty strip for the summary (since we just want text)
        return TAG_PATTERN.matcher(html).replaceAll("").trim()
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
