package com.radiojhero.app

import android.app.Activity
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import java.util.*

fun getNow(): Double {
    return Calendar.getInstance().timeInMillis / 1000.0
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