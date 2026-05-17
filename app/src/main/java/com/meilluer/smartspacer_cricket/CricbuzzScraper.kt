package com.meilluer.smartspacer_cricket

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

class CricbuzzScraper {

    private val url = "https://www.cricbuzz.com/cricket-match/live-scores"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    fun fetchLiveMatches(): List<MatchInfo> {
        val matches = mutableListOf<MatchInfo>()
        try {
            val doc: Document = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(10000)
                .get()

            val matchElements = doc.select("div.cb-mtch-lst.cb-col.cb-col-100.cb-tms-itm")

            for (element in matchElements) {
                val match = parseMatchElement(element)
                if (match != null) {
                    matches.add(match)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return matches
    }

    private fun parseMatchElement(element: Element): MatchInfo? {
        try {
            val teams = element.select("div.cb-hm-scg-tm-nm")
            if (teams.size < 2) return null

            val team1 = teams[0].text().trim().split(" ").last()
            val team2 = teams[1].text().trim().split(" ").last()

            val scores = element.select("div.cb-hm-scg-tm-scr")
            val team1ScoreRaw = if (scores.size > 0) scores[0].text() else null
            val team2ScoreRaw = if (scores.size > 1) scores[1].text() else null

            // Extract overs from score strings like "110-2 (16.4)"
            val oversRegex = """\((\d+\.?\d*)\)""".toRegex()
            val team1Overs = team1ScoreRaw?.let { oversRegex.find(it)?.groupValues?.get(1) }
            val team2Overs = team2ScoreRaw?.let { oversRegex.find(it)?.groupValues?.get(1) }

            val team1Score = team1ScoreRaw?.replace(oversRegex, "")?.trim()
            val team2Score = team2ScoreRaw?.replace(oversRegex, "")?.trim()

            val statusElement = element.select("div.cb-mtch-sts").first()
            val status = statusElement?.text()?.trim()

            // Run Rate (CRR/NRR) often in status or as separate text
            val rrRegex = """(?:CRR|NRR):\s*(\d+\.?\d*)""".toRegex(RegexOption.IGNORE_CASE)
            val runRate = status?.let { rrRegex.find(it)?.value }

            return MatchInfo(
                team1 = team1,
                team2 = team2,
                team1Score = team1Score,
                team2Score = team2Score,
                overs = team2Overs ?: team1Overs, // Usually the batting team's overs
                runRate = runRate,
                status = status
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
