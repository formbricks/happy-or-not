package com.formbricks.kiosk.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.formbricks.kiosk.KioskViewModel
import com.formbricks.kiosk.data.KioskSettings
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminScreen(vm: KioskViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(vm.settings) }
    var reasonsText by remember { mutableStateOf(vm.settings.reasons.joinToString("\n")) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Kiosk admin",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = vm::closeAdmin) { Text("Back to survey") }
        }

        // ---- Results ----
        Section("Results") {
            val stats = vm.stats
            if (stats == null) {
                Text("Loading…")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f)) { MoodBreakdown("Today", stats.today) }
                    Column(Modifier.weight(1f)) { MoodBreakdown("All time", stats.allTime) }
                }
                Spacer(Modifier.height(20.dp))
                Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                DayTable(stats.lastDays.map { it.day to it.counts })
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = vm::refreshStats) { Text("Refresh") }
        }

        // ---- Survey content ----
        Section("Survey") {
            Field("Question", draft.question) { draft = draft.copy(question = it) }
            Field("Thank-you message", draft.thankYou) { draft = draft.copy(thankYou = it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ask a follow-up question after each answer", modifier = Modifier.weight(1f))
                Switch(checked = draft.followUpEnabled, onCheckedChange = { draft = draft.copy(followUpEnabled = it) })
            }
            Field("Follow-up question", draft.followUpQuestion) { draft = draft.copy(followUpQuestion = it) }
            Field("Follow-up answers (one per line, up to 9)", reasonsText, singleLine = false) { reasonsText = it }
            Field("Admin PIN (4–8 digits)", draft.pin) { v -> draft = draft.copy(pin = v.filter { it.isDigit() }.take(8)) }
        }

        // ---- Formbricks ----
        Section("Send responses to Formbricks (optional)") {
            Text(
                "Create a survey in Formbricks with a 4-point rating question (and optionally a single-select " +
                    "question for the follow-up answers), then paste the IDs below. Responses are queued on this " +
                    "device and sent when online. Scores: 4 = very happy … 1 = very unhappy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Field("Formbricks URL", draft.formbricksHost) { draft = draft.copy(formbricksHost = it.trim()) }
            Field("Environment ID", draft.environmentId) { draft = draft.copy(environmentId = it.trim()) }
            Field("Survey ID", draft.surveyId) { draft = draft.copy(surveyId = it.trim()) }
            Field("Rating question ID", draft.ratingQuestionId) { draft = draft.copy(ratingQuestionId = it.trim()) }
            Field("Follow-up question ID (optional)", draft.reasonQuestionId) { draft = draft.copy(reasonQuestionId = it.trim()) }
            Text(vm.syncStatus, style = MaterialTheme.typography.bodyMedium)
            vm.stats?.let { Text("${it.pendingSync} responses waiting to be sent", style = MaterialTheme.typography.bodyMedium) }
            OutlinedButton(onClick = vm::syncFromAdmin, enabled = vm.settings.syncEnabled) { Text("Sync now") }
        }

        // ---- Actions ----
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = {
                val reasons = reasonsText.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(9)
                if (draft.pin.length < 4) {
                    toast(context, "PIN must be at least 4 digits")
                } else {
                    val saved = draft.copy(reasons = reasons)
                    vm.saveSettings(saved)
                    draft = saved
                    reasonsText = reasons.joinToString("\n")
                    toast(context, "Saved")
                }
            }) { Text("Save settings") }
            OutlinedButton(onClick = {
                scope.launch { shareCsv(context, vm.exportCsv()) }
            }) { Text("Export CSV") }
            OutlinedButton(onClick = { (context as? Activity)?.startLockTask() }) { Text("Pin app to screen") }
            OutlinedButton(onClick = {
                runCatching { (context as? Activity)?.stopLockTask() }
            }) { Text("Unpin") }
            Button(
                onClick = { confirmClear = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete all responses") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all responses?") },
            text = { Text("This removes every response stored on this device, including ones not yet sent to Formbricks.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearData()
                    confirmClear = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 900.dp),
    )
}

/** Share of "happy" + "very happy" answers. */
private fun satisfaction(counts: IntArray): String {
    val total = counts.sum()
    return if (total == 0) "–" else "${(counts[3] + counts[2]) * 100 / total}%"
}

@Composable
private fun MoodBreakdown(title: String, counts: IntArray) {
    val total = counts.sum()
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(
        "$total responses · ${satisfaction(counts)} positive",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Mood.entries.forEach { mood ->
        val n = counts[mood.score - 1]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            MoodFace(mood, Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (n > 0) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(n.toFloat() / total)
                            .background(mood.color)
                    )
                }
            }
            Text("$n", textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
        }
    }
}

@Composable
private fun DayTable(rows: List<Pair<String, IntArray>>) {
    val headerStyle = MaterialTheme.typography.labelLarge
    Row {
        Text("Day", style = headerStyle, modifier = Modifier.weight(2f))
        Mood.entries.forEach { mood ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { MoodFace(mood, Modifier.size(20.dp)) }
        }
        Text("Total", style = headerStyle, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text("Positive", style = headerStyle, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
    }
    rows.forEach { (day, counts) ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Text(day, modifier = Modifier.weight(2f))
            Mood.entries.forEach { mood ->
                Text("${counts[mood.score - 1]}", textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            Text("${counts.sum()}", textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text(satisfaction(counts), textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
        }
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun shareCsv(context: Context, csv: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
    val file = File(dir, "kiosk-responses-$stamp.csv").apply { writeText(csv) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Kiosk responses")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Export responses"))
}
