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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bloom.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {
    val c = Bloom.colors
    var step by remember { mutableIntStateOf(0) }
    var start by remember { mutableStateOf<LocalDate?>(null) }
    var cycle by remember { mutableIntStateOf(28) }
    var period by remember { mutableIntStateOf(5) }
    var picking by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().background(c.cream).statusBarsPadding().navigationBarsPadding().padding(28.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            repeat(3) { Box(Modifier.height(5.dp).size(if (it == step) 28.dp else 8.dp).background(if (it == step) c.rose else c.hairline, CircleShape)) }
        }
        AnimatedContent(
            step, Modifier.weight(1f).fillMaxWidth(), label = "onb",
            transitionSpec = { (slideInHorizontally(tween(350)) { it / 4 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(150))) },
        ) { s ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                when (s) {
                    0 -> {
                        androidx.compose.foundation.Image(painterResource(R.drawable.ic_petals), null, Modifier.size(150.dp))
                        Spacer(Modifier.height(20.dp))
                        Text("Bloom", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(12.dp))
                        Text("A calm, private place to understand your cycle.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = c.inkMuted)
                        Spacer(Modifier.height(32.dp))
                        BloomCard {
                            Promise("Stays on your phone", "Everything is encrypted and stored only on this device.")
                            Spacer(Modifier.height(12.dp))
                            Promise("No account, no tracking", "No sign-up, analytics, ads or internet access — ever.")
                            Spacer(Modifier.height(12.dp))
                            Promise("Yours to take", "Export or back up everything whenever you like.")
                        }
                    }
                    1 -> {
                        Text("When did your last period start?", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text("An approximate date is fine — you can fix it any time.", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(28.dp))
                        ChipFlow {
                            BloomChip("Today", start == today) { start = today }
                            BloomChip("Yesterday", start == today.minusDays(1)) { start = today.minusDays(1) }
                            BloomChip("A week ago", start == today.minusDays(7)) { start = today.minusDays(7) }
                            BloomChip("Pick a date…", start != null && start !in listOf(today, today.minusDays(1), today.minusDays(7))) { picking = true }
                            BloomChip("I'm not sure", start == null, tint = c.lavenderDeep) { start = null }
                        }
                        start?.let {
                            Spacer(Modifier.height(20.dp))
                            Text(it.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())), style = MaterialTheme.typography.titleLarge, color = c.rose)
                        }
                    }
                    else -> {
                        Text("Your usual rhythm", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text("Bloom starts with these, then learns from what you log. Not sure? The defaults are a fine start.",
                            style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        BloomCard {
                            Stepper("Cycle length", cycle, 21..45, " days") { cycle = it }
                            Stepper("Period length", period, 2..10, " days") { period = it }
                        }
                    }
                }
            }
        }
        PrimaryButton(if (step < 2) "Continue" else "Start using Bloom") {
            if (step < 2) step++ else {
                vm.update { it.copy(onboardingDone = true, defaultCycleLength = cycle, defaultPeriodLength = period) }
                start?.let { vm.seedFirstPeriod(it, period) }
                onDone()
            }
        }
        if (step > 0) Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            TextButton({ step-- }) { Text("Back", color = c.inkMuted) }
        }
    }

    if (picking) {
        val ps = rememberDatePickerState(
            initialSelectedDateMillis = (start ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().let { !it.isAfter(today) && it.isAfter(today.minusDays(120)) }
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton({
                ps.selectedDateMillis?.let { start = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; picking = false
            }) { Text("Done") } },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel") } },
        ) { DatePicker(ps) }
    }
}

@Composable
private fun Promise(title: String, body: String) {
    val c = Bloom.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.padding(top = 7.dp).size(8.dp).background(c.rose, CircleShape))
        Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted) }
    }
}
