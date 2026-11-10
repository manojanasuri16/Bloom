package com.bloom.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloom.app.domain.Phase
import kotlin.math.cos
import kotlin.math.sin

/** Responds on press-down (not release) with a critically-damped spring, per Apple's "response" rule. */
fun Modifier.bloomClick(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f), label = "press",
    )
    this.scale(s).clickable(src, null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun BloomCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Bloom.colors
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.surface)
            .border(1.dp, c.hairline, RoundedCornerShape(26.dp)).padding(20.dp)
    ) { content() }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Bloom.colors.inkMuted,
        modifier = modifier.padding(horizontal = 4.dp))
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Bloom.colors
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (enabled) c.rose else c.hairline)
            .bloomClick(enabled) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) c.onRose else c.inkMuted) }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Bloom.colors
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.surfaceHigh)
            .bloomClick(onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = c.ink) }
}

/** Selectable pill used for symptoms, moods and other multi-choice logging. */
@Composable
fun BloomChip(label: String, selected: Boolean, tint: Color = Bloom.colors.rose, onClick: () -> Unit) {
    val c = Bloom.colors
    val bg by animateColorAsState(if (selected) tint.copy(alpha = if (c.dark) 0.28f else 0.16f) else c.surfaceHigh, label = "chipBg")
    val fg by animateColorAsState(if (selected) (if (c.dark) tint else tint) else c.ink, label = "chipFg")
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(bg)
            .border(1.dp, if (selected) tint.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(20.dp))
            .bloomClick(role = Role.Checkbox) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .semantics { contentDescription = if (selected) "$label, selected" else label },
    ) { Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium), color = fg) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

fun phaseColor(p: Phase?, c: BloomColors): Color = when (p) {
    Phase.MENSTRUAL -> c.rose
    Phase.FOLLICULAR -> c.lavender
    Phase.FERTILE, Phase.OVULATION -> c.lavenderDeep
    Phase.LUTEAL -> if (c.dark) Color(0xFFD08AA0) else Color(0xFFE59BAE)
    null -> c.hairline
}

/**
 * The cycle dial. Segments show the predicted shape of the cycle (period, fertile window, rest);
 * a dot travels along it to "today". Position springs into place on first draw.
 */
@Composable
fun CycleRing(
    cycleDay: Int?, cycleLength: Int, periodLength: Int, fertileStart: Int, fertileEnd: Int,
    phase: Phase?, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit,
) {
    val c = Bloom.colors
    val len = cycleLength.coerceAtLeast(15)
    val target = cycleDay?.let { (it - 0.5f).coerceIn(0f, len.toFloat()) / len } ?: 0f
    val prog by animateFloatAsState(target, spring(dampingRatio = 0.85f, stiffness = 60f), label = "ring")
    val dotColor = phaseColor(phase, c)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = Offset(inset, inset)
            fun seg(fromDay: Float, toDay: Float, color: Color) {
                val gap = 1.6f
                val start = -90f + fromDay / len * 360f + gap
                val sweep = (toDay - fromDay) / len * 360f - gap * 2
                if (sweep > 0) drawArc(color, start, sweep, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            val base = c.hairline.copy(alpha = if (c.dark) 0.14f else 0.07f)
            val p = periodLength.coerceIn(1, len - 8).toFloat()
            val fs = (fertileStart - 1).toFloat().coerceIn(p + 1, len - 2f)
            val fe = fertileEnd.toFloat().coerceIn(fs + 1, len - 1f)
            seg(0f, p, c.rose.copy(alpha = 0.85f))
            seg(p, fs, base)
            seg(fs, fe, c.lavenderDeep.copy(alpha = 0.75f))
            seg(fe, len.toFloat(), base)
            if (cycleDay != null) {
                val ang = Math.toRadians((-90f + prog * 360f).toDouble())
                val r = arcSize.width / 2
                val center = Offset(this.size.width / 2, this.size.height / 2)
                val pos = Offset(center.x + (r * cos(ang)).toFloat(), center.y + (r * sin(ang)).toFloat())
                drawCircle(c.cream, stroke * 0.95f, pos)
                drawCircle(dotColor, stroke * 0.62f, pos)
            }
        }
        content()
    }
}

@Composable
fun Spacer8() = Spacer(Modifier.height(8.dp))
@Composable
fun Spacer16() = Spacer(Modifier.height(16.dp))
@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun Stepper(label: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) {
    val c = Bloom.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        StepBtn("−", value > range.first) { onChange(value - 1) }
        Text("$value$suffix", Modifier.width(72.dp), style = MaterialTheme.typography.titleMedium,
            color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepBtn("+", value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepBtn(t: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Bloom.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(c.surfaceHigh).bloomClick(enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, style = MaterialTheme.typography.titleLarge, color = if (enabled) c.ink else c.inkMuted.copy(alpha = 0.4f)) }
}

val ScreenPadding = PaddingValues(horizontal = 20.dp)
