package com.swiperf.app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        VerdictButton(
            label = "+",
            isActive = currentVerdict == Verdict.LIKE,
            activeColor = PerfettoColors.POSITIVE_COLOR,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onVerdict(Verdict.LIKE)
            }
        )
        VerdictButton(
            label = "\u2212",
            isActive = currentVerdict == Verdict.DISLIKE,
            activeColor = PerfettoColors.NEGATIVE_COLOR,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onVerdict(Verdict.DISLIKE)
            }
        )
        VerdictButton(
            label = "\u00d7",
            isActive = currentVerdict == Verdict.DISCARD,
            activeColor = PerfettoColors.DISCARD_COLOR,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onVerdict(Verdict.DISCARD)
            }
        )
    }
}

@Composable
private fun VerdictButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isActive) activeColor else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
