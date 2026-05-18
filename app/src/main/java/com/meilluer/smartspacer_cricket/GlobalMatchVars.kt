package com.meilluer.smartspacer_cricket

import android.content.Context
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider

var home_team: String = ""
var away_team: String = ""
var home_score: String = ""
var away_score: String = ""
var home_overs: String = ""
var away_overs: String = ""
var status: String = ""
var CRR: String = ""
var RR: String = ""
var dismiss_flag: Boolean = false

object GlobalMatchVarsStore {
    private const val PREFS_NAME = "global_match_vars"
    private const val KEY_HOME_TEAM = "home_team"
    private const val KEY_AWAY_TEAM = "away_team"
    private const val KEY_HOME_SCORE = "home_score"
    private const val KEY_AWAY_SCORE = "away_score"
    private const val KEY_HOME_OVERS = "home_overs"
    private const val KEY_AWAY_OVERS = "away_overs"
    private const val KEY_STATUS = "status"
    private const val KEY_CRR = "crr"
    private const val KEY_RR = "rr"
    private const val KEY_DISMISS_FLAG = "dismiss_flag"

    fun hydrate(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        home_team = prefs.getString(KEY_HOME_TEAM, "").orEmpty()
        away_team = prefs.getString(KEY_AWAY_TEAM, "").orEmpty()
        home_score = prefs.getString(KEY_HOME_SCORE, "").orEmpty()
        away_score = prefs.getString(KEY_AWAY_SCORE, "").orEmpty()
        home_overs = prefs.getString(KEY_HOME_OVERS, "").orEmpty()
        away_overs = prefs.getString(KEY_AWAY_OVERS, "").orEmpty()
        status = prefs.getString(KEY_STATUS, "").orEmpty()
        CRR = prefs.getString(KEY_CRR, "").orEmpty()
        RR = prefs.getString(KEY_RR, "").orEmpty()
        dismiss_flag = prefs.getBoolean(KEY_DISMISS_FLAG, false)
    }

    fun update(context: Context, match: MatchInfo?, favoriteTeams: Set<String>) {
        if (match == null) {
            setValues(context, "", "", "", "", "", "", "", "", "")
            return
        }

        val followedTeam = when {
            favoriteTeams.contains(match.team1) -> match.team1
            favoriteTeams.contains(match.team2) -> match.team2
            else -> match.team1
        }

        val followedIsTeam1 = followedTeam == match.team1
        setValues(
            context = context,
            newHomeTeam = followedTeam,
            newAwayTeam = if (followedIsTeam1) match.team2 else match.team1,
            newHomeScore = if (followedIsTeam1) match.team1Score.orEmpty() else match.team2Score.orEmpty(),
            newAwayScore = if (followedIsTeam1) match.team2Score.orEmpty() else match.team1Score.orEmpty(),
            newHomeOvers = if (followedIsTeam1) match.team1Overs.orEmpty() else match.team2Overs.orEmpty(),
            newAwayOvers = if (followedIsTeam1) match.team2Overs.orEmpty() else match.team1Overs.orEmpty(),
            newStatus = match.status.orEmpty(),
            newCRR = match.crr.orEmpty(),
            newRR = match.rr.orEmpty()
        )
    }

    private fun setValues(
        context: Context,
        newHomeTeam: String,
        newAwayTeam: String,
        newHomeScore: String,
        newAwayScore: String,
        newHomeOvers: String,
        newAwayOvers: String,
        newStatus: String,
        newCRR: String,
        newRR: String
    ) {
        home_team = newHomeTeam
        away_team = newAwayTeam
        home_score = newHomeScore
        away_score = newAwayScore
        home_overs = newHomeOvers
        away_overs = newAwayOvers
        status = newStatus
        CRR = newCRR
        RR = newRR

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOME_TEAM, home_team)
            .putString(KEY_AWAY_TEAM, away_team)
            .putString(KEY_HOME_SCORE, home_score)
            .putString(KEY_AWAY_SCORE, away_score)
            .putString(KEY_HOME_OVERS, home_overs)
            .putString(KEY_AWAY_OVERS, away_overs)
            .putString(KEY_STATUS, status)
            .putString(KEY_CRR, CRR)
            .putString(KEY_RR, RR)
            .putBoolean(KEY_DISMISS_FLAG, dismiss_flag)
            .apply()

        SmartspacerTargetProvider.notifyChange(context, Target::class.java, smartspacerId = "notify")
    }

    fun setDismissFlag(context: Context, value: Boolean) {
        dismiss_flag = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISMISS_FLAG, dismiss_flag)
            .apply()
        SmartspacerTargetProvider.notifyChange(context, Target::class.java, smartspacerId = "notify")
    }
}
