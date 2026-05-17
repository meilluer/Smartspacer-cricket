package com.meilluer.smartspacer_cricket

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MatchAdapter(private var matches: List<MatchInfo>) : RecyclerView.Adapter<MatchAdapter.MatchViewHolder>() {

    class MatchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTeam1: TextView = view.findViewById(R.id.tvTeam1)
        val tvTeam1Score: TextView = view.findViewById(R.id.tvTeam1Score)
        val tvTeam2: TextView = view.findViewById(R.id.tvTeam2)
        val tvTeam2Score: TextView = view.findViewById(R.id.tvTeam2Score)
        val tvOvers: TextView = view.findViewById(R.id.tvOvers)
        val tvRunRate: TextView = view.findViewById(R.id.tvRunRate)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.match_item, parent, false)
        return MatchViewHolder(view)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        val match = matches[position]
        holder.tvTeam1.text = match.team1
        holder.tvTeam1Score.text = match.team1Score ?: ""
        holder.tvTeam2.text = match.team2
        holder.tvTeam2Score.text = match.team2Score ?: ""
        holder.tvOvers.text = if (match.overs != null) "Overs: ${match.overs}" else ""
        holder.tvRunRate.text = match.runRate ?: ""
        holder.tvStatus.text = match.status ?: ""
    }

    override fun getItemCount() = matches.size

    fun updateMatches(newMatches: List<MatchInfo>) {
        matches = newMatches
        notifyDataSetChanged()
    }
}
