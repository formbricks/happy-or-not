package com.formbricks.kiosk.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface SendResult {
    data object Ok : SendResult
    /**
     * Not delivered. The response stays queued and is retried later, also on 4xx:
     * every response has the same shape, so a 4xx almost always means a settings
     * mistake (wrong environment, survey or question ID) that the operator fixes.
     */
    data class Failed(val message: String) : SendResult
}

/**
 * Sends kiosk responses to a Formbricks survey through the public Client API
 * (`POST /api/v1/client/{environmentId}/responses`). No API key is needed:
 * this is the same endpoint the Formbricks link and in-app surveys use.
 */
object FormbricksClient {

    fun send(s: KioskSettings, r: StoredResponse): SendResult {
        return try {
            val base = s.formbricksHost.trim().trimEnd('/')
            val url = URL("$base/api/v1/client/${s.environmentId.trim()}/responses")

            val data = JSONObject().put(s.ratingQuestionId.trim(), r.score)
            if (!r.reason.isNullOrBlank() && s.reasonQuestionId.isNotBlank()) {
                data.put(s.reasonQuestionId.trim(), r.reason)
            }
            val body = JSONObject()
                .put("surveyId", s.surveyId.trim())
                .put("finished", true)
                .put("data", data)
                .toString()

            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code in 200..299) {
                    SendResult.Ok
                } else {
                    val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(300).orEmpty()
                    SendResult.Failed("HTTP $code $detail".trim())
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            SendResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
