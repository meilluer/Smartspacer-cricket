package com.meilluer.smartspacer_cricket

data class MatchInfo(
    val team1: String,
    val team2: String,
    val team1Score: String? = null,
    val team2Score: String? = null,
    val overs: String? = null,
    val runRate: String? = null,
    val status: String? = null,
    val matchDetails: String? = null
)
