package com.bloom.app.data

import com.bloom.app.domain.BloomData
import com.bloom.app.domain.DayLog
import com.bloom.app.domain.Flow
import com.bloom.app.domain.Intimacy
import com.bloom.app.domain.Settings
import com.bloom.app.security.Crypto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Portable, versioned, human-readable representation of Bloom data (dates are ISO-8601). */
@Serializable
data class ExportDay(
    val date: String,
    val flow: String = "NONE",
    val symptoms: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val discharge: String? = null,
    val intimacy: String = "NONE",
    val bbtCelsius: Double? = null,
    val notes: String = "",
)

@Serializable
data class ExportSettings(
    val defaultCycleLength: Int = 28,
    val defaultPeriodLength: Int = 5,
    val lutealLength: Int = 14,
    val theme: String = "SYSTEM",
    val fahrenheit: Boolean = false,
    val weekStartsMonday: Boolean = false,
    val discreetWidgets: Boolean = false,
    val periodReminder: Boolean = false,
    val periodReminderDaysBefore: Int = 2,
    val dailyReminder: Boolean = false,
    val dailyReminderHour: Int = 21,
    val dailyReminderMinute: Int = 0,
)

@Serializable
data class ExportDocument(
    val format: String = "",          // deliberately no default: foreign JSON must not look valid
    val schema: Int = 0,
    val exportedAt: String = "",
    val appVersion: String = "",
    val settings: ExportSettings? = null,
    val days: List<ExportDay> = emptyList(),
) {
    companion object { const val FORMAT = "bloom-export"; const val SCHEMA = 1 }
}

@Serializable
private data class EncryptedEnvelope(
    val format: String = ENC_FORMAT,
    val schema: Int = 1,
    val kdf: String = "PBKDF2-HMAC-SHA256",
    val iterations: Int,
    val salt: String,
    val data: String,           // base64( IV || ciphertext+tag ), AAD = "bloom-encrypted-backup:1"
)

private const val ENC_FORMAT = "bloom-encrypted-backup"
private val AAD = "bloom-encrypted-backup:1".toByteArray()

class ImportException(message: String) : Exception(message)

data class ImportPreview(
    val dayCount: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val hasSettings: Boolean,
    val logs: List<DayLog>,
    val settings: ExportSettings?,
    val skipped: Int,
)

object Portability {
    const val PBKDF2_ITERATIONS = 600_000
    private const val MAX_DAYS = 36_600           // 100 years of daily logs
    private const val MAX_NOTE = 10_000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    // ---- build ----------------------------------------------------------------------------

    fun toDocument(data: BloomData, appVersion: String, now: String): ExportDocument = ExportDocument(
        format = ExportDocument.FORMAT, schema = ExportDocument.SCHEMA, exportedAt = now, appVersion = appVersion,
        settings = data.settings.let {
            ExportSettings(
                it.defaultCycleLength, it.defaultPeriodLength, it.lutealLength, it.theme, it.fahrenheit,
                it.weekStartsMonday, it.discreetWidgets, it.periodReminder, it.periodReminderDaysBefore,
                it.dailyReminder, it.dailyReminderHour, it.dailyReminderMinute,
            )
        },
        days = data.logs.filter { !it.isEmpty }.sortedBy { it.date }.map {
            ExportDay(
                LocalDate.ofEpochDay(it.date).toString(), it.flow.name, it.symptoms.sorted(), it.moods.sorted(),
                it.discharge, it.intimacy.name, it.bbt, it.notes,
            )
        },
    )

    fun toJson(doc: ExportDocument): String = json.encodeToString(ExportDocument.serializer(), doc)

    // ---- CSV ------------------------------------------------------------------------------

    val CSV_HEADER = listOf("date", "flow", "symptoms", "moods", "discharge", "intimacy", "bbt_celsius", "notes")

    fun toCsv(doc: ExportDocument): String = buildString {
        append(CSV_HEADER.joinToString(",")).append("\r\n")
        for (d in doc.days) {
            listOf(
                d.date, d.flow.lowercase(), d.symptoms.joinToString(";"), d.moods.joinToString(";"),
                d.discharge ?: "", d.intimacy.lowercase(), d.bbtCelsius?.toString() ?: "", d.notes,
            ).joinTo(this, ",") { csvEscape(it) }
            append("\r\n")
        }
    }

    /** Quotes per RFC 4180 and defuses spreadsheet formula injection (=,+,-,@ at start). */
    fun csvEscape(raw: String): String {
        var v = raw
        if (v.isNotEmpty() && v[0] in "=+-@\t\r") v = "'$v"
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        val t = text.removePrefix("﻿")
        while (i < t.length) {
            val c = t[i]
            when {
                inQuotes -> if (c == '"') {
                    if (i + 1 < t.length && t[i + 1] == '"') { sb.append('"'); i++ } else inQuotes = false
                } else sb.append(c)
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(sb.toString()); sb.clear() }
                c == '\r' -> {}
                c == '\n' -> { row.add(sb.toString()); sb.clear(); rows.add(row); row = mutableListOf() }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) { row.add(sb.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    private fun csvUnescape(v: String) =
        if (v.length > 1 && v[0] == '\'' && v[1] in "=+-@\t\r") v.substring(1) else v

    fun parseCsv(text: String): ImportPreview {
        val rows = parseCsvRows(text)
        if (rows.isEmpty()) throw ImportException("The file is empty.")
        val header = rows[0].map { it.trim().lowercase() }
        val idx = { name: String -> header.indexOf(name) }
        if (idx("date") < 0) throw ImportException("This CSV has no \"date\" column, so it doesn't look like a Bloom export.")
        val days = rows.drop(1).map { r ->
            fun col(n: String) = idx(n).takeIf { it >= 0 && it < r.size }?.let { csvUnescape(r[it]) } ?: ""
            ExportDay(
                date = col("date").trim(), flow = col("flow").uppercase().ifBlank { "NONE" },
                symptoms = col("symptoms").split(';').map { it.trim() }.filter { it.isNotEmpty() },
                moods = col("moods").split(';').map { it.trim() }.filter { it.isNotEmpty() },
                discharge = col("discharge").trim().ifBlank { null },
                intimacy = col("intimacy").uppercase().ifBlank { "NONE" },
                bbtCelsius = col("bbt_celsius").toDoubleOrNull(),
                notes = col("notes"),
            )
        }
        return validate(ExportDocument(format = ExportDocument.FORMAT, schema = ExportDocument.SCHEMA, days = days))
    }

    // ---- JSON / encrypted -----------------------------------------------------------------

    fun isEncrypted(text: String) = text.trimStart().startsWith("{") && "\"$ENC_FORMAT\"" in text.take(200)

    fun parseJson(text: String): ImportPreview {
        val doc = try { json.decodeFromString(ExportDocument.serializer(), text) }
        catch (e: Exception) { throw ImportException("This file isn't a valid Bloom export.") }
        if (doc.format != ExportDocument.FORMAT) throw ImportException("This file isn't a Bloom export.")
        if (doc.schema > ExportDocument.SCHEMA)
            throw ImportException("This export was made by a newer version of Bloom (schema ${doc.schema}). Please update the app.")
        return validate(doc)
    }

    fun encrypt(plainJson: String, password: CharArray, iterations: Int = PBKDF2_ITERATIONS): String {
        require(password.size >= 8) { "Use at least 8 characters." }
        val salt = Crypto.randomBytes(16)
        val key = Crypto.aesKey(Crypto.pbkdf2(password, salt, iterations))
        val sealed = Crypto.seal(key, plainJson.toByteArray(), AAD)
        return json.encodeToString(EncryptedEnvelope.serializer(),
            EncryptedEnvelope(iterations = iterations, salt = Crypto.b64(salt), data = Crypto.b64(sealed)))
    }

    fun decrypt(envelopeJson: String, password: CharArray): String {
        val env = try { json.decodeFromString(EncryptedEnvelope.serializer(), envelopeJson) }
        catch (e: Exception) { throw ImportException("This isn't a valid Bloom backup file.") }
        if (env.format != ENC_FORMAT || env.schema != 1) throw ImportException("Unsupported backup format.")
        if (env.iterations !in 100_000..5_000_000) throw ImportException("Unsupported backup parameters.")
        return try {
            val key = Crypto.aesKey(Crypto.pbkdf2(password, Crypto.unb64(env.salt), env.iterations))
            String(Crypto.open(key, Crypto.unb64(env.data), AAD))
        } catch (e: Exception) { throw ImportException("Wrong password, or the backup file is damaged.") }
    }

    // ---- validation -----------------------------------------------------------------------

    fun validate(doc: ExportDocument): ImportPreview {
        if (doc.days.size > MAX_DAYS) throw ImportException("Too many entries (${doc.days.size}).")
        var skipped = 0
        val byDate = LinkedHashMap<Long, DayLog>()
        for (d in doc.days) {
            val date = try { LocalDate.parse(d.date) } catch (e: DateTimeParseException) { skipped++; continue }
            if (date.year !in 1950..2200) { skipped++; continue }
            val log = DayLog(
                date = date.toEpochDay(),
                flow = Flow.entries.firstOrNull { it.name.equals(d.flow, true) } ?: Flow.NONE,
                symptoms = d.symptoms.map { it.take(40) }.toSet(),
                moods = d.moods.map { it.take(40) }.toSet(),
                discharge = d.discharge?.take(40),
                intimacy = Intimacy.entries.firstOrNull { it.name.equals(d.intimacy, true) } ?: Intimacy.NONE,
                bbt = d.bbtCelsius?.takeIf { it in 30.0..43.0 },
                notes = d.notes.take(MAX_NOTE),
            )
            if (log.isEmpty) skipped++ else byDate[log.date] = log
        }
        val s = doc.settings?.let {
            it.copy(
                defaultCycleLength = it.defaultCycleLength.coerceIn(15, 90),
                defaultPeriodLength = it.defaultPeriodLength.coerceIn(1, 14),
                lutealLength = it.lutealLength.coerceIn(8, 20),
                periodReminderDaysBefore = it.periodReminderDaysBefore.coerceIn(0, 7),
                dailyReminderHour = it.dailyReminderHour.coerceIn(0, 23),
                dailyReminderMinute = it.dailyReminderMinute.coerceIn(0, 59),
            )
        }
        val logs = byDate.values.sortedBy { it.date }
        return ImportPreview(
            logs.size, logs.firstOrNull()?.let { LocalDate.ofEpochDay(it.date) },
            logs.lastOrNull()?.let { LocalDate.ofEpochDay(it.date) }, s != null, logs, s, skipped,
        )
    }

    /** Applies an import. [replace] wipes existing logs first; otherwise imported days win per-date. */
    fun apply(current: BloomData, preview: ImportPreview, replace: Boolean, includeSettings: Boolean): BloomData {
        val merged = if (replace) preview.logs
        else (current.logs.associateBy { it.date } + preview.logs.associateBy { it.date }).values.sortedBy { it.date }
        val s = if (includeSettings && preview.settings != null) current.settings.withImported(preview.settings) else current.settings
        return current.copy(logs = merged, settings = s)
    }

    private fun Settings.withImported(e: ExportSettings) = copy(
        defaultCycleLength = e.defaultCycleLength, defaultPeriodLength = e.defaultPeriodLength,
        lutealLength = e.lutealLength, theme = e.theme, fahrenheit = e.fahrenheit,
        weekStartsMonday = e.weekStartsMonday, discreetWidgets = e.discreetWidgets,
        periodReminder = e.periodReminder, periodReminderDaysBefore = e.periodReminderDaysBefore,
        dailyReminder = e.dailyReminder, dailyReminderHour = e.dailyReminderHour, dailyReminderMinute = e.dailyReminderMinute,
        onboardingDone = true,
    )
}
