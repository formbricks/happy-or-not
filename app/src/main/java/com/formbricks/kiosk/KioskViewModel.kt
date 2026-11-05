package com.formbricks.kiosk

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.formbricks.kiosk.data.FormbricksClient
import com.formbricks.kiosk.data.KioskSettings
import com.formbricks.kiosk.data.ResponseDb
import com.formbricks.kiosk.data.SendResult
import com.formbricks.kiosk.data.SettingsStore
import com.formbricks.kiosk.data.Stats
import com.formbricks.kiosk.data.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

sealed interface Screen {
    data object Survey : Screen
    data class Reason(val responseId: Long, val score: Int) : Screen
    data class Thanks(val score: Int) : Screen
    data object Admin : Screen
}

class KioskViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val db = ResponseDb(app)

    var settings by mutableStateOf(store.load())
        private set
    var screen by mutableStateOf<Screen>(Screen.Survey)
        private set
    var showPinDialog by mutableStateOf(false)
        private set
    var stats by mutableStateOf<Stats?>(null)
        private set
    var syncStatus by mutableStateOf("")
        private set

    private var timer: Job? = null
    private var cornerTaps = 0
    private var firstCornerTapAt = 0L

    init {
        viewModelScope.launch {
            while (isActive) {
                syncNow()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    // ---- Survey flow -------------------------------------------------------

    fun onMood(score: Int) {
        // Ignore taps while the follow-up or thank-you screen is showing:
        // this is the built-in cooldown that stops one person spamming a face.
        if (screen !is Screen.Survey) return
        val id = db.insert(score, settings.question)
        val s = settings
        if (s.followUpEnabled && s.reasons.isNotEmpty()) {
            screen = Screen.Reason(id, score)
            after(REASON_TIMEOUT_MS) { showThanks(score) }
        } else {
            showThanks(score)
        }
    }

    fun onReason(reason: String) {
        val current = screen as? Screen.Reason ?: return
        db.setReason(current.responseId, reason, settings.followUpQuestion)
        showThanks(current.score)
    }

    fun skipReason() {
        val current = screen as? Screen.Reason ?: return
        showThanks(current.score)
    }

    private fun showThanks(score: Int) {
        screen = Screen.Thanks(score)
        after(THANKS_MS) { screen = Screen.Survey }
    }

    private fun after(ms: Long, block: () -> Unit) {
        timer?.cancel()
        timer = viewModelScope.launch {
            delay(ms)
            block()
        }
    }

    // ---- Admin -------------------------------------------------------------

    /** Five taps in the top-left corner within three seconds open the PIN prompt. */
    fun onCornerTap() {
        val now = SystemClock.elapsedRealtime()
        if (now - firstCornerTapAt > 3_000) {
            firstCornerTapAt = now
            cornerTaps = 0
        }
        cornerTaps++
        if (cornerTaps >= 5) {
            cornerTaps = 0
            showPinDialog = true
        }
    }

    fun dismissPin() {
        showPinDialog = false
    }

    fun tryUnlock(pin: String): Boolean {
        if (pin != settings.pin) return false
        showPinDialog = false
        timer?.cancel()
        screen = Screen.Admin
        refreshStats()
        return true
    }

    fun closeAdmin() {
        screen = Screen.Survey
    }

    fun saveSettings(s: KioskSettings) {
        store.save(s)
        settings = s
        viewModelScope.launch { syncNow() }
    }

    fun refreshStats() {
        viewModelScope.launch {
            stats = withContext(Dispatchers.IO) { db.stats() }
        }
    }

    fun clearData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.clear() }
            refreshStats()
        }
    }

    suspend fun exportCsv(): String = withContext(Dispatchers.IO) { db.toCsv() }

    fun syncFromAdmin() {
        viewModelScope.launch { syncNow(includeRecent = true) }
    }

    fun testConnection() {
        val s = settings
        syncStatus = "Testing connection…"
        viewModelScope.launch {
            syncStatus = withContext(Dispatchers.IO) { FormbricksClient.testConnection(s) }
        }
    }

    /**
     * Debug builds only: lets CI and developers configure the Formbricks connection with
     * `adb shell am start … --es fb_host … --es fb_api_key … --es fb_dataset_id …`.
     */
    fun applyDebugConfig(values: Map<String, String>) {
        if (values.isEmpty()) return
        var s = settings
        values["fb_host"]?.let { s = s.copy(formbricksHost = it) }
        values["fb_api_key"]?.let { s = s.copy(apiKey = it) }
        values["fb_dataset_id"]?.let { s = s.copy(datasetId = it) }
        values["fb_location"]?.let { s = s.copy(locationName = it) }
        values["fb_kiosk_id"]?.let { s = s.copy(kioskId = it) }
        saveSettings(s)
    }

    // ---- Formbricks sync ---------------------------------------------------

    /**
     * Pushes queued responses to Formbricks. Responses younger than the
     * follow-up timeout are held back so the reason is sent with the score.
     */
    private suspend fun syncNow(includeRecent: Boolean = false) {
        val s = settings
        if (!s.syncEnabled) {
            syncStatus = "Not connected to Formbricks yet. Responses are stored on this device only."
            return
        }
        val cutoff = if (includeRecent) Long.MAX_VALUE
        else System.currentTimeMillis() - REASON_TIMEOUT_MS - 5_000
        val (sent, error) = withContext(Dispatchers.IO) {
            var sent = 0
            var error: String? = null
            for (r in db.pending(olderThan = cutoff, limit = 100)) {
                when (val result = FormbricksClient.send(s, r, BuildConfig.VERSION_NAME)) {
                    SendResult.Ok -> {
                        db.setSyncState(r.id, SyncState.SENT)
                        sent++
                    }
                    is SendResult.Failed -> {
                        error = result.message
                        break
                    }
                }
            }
            sent to error
        }
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        syncStatus = when {
            error != null -> "Last sync $time: $sent answers sent, then failed. $error"
            sent > 0 -> "Last sync $time: $sent answers sent. Everything is sent."
            else -> "Last sync $time: everything is sent."
        }
        if (screen is Screen.Admin) refreshStats()
    }

    companion object {
        const val REASON_TIMEOUT_MS = 12_000L
        const val THANKS_MS = 3_000L
        const val SYNC_INTERVAL_MS = 30_000L
    }
}
