package com.bloom.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bloom.app.domain.DayLog
import com.bloom.app.domain.Flow
import com.bloom.app.domain.Intimacy
import com.bloom.app.domain.Narrative
import com.bloom.app.domain.Options
import com.bloom.app.domain.Phase
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val titleFmt = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

/**
 * Bottom sheet for logging one day. Everything saves instantly — there is no Save button to forget.
 * Left/right arrows move between days without closing the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(vm: AppViewModel, initial: LocalDate, onDismiss: () -> Unit) {
    val c = Bloom.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var date by remember { mutableStateOf(initial) }
    var dir by remember { mutableStateOf(1) }
    val today by vm.today.collectAsStateCompat()
    val engine by vm.engine.collectAsStateCompat()
    val settings by vm.settings.collectAsStateCompat()
    val data by vm.data.collectAsStateCompat()

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = c.cream,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                NavArrow("‹", true) { dir = -1; date = date.minusDays(1) }
                AnimatedContent(
                    date, Modifier.weight(1f),
                    transitionSpec = {
                        (slideInHorizontally(tween(260)) { it / 3 * dir } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 3 * dir } + fadeOut(tween(120)))
                    }, label = "date",
                ) { d ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (d == today) "Today" else d.format(titleFmt), style = MaterialTheme.typography.titleLarge)
                        val info = engine.dayInfo(d.toEpochDay())
                        Text(
                            info.cycleDay?.let { "Cycle day $it" + (info.phase?.let { p -> " · ${p.label}" } ?: "") } ?: "",
                            style = MaterialTheme.typography.bodySmall, color = c.inkMuted,
                        )
                    }
                }
                NavArrow("›", date.isBefore(today.plusDays(60))) { dir = 1; date = date.plusDays(1) }
            }
            if (date.isAfter(today)) {
                FutureInfo(vm, date)
            } else {
                val existing = data.logs.firstOrNull { it.date == date.toEpochDay() } ?: DayLog(date.toEpochDay())
                androidx.compose.runtime.key(date) { DayEditor(existing, settings.fahrenheit) { vm.save(it) } }
            }
        }
    }
}

@Composable
private fun NavArrow(t: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Bloom.colors
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (enabled) c.surfaceHigh else c.cream).bloomClick(enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, style = MaterialTheme.typography.headlineSmall, color = if (enabled) c.ink else c.hairline) }
}

@Composable
private fun FutureInfo(vm: AppViewModel, date: LocalDate) {
    val c = Bloom.colors
    val engine by vm.engine.collectAsStateCompat()
    val info = engine.dayInfo(date.toEpochDay())
    Column(Modifier.padding(24.dp).height(220.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Forecast", style = MaterialTheme.typography.labelSmall, color = c.inkMuted)
        val line = when {
            info.isPeriodPredicted -> "Your period is likely around this day."
            info.isOvulationEstimate -> "Estimated ovulation day — could shift by a few days."
            info.isFertile -> "Estimated fertile window."
            info.phase == Phase.LUTEAL -> "Luteal phase (estimated)."
            info.phase == Phase.FOLLICULAR -> "Follicular phase (estimated)."
            else -> "Not enough data to estimate this day yet."
        }
        Text(line, style = MaterialTheme.typography.headlineSmall)
        Text("You can log once the day arrives. Predictions sharpen with every cycle you track.",
            style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    }
}

@Composable
private fun DayEditor(log: DayLog, fahrenheit: Boolean, onChange: (DayLog) -> Unit) {
    val c = Bloom.colors
    val current = rememberUpdatedState(log)
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        LogGroup("Flow") { FlowPicker(log.flow) { onChange(log.copy(flow = it)) } }
        LogGroup("Symptoms") {
            ChipFlow { Options.symptoms.forEach { o ->
                BloomChip(o.label, o.id in log.symptoms) { onChange(log.copy(symptoms = log.symptoms.toggle(o.id))) }
            } }
        }
        LogGroup("Mood") {
            ChipFlow { Options.moods.forEach { o ->
                BloomChip(o.label, o.id in log.moods, tint = c.lavenderDeep) { onChange(log.copy(moods = log.moods.toggle(o.id))) }
            } }
        }
        LogGroup("Discharge") {
            ChipFlow { Options.discharge.forEach { o ->
                BloomChip(o.label, log.discharge == o.id, tint = c.lavenderDeep) {
                    onChange(log.copy(discharge = if (log.discharge == o.id) null else o.id))
                }
            } }
        }
        LogGroup("Intimacy") {
            ChipFlow { Intimacy.entries.filter { it != Intimacy.NONE }.forEach { i ->
                BloomChip(i.label, log.intimacy == i) { onChange(log.copy(intimacy = if (log.intimacy == i) Intimacy.NONE else i)) }
            } }
        }
        LogGroup("Temperature") { BbtField(log.bbt, fahrenheit) { onChange(current.value.copy(bbt = it)) } }
        LogGroup("Notes") { NotesField(log.notes) { onChange(current.value.copy(notes = it)) } }
        Box(Modifier.height(24.dp))
    }
}

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

@Composable
private fun LogGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { SectionLabel(title); content() }
}

@Composable
fun FlowPicker(selected: Flow, onSelect: (Flow) -> Unit) {
    val c = Bloom.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Flow.entries.forEach { f ->
            val on = f == selected
            val drops = f.ordinal
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(if (on) c.rose else c.surfaceHigh)
                    .bloomClick { onSelect(if (on && f != Flow.NONE) Flow.NONE else f) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (drops == 0) Box(Modifier.size(width = 12.dp, height = 2.dp).background(if (on) c.onRose else c.inkMuted, CircleShape))
                    repeat(drops) { Box(Modifier.size(7.dp + (drops).dp).background(if (on) c.onRose else c.rose, CircleShape)) }
                }
                Text(f.label, style = MaterialTheme.typography.labelMedium, color = if (on) c.onRose else c.ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BbtField(celsius: Double?, fahrenheit: Boolean, onChange: (Double?) -> Unit) {
    val c = Bloom.colors
    fun show(v: Double?) = v?.let { "%.2f".format(if (fahrenheit) it * 9 / 5 + 32 else it) } ?: ""
    var text by remember(fahrenheit) { mutableStateOf(show(celsius)) }
    OutlinedTextField(
        text, { t ->
            text = t
            val v = t.replace(',', '.').toDoubleOrNull()
            val cel = v?.let { if (fahrenheit) (it - 32) * 5 / 9 else it }
            onChange(cel?.takeIf { it in 30.0..43.0 }.takeIf { t.isNotBlank() })
        },
        Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text("Basal body temperature (°${if (fahrenheit) "F" else "C"})") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = c.hairline, focusedBorderColor = c.rose, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface),
    )
}

@Composable
private fun NotesField(saved: String, onChange: (String) -> Unit) {
    val c = Bloom.colors
    var text by remember { mutableStateOf(saved) }
    val latest = rememberUpdatedState(text)
    val push = rememberUpdatedState(onChange)
    LaunchedEffect(text) { if (text != saved) { delay(500); push.value(text) } }
    DisposableEffect(Unit) { onDispose { if (latest.value != saved) push.value(latest.value) } }
    OutlinedTextField(
        text, { text = it.take(10_000) }, Modifier.fillMaxWidth().height(120.dp),
        placeholder = { Text("Anything worth remembering…") }, shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = c.hairline, focusedBorderColor = c.rose, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface),
    )
}
