package com.radiojhero.app

import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONObject
import java.text.Normalizer
import java.util.*

fun getNow(): Long {
    return Calendar.getInstance().timeInMillis
}

fun Long.toDate(): Date {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = this
    return calendar.time
}

fun switchTheme(theme: String) {
    AppCompatDelegate.setDefaultNightMode(
        when (theme) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    )
}

fun pmDocToHTML(doc: String): String = buildString {
    val json = JSONObject(doc)
    val document = json.getJSONArray("content").getJSONObject(0)

    if (document.getString("type") == "raw") {
        append(document.getJSONArray("content").getJSONObject(0).getString("text"))
        return@buildString
    }

    val contents = document.getJSONArray("content")
    for (i in 0 until contents.length()) {
        val fragment = contents.getJSONObject(i)
        val marks = fragment.optJSONArray("marks")
        var text = fragment.getString("text")

        if (marks == null) {
            append(text)
            continue
        }

        for (j in 0 until marks.length()) {
            val mark = marks.getJSONObject(j)
            val attrs = mark.getJSONObject("attrs")

            try {
                text = when (mark.getString("type")) {
                    "bold" -> "<strong>$text</strong>"
                    "italic" -> "<em>$text</em>"
                    "b" -> "<b>$text</b>"
                    "i" -> "<i>$text</i>"
                    "strike" -> "<s>$text</s>"
                    "cite" -> if (attrs.optBoolean("quotes")) "“$text”" else "<cite>$text</cite>"
                    "code" -> "<code>$text</code>"
                    "subscript" -> "<sub>$text</sub>"
                    "superscript" -> "<sup>$text</sup>"
                    "highlight" -> "<mark>$text</mark>"
                    "ruby" -> "<ruby>$text<rp>(</rp><rt>${attrs.getString("text")}</rt><rp>)</rp></ruby>"
                    "language" -> "<span lang=\"${attrs.getString("lang")}\">$text</span>"
                    else -> text
                }
            } catch (error: Throwable) {
                println(error)
            }
        }

        append(text)
    }
}

val REGEX_UNACCENT = "\\p{InCombiningDiacriticalMarks}+".toRegex()

fun CharSequence.normalize(): String {
    val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
    return REGEX_UNACCENT.replace(temp, "").lowercase()
}