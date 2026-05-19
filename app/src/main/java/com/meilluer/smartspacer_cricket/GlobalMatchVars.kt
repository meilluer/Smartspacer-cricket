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
var wicket_info: String = ""
var second_innings: Boolean = false
var isFinished: Boolean = false
var dismiss_flag: Boolean = false
var wicket_flag: Boolean = false
var last_wicket_count: Int = 0
var last_match_id: Long = -1L

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
    private const val KEY_WICKET_INFO = "wicket_info"
    private const val KEY_SECOND_INNINGS = "second_innings"
    private const val KEY_IS_FINISHED = "is_finished"
    private const val KEY_DISMISS_FLAG = "dismiss_flag"
    private const val KEY_WICKET_FLAG = "wicket_flag"
    private const val KEY_LAST_WICKET_COUNT = "last_wicket_count"
    private const val KEY_LAST_MATCH_ID = "last_match_id"

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
        wicket_info = prefs.getString(KEY_WICKET_INFO, "").orEmpty()
        second_innings = prefs.getBoolean(KEY_SECOND_INNINGS, false)
        isFinished = prefs.getBoolean(KEY_IS_FINISHED, false)
        dismiss_flag = prefs.getBoolean(KEY_DISMISS_FLAG, false)
        wicket_flag = prefs.getBoolean(KEY_WICKET_FLAG, false)
        last_wicket_count = prefs.getInt(KEY_LAST_WICKET_COUNT, 0)
        last_match_id = prefs.getLong(KEY_LAST_MATCH_ID, -1L)
    }

    fun update(context: Context, match: MatchInfo?, favoriteTeams: Set<String>) {
        if (match == null) {
            last_match_id = -1L
            last_wicket_count = 0
            setValues(context, "", "", "", "", "", "", "", "", "", "", false, false)
            return
        }

        if (match.matchId != last_match_id) {
            last_match_id = match.matchId
            last_wicket_count = extractWickets(match.team1Score) + extractWickets(match.team2Score)
        }

        val followedTeam = when {
            favoriteTeams.contains(match.team1) -> match.team1
            favoriteTeams.contains(match.team2) -> match.team2
            else -> match.team1
        }

        val followedIsTeam1 = followedTeam == match.team1

        val currentWickets = extractWickets(match.team1Score) + extractWickets(match.team2Score)
        if (currentWickets > last_wicket_count && last_wicket_count != 0) {
            setWicketFlag(context, true)
            CricketScheduler.scheduleWicketFlagReset(context)
        }
        last_wicket_count = currentWickets

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
            newRR = match.rr.orEmpty(),
            newWicketInfo = match.wicketInfo.orEmpty(),
            newSecondInnings = match.second_innings,
            newIsFinished = match.isFinished
        )
    }

    private fun extractWickets(score: String?): Int {
        if (score == null) return 0
        val regex = """/(\d+)""".toRegex()
        return regex.findAll(score).sumOf { it.groupValues[1].toInt() }
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
        newRR: String,
        newWicketInfo: String,
        newSecondInnings: Boolean,
        newIsFinished: Boolean
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
        wicket_info = newWicketInfo
        second_innings = newSecondInnings
        isFinished = newIsFinished

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
            .putString(KEY_WICKET_INFO, wicket_info)
            .putBoolean(KEY_SECOND_INNINGS, second_innings)
            .putBoolean(KEY_IS_FINISHED, isFinished)
            .putBoolean(KEY_DISMISS_FLAG, dismiss_flag)
            .putBoolean(KEY_WICKET_FLAG, wicket_flag)
            .putInt(KEY_LAST_WICKET_COUNT, last_wicket_count)
            .putLong(KEY_LAST_MATCH_ID, last_match_id)
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

    fun setWicketFlag(context: Context, value: Boolean) {
        wicket_flag = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WICKET_FLAG, wicket_flag)
            .apply()
        SmartspacerTargetProvider.notifyChange(context, Target::class.java, smartspacerId = "notify")
    }
}
