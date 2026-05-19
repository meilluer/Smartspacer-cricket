package com.meilluer.smartspacer_cricket

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MatchAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var fabFavorites: FloatingActionButton
    private lateinit var tvLastUpdated: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var toolbar: Toolbar
    
    private val scraper = CricbuzzScraper()
    private var allMatches = listOf<MatchInfo>()
    private var favoriteTeams = mutableSetOf<String>()
    
    private var refreshIntervalMinutes = 5
    private var refreshJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadSettings()
        GlobalMatchVarsStore.hydrate(this)
        setupUI()
        allMatches = MatchCache.load(this)
        syncGlobalMatchVars()
        updateDisplay("Fetching Cricbuzz scores...")
        maybeRequestExactAlarmPermission()
        CricketScheduler.initialize(this, allMatches)
        startPeriodicRefresh()
    }

    private fun setupUI() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        fabFavorites = findViewById(R.id.fabFavorites)
        tvLastUpdated = findViewById(R.id.tvLastUpdated)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        adapter = MatchAdapter(listOf())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            refreshMatches()
        }

        fabFavorites.setOnClickListener {
            showFavoriteSelectionDialog()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showIntervalSelectionDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (true) {
                refreshMatches()
                delay(refreshIntervalMinutes * 60 * 1000L)
            }
        }
    }

    private fun refreshMatches() {
        if (!isNetworkAvailable()) {
            allMatches = emptyList()
            updateDisplay("No internet connection. Connect to the internet and pull to refresh.")
            updateTimestamp()
            swipeRefresh.isRefreshing = false
            return
        }

        swipeRefresh.setRefreshing(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                scraper.fetchLiveMatches()
            }
            allMatches = result.matches
            val matchesForScheduling = if (result.matches.isNotEmpty()) {
                TrackedMatchEnricher.enrich(result.matches, favoriteTeams, scraper).also {
                    MatchCache.save(this@MainActivity, it)
                }
            } else {
                MatchCache.load(this@MainActivity)
            }
            allMatches = matchesForScheduling
            syncGlobalMatchVars(matchesForScheduling)
            CricketScheduler.rebuildSchedules(
                context = this@MainActivity,
                matches = matchesForScheduling,
                favoriteTeams = favoriteTeams,
                intervalMinutes = refreshIntervalMinutes
            )
            updateDisplay(result.errorMessage)
            updateTimestamp()
            swipeRefresh.setRefreshing(false)
        }
    }

    private fun updateTimestamp() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentTime = sdf.format(Date())
        tvLastUpdated.text = "Last Updated: $currentTime (Every $refreshIntervalMinutes min)"
    }

    private fun updateDisplay(message: String? = null) {
        val sortedMatches = allMatches.sortedByDescending { match ->
            favoriteTeams.contains(match.team1) || favoriteTeams.contains(match.team2)
        }
        adapter.updateMatches(sortedMatches)

        if (sortedMatches.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
            tvEmptyState.text = message ?: "No Cricbuzz matches are available right now."
        } else {
            recyclerView.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE
        }
    }

    private fun showIntervalSelectionDialog() {
        val intervals = arrayOf("5 Minutes", "6 Minutes", "7 Minutes", "8 Minutes", "9 Minutes", "10 Minutes")
        val intervalValues = intArrayOf(5, 6, 7, 8, 9, 10)
        var selectedIndex = intervalValues.indexOf(refreshIntervalMinutes)
        if (selectedIndex == -1) selectedIndex = 0

        AlertDialog.Builder(this)
            .setTitle("Select Refresh Interval")
            .setSingleChoiceItems(intervals, selectedIndex) { dialog, which ->
                refreshIntervalMinutes = intervalValues[which]
                saveSettings()
                startPeriodicRefresh()
                updateTimestamp()
                dialog.dismiss()
                Toast.makeText(this, "Refresh interval set to $refreshIntervalMinutes minutes", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFavoriteSelectionDialog() {
        val teams = allMatches.flatMap { listOf(it.team1, it.team2) }.distinct().sorted()
        if (teams.isEmpty()) {
            Toast.makeText(this, "No matches found. Try refreshing.", Toast.LENGTH_SHORT).show()
            return
        }

        val teamArray = teams.toTypedArray()
        val checkedItems = BooleanArray(teamArray.size) { index ->
            favoriteTeams.contains(teamArray[index])
        }

        AlertDialog.Builder(this)
            .setTitle("Select Favorite Teams")
            .setMultiChoiceItems(teamArray, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    favoriteTeams.add(teamArray[which])
                } else {
                    favoriteTeams.remove(teamArray[which])
                }
            }
            .setPositiveButton("OK") { _, _ ->
                saveSettings()
                syncGlobalMatchVars()
                updateDisplay()
                CricketScheduler.initialize(this, allMatches)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveSettings() {
        AppSettings.setFavoriteTeams(this, favoriteTeams)
        AppSettings.setRefreshIntervalMinutes(this, refreshIntervalMinutes)
    }

    private fun loadSettings() {
        favoriteTeams = AppSettings.getFavoriteTeams(this).toMutableSet()
        refreshIntervalMinutes = AppSettings.getRefreshIntervalMinutes(this)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun maybeRequestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val alarmManager = getSystemService(AlarmManager::class.java)
        if (alarmManager.canScheduleExactAlarms()) return

        AlertDialog.Builder(this)
            .setTitle("Allow exact alarms")
            .setMessage("Enable exact alarms so the app can check Cricbuzz at 8:00 AM, 10 minutes before followed matches, and at your chosen live refresh interval.")
            .setPositiveButton("Allow") { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun syncGlobalMatchVars(matches: List<MatchInfo> = allMatches) {
        val selectedMatch = TrackedMatchSelector.selectPrimaryMatch(matches, favoriteTeams)
        GlobalMatchVarsStore.update(this, selectedMatch, favoriteTeams)
    }
}
