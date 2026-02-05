package com.cjlhll.iptv

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val PREF_NAME = "iptv_prefs"
    private const val KEY_LIVE_SOURCE = "live_source"
    private const val KEY_EPG_SOURCE = "epg_source"
    private const val KEY_LAST_CHANNEL_URL = "last_channel_url"
    private const val KEY_LAST_CHANNEL_TITLE = "last_channel_title"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getLiveSource(context: Context): String {
        return getPrefs(context).getString(KEY_LIVE_SOURCE, "") ?: ""
    }

    fun setLiveSource(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_LIVE_SOURCE, url).apply()
    }

    fun getEpgSource(context: Context): String {
        return getPrefs(context).getString(KEY_EPG_SOURCE, "") ?: ""
    }

    fun setEpgSource(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_EPG_SOURCE, url).apply()
    }

    fun getLastChannel(context: Context): Pair<String?, String?> {
        val prefs = getPrefs(context)
        val url = prefs.getString(KEY_LAST_CHANNEL_URL, null)
        val title = prefs.getString(KEY_LAST_CHANNEL_TITLE, null)
        return Pair(url, title)
    }

    fun setLastChannel(context: Context, url: String, title: String) {
        getPrefs(context).edit()
            .putString(KEY_LAST_CHANNEL_URL, url)
            .putString(KEY_LAST_CHANNEL_TITLE, title)
            .apply()
    }
}
