package com.meilluer.smartspacer_cricket

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.TapAction
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate

import android.graphics.drawable.Icon as AndroidIcon

class Target: SmartspacerTargetProvider() {
    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        val homeOversVal = home_overs.toDoubleOrNull() ?: 0.0
        val awayOversVal = away_overs.toDoubleOrNull() ?: 0.0

        var formattedHomeOvers = ""
        if(home_overs!=""){
            formattedHomeOvers="( $home_overs )"
            if(home_overs.endsWith(".6")){
                val wholeOvers = home_overs.substringBefore(".").toInt()
                formattedHomeOvers = "( ${wholeOvers + 1}.0 )"
            }
        }

        var formattedAwayOvers = ""
        if (away_overs!=""){
            formattedAwayOvers="( $away_overs )"
            if(away_overs.endsWith(".6")){
                val wholeOvers = away_overs.substringBefore(".").toInt()
                formattedAwayOvers = "( ${wholeOvers + 1}.0 )"
            }
        }

        var subtitle = status
        if (wicket_flag && wicket_info.isNotBlank()) {
            subtitle = wicket_info
        } else if (!isFinished && !second_innings && (homeOversVal >= 2.0 || awayOversVal >= 2.0)) {
            if (CRR.isNotBlank()) {
                subtitle = "CRR: $CRR"
            }
        }

        if (dismiss_flag) {
            return emptyList()
        }

        val targets = mutableListOf<SmartspaceTarget>()
        targets.add(
            TargetTemplate.Basic(
                id = "notify",
                componentName = ComponentName(context!!, Target::class.java),
                title = Text("$home_team $home_score $formattedHomeOvers  - $away_score $formattedAwayOvers $away_team"),
                subtitle = Text(subtitle),
                icon = Icon(AndroidIcon.createWithResource(context, R.drawable.cricket_icon_icons_com_53564__1_),shouldTint=false),
                onClick = TapAction(intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "https://www.google.com/search?q=" +
                                Uri.encode("$home_team vs $away_team")
                    )))
            ).create()
        )
        return targets
    }

    override fun getConfig(smartspacerId: String?): Config {
        return Config(
            label = "Smartspacer cricket",
            description = "Display scores of cricket matches from Cricbuzz.",
            icon = android.graphics.drawable.Icon.createWithResource(context, R.drawable.cricket_icon_icons_com_53564__1_),
        )
    }

    override fun onDismiss(smartspacerId: String, targetId: String): Boolean {
        GlobalMatchVarsStore.setDismissFlag(context!!, false)
        notifyChange()
        return true
    }
}
