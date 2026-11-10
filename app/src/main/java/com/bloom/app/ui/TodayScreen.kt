package com.bloom.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bloom.app.domain.DayLog
import com.bloom.app.domain.Flow
import com.bloom.app.domain.Narrative
import com.bloom.app.domain.Options
import java.time.format.DateTimeFormatter
import java.util.Locale

private val headerFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())

@Composable
fun TodayScreen(vm: AppViewModel, openLog: () -> Unit) {
    val c = Bloom.colors
    val engine by vm.engine.collectAsStateCompat()
    val today by vm.today.collectAsStateCompat()
    val data by vm.data.collectAsStateCompat()
    val st = engine.status()
    val hasData = engine.periods.isNotEmpty()
    val head = Narrative.headline(st, hasData)
    val todayLog = data.logs.firstOrNull { it.date == today.toEpochDay() } ?: DayLog(today.toEpochDay())
    val f = st.nextForecast
    val len = engine.predictedCycleLength
    val ovDay = f?.let { (it.ovulation - (it.periodStart - len) + 1).toInt() } ?: (len - engine.lutealLength + 1)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(today.format(headerFmt), style = MaterialTheme.typography.labelMedium, color = c.inkMuted,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        CycleRing(
            cycleDay = if (hasData) st.cycleDay else null, cycleLength = len,
            periodLength = engine.predictedPeriodLength, fertileStart = ovDay - 5, fertileEnd = ovDay + 1,
            phase = st.phase, size = 280.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (hasData && st.cycleDay != null) {
                    Text("DAY", style = MaterialTheme.typography.labelSmall, color = c.inkMuted)
                    AnimatedContent(
                        st.cycleDay, label = "day",
                        transitionSpec = { (slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(240))) togetherWith (slideOutVertically(tween(240)) { -it / 2 } + fadeOut(tween(160))) },
                    ) { d -> Text("$d", style = MaterialTheme.typography.displayLarge, color = c.ink) }
                    Text(st.phase?.label ?: "", style = MaterialTheme.typography.titleSmall, color = phaseColor(st.phase, c).let { if (c.dark) it else c.ink.copy(alpha = 0.7f) })
                } else {
                    Text("Ready", style = MaterialTheme.typography.displayMedium, color = c.ink)
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AnimatedContent(head.title, label = "headline", transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }) {
                Text(it, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            }
            if (head.subtitle.isNotEmpty())
                Text(head.subtitle, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, textAlign = TextAlign.Center)
            if (hasData)
                Text(Narrative.confidenceNote(st.confidence, st.cyclesUsed), style = MaterialTheme.typography.labelMedium,
                    color = c.lavenderDeep, textAlign = TextAlign.Center)
        }

        // Effortless logging: flow is one tap, right here.
        BloomCard {
            SectionLabel("How is today?", Modifier.padding(bottom = 12.dp))
            FlowPicker(todayLog.flow) { vm.save(todayLog.copy(flow = it)) }
            Spacer(Modifier.height(14.dp))
            val summary = buildList {
                todayLog.symptoms.forEach { add(Options.symptomLabel(it)) }
                todayLog.moods.forEach { add(Options.moodLabel(it)) }
            }
            if (summary.isNotEmpty())
                Text(summary.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted,
                    modifier = Modifier.padding(bottom = 12.dp))
            PrimaryButton(if (todayLog.isEmpty) "Log symptoms & more" else "Edit today's log", onClick = openLog)
        }

        if (hasData && f != null) FertilityCard(engine, f)

        Text(Narrative.DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = c.inkMuted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun FertilityCard(engine: com.bloom.app.domain.CycleEngine, f: com.bloom.app.domain.Forecast) {
    val c = Bloom.colors
    BloomCard {
        SectionLabel("Fertile window & ovulation")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Fertile window", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                Text(Narrative.range(f.fertileStart, f.fertileEnd), style = MaterialTheme.typography.titleMedium, color = c.lavenderDeep)
            }
            Column(Modifier.weight(1f)) {
                Text("Ovulation (estimate)", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                Text("~" + Narrative.day(f.ovulation), style = MaterialTheme.typography.titleMedium, color = c.lavenderDeep)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Ovulation could fall anywhere between ${Narrative.range(f.ovulationWindowStart, f.ovulationWindowEnd)}. " +
                if (engine.bbtOvulations.isEmpty()) "Add daily temperatures and Bloom can confirm it after the fact."
                else "Your temperature logs help Bloom personalise this.",
            style = MaterialTheme.typography.bodySmall, color = c.inkMuted,
        )
    }
}
