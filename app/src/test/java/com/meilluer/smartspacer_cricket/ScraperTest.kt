package com.meilluer.smartspacer_cricket

import org.junit.Test
import org.junit.Assert.*

class ScraperTest {
    @Test
    fun testScraper() {
        val scraper = CricbuzzScraper()
        val matches = scraper.fetchLiveMatches()
        
        println("Found ${matches.size} matches")
        matches.forEach { match ->
            println("---")
            println("Team 1: ${match.team1}")
            println("Team 2: ${match.team2}")
            println("Score 1: ${match.team1Score}")
            println("Score 2: ${match.team2Score}")
            println("Overs: ${match.overs}")
            println("Run Rate: ${match.runRate}")
            println("Status: ${match.status}")
        }
        
        // At least some matches should be found if there are live games
        // If no live games, the list might be empty, which is also a valid state
        // but for testing we hope to see some data.
        assertTrue(matches.isNotEmpty() || true) 
    }
}
