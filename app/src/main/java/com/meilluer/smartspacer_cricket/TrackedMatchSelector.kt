package com.meilluer.smartspacer_cricket

import kotlin.random.Random

object TrackedMatchSelector {

    fun selectTrackedMatches(
        matches: List<MatchInfo>,
        favoriteTeams: Set<String>
    ): List<MatchInfo> {
        val favoriteMatches = matches.filter { favoriteTeams.contains(it.team1) || favoriteTeams.contains(it.team2) }

        return favoriteMatches
            .groupBy { match -> match.startTimeMillis ?: match.matchId }
            .values
            .map { simultaneousMatches ->
                if (simultaneousMatches.size == 1) {
                    simultaneousMatches.first()
                } else {
                    val seedBase = simultaneousMatches.first().startTimeMillis ?: simultaneousMatches.first().matchId
                    val random = Random(seedBase.hashCode())
                    simultaneousMatches[random.nextInt(simultaneousMatches.size)]
                }
            }
    }

    fun selectPrimaryMatch(
        matches: List<MatchInfo>,
        favoriteTeams: Set<String>,
        now: Long = System.currentTimeMillis()
    ): MatchInfo? {
        val trackedMatches = selectTrackedMatches(matches, favoriteTeams)
        if (trackedMatches.isEmpty()) return null

        return trackedMatches
            .filter { hasStarted(it, now) && !isFinished(it) }
            .minByOrNull { it.startTimeMillis ?: Long.MAX_VALUE }
            ?: trackedMatches
                .filter { (it.startTimeMillis ?: Long.MAX_VALUE) > now && !isFinished(it) }
                .minByOrNull { it.startTimeMillis ?: Long.MAX_VALUE }
            ?: trackedMatches.maxByOrNull { it.startTimeMillis ?: Long.MIN_VALUE }
    }

    fun hasStarted(match: MatchInfo, now: Long): Boolean =
        (match.startTimeMillis ?: Long.MAX_VALUE) <= now

    fun isFinished(match: MatchInfo): Boolean {
        val statusText = match.status.orEmpty().lowercase()
        val state = match.matchState.orEmpty().lowercase()
        return state.contains("complete") ||
            state.contains("stumps") ||
            (statusText.contains("won") && !statusText.contains("won the toss"))
    }
}
