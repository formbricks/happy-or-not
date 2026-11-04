package com.formbricks.kiosk.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Score scale: 4 = very happy, 3 = happy, 2 = unhappy, 1 = very unhappy. */
fun scoreLabel(score: Int): String = when (score) {
    4 -> "Very happy"
    3 -> "Happy"
    2 -> "Unhappy"
    1 -> "Very unhappy"
    else -> "Unknown"
}

/** Sync states stored in the `synced` column. */
object SyncState {
    const val PENDING = 0
    const val SENT = 1
}

data class StoredResponse(
    val id: Long,
    val timestamp: Long,
    val score: Int,
    val reason: String?,
    val syncState: Int,
)

/** Counts are indexed by score - 1, so counts[3] is "very happy". */
data class DayCounts(val day: String, val counts: IntArray)

data class Stats(
    val today: IntArray,
    val allTime: IntArray,
    val lastDays: List<DayCounts>,
    val pendingSync: Int,
)

class ResponseDb(context: Context) : SQLiteOpenHelper(context, "responses.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE responses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                score INTEGER NOT NULL,
                reason TEXT,
                synced INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_responses_ts ON responses(ts)")
        db.execSQL("CREATE INDEX idx_responses_synced ON responses(synced)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(score: Int, timestamp: Long = System.currentTimeMillis()): Long {
        val values = ContentValues().apply {
            put("ts", timestamp)
            put("score", score)
        }
        return writableDatabase.insert("responses", null, values)
    }

    fun setReason(id: Long, reason: String) {
        val values = ContentValues().apply { put("reason", reason) }
        writableDatabase.update("responses", values, "id = ?", arrayOf(id.toString()))
    }

    fun setSyncState(id: Long, state: Int) {
        val values = ContentValues().apply { put("synced", state) }
        writableDatabase.update("responses", values, "id = ?", arrayOf(id.toString()))
    }

    /** Pending responses created before [olderThan], oldest first. */
    fun pending(olderThan: Long, limit: Int): List<StoredResponse> = query(
        "SELECT id, ts, score, reason, synced FROM responses WHERE synced = ${SyncState.PENDING} " +
            "AND ts < ? ORDER BY ts LIMIT $limit",
        arrayOf(olderThan.toString()),
    )

    fun all(): List<StoredResponse> =
        query("SELECT id, ts, score, reason, synced FROM responses ORDER BY ts", emptyArray())

    fun clear() {
        writableDatabase.delete("responses", null, null)
    }

    fun stats(days: Int = 7): Stats {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayMs = startOfToday.timeInMillis
        val since = (startOfToday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(days - 1)) }.timeInMillis

        val today = IntArray(4)
        val allTime = IntArray(4)
        readableDatabase.rawQuery(
            "SELECT score, COUNT(*), SUM(CASE WHEN ts >= ? THEN 1 ELSE 0 END) FROM responses GROUP BY score",
            arrayOf(todayMs.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val i = c.getInt(0) - 1
                if (i in 0..3) {
                    allTime[i] = c.getInt(1)
                    today[i] = c.getInt(2)
                }
            }
        }

        val byDay = linkedMapOf<String, IntArray>()
        val fmt = SimpleDateFormat("EEE dd MMM", Locale.getDefault())
        val cal = Calendar.getInstance().apply { timeInMillis = todayMs }
        repeat(days) {
            byDay[fmt.format(cal.time)] = IntArray(4)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        readableDatabase.rawQuery(
            "SELECT ts, score FROM responses WHERE ts >= ?",
            arrayOf(since.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val key = fmt.format(Date(c.getLong(0)))
                val i = c.getInt(1) - 1
                if (i in 0..3) byDay[key]?.let { it[i]++ }
            }
        }

        val pendingCount = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM responses WHERE synced = ${SyncState.PENDING}",
            emptyArray(),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

        return Stats(today, allTime, byDay.map { DayCounts(it.key, it.value) }, pendingCount)
    }

    fun toCsv(): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val sb = StringBuilder("id,timestamp,score,label,reason,sync_state\n")
        for (r in all()) {
            val sync = if (r.syncState == SyncState.SENT) "sent" else "pending"
            sb.append(r.id).append(',')
                .append(iso.format(Date(r.timestamp))).append(',')
                .append(r.score).append(',')
                .append(csvEscape(scoreLabel(r.score))).append(',')
                .append(csvEscape(r.reason ?: "")).append(',')
                .append(sync).append('\n')
        }
        return sb.toString()
    }

    private fun csvEscape(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun query(sql: String, args: Array<String>): List<StoredResponse> =
        readableDatabase.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        StoredResponse(
                            id = c.getLong(0),
                            timestamp = c.getLong(1),
                            score = c.getInt(2),
                            reason = if (c.isNull(3)) null else c.getString(3),
                            syncState = c.getInt(4),
                        )
                    )
                }
            }
        }
}
