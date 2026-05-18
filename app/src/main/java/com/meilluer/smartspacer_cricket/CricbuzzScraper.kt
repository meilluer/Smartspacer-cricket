package com.meilluer.smartspacer_cricket

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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
            parseMatch(matchObject)?.let(matches::add)
        }

        return matches
    }

    private fun parseMatch(matchObject: JSONObject): MatchInfo? {
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

        return MatchInfo(
            matchId = matchId,
            team1 = team1,
            team2 = team2,
            team1Score = team1Score,
            team2Score = team2Score,
            overs = activeOvers,
            runRate = runRate,
            status = status,
            matchDetails = matchDetails,
            matchState = matchState,
            startTimeMillis = startTimeMillis
        )
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
        val innings = buildList {
            val keys = teamScoreObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val inningsObject = teamScoreObject.optJSONObject(key) ?: continue
                val order = key.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE
                add(order to inningsObject)
            }
        }.sortedBy { it.first }

        val latestOvers = innings.lastOrNull()?.second?.optDouble("overs", Double.NaN) ?: Double.NaN
        return if (latestOvers.isNaN()) null else latestOvers.toString()
    }
}
