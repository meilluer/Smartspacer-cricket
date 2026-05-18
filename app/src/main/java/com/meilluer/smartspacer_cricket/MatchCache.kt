package com.meilluer.smartspacer_cricket

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object MatchCache {
    private const val PREFS_NAME = "match_cache"
    private const val KEY_MATCHES_JSON = "matches_json"
    private const val KEY_LAST_UPDATED_AT = "last_updated_at"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, matches: List<MatchInfo>) {
        val jsonArray = JSONArray()
        matches.forEach { match ->
            jsonArray.put(
                JSONObject().apply {
                    put("matchId", match.matchId)
                    put("team1", match.team1)
                    put("team2", match.team2)
                    put("team1Score", match.team1Score)
                    put("team2Score", match.team2Score)
                    put("overs", match.overs)
                    put("runRate", match.runRate)
                    put("status", match.status)
                    put("matchDetails", match.matchDetails)
                    put("matchState", match.matchState)
                    put("startTimeMillis", match.startTimeMillis)
                }
            )
        }

        prefs(context).edit()
            .putString(KEY_MATCHES_JSON, jsonArray.toString())
            .putLong(KEY_LAST_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun load(context: Context): List<MatchInfo> {
        val rawJson = prefs(context).getString(KEY_MATCHES_JSON, null) ?: return emptyList()
        val jsonArray = JSONArray(rawJson)
        val matches = mutableListOf<MatchInfo>()

        for (index in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(index) ?: continue
            matches.add(
                MatchInfo(
                    matchId = item.optLong("matchId", -1L),
                    team1 = item.optString("team1"),
                    team2 = item.optString("team2"),
                    team1Score = item.optString("team1Score").ifBlank { null },
                    team2Score = item.optString("team2Score").ifBlank { null },
                    overs = item.optString("overs").ifBlank { null },
                    runRate = item.optString("runRate").ifBlank { null },
                    status = item.optString("status").ifBlank { null },
                    matchDetails = item.optString("matchDetails").ifBlank { null },
                    matchState = item.optString("matchState").ifBlank { null },
                    startTimeMillis = item.optLong("startTimeMillis", -1L).takeIf { it > 0L }
                )
            )
        }

        return matches
    }
}
