package com.bloom.app.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure, deterministic cycle maths. No Android, no clocks (today is always passed in) so every
 * rule here is unit-testable. All dates are epoch days.
 *
 * Method (see docs/ALGORITHM.md): periods are detected from flow logs, cycle lengths are the gaps
 * between period starts, outliers are discarded, and the next start is a recency-weighted mean
 * shrunk toward the user's own default while data is thin. The prediction is always a *window*
 * whose width comes from the user's observed variability, never a single "exact" day.
 */

data class Period(val start: Long, val end: Long) {
    val length get() = (end - start + 1).toInt()
    operator fun contains(d: Long) = d in start..end
}

enum class Confidence(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High") }

enum class Phase(val label: String) {
    MENSTRUAL("Menstrual"), FOLLICULAR("Follicular"), FERTILE("Fertile window"),
    OVULATION("Ovulation"), LUTEAL("Luteal")
}

/** A forecast for one upcoming cycle. */
data class Forecast(
    val periodStart: Long,
    val periodWindowStart: Long,
    val periodWindowEnd: Long,
    val periodEnd: Long,
    val ovulation: Long,
    val ovulationWindowStart: Long,
    val ovulationWindowEnd: Long,
    val fertileStart: Long,
    val fertileEnd: Long,
)

data class CycleStats(
    val validLengths: List<Int>,        // oldest → newest, outliers removed
    val mean: Double?,
    val median: Double?,
    val sd: Double?,
    val min: Int?,
    val max: Int?,
    val avgPeriodLength: Double?,
    val periodsLogged: Int,
) {
    val count get() = validLengths.size
}

data class DayInfo(
    val date: Long,
    val cycleDay: Int?,
    val phase: Phase?,
    val isPeriodLogged: Boolean,
    val isPeriodPredicted: Boolean,
    val isFertile: Boolean,
    val isOvulationEstimate: Boolean,
    val isOvulationConfirmed: Boolean,
    val isFuture: Boolean,
)

data class Status(
    val cycleDay: Int?,
    val phase: Phase?,
    val inPeriod: Boolean,
    val daysUntilPeriod: Int?,        // null if unknown; negative never (see lateDays)
    val lateDays: Int,                // days past the END of the prediction window
    val inPredictionWindow: Boolean,
    val nextForecast: Forecast?,
    val confidence: Confidence,
    val cyclesUsed: Int,
)

class CycleEngine(
    logs: List<DayLog>,
    private val settings: Settings,
    private val today: Long,
) {
    private val logsByDate: Map<Long, DayLog> = logs.associateBy { it.date }
    val periods: List<Period> = detectPeriods(logs)
    val stats: CycleStats
    val bbtOvulations: Map<Long, Long>   // cycleStart -> confirmed ovulation day

    /** Luteal length (days from ovulation to next period): personalised from BBT if possible. */
    val lutealLength: Int

    init {
        stats = computeStats(periods, today)
        bbtOvulations = detectAllBbtOvulations(logsByDate, periods)
        lutealLength = personalisedLuteal() ?: settings.lutealLength
    }

    // ---- prediction ------------------------------------------------------------------------

    private val effectiveSd: Double
        get() {
            val n = stats.count
            val sd = stats.sd
            return when {
                n >= 3 && sd != null -> max(sd, 1.0)
                n == 2 && sd != null -> max(sd, 2.5)
                else -> 3.5   // population-typical, deliberately wide when we know little
            }
        }

    /** Predicted length of a full cycle, in days (rounded), 15..90. */
    val predictedCycleLength: Int
        get() = predictedCycleLengthExact.roundToInt().coerceIn(MIN_CYCLE, MAX_CYCLE)

    private val predictedCycleLengthExact: Double
        get() {
            val prior = settings.defaultCycleLength.toDouble()
            val lens = stats.validLengths
            if (lens.isEmpty()) return prior
            var wSum = 0.0
            var xSum = 0.0
            lens.reversed().take(RECENT_CYCLES).forEachIndexed { i, x ->
                val w = Math.pow(RECENCY_DECAY, i.toDouble())
                wSum += w; xSum += w * x
            }
            // Shrink toward the user's stated default while data is thin.
            val k = when (lens.size) { 1 -> 1.5; 2 -> 1.0; else -> 0.4 }
            return (xSum + k * prior) / (wSum + k)
        }

    val predictedPeriodLength: Int
        get() = stats.avgPeriodLength?.let { (it).roundToInt().coerceIn(1, 14) } ?: settings.defaultPeriodLength

    val confidence: Confidence
        get() {
            val n = stats.count
            val sd = stats.sd ?: return Confidence.LOW
            return when {
                n >= 6 && sd <= 2.0 -> Confidence.HIGH
                n >= 3 && sd <= 4.0 -> Confidence.MEDIUM
                else -> Confidence.LOW
            }
        }

    /** Half-width (days) of the ~80 % window for the [k]-th cycle ahead (k ≥ 1). */
    private fun halfWidth(k: Int): Int =
        ceil(1.28 * effectiveSd * sqrt(k.toDouble())).toInt().coerceIn(1, 10)

    /** Forecasts for the next [count] cycles, or empty when no period has ever been logged. */
    fun forecasts(count: Int = 4): List<Forecast> {
        val last = periods.lastOrNull() ?: return emptyList()
        val len = predictedCycleLength
        val plen = predictedPeriodLength
        return (1..count).map { k ->
            val start = last.start + len.toLong() * k
            val hw = halfWidth(k)
            val ov = start - lutealLength
            // Ovulation timing adds its own ~2-day wobble on top of cycle-length uncertainty.
            val ovHw = ceil(sqrt((hw * hw + 4).toDouble())).toInt()
            Forecast(
                periodStart = start,
                periodWindowStart = start - hw,
                periodWindowEnd = start + hw,
                periodEnd = start + plen - 1,
                ovulation = ov,
                ovulationWindowStart = ov - ovHw,
                ovulationWindowEnd = ov + ovHw,
                fertileStart = ov - 5,
                fertileEnd = ov + 1,
            )
        }
    }

    /** The first forecast whose window has not been fully passed (or the most overdue one). */
    private fun relevantForecast(): Forecast? {
        val fs = forecasts(1)
        return fs.firstOrNull()
    }

    fun status(): Status {
        val f = relevantForecast()
        val info = dayInfo(today)
        val inPeriod = info.isPeriodLogged
        val daysUntil = f?.let { (it.periodStart - today).toInt() }
        val late = f?.let { max(0, (today - it.periodWindowEnd).toInt()) } ?: 0
        val inWindow = f != null && today in f.periodWindowStart..f.periodWindowEnd && !inPeriod
        return Status(
            cycleDay = info.cycleDay, phase = info.phase, inPeriod = inPeriod,
            daysUntilPeriod = daysUntil, lateDays = if (inPeriod) 0 else late,
            inPredictionWindow = inWindow, nextForecast = f, confidence = confidence,
            cyclesUsed = stats.count,
        )
    }

    // ---- per-day classification ------------------------------------------------------------

    fun dayInfo(date: Long): DayInfo {
        val logged = periods.any { date in it } ||
            (logsByDate[date]?.flow?.isPeriod == true)
        val future = date > today
        val prev = periods.lastOrNull { it.start <= date }
        if (prev == null) {
            return DayInfo(date, null, null, logged, false, false, false, false, future)
        }
        val idx = periods.indexOf(prev)
        val next = periods.getOrNull(idx + 1)
        val cycleDay = (date - prev.start + 1).toInt()

        return if (next != null) {
            // Historical, fully known cycle: use what actually happened.
            val len = (next.start - prev.start).toInt()
            val ovConfirmed = bbtOvulations[prev.start]
            val ov = ovConfirmed ?: (next.start - lutealLength)
            val plausible = len in MIN_CYCLE..MAX_CYCLE
            val phase = when {
                date in prev -> Phase.MENSTRUAL
                !plausible -> Phase.FOLLICULAR
                date == ov -> Phase.OVULATION
                date in (ov - 5)..(ov + 1) -> Phase.FERTILE
                date < ov -> Phase.FOLLICULAR
                else -> Phase.LUTEAL
            }
            DayInfo(
                date, cycleDay, phase, logged, false,
                isFertile = plausible && date in (ov - 5)..(ov + 1),
                isOvulationEstimate = plausible && date == ov && ovConfirmed == null,
                isOvulationConfirmed = plausible && date == ov && ovConfirmed != null,
                isFuture = future,
            )
        } else {
            // Current / future cycles: project from the last logged period.
            val len = predictedCycleLength
            // 0 = current cycle. Past/today stays in the current cycle even when overdue, so the
            // cycle-day counter keeps running instead of silently restarting.
            val k = if (date <= today) 0 else ((date - prev.start) / len).toInt()
            val cycleStart = prev.start + k.toLong() * len
            val cd = (date - cycleStart + 1).toInt()
            val ovConfirmed = if (k == 0) bbtOvulations[prev.start] else null
            val ov = ovConfirmed ?: (cycleStart + len - lutealLength)
            val plen = if (k == 0) max(prev.length, predictedPeriodLength) else predictedPeriodLength
            val predictedPeriod = k >= 1 && cd <= predictedPeriodLength
            val inLoggedPeriod = k == 0 && date in prev
            val phase = when {
                inLoggedPeriod -> Phase.MENSTRUAL
                k >= 1 && cd <= predictedPeriodLength -> Phase.MENSTRUAL
                date == ov -> Phase.OVULATION
                date in (ov - 5)..(ov + 1) -> Phase.FERTILE
                date < ov -> Phase.FOLLICULAR
                else -> Phase.LUTEAL
            }
            // In the current cycle, a period still "ongoing" per logs: the lone first logged
            // days are covered by [prev]; extra expected days are shown as predicted.
            val ongoingExpected = k == 0 && date > prev.end && date < prev.start + plen &&
                today <= prev.end + 1 && date > today
            DayInfo(
                date, if (k == 0) cycleDay else cd, phase, logged,
                isPeriodPredicted = !logged && (predictedPeriod || ongoingExpected),
                isFertile = date in (ov - 5)..(ov + 1),
                isOvulationEstimate = date == ov && ovConfirmed == null,
                isOvulationConfirmed = date == ov && ovConfirmed != null,
                isFuture = future,
            )
        }
    }

    // ---- internals -------------------------------------------------------------------------

    private fun personalisedLuteal(): Int? {
        val lens = periods.zipWithNext().mapNotNull { (a, b) ->
            bbtOvulations[a.start]?.let { ov -> (b.start - ov).toInt() }
        }.filter { it in 8..20 }
        if (lens.size < 2) return null
        return median(lens.map { it.toDouble() }).roundToInt()
    }

    companion object {
        const val MIN_CYCLE = 15
        const val MAX_CYCLE = 90
        /** Cycle lengths outside this band are treated as missed logging / skipped cycles for stats. */
        const val STAT_MIN = 15
        const val STAT_MAX = 60
        const val RECENT_CYCLES = 6
        const val RECENCY_DECAY = 0.8
        /** Max gap (in days between two flow days) still counted as the same period. */
        const val PERIOD_GAP_TOLERANCE = 2

        /** Groups flow logs into periods. Spotting only extends a period, never starts one. */
        fun detectPeriods(logs: List<DayLog>): List<Period> {
            val flowDays = logs.filter { it.flow.isPeriod }.map { it.date }.sorted()
            if (flowDays.isEmpty()) return emptyList()
            val spotting = logs.filter { it.flow == Flow.SPOTTING }.map { it.date }.toHashSet()
            val groups = mutableListOf<MutableList<Long>>()
            for (d in flowDays) {
                val g = groups.lastOrNull()
                if (g != null && d - g.last() <= PERIOD_GAP_TOLERANCE) g.add(d) else groups.add(mutableListOf(d))
            }
            return groups.map { g ->
                var s = g.first(); var e = g.last()
                while ((s - 1) in spotting) s--
                while ((e + 1) in spotting) e++
                Period(s, e)
            }
        }

        fun computeStats(periods: List<Period>, today: Long): CycleStats {
            val raw = periods.zipWithNext { a, b -> (b.start - a.start).toInt() }
                .filter { it in STAT_MIN..STAT_MAX }
            val lens = removeOutliers(raw)
            // The latest period might still be ongoing → its length isn't final yet.
            val completed = periods.filterIndexed { i, p ->
                (i < periods.lastIndex || p.end < today - 1) && p.length <= 14
            }
            val avgP = if (completed.isEmpty()) null else completed.takeLast(6).map { it.length }.average()
            val mean = if (lens.isEmpty()) null else lens.average()
            return CycleStats(
                validLengths = lens,
                mean = mean,
                median = if (lens.isEmpty()) null else median(lens.map { it.toDouble() }),
                sd = if (lens.size >= 2) sampleSd(lens.map { it.toDouble() }) else null,
                min = lens.minOrNull(), max = lens.maxOrNull(),
                avgPeriodLength = avgP,
                periodsLogged = periods.size,
            )
        }

        /** Median/MAD outlier rejection; only kicks in with ≥ 5 samples so we never discard thin data. */
        fun removeOutliers(lens: List<Int>): List<Int> {
            if (lens.size < 5) return lens
            val med = median(lens.map { it.toDouble() })
            val mad = max(1.5, 1.4826 * median(lens.map { abs(it - med) }))
            return lens.filter { abs(it - med) <= 3 * mad }
        }

        fun median(xs: List<Double>): Double {
            val s = xs.sorted()
            val n = s.size
            return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
        }

        fun sampleSd(xs: List<Double>): Double {
            val m = xs.average()
            return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
        }

        /**
         * Basal-body-temperature "3 over 6" rule: three consecutive readings above the highest of
         * the previous six, the third at least 0.2 °C above that coverline. Ovulation is the day
         * before the first high reading. Returns null when the pattern isn't present.
         */
        fun detectBbtOvulation(readings: List<Pair<Long, Double>>): Long? {
            if (readings.size < 9) return null
            val r = readings.sortedBy { it.first }
            for (i in 6..r.size - 3) {
                // Readings must be near-contiguous to be meaningful.
                if (r[i + 2].first - r[i - 6].first > 12) continue
                val cover = r.subList(i - 6, i).maxOf { it.second }
                val a = r[i].second; val b = r[i + 1].second; val c = r[i + 2].second
                if (a > cover && b > cover && c >= cover + 0.2) return r[i].first - 1
            }
            return null
        }

        private fun detectAllBbtOvulations(
            logsByDate: Map<Long, DayLog>, periods: List<Period>,
        ): Map<Long, Long> {
            val out = mutableMapOf<Long, Long>()
            periods.forEachIndexed { i, p ->
                val end = periods.getOrNull(i + 1)?.start?.minus(1) ?: (p.start + MAX_CYCLE)
                val readings = (p.start..end).mapNotNull { d ->
                    logsByDate[d]?.bbt?.let { d to it }
                }
                detectBbtOvulation(readings)?.let { ov ->
                    if (ov > p.end) out[p.start] = ov
                }
            }
            return out
        }
    }
}
