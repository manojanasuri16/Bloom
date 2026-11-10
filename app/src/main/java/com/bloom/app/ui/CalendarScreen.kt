package com.bloom.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bloom.app.domain.CycleEngine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val SPAN = 1200   // months either side of the anchor — effectively unbounded paging

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(vm: AppViewModel, openDay: (LocalDate) -> Unit) {
    val c = Bloom.colors
    val engine by vm.engine.collectAsStateCompat()
    val settings by vm.settings.collectAsStateCompat()
    val today by vm.today.collectAsStateCompat()
    val anchor = YearMonth.from(today)
    val pager = rememberPagerState(initialPage = SPAN) { SPAN * 2 }
    val scope = rememberCoroutineScope()
    val month = anchor.plusMonths((pager.currentPage - SPAN).toLong())
    val data by vm.data.collectAsStateCompat()
    val logged = data.logs.associateBy { it.date }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (pager.currentPage != SPAN) SoftButton("Today") { scope.launch { pager.animateScrollToPage(SPAN) } }
        }
        WeekdayHeader(settings.weekStartsMonday)
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 12.dp, beyondViewportPageCount = 1) { page ->
            MonthGrid(anchor.plusMonths((page - SPAN).toLong()), settings.weekStartsMonday, engine, logged, today, openDay)
        }
        Legend()
    }
}

@Composable
private fun WeekdayHeader(monday: Boolean) {
    val c = Bloom.colors
    val first = if (monday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        for (i in 0..6) Text(
            first.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = c.inkMuted,
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth, monday: Boolean, engine: CycleEngine,
    logged: Map<Long, com.bloom.app.domain.DayLog>, today: LocalDate, openDay: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - (if (monday) 1 else 7)).mod(7)
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until 6) {
            Row(Modifier.fillMaxWidth().height(if (r < rows) 52.dp else 0.dp)) {
                for (col in 0..6) {
                    val dayNum = r * 7 + col - offset + 1
                    Box(Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.Center) {
                        if (r < rows && dayNum in 1..month.lengthOfMonth()) {
                            val d = month.atDay(dayNum)
                            DayCell(d, engine, logged[d.toEpochDay()], today) { openDay(d) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, engine: CycleEngine, log: com.bloom.app.domain.DayLog?, today: LocalDate, onClick: () -> Unit) {
    val c = Bloom.colors
    val info = engine.dayInfo(d.toEpochDay())
    val isToday = d == today
    val periodLogged = info.isPeriodLogged
    val predicted = info.isPeriodPredicted
    val fertileTint = info.isFertile && !periodLogged
    val bg = when {
        periodLogged -> c.rose
        fertileTint -> c.lavenderSoft
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val fg = if (periodLogged) c.onRose else if (d.isAfter(today) && !predicted && !fertileTint) c.inkMuted else c.ink
    val desc = buildString {
        append(d.toString())
        if (periodLogged) append(", period")
        if (predicted) append(", period predicted")
        if (info.isOvulationEstimate) append(", estimated ovulation")
        else if (fertileTint) append(", fertile window")
        if (log != null && !log.isEmpty) append(", has log")
    }
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(bg)
            .then(if (predicted) Modifier.drawBehind {
                drawCircle(c.rose, size.minDimension / 2 - 1.5.dp.toPx(),
                    style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))))
            } else Modifier)
            .then(if (isToday) Modifier.border(2.dp, c.ink, CircleShape) else Modifier)
            .bloomClick(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = if (isToday) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium), color = fg)
        if (info.isOvulationEstimate || info.isOvulationConfirmed)
            Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(7.dp).background(c.lavenderDeep, CircleShape))
        if (log != null && !log.isEmpty && !periodLogged)
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(4.dp).background(c.rose, CircleShape))
        if (log != null && !log.isEmpty && periodLogged)
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(4.dp).background(c.onRose, CircleShape))
    }
}

@Composable
private fun Legend() {
    val c = Bloom.colors
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        LegendItem("Period") { Box(Modifier.size(12.dp).background(c.rose, CircleShape)) }
        LegendItem("Predicted") { Box(Modifier.size(12.dp).drawBehind {
            drawCircle(c.rose, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))) }) }
        LegendItem("Fertile") { Box(Modifier.size(12.dp).background(c.lavenderSoft, CircleShape).border(1.dp, c.lavender, CircleShape)) }
        LegendItem("Ovulation") { Box(Modifier.size(8.dp).background(c.lavenderDeep, CircleShape)) }
    }
}

@Composable
private fun LegendItem(label: String, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon(); Text(label, style = MaterialTheme.typography.bodySmall, color = Bloom.colors.inkMuted)
    }
}
