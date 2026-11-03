package dev.fourthbreakfast.planner.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Shared preferences, under one file name. */
class AndroidPrefs(context: Context, name: String) : Prefs {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)

    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)

    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) = prefs.edit().putString(key, value).apply()
}

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

@Composable
actual fun rememberIs24Hour(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

// with animations switched off in the phone's settings, screens just change
@Composable
actual fun rememberAnimationsOn(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}

@Composable
actual fun rememberAskToNotify(): () -> Unit {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun rememberKeyboardShown(): Boolean = WindowInsets.isImeVisible
