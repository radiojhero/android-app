package com.radiojhero.app.ui.settings

import android.content.SharedPreferences
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.onesignal.OneSignal
import com.radiojhero.app.BuildConfig
import com.radiojhero.app.R
import com.radiojhero.app.switchTheme
import com.radiojhero.app.ui.components.AppTopBar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** A setting stored in the default shared preferences and picked from a list. */
private data class ListSetting(
    val key: String,
    val defaultValue: String,
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
    @param:ArrayRes val entries: Int,
    @param:ArrayRes val entryValues: Int,
)

private val FormatSetting = ListSetting(
    "format",
    "mp3",
    R.string.setting_format,
    R.drawable.ic_baseline_music_note_24,
    R.array.format_entries,
    R.array.format_values,
)

private val AppearanceSetting = ListSetting(
    "appearance",
    "system",
    R.string.setting_appearance,
    R.drawable.ic_baseline_brightness_4_24,
    R.array.appearance_entries,
    R.array.appearance_values,
)

private val NotificationSetting = ListSetting(
    "notification",
    "none",
    R.string.setting_notifications,
    R.drawable.ic_baseline_notifications_24,
    R.array.notifications_entries,
    R.array.notifications_values,
)

@Composable
fun SettingsScreen(
    scrollToTop: Flow<Unit>,
    onOpenAbout: () -> Unit,
    onFormatChange: (format: String) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollToTop) {
        scrollToTop.collect { scrollState.animateScrollTo(0) }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(scrolled = scrollState.value > 0)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_about)) },
                modifier = Modifier.clickable(onClick = onOpenAbout),
                leadingContent = { SettingIcon(R.drawable.ic_baseline_help_24) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            ListSettingItem(preferences, FormatSetting) {
                println("Changing preference 'format'")
                onFormatChange(it)
            }

            ListSettingItem(preferences, AppearanceSetting) {
                println("Changing preference 'appearance'")
                switchTheme(it)
            }

            ListSettingItem(preferences, NotificationSetting) {
                println("Changing preference 'notification'")
                scope.launch { setNotificationMode(it) }
            }

            Text(
                stringResource(R.string.version_name_and_text),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ListSettingItem(
    preferences: SharedPreferences,
    setting: ListSetting,
    onChange: (value: String) -> Unit,
) {
    val value by rememberPreference(preferences, setting.key, setting.defaultValue)
    val entries = stringArrayResource(setting.entries)
    val entryValues = stringArrayResource(setting.entryValues)
    val selectedIndex = entryValues.indexOf(value)
    var isDialogOpen by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(setting.title)) },
        modifier = Modifier.clickable { isDialogOpen = true },
        supportingContent = entries.getOrNull(selectedIndex)?.let { { Text(it) } },
        leadingContent = { SettingIcon(setting.icon) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )

    if (!isDialogOpen) {
        return
    }

    AlertDialog(
        onDismissRequest = { isDialogOpen = false },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { isDialogOpen = false }) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(stringResource(setting.title)) },
        text = {
            Column {
                entries.forEachIndexed { index, entry ->
                    val select = {
                        isDialogOpen = false
                        val newValue = entryValues[index]
                        if (newValue != value) {
                            preferences.edit { putString(setting.key, newValue) }
                            onChange(newValue)
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = index == selectedIndex,
                                onClick = select,
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = null)
                        Text(entry, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun SettingIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null)
}

/** The current value of a string preference, kept up to date when it changes elsewhere. */
@Composable
private fun rememberPreference(
    preferences: SharedPreferences,
    key: String,
    defaultValue: String,
): State<String> {
    val state = remember(preferences, key) {
        mutableStateOf(preferences.getString(key, defaultValue) ?: defaultValue)
    }

    DisposableEffect(preferences, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                state.value = preferences.getString(key, defaultValue) ?: defaultValue
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return state
}

private suspend fun setNotificationMode(mode: String) {
    val newValue = when (mode) {
        "shows" -> 1
        "shows_articles" -> 2
        else -> 0
    }
    println("Setting notification mode to $newValue...")

    if (newValue != 0) {
        OneSignal.consentGiven = OneSignal.Notifications.requestPermission(true)
    }

    if (!OneSignal.Notifications.permission) {
        return
    }

    OneSignal.User.addTag("notification_mode", "$newValue")

    if (BuildConfig.DEBUG) {
        OneSignal.User.addTag("debug_mode", "1")
    }
}
