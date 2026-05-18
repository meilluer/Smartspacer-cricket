package com.meilluer.smartspacer_cricket

data class MatchInfo(
    val matchId: Long,
    val team1: String,
    val team2: String,
    val team1Score: String? = null,
    val team2Score: String? = null,
    val team1Overs: String? = null,
    val team2Overs: String? = null,
    val overs: String? = null,
    val runRate: String? = null,
    val status: String? = null,
    val matchDetails: String? = null,
    val matchState: String? = null,
    val startTimeMillis: Long? = null,
    val crr: String? = null,
    val rr: String? = null
)
