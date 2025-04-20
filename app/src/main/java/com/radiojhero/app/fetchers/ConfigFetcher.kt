package com.radiojhero.app.fetchers

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.io.BufferedInputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import androidx.core.content.edit

class ConfigFetcher {
    companion object {
        val hasLoaded: Boolean get() = mSharedPreferences.getBoolean("remoteConfigLoaded", false)
        private var mIsRunning = false
        private var mConfigTimer: Timer? = null
        private lateinit var mSharedPreferences: SharedPreferences

        fun start(context: Context) {
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

            if (mIsRunning) {
                return
            }

            mIsRunning = true
            println("Configuration fetcher started.")
            mConfigTimer = Timer()
            mConfigTimer?.scheduleAtFixedRate(0, 3600000) {
                fetch()
            }
        }

        fun stop() {
            if (!mIsRunning) {
                return
            }

            mConfigTimer?.cancel()
            mConfigTimer = null
            println("Configuration fetcher stopped.")
        }

        fun getConfig(key: String): String? {
            return mSharedPreferences.getString(key, "")
        }

        private fun fetch() {
            println("Fetching configuration...")

            val url = "https://wp.radiojhero.com/android-app.properties"
            val urlObject = URL(url)
            val urlConnection = urlObject.openConnection() as HttpURLConnection

            try {
                val inputStream = BufferedInputStream(urlConnection.inputStream)
                val s = Scanner(inputStream).useDelimiter("\\A")
                serialize(if (s.hasNext()) s.next() else "")
            } catch (error: Throwable) {
                if (!mSharedPreferences.getBoolean("remoteConfigLoaded", false)) {
                    throw error
                }
            } finally {
                urlConnection.disconnect()
            }
        }

        private fun serialize(data: String?) {
            val properties = Properties().apply {
                load(StringReader(data))
            }

            mSharedPreferences.edit {
                for (property in properties) {
                    putString(property.key as String, property.value as String?)
                }

                putBoolean("remoteConfigLoaded", true)
            }
            println("Configuration fetched and parsed.")
        }
    }
}