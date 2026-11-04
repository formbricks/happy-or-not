package com.formbricks.kiosk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.formbricks.kiosk.KioskViewModel
import com.formbricks.kiosk.Screen
import com.formbricks.kiosk.data.KioskSettings
import kotlinx.coroutines.delay

@Composable
fun KioskApp(vm: KioskViewModel) {
    val screen = vm.screen

    // Swallow the back gesture on the public screens; in admin it returns to the survey.
    BackHandler {
        if (screen is Screen.Admin) vm.closeAdmin()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Crossfade(targetState = screen, label = "screen") { s ->
            when (s) {
                Screen.Survey -> SurveyScreen(vm.settings, vm::onMood)
                is Screen.Reason -> ReasonScreen(vm.settings, Mood.of(s.score), vm::onReason, vm::skipReason)
                is Screen.Thanks -> ThanksScreen(vm.settings, Mood.of(s.score))
                Screen.Admin -> AdminScreen(vm)
            }
        }

        if (screen !is Screen.Admin) {
            Text(
                "Powered by Formbricks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
            // Hidden admin entry: five quick taps in the top-left corner.
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .size(96.dp)
                    .semantics { contentDescription = "Admin corner" }
                    .pointerInput(Unit) { detectTapGestures { vm.onCornerTap() } }
            )
        }

        if (vm.showPinDialog) {
            PinDialog(onDismiss = vm::dismissPin, onSubmit = vm::tryUnlock)
        }
    }
}

@Composable
private fun SurveyScreen(settings: KioskSettings, onMood: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            settings.question,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.widthIn(max = 900.dp),
        )
        Spacer(Modifier.height(48.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            val gap = 28.dp
            val byWidth = (maxWidth - gap * 3) / 4
            val byHeight = if (maxHeight.value.isFinite()) maxHeight * 0.9f else byWidth
            val faceSize = min(min(byWidth, byHeight), 220.dp)
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                Mood.entries.forEach { mood ->
                    MoodButton(mood, faceSize, onClick = { onMood(mood.score) })
                }
            }
        }
    }
}

@Composable
private fun ReasonScreen(
    settings: KioskSettings,
    mood: Mood,
    onReason: (String) -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MoodFace(mood, Modifier.size(88.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            settings.followUpQuestion,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Column(
            Modifier.widthIn(max = 900.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            settings.reasons.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { reason ->
                        OutlinedButton(
                            onClick = { onReason(reason) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(reason, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        }
                    }
                    // Keep buttons in a short last row the same width as the others.
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onSkip) {
            Text("Skip", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ThanksScreen(settings: KioskSettings, mood: Mood) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MoodFace(mood, Modifier.size(140.dp))
        Spacer(Modifier.height(32.dp))
        Text(
            settings.thankYou,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onSubmit: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun submit() {
        if (!onSubmit(pin)) {
            wrong = true
            pin = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v ->
                        pin = v.filter { it.isDigit() }.take(8)
                        wrong = false
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    isError = wrong,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.focusRequester(focus),
                )
                if (wrong) {
                    Text("Wrong PIN", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                LaunchedEffect(Unit) {
                    delay(150)
                    runCatching { focus.requestFocus() }
                }
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("Unlock") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
