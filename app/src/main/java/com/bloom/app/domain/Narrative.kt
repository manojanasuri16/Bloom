package com.bloom.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Human wording for predictions. Kept pure so the honesty of the copy is unit-tested. */
object Narrative {
    private val md = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    fun day(epoch: Long): String = LocalDate.ofEpochDay(epoch).format(md)
    fun range(a: Long, b: Long): String = if (a == b) day(a) else "${day(a)} – ${day(b)}"

    data class Headline(val title: String, val subtitle: String)

    fun headline(st: Status, hasData: Boolean): Headline {
        if (!hasData) return Headline("Welcome to Bloom", "Log the first day of your period to begin.")
        val f = st.nextForecast
        return when {
            st.inPeriod -> Headline(
                "Period · day ${st.cycleDay}",
                f?.let { "Next one is expected around ${day(it.periodStart)}." } ?: "",
            )
            f == null -> Headline("Cycle day ${st.cycleDay ?: 1}", "")
            st.lateDays > 0 -> Headline(
                if (st.lateDays == 1) "Period is 1 day late" else "Period is ${st.lateDays} days late",
                "Cycles vary — this is common. Log it when it starts and Bloom will adjust.",
            )
            st.inPredictionWindow -> Headline(
                "Period may start any day now",
                "Expected ${range(f.periodWindowStart, f.periodWindowEnd)}.",
            )
            else -> {
                val n = st.daysUntilPeriod ?: 0
                Headline(
                    when (n) { 0 -> "Period expected today"; 1 -> "Period expected tomorrow"; else -> "Period in about $n days" },
                    "Most likely ${range(f.periodWindowStart, f.periodWindowEnd)}.",
                )
            }
        }
    }

    fun confidenceNote(c: Confidence, cycles: Int): String = when (c) {
        Confidence.HIGH -> "High confidence · based on your last $cycles cycles"
        Confidence.MEDIUM -> "Medium confidence · based on $cycles cycles"
        Confidence.LOW -> if (cycles == 0) "Low confidence · using your typical cycle until Bloom learns more"
        else "Low confidence · $cycles cycle${if (cycles == 1) "" else "s"} logged so far, or cycles vary a lot"
    }

    const val DISCLAIMER =
        "Bloom's predictions are statistical estimates based on your own logs. They are not medical advice " +
            "and must not be used for contraception or to diagnose anything. Talk to a clinician about concerns."

    fun regularity(sd: Double?, n: Int): String = when {
        sd == null || n < 3 -> "Learning"
        sd <= 2.0 -> "Very regular"
        sd <= 4.0 -> "Fairly regular"
        else -> "Irregular"
    }
}
