package com.bloom.app.domain

import kotlinx.serialization.Serializable

/** Menstrual flow intensity. Order matters: ordinal is the intensity level. */
@Serializable
enum class Flow(val label: String) {
    NONE("None"), SPOTTING("Spotting"), LIGHT("Light"), MEDIUM("Medium"), HEAVY("Heavy");

    /** Counts as a "real" period day (spotting alone does not start a period). */
    val isPeriod get() = ordinal >= LIGHT.ordinal
}

@Serializable
enum class Intimacy(val label: String) {
    NONE("None"), PROTECTED("Protected"), UNPROTECTED("Unprotected")
}

/** One logged day. [date] is a `LocalDate.toEpochDay()`; timezone-free by design. */
@Serializable
data class DayLog(
    val date: Long,
    val flow: Flow = Flow.NONE,
    val symptoms: Set<String> = emptySet(),
    val moods: Set<String> = emptySet(),
    val discharge: String? = null,
    val intimacy: Intimacy = Intimacy.NONE,
    /** Basal body temperature in °C (always stored in Celsius). */
    val bbt: Double? = null,
    val notes: String = "",
) {
    val isEmpty: Boolean
        get() = flow == Flow.NONE && symptoms.isEmpty() && moods.isEmpty() && discharge == null &&
            intimacy == Intimacy.NONE && bbt == null && notes.isBlank()
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Settings(
    val onboardingDone: Boolean = false,
    val defaultCycleLength: Int = 28,
    val defaultPeriodLength: Int = 5,
    /** Days between ovulation and the next period; 14 unless personalised from BBT (population mean ≈12.4, Bull 2019). */
    val lutealLength: Int = 13,
    val theme: String = ThemeMode.SYSTEM.name,
    val fahrenheit: Boolean = false,
    val weekStartsMonday: Boolean = false,
    // Privacy
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val pinSalt: String? = null,
    val pinHash: String? = null,
    val hideInRecents: Boolean = true,
    val discreetWidgets: Boolean = false,
    // Reminders
    val periodReminder: Boolean = false,
    val periodReminderDaysBefore: Int = 2,
    val dailyReminder: Boolean = false,
    val dailyReminderHour: Int = 21,
    val dailyReminderMinute: Int = 0,
    val lastBackupEpochDay: Long? = null,
) {
    val themeMode get() = runCatching { ThemeMode.valueOf(theme) }.getOrDefault(ThemeMode.SYSTEM)
}

/** The full persisted document. [version] is the on-disk schema version. */
@Serializable
data class BloomData(
    val version: Int = 1,
    val settings: Settings = Settings(),
    val logs: List<DayLog> = emptyList(),
)
