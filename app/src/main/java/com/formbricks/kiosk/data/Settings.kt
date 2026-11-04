package com.formbricks.kiosk.data

import android.content.Context

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
    // Optional sync to a Formbricks survey (Formbricks Cloud or self-hosted).
    val formbricksHost: String = "https://app.formbricks.com",
    val environmentId: String = "",
    val surveyId: String = "",
    val ratingQuestionId: String = "",
    val reasonQuestionId: String = "",
) {
    val syncEnabled: Boolean
        get() = environmentId.isNotBlank() && surveyId.isNotBlank() && ratingQuestionId.isNotBlank()
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
            environmentId = str("environmentId", d.environmentId),
            surveyId = str("surveyId", d.surveyId),
            ratingQuestionId = str("ratingQuestionId", d.ratingQuestionId),
            reasonQuestionId = str("reasonQuestionId", d.reasonQuestionId),
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
            .putString("environmentId", s.environmentId)
            .putString("surveyId", s.surveyId)
            .putString("ratingQuestionId", s.ratingQuestionId)
            .putString("reasonQuestionId", s.reasonQuestionId)
            .apply()
    }
}
