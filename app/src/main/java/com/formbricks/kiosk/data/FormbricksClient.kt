package com.formbricks.kiosk.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed interface SendResult {
    data object Ok : SendResult

    /**
     * Not delivered. The response stays queued and is retried later. This includes 4xx answers:
     * every response has the same shape, so a 4xx almost always means a settings mistake
     * (wrong API key or dataset ID) that the operator fixes from the admin screen.
     */
    data class Failed(val message: String) : SendResult
}

/**
 * Writes kiosk responses into a Formbricks Feedback Dataset through the Feedback Records API
 * (`POST /api/v3/feedbackRecords`).
 *
 * Each response becomes one submission with up to two records:
 *  - `satisfaction`: field type `rating`, value 1–4 (4 = very happy)
 *  - `reason`: field type `categorical`, the follow-up answer (only when one was picked)
 *
 * The submission id is derived from the kiosk id and the local response id, so a retry after a
 * partial failure is safe: Formbricks answers 409 for a record it already has, which counts as sent.
 */
object FormbricksClient {

    const val SOURCE_TYPE = "kiosk"
    const val FIELD_SATISFACTION = "satisfaction"
    const val FIELD_REASON = "reason"

    fun buildRecords(s: KioskSettings, r: StoredResponse, appVersion: String): List<JSONObject> {
        val submissionId = "${s.kioskId}-${r.id}"
        val collectedAt = iso8601(r.timestamp)
        val language = Locale.getDefault().language.take(10)
        val metadata = JSONObject()
            .put("kiosk_id", s.kioskId)
            .put("location", s.locationName.ifBlank { s.kioskId })
            .put("mood", scoreLabel(r.score))
            .put("scale_max", 4)
            .put("app", "formbricks-kiosk")
            .put("app_version", appVersion)

        fun base(fieldId: String, fieldType: String, label: String?) = JSONObject()
            .put("tenant_id", s.datasetId.trim())
            .put("source_type", SOURCE_TYPE)
            .put("source_id", s.kioskId)
            .put("source_name", s.locationName.ifBlank { s.kioskId })
            .put("submission_id", submissionId)
            .put("field_id", fieldId)
            .put("field_type", fieldType)
            .put("field_label", label ?: JSONObject.NULL)
            .put("collected_at", collectedAt)
            .put("language", language)
            .put("metadata", metadata)

        val records = mutableListOf(
            base(FIELD_SATISFACTION, "rating", r.question).put("value_number", r.score)
        )
        if (!r.reason.isNullOrBlank()) {
            records += base(FIELD_REASON, "categorical", r.followUpQuestion)
                .put("value_text", r.reason)
                .put("value_id", slug(r.reason))
        }
        return records
    }

    fun send(s: KioskSettings, r: StoredResponse, appVersion: String): SendResult {
        for (record in buildRecords(s, r, appVersion)) {
            val result = request(s, "POST", endpoint(s), record.toString())
            // 409: this record was already stored by an earlier attempt.
            if (result.code !in 200..299 && result.code != 409) {
                return SendResult.Failed(describe(result))
            }
        }
        return SendResult.Ok
    }

    /** Checks URL, API key and dataset access without writing anything. */
    fun testConnection(s: KioskSettings): String {
        if (!s.syncEnabled) return "Enter the Formbricks URL, API key and dataset ID first."
        val url = endpoint(s) + "?tenant_id=" + URLEncoder.encode(s.datasetId.trim(), "UTF-8") + "&limit=1"
        val result = request(s, "GET", url, null)
        return if (result.code in 200..299) "Connected: this kiosk can write to the dataset."
        else describe(result)
    }

    private data class HttpResult(val code: Int, val body: String, val error: String? = null)

    private fun endpoint(s: KioskSettings) = s.formbricksHost.trim().trimEnd('/') + "/api/v3/feedbackRecords"

    private fun request(s: KioskSettings, method: String, url: String, body: String?): HttpResult {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("x-api-key", s.apiKey.trim())
                conn.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                HttpResult(code, text)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            HttpResult(-1, "", e.message ?: e.javaClass.simpleName)
        }
    }

    private fun describe(r: HttpResult): String {
        if (r.code == -1) return "Can't reach Formbricks: ${r.error}"
        val detail = runCatching {
            val json = JSONObject(r.body)
            json.optString("detail").ifBlank { json.optString("title") }.ifBlank { json.optString("message") }
        }.getOrNull().orEmpty().ifBlank { r.body.take(200) }
        val hint = when (r.code) {
            401 -> "The API key is not valid."
            403 -> "The API key has no access to this dataset. Check the dataset ID, and that the key has " +
                "write access to a workspace the dataset is assigned to."
            404 -> "This Formbricks instance doesn't offer the Feedback Records API. Check the URL."
            else -> "Formbricks answered HTTP ${r.code}."
        }
        return if (detail.isBlank()) hint else "$hint ($detail)"
    }

    private fun iso8601(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ms))

    private fun slug(v: String): String =
        v.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_').take(255).ifBlank { "option" }
}
