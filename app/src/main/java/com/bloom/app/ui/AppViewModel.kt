package com.bloom.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloom.app.data.BloomRepository
import com.bloom.app.domain.BloomData
import com.bloom.app.domain.CycleEngine
import com.bloom.app.domain.DayLog
import com.bloom.app.domain.Settings
import com.bloom.app.security.PinLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = BloomRepository.get(app)
    private val prefs = app.getSharedPreferences("bloom_lock", Context.MODE_PRIVATE)

    val today = MutableStateFlow(LocalDate.now())
    val data: StateFlow<BloomData> = repo.data
    val settings: StateFlow<Settings> = data.map { it.settings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.snapshot().settings)

    val engine: StateFlow<CycleEngine> = combine(data, today) { d, t -> CycleEngine(d.logs, d.settings, t.toEpochDay()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.snapshot().let { CycleEngine(it.logs, it.settings, today.value.toEpochDay()) })

    val locked = MutableStateFlow(repo.snapshot().settings.lockEnabled)
    val loadError get() = repo.loadError

    fun refreshToday() { today.value = LocalDate.now() }

    fun save(log: DayLog) = repo.upsert(log)
    fun delete(date: Long) = repo.delete(date)
    fun update(f: (Settings) -> Settings) = repo.updateSettings(f)

    // ---- lock ------------------------------------------------------------------------------
    fun lockIfEnabled() { if (settings.value.lockEnabled) locked.value = true }
    fun unlock() { locked.value = false; prefs.edit().putInt("fails", 0).apply() }

    fun failures() = prefs.getInt("fails", 0)
    fun lockedUntil() = prefs.getLong("until", 0L)

    /** Verifies off the main thread (PBKDF2 is deliberately slow). */
    suspend fun tryPin(pin: String): Boolean {
        val ok = withContext(Dispatchers.Default) { PinLock.verify(pin, settings.value) }
        if (ok) unlock() else {
            val f = failures() + 1
            val wait = PinLock.lockoutMs(f)
            prefs.edit().putInt("fails", f).putLong("until", if (wait > 0) System.currentTimeMillis() + wait else 0L).apply()
        }
        return ok
    }

    suspend fun setPin(pin: String) {
        val (salt, hash) = withContext(Dispatchers.Default) { PinLock.hash(pin) }
        update { it.copy(lockEnabled = true, pinSalt = salt, pinHash = hash) }
    }

    fun disableLock() {
        update { it.copy(lockEnabled = false, biometricEnabled = false, pinSalt = null, pinHash = null) }
        locked.update { false }
    }

    /** Logs a typical period for onboarding, starting [start]; user can edit any day afterwards. */
    fun seedFirstPeriod(start: LocalDate, periodLength: Int) {
        val end = minOf(LocalDate.now(), start.plusDays(periodLength - 1L))
        var d = start
        while (!d.isAfter(end)) {
            if (repo.log(d) == null) repo.upsert(DayLog(d.toEpochDay(), flow = com.bloom.app.domain.Flow.MEDIUM))
            d = d.plusDays(1)
        }
    }
}
