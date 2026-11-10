package com.bloom.app

import com.bloom.app.data.*
import com.bloom.app.domain.*
import com.bloom.app.security.Crypto
import com.bloom.app.security.PinLock
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PortabilityTest {
    private val day = LocalDate.of(2026, 3, 14).toEpochDay()
    private val sample = BloomData(
        settings = Settings(defaultCycleLength = 30, pinHash = "SECRET", pinSalt = "SALT", fahrenheit = true),
        logs = listOf(
            DayLog(day, Flow.HEAVY, setOf("cramps", "bloating"), setOf("calm"), "creamy", Intimacy.PROTECTED, 36.55,
                "Rough \"day\", needed rest,\nsecond line"),
            DayLog(day + 1, Flow.LIGHT, notes = "=HYPERLINK(\"http://evil\")"),
            DayLog(day + 40, bbt = 36.4),
        ),
    )
    private fun doc() = Portability.toDocument(sample, "1.0.0", "2026-03-20T10:00:00Z")

    @Test fun jsonRoundTripIsLossless() {
        val preview = Portability.parseJson(Portability.toJson(doc()))
        assertEquals(sample.logs.sortedBy { it.date }, preview.logs)
        assertEquals(0, preview.skipped)
        assertEquals(30, preview.settings!!.defaultCycleLength)
    }

    @Test fun exportNeverLeaksPinMaterial() {
        val json = Portability.toJson(doc())
        assertFalse(json.contains("SECRET")); assertFalse(json.contains("SALT")); assertFalse(json.contains("pinHash"))
    }

    @Test fun csvRoundTripHandlesQuotesNewlinesAndFormulaInjection() {
        val csv = Portability.toCsv(doc())
        assertTrue("formula must be defused", csv.contains("'=HYPERLINK"))
        val preview = Portability.parseCsv(csv)
        assertEquals(sample.logs.sortedBy { it.date }, preview.logs)
    }

    @Test fun csvRejectsFilesWithoutDateColumn() {
        try { Portability.parseCsv("a,b\n1,2"); fail() } catch (e: ImportException) { assertTrue(e.message!!.contains("date")) }
    }

    @Test fun invalidRowsAreSkippedNotFatal() {
        val d = ExportDocument(format = "bloom-export", schema = 1, days = listOf(
            ExportDay("2026-01-01", "LIGHT"), ExportDay("not-a-date", "LIGHT"), ExportDay("1800-01-01", "LIGHT"),
            ExportDay("2026-01-02", "bogus", bbtCelsius = 99.0),   // empty after sanitising → skipped
        ))
        val p = Portability.validate(d)
        assertEquals(1, p.dayCount); assertEquals(3, p.skipped)
    }

    @Test fun newerSchemaIsRejectedWithHelpfulMessage() {
        val json = Portability.toJson(doc()).replace("\"schema\": 1", "\"schema\": 99")
        try { Portability.parseJson(json); fail() } catch (e: ImportException) { assertTrue(e.message!!.contains("newer")) }
    }

    @Test fun garbageIsRejected() {
        try { Portability.parseJson("{\"hello\":1}"); fail() } catch (e: ImportException) {}
        try { Portability.parseJson("not json"); fail() } catch (e: ImportException) {}
    }

    @Test fun mergeKeepsExistingAndImportedWinsOnConflict() {
        val incoming = Portability.validate(ExportDocument(format = "bloom-export", schema = 1, days = listOf(
            ExportDay("2026-03-14", "LIGHT"), ExportDay("2026-05-01", "MEDIUM"))))
        val merged = Portability.apply(sample, incoming, replace = false, includeSettings = false)
        assertEquals(4, merged.logs.size)
        assertEquals(Flow.LIGHT, merged.logs.first { it.date == day }.flow)
        val replaced = Portability.apply(sample, incoming, replace = true, includeSettings = false)
        assertEquals(2, replaced.logs.size)
        assertEquals("SECRET", replaced.settings.pinHash)   // PIN is never touched by import
    }

    @Test fun encryptedBackupRoundTripAndWrongPassword() {
        val plain = Portability.toJson(doc())
        val enc = Portability.encrypt(plain, "correct horse".toCharArray(), iterations = 100_000)
        assertTrue(Portability.isEncrypted(enc))
        assertFalse(enc.contains("cramps"))
        assertEquals(plain, Portability.decrypt(enc, "correct horse".toCharArray()))
        try { Portability.decrypt(enc, "wrong password".toCharArray()); fail() } catch (e: ImportException) {}
        // tamper with ciphertext → authentication failure
        val tampered = enc.replace("\"data\": \"", "\"data\": \"AAAA")
        try { Portability.decrypt(tampered, "correct horse".toCharArray()); fail() } catch (e: ImportException) {}
    }

    @Test fun encryptedStoreRoundTripSurvivesTornWrite() {
        val f = File.createTempFile("bloom", ".dat"); f.delete()
        val key = Crypto.aesKey(Crypto.randomBytes(32))
        val store = EncryptedStore(f) { key }
        assertEquals(BloomData(), store.read())
        store.write(sample); store.write(sample.copy(logs = sample.logs.take(1)))
        assertEquals(1, store.read().logs.size)
        assertFalse("ciphertext must not contain plaintext", String(f.readBytes(), Charsets.ISO_8859_1).contains("cramps"))
        f.writeBytes(byteArrayOf(1, 2, 3))           // corrupt the primary file
        assertEquals(3, store.read().logs.size)       // falls back to previous generation (.bak)
        File(f.path + ".bak").delete(); 
        try { store.read(); fail() } catch (e: EncryptedStore.StoreCorruptException) {}
        f.delete()
    }

    @Test fun pinHashingAndLockout() {
        val (salt, hash) = PinLock.hash("2468")
        val s = Settings(pinSalt = salt, pinHash = hash)
        assertTrue(PinLock.verify("2468", s)); assertFalse(PinLock.verify("1234", s))
        assertTrue(PinLock.isValidFormat("1234")); assertFalse(PinLock.isValidFormat("12a4")); assertFalse(PinLock.isValidFormat("123"))
        assertEquals(0, PinLock.lockoutMs(3)); assertTrue(PinLock.lockoutMs(5) > 0)
        assertNotEquals(PinLock.hash("2468").second, hash)   // salted
    }
}
