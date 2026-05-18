package com.meilluer.smartspacer_cricket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CricketAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CricketScheduler.initialize(context)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scraper = CricbuzzScraper()
                val result = scraper.fetchLiveMatches()
                if (result.matches.isNotEmpty()) {
                    MatchCache.save(context, result.matches)
                }
                CricketScheduler.rebuildSchedules(
                    context = context,
                    matches = if (result.matches.isNotEmpty()) result.matches else MatchCache.load(context),
                    favoriteTeams = AppSettings.getFavoriteTeams(context),
                    intervalMinutes = AppSettings.getRefreshIntervalMinutes(context)
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
