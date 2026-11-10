package com.bloom.app.data

import android.content.Context
import com.bloom.app.domain.BloomData
import com.bloom.app.domain.DayLog
import com.bloom.app.domain.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * In-memory source of truth backed by [EncryptedStore]. Reads are synchronous (the document is a
 * few KB), writes are serialised and persisted off the main thread. Widgets read via [snapshot].
 */
class BloomRepository private constructor(private val store: EncryptedStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val _data = MutableStateFlow(BloomData())
    val data: StateFlow<BloomData> = _data.asStateFlow()

    /** Set when stored data exists but cannot be decrypted (e.g. keystore wiped by the OS). */
    @Volatile var loadError: Boolean = false
        private set

    /** Invoked after every successful mutation (used to refresh widgets & reminders). */
    @Volatile var onChanged: (() -> Unit)? = null

    init {
        try { _data.value = store.read() } catch (e: EncryptedStore.StoreCorruptException) { loadError = true }
    }

    fun snapshot(): BloomData = _data.value

    fun upsert(log: DayLog) = mutate { d ->
        val rest = d.logs.filter { it.date != log.date }
        d.copy(logs = if (log.isEmpty) rest else (rest + log).sortedBy { it.date })
    }

    fun delete(date: Long) = mutate { d -> d.copy(logs = d.logs.filter { it.date != date }) }

    fun updateSettings(f: (Settings) -> Settings) = mutate { it.copy(settings = f(it.settings)) }

    fun replaceAll(data: BloomData) = mutate { data }

    /** Irreversibly erases all data and the encryption key's ciphertext. */
    fun eraseEverything() {
        _data.value = BloomData()
        scope.launch { writeLock.withLock { store.wipe() }; onChanged?.invoke() }
    }

    fun log(date: LocalDate): DayLog? = _data.value.logs.firstOrNull { it.date == date.toEpochDay() }

    private fun mutate(f: (BloomData) -> BloomData) {
        _data.update(f)
        scope.launch {
            writeLock.withLock { runCatching { store.write(_data.value) } }
            onChanged?.invoke()
        }
    }

    companion object {
        @Volatile private var instance: BloomRepository? = null
        fun get(context: Context): BloomRepository = instance ?: synchronized(this) {
            instance ?: BloomRepository(EncryptedStore(context.applicationContext)).also { instance = it }
        }
    }
}
