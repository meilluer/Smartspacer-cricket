package com.meilluer.smartspacer_cricket

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_FAVORITE_TEAMS = "favorite_teams"
    private const val KEY_REFRESH_INTERVAL = "refresh_interval"
    private const val KEY_SCHEDULED_PREMATCH_IDS = "scheduled_prematch_ids"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFavoriteTeams(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FAVORITE_TEAMS, emptySet()) ?: emptySet()

    fun setFavoriteTeams(context: Context, teams: Set<String>) {
        prefs(context).edit().putStringSet(KEY_FAVORITE_TEAMS, teams).apply()
    }

    fun getRefreshIntervalMinutes(context: Context): Int =
        prefs(context).getInt(KEY_REFRESH_INTERVAL, 5)

    fun setRefreshIntervalMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_REFRESH_INTERVAL, minutes).apply()
    }

    fun getScheduledPreMatchIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SCHEDULED_PREMATCH_IDS, emptySet()) ?: emptySet()

    fun setScheduledPreMatchIds(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY_SCHEDULED_PREMATCH_IDS, ids).apply()
    }
}
