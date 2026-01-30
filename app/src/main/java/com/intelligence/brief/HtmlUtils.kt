package com.intelligence.brief

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import java.util.regex.Pattern

object HtmlTextMapper {
    fun fromHtml(html: String): AnnotatedString {
        return buildAnnotatedString {
            var currentIndex = 0
            val boldPattern = Pattern.compile("<b>(.*?)</b>", Pattern.DOTALL)
            val matcher = boldPattern.matcher(html)

            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                
                // Append text before the tag
                if (start > currentIndex) {
                    append(html.substring(currentIndex, start).replace("<br>", "\n").replace("&nbsp;", " ").replace("&amp;", "&"))
                }

                // Append the bold content
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(matcher.group(1)?.replace("<br>", "\n")?.replace("&nbsp;", " ")?.replace("&amp;", "&") ?: "")
                }
                
                currentIndex = end
            }

            // Append remaining text
            if (currentIndex < html.length) {
                append(html.substring(currentIndex).replace("<br>", "\n").replace("&nbsp;", " ").replace("&amp;", "&"))
            }
        }
    }
}
