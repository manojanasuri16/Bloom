package com.bloom.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bloom.app.domain.CycleEngine
import com.bloom.app.domain.Narrative
import com.bloom.app.domain.Options
import com.bloom.app.domain.Phase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(vm: AppViewModel) {
    val c = Bloom.colors
    val engine by vm.engine.collectAsStateCompat()
    val data by vm.data.collectAsStateCompat()
    val st = engine.stats

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Insights", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))

        if (engine.periods.isEmpty()) {
            BloomCard {
                Text("Nothing to show yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("Log your period for a couple of cycles and Bloom will start to reveal patterns in your cycle length, symptoms and mood.",
                    style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Cycle", st.mean?.let { "${it.roundToInt()}" } ?: "—", "days avg", Modifier.weight(1f))
            Tile("Period", st.avgPeriodLength?.let { "${it.roundToInt()}" } ?: "—", "days avg", Modifier.weight(1f))
            Tile("Pattern", "", Narrative.regularity(st.sd, st.count), Modifier.weight(1f), big = false)
        }

        if (st.count >= 1) BloomCard {
            SectionLabel("Cycle length")
            Spacer(Modifier.height(4.dp))
            Text(
                if (st.min != null && st.max != null && st.min != st.max) "Ranging ${st.min}–${st.max} days" else "Based on ${st.count} cycle${if (st.count == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(14.dp))
            CycleBars(st.validLengths.takeLast(12), st.mean ?: 28.0)
            if (st.sd != null) {
                Spacer(Modifier.height(10.dp))
                Text("Typical variation ±${"%.1f".format(st.sd)} days. Bloom widens its prediction window to match.",
                    style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            }
        }

        val sym = patterns(vm, engine)
        if (sym.isNotEmpty()) BloomCard {
            SectionLabel("Patterns in your logs")
            Spacer(Modifier.height(10.dp))
            sym.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp)) }
        }

        val temps = data.logs.filter { it.bbt != null }.takeLast(40)
        if (temps.size >= 5) BloomCard {
            SectionLabel("Basal temperature")
            Spacer(Modifier.height(12.dp))
            TempChart(temps.map { it.date to it.bbt!! }, engine.bbtOvulations.values.toSet())
            if (engine.bbtOvulations.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Confirmed ovulation: " + engine.bbtOvulations.values.sorted().takeLast(3).joinToString { Narrative.day(it) } +
                    " · your luteal phase ≈ ${engine.lutealLength} days.", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            }
        }

        BloomCard {
            SectionLabel("History")
            Spacer(Modifier.height(8.dp))
            val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
            engine.periods.reversed().take(24).forEachIndexed { _, p ->
                val idx = engine.periods.indexOf(p)
                val next = engine.periods.getOrNull(idx + 1)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(LocalDate.ofEpochDay(p.start).format(fmt), style = MaterialTheme.typography.titleSmall)
                        Text("${p.length}-day period", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                    }
                    Text(next?.let { "${it.start - p.start} day cycle" } ?: "current", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                }
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun Tile(label: String, value: String, caption: String, modifier: Modifier, big: Boolean = true) {
    val c = Bloom.colors
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(c.surface).border(1.dp, c.hairline, RoundedCornerShape(20.dp)).padding(14.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.inkMuted)
        Spacer(Modifier.height(6.dp))
        if (big) {
            Text(value, style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp, lineHeight = 42.sp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        } else {
            Text(caption, style = MaterialTheme.typography.titleMedium, color = c.lavenderDeep, modifier = Modifier.height(64.dp))
        }
    }
}

private fun patterns(vm: AppViewModel, engine: CycleEngine): List<String> {
    val logs = vm.data.value.logs
    if (logs.size < 10) return emptyList()
    val out = mutableListOf<String>()
    val byPhase = logs.groupBy { engine.dayInfo(it.date).phase }
    fun top(ids: (com.bloom.app.domain.DayLog) -> Set<String>, label: (String) -> String, noun: String) {
        val all = logs.flatMap { ids(it) }.groupingBy { it }.eachCount()
        val best = all.maxByOrNull { it.value } ?: return
        if (best.value < 3) return
        val phaseCounts = byPhase.mapValues { (_, l) -> l.count { best.key in ids(it) } }
        val topPhase = phaseCounts.filter { it.key != null }.maxByOrNull { it.value }
        out += "Your most logged $noun is ${label(best.key).lowercase()} (${best.value} days)" +
            (topPhase?.takeIf { it.value >= 3 && it.value * 2 >= best.value }?.let { ", most often in your ${it.key!!.label.lowercase()} phase" } ?: "") + "."
    }
    top({ it.symptoms }, Options::symptomLabel, "symptom")
    top({ it.moods }, Options::moodLabel, "mood")
    return out
}

@Composable
private fun CycleBars(lens: List<Int>, mean: Double) {
    val c = Bloom.colors
    val progress = remember(lens) { Animatable(0f) }
    LaunchedEffect(lens) { progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 90f)) }
    val maxV = (lens.maxOrNull() ?: 30).coerceAtLeast(mean.roundToInt()) + 4
    val minV = ((lens.minOrNull() ?: 20).coerceAtMost(mean.roundToInt()) - 8).coerceAtLeast(0)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val n = lens.size.coerceAtLeast(1)
        val gap = 8.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        lens.forEachIndexed { i, v ->
            val h = (v - minV).toFloat() / (maxV - minV) * size.height * progress.value
            drawRoundRect(if (i == lens.lastIndex) c.rose else c.blush.copy(alpha = if (c.dark) 0.6f else 1f), Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(8.dp.toPx()))
        }
        val y = size.height - (mean.toFloat() - minV) / (maxV - minV) * size.height
        drawLine(c.lavenderDeep, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    }
}

@Composable
private fun TempChart(points: List<Pair<Long, Double>>, ovulations: Set<Long>) {
    val c = Bloom.colors
    val lo = points.minOf { it.second } - 0.1
    val hi = points.maxOf { it.second } + 0.1
    val d0 = points.first().first
    val span = (points.last().first - d0).coerceAtLeast(1).toFloat()
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        var prev: Offset? = null
        points.forEach { (d, t) ->
            val p = Offset((d - d0) / span * size.width, size.height - ((t - lo) / (hi - lo)).toFloat() * size.height)
            prev?.let { drawLine(c.lavender, it, p, 2.dp.toPx()) }
            drawCircle(c.lavenderDeep, 3.dp.toPx(), p); prev = p
        }
        ovulations.filter { it in d0..points.last().first }.forEach { o ->
            val x = (o - d0) / span * size.width
            drawLine(c.rose, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
    }
}
