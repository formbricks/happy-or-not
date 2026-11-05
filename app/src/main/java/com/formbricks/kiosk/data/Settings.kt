package com.formbricks.kiosk.data

import android.content.Context
import java.util.UUID

/** Everything an operator can change from the admin screen. */
data class KioskSettings(
    val question: String = "How satisfied are you with our service today?",
    val thankYou: String = "Thank you for your feedback!",
    val followUpEnabled: Boolean = true,
    val followUpQuestion: String = "What was the main reason?",
    val reasons: List<String> = listOf(
        "Friendly staff",
        "Waiting time",
        "Cleanliness",
        "Product selection",
        "Price",
        "Something else",
    ),
    val pin: String = "1234",
    // Where the feedback goes: a Feedback Dataset in Formbricks (Cloud or self-hosted).
    val formbricksHost: String = "https://app.formbricks.com",
    val apiKey: String = "",
    val datasetId: String = "",
    // Which kiosk this is. Sent with every record so dashboards can be split by location.
    val locationName: String = "",
    val kioskId: String = "",
) {
    val syncEnabled: Boolean
        get() = formbricksHost.isNotBlank() && apiKey.isNotBlank() && datasetId.isNotBlank()
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE)

    fun load(): KioskSettings {
        val d = KioskSettings()
        fun str(key: String, default: String) = prefs.getString(key, default) ?: default
        return KioskSettings(
            question = str("question", d.question),
            thankYou = str("thankYou", d.thankYou),
            followUpEnabled = prefs.getBoolean("followUpEnabled", d.followUpEnabled),
            followUpQuestion = str("followUpQuestion", d.followUpQuestion),
            reasons = prefs.getString("reasons", null)
                ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: d.reasons,
            pin = str("pin", d.pin),
            formbricksHost = str("formbricksHost", d.formbricksHost),
            apiKey = str("apiKey", d.apiKey),
            datasetId = str("datasetId", d.datasetId),
            locationName = str("locationName", d.locationName),
            kioskId = str("kioskId", "").ifBlank { defaultKioskId() },
        )
    }

    fun save(s: KioskSettings) {
        prefs.edit()
            .putString("question", s.question)
            .putString("thankYou", s.thankYou)
            .putBoolean("followUpEnabled", s.followUpEnabled)
            .putString("followUpQuestion", s.followUpQuestion)
            .putString("reasons", s.reasons.joinToString("\n"))
            .putString("pin", s.pin)
            .putString("formbricksHost", s.formbricksHost)
            .putString("apiKey", s.apiKey)
            .putString("datasetId", s.datasetId)
            .putString("locationName", s.locationName)
            .putString("kioskId", s.kioskId.ifBlank { defaultKioskId() })
            .apply()
    }

    /** A random id created on first launch, so every kiosk is distinguishable out of the box. */
    private fun defaultKioskId(): String {
        val existing = prefs.getString("generatedKioskId", null)
        if (existing != null) return existing
        val id = "kiosk-" + UUID.randomUUID().toString().take(8)
        prefs.edit().putString("generatedKioskId", id).apply()
        return id
    }
}
