package com.kamalpost.taskmanager.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamalpost.taskmanager.TimerState
import com.kamalpost.taskmanager.ui.theme.AccentBlue
import com.kamalpost.taskmanager.ui.theme.AccentGreen
import com.kamalpost.taskmanager.ui.theme.AccentRed
import com.kamalpost.taskmanager.ui.theme.AccentYellow
import com.kamalpost.taskmanager.ui.theme.Border
import com.kamalpost.taskmanager.ui.theme.TextMuted

/** Dedicated pomodoro screen with a circular countdown. */
@Composable
fun TimerScreen(
    timer: TimerState,
    onToggle: () -> Unit,
    onPreset: (Int) -> Unit
) {
    val total = (timer.presetMinutes * 60).coerceAtLeast(1)
    val progress = timer.secondsLeft.toFloat() / total
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400),
        label = "timerProgress"
    )

    // Same thresholds as the web app: urgent <= 1 min, warning <= 5 min
    val ringColor = when {
        timer.secondsLeft <= 60 -> AccentRed
        timer.secondsLeft <= 5 * 60 -> AccentYellow
        else -> AccentGreen
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(260.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = Border,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%02d:%02d".format(timer.secondsLeft / 60, timer.secondsLeft % 60),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = ringColor
                )
                Text(
                    if (timer.running) "Focus time"
                    else if (timer.secondsLeft == 0) "Session complete"
                    else "Paused",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        LargeFloatingActionButton(
            onClick = onToggle,
            containerColor = if (timer.running) AccentRed.copy(alpha = 0.2f) else AccentGreen.copy(alpha = 0.2f),
            contentColor = if (timer.running) AccentRed else AccentGreen
        ) {
            Icon(
                if (timer.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (timer.running) "Pause" else "Start",
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(36.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(25, 10, 5).forEach { min ->
                val active = timer.presetMinutes == min
                FilterChip(
                    selected = active,
                    onClick = { onPreset(min) },
                    label = {
                        Text(
                            "$min min",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.15f),
                        selectedLabelColor = AccentBlue,
                        labelColor = TextMuted
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "The timer keeps running in the background\nwith a live notification.",
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}
