package com.meilluer.smartspacer_cricket

object TrackedMatchEnricher {

    fun enrich(matches: List<MatchInfo>, favoriteTeams: Set<String>, scraper: CricbuzzScraper): List<MatchInfo> {
        val selectedMatch = TrackedMatchSelector.selectPrimaryMatch(matches, favoriteTeams) ?: return matches
        val enrichedMatch = scraper.enrichWithWicketInfo(selectedMatch)

        return matches.map { match ->
            if (match.matchId == enrichedMatch.matchId) enrichedMatch else match
        }
    }
}
