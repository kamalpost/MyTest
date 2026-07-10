package com.kamalpost.taskmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.kamalpost.taskmanager.ui.theme.Surface2
import com.kamalpost.taskmanager.ui.theme.TextMuted

/** Pomodoro bar under the top app bar — display, start/stop, presets. */
@Composable
fun TimerBar(
    timer: TimerState,
    onToggle: () -> Unit,
    onPreset: (Int) -> Unit
) {
    val minutes = timer.secondsLeft / 60
    val seconds = timer.secondsLeft % 60
    val display = "%02d:%02d".format(minutes, seconds)

    // Same threshold colors as the web app: urgent <= 1 min, warning <= 5 min
    val timerColor = when {
        timer.secondsLeft <= 60 -> AccentRed
        timer.secondsLeft <= 5 * 60 -> AccentYellow
        else -> AccentGreen
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface2)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            display,
            color = timerColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        OutlinedButton(
            onClick = onToggle,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (timer.running) AccentRed else AccentGreen
            ),
            border = BorderStroke(
                1.dp,
                (if (timer.running) AccentRed else AccentGreen).copy(alpha = 0.4f)
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(if (timer.running) "Stop" else "Start", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Spacer(Modifier.weight(1f))

        listOf(25, 10, 5).forEach { min ->
            val active = timer.presetMinutes == min
            Text(
                "${min}m",
                color = if (active) AccentBlue else TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .border(
                        1.dp,
                        if (active) AccentBlue else Border,
                        RoundedCornerShape(5.dp)
                    )
                    .background(
                        if (active) AccentBlue.copy(alpha = 0.15f) else Surface2,
                        RoundedCornerShape(5.dp)
                    )
                    .clickable { onPreset(min) }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}
