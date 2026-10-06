package dev.asrithtanniru.claudecap.widget

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.edit
import dev.asrithtanniru.claudecap.data.UsageRepository

/**
 * Remembers when a background refresh (widget tap or worker) failed with a network error on a
 * metered connection. That's the signature of Android blocking the app's background data on
 * mobile data -- there's no public API to read that per-app toggle, so it's inferred here and
 * surfaced in the app. Only background refreshes should call [record]: the app itself runs in
 * the foreground, where that restriction doesn't apply.
 */
object BackgroundDataHint {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_SHOWING = "background_data_hint"

    fun record(context: Context, lastResultKind: String?) {
        when (lastResultKind) {
            UsageRepository.RESULT_NETWORK_ERROR -> if (isOnMeteredNetwork(context)) set(context, true)
            UsageRepository.RESULT_SUCCESS -> set(context, false)
        }
    }

    fun isShowing(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOWING, false)

    fun clear(context: Context) = set(context, false)

    private fun isOnMeteredNetwork(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.isActiveNetworkMetered
    }

    private fun set(context: Context, showing: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOWING, showing) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
