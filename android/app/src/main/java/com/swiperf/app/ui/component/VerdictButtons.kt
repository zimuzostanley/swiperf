package com.swiperf.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.Verdict
import com.swiperf.app.ui.theme.PerfettoColors

@Composable
fun VerdictButtons(
    currentVerdict: Verdict?,
    onVerdict: (Verdict) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        VerdictBtn(
            label = "+",
            isActive = currentVerdict == Verdict.LIKE,
            activeColor = PerfettoColors.POSITIVE_COLOR,
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onVerdict(Verdict.LIKE) }
        )
        VerdictBtn(
            label = "\u2212",
            isActive = currentVerdict == Verdict.DISLIKE,
            activeColor = PerfettoColors.NEGATIVE_COLOR,
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onVerdict(Verdict.DISLIKE) }
        )
        VerdictBtn(
            label = "\u00d7",
            isActive = currentVerdict == Verdict.DISCARD,
            activeColor = PerfettoColors.DISCARD_COLOR,
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onVerdict(Verdict.DISCARD) }
        )
    }
}

@Composable
private fun VerdictBtn(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(shape)
            .background(
                if (isActive) activeColor
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
