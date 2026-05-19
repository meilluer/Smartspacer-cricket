package com.meilluer.smartspacer_cricket

import org.jsoup.Jsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class CricbuzzScraper {

    private val url = "https://www.cricbuzz.com/cricket-match/live-scores"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun fetchLiveMatches(): ScrapeResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return ScrapeResult(
                        matches = emptyList(),
                        errorMessage = "Cricbuzz returned ${response.code}. Pull to refresh and try again."
                    )
                }

                val html = response.body?.string().orEmpty()
                val matches = parseMatchesFromPage(html)

                if (matches.isEmpty()) {
                    ScrapeResult(
                        matches = emptyList(),
                        errorMessage = "Cricbuzz page loaded, but no matches could be parsed."
                    )
                } else {
                    ScrapeResult(matches = matches)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            ScrapeResult(
                matches = emptyList(),
                errorMessage = "Unable to reach Cricbuzz. Check your internet connection and try again."
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ScrapeResult(
                matches = emptyList(),
                errorMessage = "Unexpected Cricbuzz response. Pull to refresh in a moment."
            )
        }
    }

    private fun parseMatchesFromPage(html: String): List<MatchInfo> {
        val normalizedHtml = html.replace("\\\"", "\"")
        val matchesArrayJson = extractMatchesArrayJson(normalizedHtml) ?: return emptyList()
        val matchesArray = JSONArray(matchesArrayJson)
        val matches = mutableListOf<MatchInfo>()

        for (index in 0 until matchesArray.length()) {
            val item = matchesArray.optJSONObject(index) ?: continue
            val matchObject = item.optJSONObject("match") ?: continue
            parseMatch(matchObject, normalizedHtml)?.let(matches::add)
        }

        return matches
    }

    private fun parseMatch(matchObject: JSONObject, normalizedHtml: String): MatchInfo? {
        val info = matchObject.optJSONObject("matchInfo") ?: return null
        val score = matchObject.optJSONObject("matchScore")
        val team1Info = info.optJSONObject("team1") ?: return null
        val team2Info = info.optJSONObject("team2") ?: return null
        val matchId = info.optLong("matchId", -1L)

        val team1 = team1Info.optString("teamSName").ifBlank { team1Info.optString("teamName") }
        val team2 = team2Info.optString("teamSName").ifBlank { team2Info.optString("teamName") }
        if (matchId <= 0 || team1.isBlank() || team2.isBlank()) return null

        val team1ScoreObject = score?.optJSONObject("team1Score")
        val team2ScoreObject = score?.optJSONObject("team2Score")
        val team1Score = team1ScoreObject?.let(::formatTeamScore)
        val team2Score = team2ScoreObject?.let(::formatTeamScore)

        val currentBatTeamId = info.optInt("currBatTeamId", -1)
        val team1Id = team1Info.optInt("teamId", -1)
        val team2Id = team2Info.optInt("teamId", -1)
        val currentInningsId = when (currentBatTeamId) {
            team1Id -> extractLatestInnings(team1ScoreObject)?.inningsId
            team2Id -> extractLatestInnings(team2ScoreObject)?.inningsId
            else -> null
        }
        val team1Overs = team1ScoreObject?.let(::extractLatestOvers)
        val team2Overs = team2ScoreObject?.let(::extractLatestOvers)
        val activeOvers = when (currentBatTeamId) {
            team1Id -> team1Overs
            team2Id -> team2Overs
            else -> team2Overs ?: team1Overs
        }

        val status = info.optString("status")
            .ifBlank { info.optString("shortStatus") }
            .ifBlank { info.optString("stateTitle") }
            .ifBlank { null }

        val runRate = status?.let {
            """(?:CRR|RRR|NRR):\s*[\d.]+""".toRegex(RegexOption.IGNORE_CASE).find(it)?.value
        }

        val matchDesc = info.optString("matchDesc").ifBlank { null }
        val seriesName = info.optString("seriesName").ifBlank { null }
        val matchDetails = listOfNotNull(seriesName, matchDesc).joinToString(" • ").ifBlank { null }
        val matchState = info.optString("state").ifBlank { info.optString("stateTitle") }.ifBlank { null }
        val startTimeMillis = info.optLong("startDate", -1L).takeIf { it > 0L }
        val crr = calculateCurrentRunRate(currentBatTeamId, team1Id, team2Id, team1ScoreObject, team2ScoreObject)
        val rr = calculateRequiredRunRate(info, team1ScoreObject, team2ScoreObject)
        val scorecardPath = extractScorecardPath(normalizedHtml, matchId)
        
        val secondInningsStarted = hasSecondInningsStarted(team1ScoreObject, team2ScoreObject)
        val normalizedStatus = status?.lowercase().orEmpty()
        val isFinished = matchState?.lowercase()?.contains("complete") == true ||
                (normalizedStatus.contains("won") && !normalizedStatus.contains("won the toss")) ||
                matchState?.lowercase()?.contains("abandoned") == true

        return MatchInfo(
            matchId = matchId,
            team1 = team1,
            team2 = team2,
            team1Id = team1Id.takeIf { it > 0 },
            team2Id = team2Id.takeIf { it > 0 },
            team1Score = team1Score,
            team2Score = team2Score,
            team1Overs = team1Overs,
            team2Overs = team2Overs,
            overs = activeOvers,
            runRate = runRate,
            status = status,
            matchDetails = matchDetails,
            matchState = matchState,
            startTimeMillis = startTimeMillis,
            currentBatTeamId = currentBatTeamId.takeIf { it > 0 },
            currentInningsId = currentInningsId,
            scorecardPath = scorecardPath,
            crr = crr,
            rr = rr,
            second_innings = secondInningsStarted && !isFinished,
            isFinished = isFinished
        )
    }

    private fun hasSecondInningsStarted(
        team1ScoreObject: JSONObject?,
        team2ScoreObject: JSONObject?
    ): Boolean {
        val allInnings = mutableListOf<JSONObject>()
        
        team1ScoreObject?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                obj.optJSONObject(keys.next())?.let { allInnings.add(it) }
            }
        }
        
        team2ScoreObject?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                obj.optJSONObject(keys.next())?.let { allInnings.add(it) }
            }
        }
        
        return allInnings.any { it.optInt("inningsId", 0) >= 2 }
    }

    private fun extractMatchesArrayJson(normalizedHtml: String): String? {
        val marker = "\"matchesList\":{\"matches\":["
        val markerIndex = normalizedHtml.indexOf(marker)
        if (markerIndex == -1) return null

        val arrayStart = normalizedHtml.indexOf('[', markerIndex)
        if (arrayStart == -1) return null

        return extractBalancedJsonArray(normalizedHtml, arrayStart)
    }

    private fun extractBalancedJsonArray(source: String, startIndex: Int): String? {
        var depth = 0
        var inString = false
        var escaping = false

        for (index in startIndex until source.length) {
            val char = source[index]

            if (escaping) {
                escaping = false
                continue
            }

            if (char == '\\') {
                escaping = true
                continue
            }

            if (char == '"') {
                inString = !inString
                continue
            }

            if (inString) continue

            if (char == '[') {
                depth++
            } else if (char == ']') {
                depth--
                if (depth == 0) {
                    return source.substring(startIndex, index + 1)
                }
            }
        }

        return null
    }

    fun enrichWithWicketInfo(match: MatchInfo): MatchInfo {
        val currentBatTeamId = match.currentBatTeamId ?: return match
        val currentInningsId = match.currentInningsId ?: return match
        val scorecardPath = match.scorecardPath ?: return match

        return try {
            val request = Request.Builder()
                .url("https://www.cricbuzz.com$scorecardPath")
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return match
                val html = response.body?.string().orEmpty()
                val wicketInfo = parseLatestWicketInfo(html, currentBatTeamId, currentInningsId)
                match.copy(wicketInfo = wicketInfo)
            }
        } catch (_: Exception) {
            match
        }
    }

    private fun parseLatestWicketInfo(
        html: String,
        currentBatTeamId: Int,
        currentInningsId: Int
    ): String? {
        val document = Jsoup.parse(html)
        val inningsSection = document.selectFirst("#scard-team-$currentBatTeamId-innings-$currentInningsId") ?: return null

        val fowRows = inningsSection.select("div.grid.scorecard-fow-grid")
            .filter { row -> row.selectFirst("a") != null && row.select("div").size >= 2 }
        val latestWicketRow = fowRows.lastOrNull() ?: return null
        val batterName = latestWicketRow.selectFirst("a")?.text()?.trim().orEmpty()
        if (batterName.isBlank()) return null

        val dismissalRows = inningsSection.select("div.grid.scorecard-bat-grid")
            .mapNotNull { row ->
                val batterLink = row.selectFirst("a[title^=View Profile Of]") ?: return@mapNotNull null
                val dismissalText = row.selectFirst("div.text-cbTxtSec")?.text()?.trim().orEmpty()
                batterLink.text().trim() to dismissalText
            }

        val dismissalText = dismissalRows.lastOrNull { it.first == batterName }?.second.orEmpty()
        if (dismissalText.isBlank() || dismissalText.equals("not out", ignoreCase = true)) return null

        return formatWicketInfo(batterName, dismissalText)
    }

    private fun formatWicketInfo(batterName: String, dismissalText: String): String {
        val cleanText = dismissalText.trim()
        val caughtAndBowled = Regex("""^c and b (.+)$""", RegexOption.IGNORE_CASE).matchEntire(cleanText)
        if (caughtAndBowled != null) {
            return "$batterName out! c & b. ${caughtAndBowled.groupValues[1].trim()}"
        }

        val caught = Regex("""^c (.+) b (.+)$""", RegexOption.IGNORE_CASE).matchEntire(cleanText)
        if (caught != null) {
            val catcher = caught.groupValues[1].trim()
            val bowler = caught.groupValues[2].trim()
            return "$batterName out! b. $bowler + c. $catcher"
        }

        val stumped = Regex("""^st (.+) b (.+)$""", RegexOption.IGNORE_CASE).matchEntire(cleanText)
        if (stumped != null) {
            val keeper = stumped.groupValues[1].trim()
            val bowler = stumped.groupValues[2].trim()
            return "$batterName out! b. $bowler + st. $keeper"
        }

        val lbw = Regex("""^lbw b (.+)$""", RegexOption.IGNORE_CASE).matchEntire(cleanText)
        if (lbw != null) {
            return "$batterName out! lbw b. ${lbw.groupValues[1].trim()}"
        }

        val bowled = Regex("""^b (.+)$""", RegexOption.IGNORE_CASE).matchEntire(cleanText)
        if (bowled != null) {
            return "$batterName out! b. ${bowled.groupValues[1].trim()}"
        }

        return "$batterName out! $cleanText"
    }

    private fun extractScorecardPath(normalizedHtml: String, matchId: Long): String? {
        val regex = Regex("""/live-cricket-scores/$matchId/([^"?#]+)""")
        val match = regex.find(normalizedHtml) ?: return null
        return "/live-cricket-scorecard/$matchId/${match.groupValues[1]}"
    }

    private fun formatTeamScore(teamScoreObject: JSONObject): String? {
        val innings = buildList {
            val keys = teamScoreObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val inningsObject = teamScoreObject.optJSONObject(key) ?: continue
                add(key to inningsObject)
            }
        }.sortedBy { entry ->
            entry.first.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE
        }

        if (innings.isEmpty()) return null

        return innings.joinToString(" & ") { (_, inningsObject) ->
            val runs = inningsObject.optInt("runs", -1)
            val wickets = inningsObject.optInt("wickets", -1)
            if (runs < 0) {
                ""
            } else if (wickets >= 0) {
                "$runs/$wickets"
            } else {
                runs.toString()
            }
        }.ifBlank { null }
    }

    private fun extractLatestOvers(teamScoreObject: JSONObject): String? {
        val latestInnings = extractLatestInnings(teamScoreObject)
        val latestOvers = latestInnings?.overs ?: Double.NaN
        return if (latestOvers.isNaN()) null else latestOvers.toString()
    }

    private fun calculateCurrentRunRate(
        currentBatTeamId: Int,
        team1Id: Int,
        team2Id: Int,
        team1ScoreObject: JSONObject?,
        team2ScoreObject: JSONObject?
    ): String? {
        val scoreObject = when (currentBatTeamId) {
            team1Id -> team1ScoreObject
            team2Id -> team2ScoreObject
            else -> team2ScoreObject ?: team1ScoreObject
        } ?: return null

        val latestInnings = extractLatestInnings(scoreObject) ?: return null
        val balls = oversToBalls(latestInnings.overs) ?: return null
        if (balls <= 0) return null

        return formatRate(latestInnings.runs * 6.0 / balls)
    }

    private fun calculateRequiredRunRate(
        info: JSONObject,
        team1ScoreObject: JSONObject?,
        team2ScoreObject: JSONObject?
    ): String? {
        val maxOvers = when (info.optString("matchFormat").uppercase(Locale.getDefault())) {
            "T20" -> 20
            "ODI" -> 50
            "T10" -> 10
            else -> return null
        }

        val team1First = extractEarliestInnings(team1ScoreObject)
        val team2First = extractEarliestInnings(team2ScoreObject)
        val innings = listOfNotNull(team1First, team2First).sortedBy { it.inningsId }
        if (innings.size < 2) return null

        val firstInnings = innings[0]
        val secondInnings = innings[1]
        if (secondInnings.inningsId < 2) return null

        val ballsBowled = oversToBalls(secondInnings.overs) ?: return null
        val totalBalls = maxOvers * 6
        val ballsRemaining = totalBalls - ballsBowled
        if (ballsRemaining <= 0) return null

        val runsRequired = (firstInnings.runs + 1) - secondInnings.runs
        if (runsRequired <= 0) return formatRate(0.0)

        return formatRate(runsRequired * 6.0 / ballsRemaining)
    }

    private fun extractEarliestInnings(teamScoreObject: JSONObject?): ParsedInnings? {
        if (teamScoreObject == null) return null

        return buildList {
            val keys = teamScoreObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val inningsObject = teamScoreObject.optJSONObject(key) ?: continue
                add(
                    ParsedInnings(
                        inningsId = inningsObject.optInt("inningsId", Int.MAX_VALUE),
                        runs = inningsObject.optInt("runs", 0),
                        overs = inningsObject.optDouble("overs", Double.NaN)
                    )
                )
            }
        }.minByOrNull { it.inningsId }
    }

    private fun extractLatestInnings(teamScoreObject: JSONObject?): ParsedInnings? {
        if (teamScoreObject == null) return null

        return buildList {
            val keys = teamScoreObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val inningsObject = teamScoreObject.optJSONObject(key) ?: continue
                add(
                    ParsedInnings(
                        inningsId = inningsObject.optInt("inningsId", 0),
                        runs = inningsObject.optInt("runs", 0),
                        overs = inningsObject.optDouble("overs", Double.NaN)
                    )
                )
            }
        }.maxByOrNull { it.inningsId }
    }

    private fun oversToBalls(overs: Double): Int? {
        if (overs.isNaN()) return null

        val wholeOvers = overs.toInt()
        val ballsPart = ((overs - wholeOvers) * 10).toInt()
        return wholeOvers * 6 + ballsPart
    }

    private fun formatRate(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private data class ParsedInnings(
        val inningsId: Int,
        val runs: Int,
        val overs: Double
    )
}
