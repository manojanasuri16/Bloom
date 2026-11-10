package com.bloom.app.widget

import android.content.Context
import com.bloom.app.R
import com.bloom.app.data.BloomRepository
import com.bloom.app.domain.CycleEngine
import com.bloom.app.domain.Phase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Kind of marker drawn for one day in the strips. */
enum class DayKind { PERIOD, PREDICTED, FERTILE, OVULATION, NONE }

data class DayCell(val date: LocalDate, val kind: DayKind, val isToday: Boolean)

/** Everything a widget needs to render, already resolved to display strings. */
sealed interface WidgetState {
    /** Discreet mode: nothing about the cycle may be exposed. */
    data object Discreet : WidgetState

    /** No period logged yet (or data unreadable). */
    data object Empty : WidgetState

    data class Data(
        val cycleDay: Int?,
        val cycleLength: Int,
        val phase: Phase?,
        val headline: String,       // "Period in 3 days"
        val window: String?,        // "Expected Nov 12–16"
        val phaseNote: String?,     // "Fertile window now" / "Ovulation around Nov 20"
        val nextPeriod: String?,    // "Next period: Nov 12–16"
        val nextOvulation: String?, // "Next ovulation: Nov 26"
        val week: List<DayCell>,    // 7 days from today
        val twoWeeks: List<DayCell>,// 14 days from today
    ) : WidgetState
}

object WidgetData {
    private val fmt get() = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    /** Never throws: any failure degrades to [WidgetState.Empty]. */
    fun load(context: Context): WidgetState =
        try { compute(context) } catch (_: Exception) { WidgetState.Empty }

    private fun compute(context: Context): WidgetState {
        val data = BloomRepository.get(context).snapshot()
        if (data.settings.discreetWidgets) return WidgetState.Discreet
        val today = LocalDate.now()
        val todayEpoch = today.toEpochDay()
        val engine = CycleEngine(data.logs, data.settings, todayEpoch)
        if (engine.periods.isEmpty()) return WidgetState.Empty

        val st = engine.status()
        val f = st.nextForecast

        val headline = when {
            st.inPeriod -> context.getString(R.string.widget_period_day, st.cycleDay ?: 1)
            st.lateDays > 0 -> context.resources.getQuantityString(R.plurals.widget_days_late, st.lateDays, st.lateDays)
            st.daysUntilPeriod != null && st.daysUntilPeriod > 0 ->
                context.resources.getQuantityString(R.plurals.widget_period_in_days, st.daysUntilPeriod, st.daysUntilPeriod)
            st.daysUntilPeriod == 0 -> context.getString(R.string.widget_period_today)
            else -> context.getString(R.string.widget_period_any_day)
        }

        val windowText = f?.let { range(it.periodWindowStart, it.periodWindowEnd) }

        // First upcoming ovulation (the current cycle's may already be behind us).
        val ov = engine.forecasts(3).firstOrNull { it.ovulation >= todayEpoch }?.ovulation
        val todayInfo = engine.dayInfo(todayEpoch)
        val note = when {
            todayInfo.isOvulationEstimate || todayInfo.isOvulationConfirmed ->
                context.getString(R.string.widget_ovulation_today)
            todayInfo.isFertile -> context.getString(R.string.widget_fertile_now)
            ov != null -> context.getString(R.string.widget_ovulation_around, day(ov))
            else -> null
        }

        fun cells(n: Int) = (0 until n).map { i ->
            val d = today.plusDays(i.toLong())
            val info = engine.dayInfo(d.toEpochDay())
            val kind = when {
                info.isPeriodLogged -> DayKind.PERIOD
                info.isPeriodPredicted -> DayKind.PREDICTED
                info.isOvulationEstimate || info.isOvulationConfirmed -> DayKind.OVULATION
                info.isFertile -> DayKind.FERTILE
                else -> DayKind.NONE
            }
            DayCell(d, kind, i == 0)
        }

        val twoWeeks = cells(14)
        return WidgetState.Data(
            cycleDay = st.cycleDay,
            cycleLength = engine.predictedCycleLength,
            phase = st.phase,
            headline = headline,
            window = windowText?.let { context.getString(R.string.widget_expected, it) },
            phaseNote = note,
            nextPeriod = windowText?.let { context.getString(R.string.widget_next_period, it) },
            nextOvulation = ov?.let { context.getString(R.string.widget_next_ovulation, day(it)) },
            week = twoWeeks.take(7),
            twoWeeks = twoWeeks,
        )
    }

    private fun day(epoch: Long) = LocalDate.ofEpochDay(epoch).format(fmt)

    /** "Nov 12–16", or "Nov 28 – Dec 2" across months. */
    private fun range(a: Long, b: Long): String {
        val s = LocalDate.ofEpochDay(a); val e = LocalDate.ofEpochDay(b)
        if (a == b) return s.format(fmt)
        return if (s.month == e.month && s.year == e.year) "${s.format(fmt)}–${e.dayOfMonth}"
        else "${s.format(fmt)} – ${e.format(fmt)}"
    }
}
