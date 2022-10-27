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
import java.util.*


fun getNow(): Double {
    return Calendar.getInstance().timeInMillis / 1000.0
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