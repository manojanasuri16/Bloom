package com.bloom.app

import com.bloom.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class CycleEngineTest {
    private val s = Settings()
    private fun period(start: Long, days: Int = 5, flow: Flow = Flow.MEDIUM) =
        (0 until days).map { DayLog(start + it, flow = flow) }
    private fun periodsAt(vararg starts: Long) = starts.flatMap { period(it) }
    private fun regular(n: Int, len: Int, first: Long = 1000): List<DayLog> =
        (0 until n).flatMap { period(first + it.toLong() * len) }

    @Test fun detectsPeriodsAndToleratesOneMissedDay() {
        val logs = listOf(DayLog(10, flow = Flow.HEAVY), DayLog(11, flow = Flow.MEDIUM), DayLog(13, flow = Flow.LIGHT),
            DayLog(50, flow = Flow.LIGHT))
        val p = CycleEngine.detectPeriods(logs)
        assertEquals(listOf(Period(10, 13), Period(50, 50)), p)
    }

    @Test fun spottingExtendsButNeverStartsAPeriod() {
        val logs = listOf(DayLog(5, flow = Flow.SPOTTING), DayLog(6, flow = Flow.LIGHT), DayLog(7, flow = Flow.LIGHT),
            DayLog(8, flow = Flow.SPOTTING), DayLog(30, flow = Flow.SPOTTING))
        assertEquals(listOf(Period(5, 8)), CycleEngine.detectPeriods(logs))
    }

    @Test fun cycleLengthsAndMean() {
        val e = CycleEngine(periodsAt(1000, 1028, 1057, 1085), s, 1090)
        assertEquals(listOf(28, 29, 28), e.stats.validLengths)
        assertEquals(28.33, e.stats.mean!!, 0.01)
    }

    @Test fun noDataFallsBackToDefaultsWithLowConfidence() {
        val e = CycleEngine(emptyList(), s, 1000)
        assertEquals(28, e.predictedCycleLength)
        assertEquals(Confidence.LOW, e.confidence)
        assertTrue(e.forecasts().isEmpty())
        assertNull(e.status().cycleDay)
    }

    @Test fun singlePeriodUsesDefaultAndWideWindow() {
        val e = CycleEngine(period(1000), s, 1003)
        val f = e.forecasts(1)[0]
        assertEquals(1028, f.periodStart)
        assertTrue("window should be wide with 1 period", f.periodWindowEnd - f.periodWindowStart >= 6)
        assertEquals(Confidence.LOW, e.confidence)
        assertEquals(4, e.status().cycleDay)
    }

    @Test fun regularCyclesGiveTightWindowHighConfidence() {
        val e = CycleEngine(regular(8, 28), s, 1000L + 7 * 28 + 10)
        assertEquals(28, e.predictedCycleLength)
        assertEquals(Confidence.HIGH, e.confidence)
        val f = e.forecasts(1)[0]
        assertEquals(1000L + 8 * 28, f.periodStart)
        assertTrue(f.periodWindowEnd - f.periodWindowStart <= 4)
    }

    @Test fun irregularCyclesWidenWindowAndLowerConfidence() {
        val lens = listOf(24, 35, 27, 38, 25, 36)
        var d = 1000L; val starts = mutableListOf(d); lens.forEach { d += it; starts += d }
        val e = CycleEngine(starts.flatMap { period(it) }, s, d + 3)
        assertNotEquals(Confidence.HIGH, e.confidence)
        val f = e.forecasts(1)[0]
        assertTrue(f.periodWindowEnd - f.periodWindowStart >= 8)
    }

    @Test fun recentCyclesWeighMoreThanOld() {
        // Old cycles 32 long, recent ones 26.
        val lens = listOf(32, 32, 32, 26, 26, 26, 26)
        var d = 1000L; val starts = mutableListOf(d); lens.forEach { d += it; starts += d }
        val e = CycleEngine(starts.flatMap { period(it) }, s, d + 1)
        assertTrue("got ${e.predictedCycleLength}", e.predictedCycleLength in 26..29)
    }

    @Test fun outlierFromMissedLoggingIsIgnored() {
        // one 70-day gap (a forgotten cycle) + normal cycles
        val e = CycleEngine(periodsAt(1000, 1028, 1056, 1126, 1154, 1182, 1210), s, 1215)
        assertFalse(e.stats.validLengths.contains(70))
        assertEquals(28, e.predictedCycleLength)
    }

    @Test fun madRejectsExtremeButKeepsNormalVariation() {
        val kept = CycleEngine.removeOutliers(listOf(27, 28, 29, 28, 27, 30, 45))
        assertFalse(kept.contains(45)); assertTrue(kept.contains(30))
        assertEquals(listOf(20, 40, 28), CycleEngine.removeOutliers(listOf(20, 40, 28))) // <5 samples untouched
    }

    @Test fun ovulationIsLutealDaysBeforeNextPeriod() {
        val e = CycleEngine(regular(6, 28), s, 1000L + 5 * 28 + 2)
        val f = e.forecasts(1)[0]
        assertEquals(f.periodStart - 14, f.ovulation)
        assertEquals(f.ovulation - 5, f.fertileStart)
        assertEquals(f.ovulation + 1, f.fertileEnd)
        assertTrue(f.ovulationWindowStart < f.ovulation && f.ovulationWindowEnd > f.ovulation)
    }

    @Test fun lateByDaysAfterWindow() {
        val e = CycleEngine(regular(6, 28), s, 1000L + 5 * 28 + 28 + 8)   // 8 days past expected
        val st = e.status()
        assertTrue(st.lateDays > 0)
        assertFalse(st.inPeriod)
        assertEquals(37, st.cycleDay)   // counter keeps running, never silently restarts
    }

    @Test fun insidePredictionWindowFlag() {
        val e = CycleEngine(regular(6, 28), s, 1000L + 6 * 28 + 1)
        assertTrue(e.status().inPredictionWindow)
        assertEquals(0, e.status().lateDays)
    }

    @Test fun phasesAcrossAHistoricCycle() {
        val e = CycleEngine(regular(4, 28), s, 1000L + 3 * 28 + 5)
        assertEquals(Phase.MENSTRUAL, e.dayInfo(1000).phase)
        assertEquals(Phase.FOLLICULAR, e.dayInfo(1007).phase)
        assertEquals(Phase.OVULATION, e.dayInfo(1000 + 28 - 14).phase)
        assertEquals(Phase.FERTILE, e.dayInfo(1000 + 28 - 14 - 3).phase)
        assertEquals(Phase.LUTEAL, e.dayInfo(1000 + 24).phase)
        assertEquals(1, e.dayInfo(1000).cycleDay)
    }

    @Test fun futureProjectionMarksPredictedPeriodDays() {
        val e = CycleEngine(regular(4, 28), s, 1000L + 3 * 28 + 5)
        val nextStart = 1000L + 4 * 28
        assertTrue(e.dayInfo(nextStart).isPeriodPredicted)
        assertTrue(e.dayInfo(nextStart + 4).isPeriodPredicted)
        assertFalse(e.dayInfo(nextStart + 5).isPeriodPredicted)
        assertTrue(e.dayInfo(nextStart + 28).isPeriodPredicted)   // second cycle ahead
        assertFalse(e.dayInfo(1000).isPeriodPredicted)            // logged days aren't "predicted"
        assertTrue(e.dayInfo(1000).isPeriodLogged)
    }

    @Test fun bbtThreeOverSixDetectsOvulation() {
        val base = (1L..10L).map { it to 36.4 }
        val rise = listOf(11L to 36.7, 12L to 36.75, 13L to 36.8)
        assertEquals(10L, CycleEngine.detectBbtOvulation(base + rise))
        // No sustained rise → null
        assertNull(CycleEngine.detectBbtOvulation((1L..14L).map { it to 36.4 + (it % 2) * 0.05 }))
        // Third reading not 0.2 above coverline → null
        assertNull(CycleEngine.detectBbtOvulation(base + listOf(11L to 36.5, 12L to 36.5, 13L to 36.55)))
    }

    @Test fun bbtPersonalisesLutealLengthAfterTwoConfirmedCycles() {
        val logs = mutableListOf<DayLog>()
        for (c in 0 until 3) {
            val start = 1000L + c * 28
            logs += period(start)
            // first high reading at offset 17 → ovulation offset 16 → luteal = 12 days
            for (d in 6..27) {
                val date = start + d
                val t = if (d >= 17) 36.8 else 36.4
                logs += DayLog(date, bbt = t)
            }
        }
        logs += period(1000L + 3 * 28)
        val e = CycleEngine(logs, s, 1000L + 3 * 28 + 2)
        assertEquals(12, e.lutealLength)
    }

    @Test fun ongoingLatestPeriodDoesNotSkewPeriodLength() {
        val logs = period(1000, 5) + period(1028, 5) + period(1056, 2)   // latest is mid-flow
        val e = CycleEngine(logs, s, 1057)
        assertEquals(5, e.predictedPeriodLength)
    }
}
