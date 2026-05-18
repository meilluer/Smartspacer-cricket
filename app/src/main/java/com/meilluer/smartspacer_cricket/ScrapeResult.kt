package com.meilluer.smartspacer_cricket

data class ScrapeResult(
    val matches: List<MatchInfo>,
    val errorMessage: String? = null
)
