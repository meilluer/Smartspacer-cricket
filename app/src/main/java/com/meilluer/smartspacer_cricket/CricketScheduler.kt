package com.meilluer.smartspacer_cricket

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object CricketScheduler {
    const val ACTION_DAILY_CHECK = "com.meilluer.smartspacer_cricket.action.DAILY_CHECK"
    const val ACTION_LIVE_POLL = "com.meilluer.smartspacer_cricket.action.LIVE_POLL"

    private const val REQUEST_CODE_DAILY = 1001
    private const val REQUEST_CODE_LIVE = 1002
    private const val PREMATCH_REQUEST_CODE_OFFSET = 200000
    private const val TEN_MINUTES_MS = 10 * 60 * 1000L

    fun initialize(context: Context, matches: List<MatchInfo> = MatchCache.load(context)) {
        scheduleDailyCheck(context)
        rebuildSchedules(
            context = context,
            matches = matches,
            favoriteTeams = AppSettings.getFavoriteTeams(context),
            intervalMinutes = AppSettings.getRefreshIntervalMinutes(context)
        )
    }

    fun rebuildSchedules(
        context: Context,
        matches: List<MatchInfo>,
        favoriteTeams: Set<String>,
        intervalMinutes: Int
    ) {
        scheduleDailyCheck(context)
        schedulePreMatchChecks(context, matches, favoriteTeams)
        scheduleLivePollingIfNeeded(context, matches, favoriteTeams, intervalMinutes)
    }

    fun scheduleDailyCheck(context: Context) {
        val now = System.currentTimeMillis()
        val nextEightAm = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        scheduleExactAlarm(
            context = context,
            triggerAtMillis = nextEightAm,
            requestCode = REQUEST_CODE_DAILY,
            action = ACTION_DAILY_CHECK
        )
    }

    private fun schedulePreMatchChecks(
        context: Context,
        matches: List<MatchInfo>,
        favoriteTeams: Set<String>
    ) {
        cancelPreMatchChecks(context)
        if (favoriteTeams.isEmpty()) return

        val now = System.currentTimeMillis()
        val endOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val scheduledIds = mutableSetOf<String>()
        matches
            .filter { involvesFavoriteTeam(it, favoriteTeams) }
            .filter { !hasStarted(it, now) }
            .filter { !isFinished(it) }
            .forEach { match ->
                val startTime = match.startTimeMillis ?: return@forEach
                if (startTime > endOfDay) return@forEach

                val preMatchTime = startTime - TEN_MINUTES_MS
                if (preMatchTime <= now) return@forEach

                scheduleExactAlarm(
                    context = context,
                    triggerAtMillis = preMatchTime,
                    requestCode = preMatchRequestCode(match.matchId),
                    action = ACTION_DAILY_CHECK
                )
                scheduledIds.add(match.matchId.toString())
            }

        AppSettings.setScheduledPreMatchIds(context, scheduledIds)
    }

    private fun scheduleLivePollingIfNeeded(
        context: Context,
        matches: List<MatchInfo>,
        favoriteTeams: Set<String>,
        intervalMinutes: Int
    ) {
        cancelLivePolling(context)
        if (favoriteTeams.isEmpty()) return

        val now = System.currentTimeMillis()
        val relevantFavoriteMatches = matches.filter { involvesFavoriteTeam(it, favoriteTeams) }

        if (relevantFavoriteMatches.any { hasStarted(it, now) && !isFinished(it) }) {
            scheduleExactAlarm(
                context = context,
                triggerAtMillis = now + intervalMinutes.coerceAtLeast(1) * 60 * 1000L,
                requestCode = REQUEST_CODE_LIVE,
                action = ACTION_LIVE_POLL
            )
            return
        }

        val nextFavoriteMatch = relevantFavoriteMatches
            .mapNotNull { match ->
                val startTime = match.startTimeMillis ?: return@mapNotNull null
                if (startTime > now && !isFinished(match)) startTime else null
            }
            .minOrNull()

        if (nextFavoriteMatch != null && nextFavoriteMatch - now <= TEN_MINUTES_MS) {
            scheduleExactAlarm(
                context = context,
                triggerAtMillis = nextFavoriteMatch,
                requestCode = REQUEST_CODE_LIVE,
                action = ACTION_LIVE_POLL
            )
        }
    }

    private fun cancelPreMatchChecks(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        AppSettings.getScheduledPreMatchIds(context).forEach { matchId ->
            val pendingIntent = buildPendingIntent(
                context = context,
                requestCode = preMatchRequestCode(matchId.toLongOrNull() ?: return@forEach),
                action = ACTION_DAILY_CHECK
            )
            alarmManager.cancel(pendingIntent)
        }
        AppSettings.setScheduledPreMatchIds(context, emptySet())
    }

    private fun cancelLivePolling(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(
            buildPendingIntent(
                context = context,
                requestCode = REQUEST_CODE_LIVE,
                action = ACTION_LIVE_POLL
            )
        )
    }

    private fun scheduleExactAlarm(
        context: Context,
        triggerAtMillis: Long,
        requestCode: Int,
        action: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, requestCode, action)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    private fun buildPendingIntent(
        context: Context,
        requestCode: Int,
        action: String
    ): PendingIntent {
        val intent = Intent(context, CricketAlarmReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun preMatchRequestCode(matchId: Long): Int {
        val boundedId = (matchId % 100000).toInt()
        return PREMATCH_REQUEST_CODE_OFFSET + boundedId
    }

    private fun involvesFavoriteTeam(match: MatchInfo, favoriteTeams: Set<String>): Boolean =
        favoriteTeams.contains(match.team1) || favoriteTeams.contains(match.team2)

    private fun hasStarted(match: MatchInfo, now: Long): Boolean =
        (match.startTimeMillis ?: Long.MAX_VALUE) <= now

    private fun isFinished(match: MatchInfo): Boolean {
        val state = match.matchState.orEmpty().lowercase()
        return state.contains("complete") || state.contains("stumps")
    }
}
