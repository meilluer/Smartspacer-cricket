package com.meilluer.smartspacer_cricket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CricketAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        GlobalMatchVarsStore.hydrate(context)
        
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CricketScheduler.initialize(context)
            return
        }

        if (intent.action == CricketScheduler.ACTION_SET_DISMISS_FLAG) {
            GlobalMatchVarsStore.setDismissFlag(context, true)
            return
        }

        if (intent.action == CricketScheduler.ACTION_RESET_WICKET_FLAG) {
            GlobalMatchVarsStore.setWicketFlag(context, false)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scraper = CricbuzzScraper()
                val result = scraper.fetchLiveMatches()
                val favoriteTeams = AppSettings.getFavoriteTeams(context)
                if (intent.action == CricketScheduler.ACTION_PREMATCH_CHECK) {
                    GlobalMatchVarsStore.setDismissFlag(context, false)
                    CricketScheduler.cancelDismissCountdown(context)
                }
                val matchesToUse = if (result.matches.isNotEmpty()) {
                    TrackedMatchEnricher.enrich(result.matches, favoriteTeams, scraper).also {
                        MatchCache.save(context, it)
                    }
                } else {
                    MatchCache.load(context)
                }
                GlobalMatchVarsStore.update(
                    context = context,
                    match = TrackedMatchSelector.selectPrimaryMatch(matchesToUse, favoriteTeams),
                    favoriteTeams = favoriteTeams
                )
                CricketScheduler.rebuildSchedules(
                    context = context,
                    matches = matchesToUse,
                    favoriteTeams = favoriteTeams,
                    intervalMinutes = AppSettings.getRefreshIntervalMinutes(context)
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
