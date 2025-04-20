package com.radiojhero.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
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

class RoundedOutlineProvider(private val radius: Float) : ViewOutlineProvider() {

    override fun getOutline(view: View?, outline: Outline?) {
        if (view == null) {
            return
        }

        val dpRadius = radius * view.resources.displayMetrics.density
        outline?.setRoundRect(0, 0, view.width, view.height, dpRadius)
    }
}

fun Activity.endEditing() {
    currentFocus?.let {
        val inputMethodManager =
            ContextCompat.getSystemService(this, InputMethodManager::class.java)
        inputMethodManager?.hideSoftInputFromWindow(it.windowToken, 0)
    }
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

fun View.expand(duration: Long) {
    measure(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    val targetHeight = measuredHeight

    layoutParams.height = 0
    visibility = View.VISIBLE
    val anim = ValueAnimator.ofInt(measuredHeight, targetHeight)
    anim.interpolator = AccelerateDecelerateInterpolator()
    anim.duration = duration
    anim.addUpdateListener {
        val layoutParams = layoutParams
        layoutParams.height = (targetHeight * it.animatedFraction).toInt()
        this.layoutParams = layoutParams
    }

    anim.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
    })
    anim.start()
}

fun View.collapse(duration: Long) {
    measure(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    val anim = ValueAnimator.ofInt(measuredHeight, 0)
    anim.interpolator = AccelerateDecelerateInterpolator()
    anim.duration = duration
    anim.addUpdateListener {
        val layoutParams = layoutParams
        layoutParams.height = (measuredHeight * (1 - it.animatedFraction)).toInt()
        this.layoutParams = layoutParams
    }

    anim.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            layoutParams.height = 0
            visibility = View.GONE
        }
    })
    anim.start()
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