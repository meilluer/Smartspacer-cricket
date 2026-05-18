package com.meilluer.smartspacer_cricket

import android.content.ComponentName
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate

import android.graphics.drawable.Icon as AndroidIcon

class Target: SmartspacerTargetProvider() {
    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        if (dismiss_flag) {
            return emptyList()
        }

        val targets = mutableListOf<SmartspaceTarget>()
        targets.add(
            TargetTemplate.Basic(
                id = "notify",
                componentName = ComponentName(context!!, Target::class.java),
                title = Text("$home_team $home_score $home_overs $away_overs $away_score $away_team"),
                subtitle = Text(status),
                icon = Icon(AndroidIcon.createWithResource(context, R.drawable.cricket_icon_icons_com_53564__1_)),
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
        GlobalMatchVarsStore.setDismissFlag(context!!, true)
        notifyChange()
        return true
    }
}
