package com.formbricks.kiosk.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/** The four answers, in the order they appear on screen (left to right). */
enum class Mood(val score: Int, val label: String, val color: Color) {
    VeryHappy(4, "Very happy", Color(0xFF16A34A)),
    Happy(3, "Happy", Color(0xFF84CC16)),
    Unhappy(2, "Unhappy", Color(0xFFF87171)),
    VeryUnhappy(1, "Very unhappy", Color(0xFFDC2626));

    companion object {
        fun of(score: Int): Mood = entries.firstOrNull { it.score == score } ?: Happy
    }
}

private val Ink = Color(0xFF1F2937)

/** A smiley drawn with Canvas, so it stays sharp at any kiosk screen size. */
@Composable
fun MoodFace(mood: Mood, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center

        drawCircle(mood.color, r, c)
        drawCircle(Color.White.copy(alpha = 0.22f), r * 0.9f, c, style = Stroke(width = r * 0.035f))

        // Eyes
        val eyeY = c.y - r * 0.22f
        val eyeDx = r * 0.32f
        val eyeR = r * 0.095f
        drawCircle(Ink, eyeR, Offset(c.x - eyeDx, eyeY))
        drawCircle(Ink, eyeR, Offset(c.x + eyeDx, eyeY))

        val stroke = Stroke(width = r * 0.09f, cap = StrokeCap.Round)

        // Angled brows for the strongest negative answer
        if (mood == Mood.VeryUnhappy) {
            val browY = eyeY - r * 0.2f
            drawLine(Ink, Offset(c.x - eyeDx - r * 0.14f, browY - r * 0.07f), Offset(c.x - eyeDx + r * 0.14f, browY + r * 0.05f), stroke.width * 0.8f, StrokeCap.Round)
            drawLine(Ink, Offset(c.x + eyeDx + r * 0.14f, browY - r * 0.07f), Offset(c.x + eyeDx - r * 0.14f, browY + r * 0.05f), stroke.width * 0.8f, StrokeCap.Round)
        }

        // Mouth: a quadratic curve; control point below the ends smiles, above frowns.
        val (halfWidth, baseY, bend) = when (mood) {
            Mood.VeryHappy -> Triple(0.44f, 0.18f, 0.62f)
            Mood.Happy -> Triple(0.38f, 0.26f, 0.32f)
            Mood.Unhappy -> Triple(0.34f, 0.46f, -0.24f)
            Mood.VeryUnhappy -> Triple(0.4f, 0.54f, -0.5f)
        }
        val left = Offset(c.x - r * halfWidth, c.y + r * baseY)
        val right = Offset(c.x + r * halfWidth, c.y + r * baseY)
        val path = Path().apply {
            moveTo(left.x, left.y)
            quadraticTo(c.x, left.y + r * bend, right.x, right.y)
        }
        if (mood == Mood.VeryHappy) {
            path.close() // open, laughing mouth
            drawPath(path, Ink)
        } else {
            drawPath(path, Ink, style = stroke)
        }
    }
}

@Composable
fun MoodButton(mood: Mood, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")
    MoodFace(
        mood,
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(size / 16, CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = mood.label },
    )
}
