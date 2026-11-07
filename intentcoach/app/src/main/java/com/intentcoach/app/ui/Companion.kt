package com.intentcoach.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intentcoach.app.data.CompanionMood
import com.intentcoach.app.data.TimeOfDay

/**
 * The cozy room and its two residents, drawn with Compose Canvas — no image assets, no
 * animation library. MVP art: simple shapes in the v1.1 pastel cast, a breathing idle
 * loop, four moods, and a small celebration. The Rive/3D plan in the project docs replaces
 * this later; the inputs (mood, time of day, celebrate) stay the same.
 *
 * Everything is laid out in "units" of 1% of the canvas height, so it scales to any size.
 */
@Composable
fun CompanionRoom(
    mood: CompanionMood,
    time: TimeOfDay,
    modifier: Modifier = Modifier,
    celebrate: Boolean = false,
    height: Dp = 190.dp
) {
    // One infinite transition drives every idle motion. It only runs while this is on
    // screen — Compose stops it when the composable leaves — so it costs nothing elsewhere.
    val transition = rememberInfiniteTransition(label = "companion")
    val breath by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    val drift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "drift"
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
    ) {
        val u = size.height / 100f
        drawRoom(time, u)
        val cx = size.width * 0.40f
        drawCompanion(mood, cx, u, breath, celebrate)
        drawPet(mood, cx + 24 * u, u, breath)
        when {
            celebrate -> drawRising(cx, u, drift, Blush, count = 4)
            mood == CompanionMood.SLEEPY -> drawRising(cx + 24 * u, u, drift, Lavender, count = 3)
            else -> Unit
        }
    }
}

private fun windowTint(time: TimeOfDay): Color = when (time) {
    TimeOfDay.MORNING -> MorningTint
    TimeOfDay.AFTERNOON -> AfternoonTint
    TimeOfDay.EVENING -> EveningTint
    TimeOfDay.NIGHT -> NightTint
}

private fun DrawScope.drawRoom(time: TimeOfDay, u: Float) {
    val w = size.width
    drawRect(Umber)

    // Lamp glow in the evening and at night — the room is warm when it's dark outside.
    val lampCenter = Offset(w * 0.13f, 34 * u)
    if (time == TimeOfDay.EVENING || time == TimeOfDay.NIGHT) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Ember.copy(alpha = 0.32f), Color.Transparent),
                center = lampCenter, radius = 55 * u
            ),
            radius = 55 * u, center = lampCenter
        )
    }

    // Window.
    val winLeft = w * 0.64f
    val winTop = 12 * u
    val winW = w * 0.26f
    val winH = 40 * u
    drawRoundRect(windowTint(time), Offset(winLeft, winTop), Size(winW, winH), CornerRadius(3 * u))
    if (time == TimeOfDay.NIGHT || time == TimeOfDay.EVENING) {
        drawCircle(Peach, radius = 4 * u, center = Offset(winLeft + winW * 0.7f, winTop + 11 * u))
        listOf(0.2f to 0.25f, 0.4f to 0.6f, 0.15f to 0.75f, 0.55f to 0.35f).forEach { (x, y) ->
            drawCircle(Cream.copy(alpha = 0.7f), radius = 0.7f * u, center = Offset(winLeft + winW * x, winTop + winH * y))
        }
    } else {
        drawCircle(Ember, radius = 5 * u, center = Offset(winLeft + winW * 0.3f, winTop + 12 * u))
    }
    drawLine(FieldBorder, Offset(winLeft + winW / 2, winTop), Offset(winLeft + winW / 2, winTop + winH), 1.2f * u)
    drawLine(FieldBorder, Offset(winLeft, winTop + winH / 2), Offset(winLeft + winW, winTop + winH / 2), 1.2f * u)

    // Lamp: pole and shade.
    drawLine(FieldBorder, Offset(lampCenter.x, lampCenter.y), Offset(lampCenter.x, 80 * u), 1.5f * u)
    val shade = Path().apply {
        moveTo(lampCenter.x - 7 * u, lampCenter.y + 2 * u)
        lineTo(lampCenter.x + 7 * u, lampCenter.y + 2 * u)
        lineTo(lampCenter.x + 4 * u, lampCenter.y - 8 * u)
        lineTo(lampCenter.x - 4 * u, lampCenter.y - 8 * u)
        close()
    }
    drawPath(shade, Ember)

    // Floor and a rug.
    drawRect(Field, Offset(0f, 80 * u), Size(w, 20 * u))
    drawOval(Clay.copy(alpha = 0.35f), Offset(w * 0.22f, 84 * u), Size(w * 0.5f, 9 * u))
}

private fun DrawScope.drawCompanion(mood: CompanionMood, cx: Float, u: Float, breath: Float, celebrate: Boolean) {
    val wilt = if (mood == CompanionMood.WILTED) 3 * u else 0f
    val lift = if (celebrate || mood == CompanionMood.THRIVING) -2 * u else 0f
    val bodyH = 26 * u + breath * 1.2f * u
    val bodyW = 22 * u
    val bodyTop = 82 * u - bodyH + lift

    drawRoundRect(Lime, Offset(cx - bodyW / 2, bodyTop), Size(bodyW, bodyH), CornerRadius(8 * u))

    val headR = 11 * u
    val head = Offset(cx, bodyTop - headR * 0.75f + wilt)
    drawCircle(Peach, radius = headR, center = head)

    val eyeY = head.y + (if (mood == CompanionMood.WILTED) 1.5f * u else -1 * u)
    val eyeDx = 4 * u
    if (mood == CompanionMood.SLEEPY) {
        // Closed eyes: two short lines.
        drawLine(Ink, Offset(head.x - eyeDx - 1.6f * u, eyeY), Offset(head.x - eyeDx + 1.6f * u, eyeY), 0.9f * u, StrokeCap.Round)
        drawLine(Ink, Offset(head.x + eyeDx - 1.6f * u, eyeY), Offset(head.x + eyeDx + 1.6f * u, eyeY), 0.9f * u, StrokeCap.Round)
    } else {
        drawCircle(Ink, radius = 1.3f * u, center = Offset(head.x - eyeDx, eyeY))
        drawCircle(Ink, radius = 1.3f * u, center = Offset(head.x + eyeDx, eyeY))
    }

    if (mood == CompanionMood.THRIVING || celebrate) {
        drawCircle(Blush.copy(alpha = 0.8f), radius = 1.8f * u, center = Offset(head.x - 7 * u, eyeY + 3 * u))
        drawCircle(Blush.copy(alpha = 0.8f), radius = 1.8f * u, center = Offset(head.x + 7 * u, eyeY + 3 * u))
    }

    val mouthTop = eyeY + 2.5f * u
    when {
        mood == CompanionMood.WILTED -> drawLine(
            Ink, Offset(head.x - 2 * u, mouthTop + 2 * u), Offset(head.x + 2 * u, mouthTop + 2 * u), 0.8f * u, StrokeCap.Round
        )
        mood == CompanionMood.SLEEPY -> drawCircle(Ink, radius = 0.9f * u, center = Offset(head.x, mouthTop + 2 * u))
        else -> {
            val big = mood == CompanionMood.THRIVING || celebrate
            val mw = if (big) 7 * u else 5 * u
            drawArc(
                Ink, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(head.x - mw / 2, mouthTop - mw / 4), size = Size(mw, mw * 0.6f),
                style = Stroke(width = 0.9f * u, cap = StrokeCap.Round)
            )
        }
    }
}

private fun DrawScope.drawPet(mood: CompanionMood, px: Float, u: Float, breath: Float) {
    val w = 16 * u + breath * 0.8f * u
    val h = 10 * u
    val top = 82 * u - h
    // Ears go flat when the room is wilted — the pet's version of a sigh.
    val earLift = if (mood == CompanionMood.WILTED) 1 * u else 4 * u
    listOf(-4.5f, 4.5f).forEach { dx ->
        val ear = Path().apply {
            moveTo(px + dx * u - 2.5f * u, top + 2 * u)
            lineTo(px + dx * u + 2.5f * u, top + 2 * u)
            lineTo(px + dx * u, top - earLift)
            close()
        }
        drawPath(ear, Blush)
    }
    drawOval(Blush, Offset(px - w / 2, top), Size(w, h))
    val eyeY = top + 4 * u
    if (mood == CompanionMood.SLEEPY) {
        drawLine(Ink, Offset(px - 3.5f * u, eyeY), Offset(px - 1.5f * u, eyeY), 0.7f * u, StrokeCap.Round)
        drawLine(Ink, Offset(px + 1.5f * u, eyeY), Offset(px + 3.5f * u, eyeY), 0.7f * u, StrokeCap.Round)
    } else {
        drawCircle(Ink, radius = 0.9f * u, center = Offset(px - 2.5f * u, eyeY))
        drawCircle(Ink, radius = 0.9f * u, center = Offset(px + 2.5f * u, eyeY))
    }
}

/** Little dots drifting upward: hearts when celebrating, "zzz" when napping. */
private fun DrawScope.drawRising(x: Float, u: Float, t: Float, color: Color, count: Int) {
    repeat(count) { i ->
        val phase = (t + i / count.toFloat()) % 1f
        val y = 40 * u - phase * 30 * u
        val dx = (if (i % 2 == 0) 1 else -1) * (3 * u + phase * 4 * u)
        drawCircle(color.copy(alpha = 1f - phase), radius = (1.2f + phase) * u, center = Offset(x + dx, y))
    }
}
