package com.bloom.app.data

import android.content.Context
import com.bloom.app.domain.BloomData
import com.bloom.app.security.Crypto
import com.bloom.app.security.KeystoreKeys
import kotlinx.serialization.json.Json
import java.io.File
import javax.crypto.SecretKey

/**
 * The single on-device database: one JSON document, AES-256-GCM encrypted with a non-exportable
 * Android Keystore key. Cycle data is tiny (a few KB per year), so a whole-file atomic rewrite is
 * simpler and more robust than a SQL engine, and there is no native crypto library to trust.
 *
 * Layout: magic "BLM1" || IV || ciphertext+tag. Writes go to a temp file then atomic rename; the
 * previous generation is kept as ".bak" so a torn write can never lose history.
 */
class EncryptedStore(private val file: File, private val keyProvider: () -> SecretKey) {

    constructor(context: Context) : this(
        File(context.filesDir, "bloom.dat"), { KeystoreKeys.masterKey() }
    )

    private val bak get() = File(file.path + ".bak")
    private val tmp get() = File(file.path + ".tmp")

    fun exists() = file.exists() || bak.exists()

    fun read(): BloomData {
        for (f in listOf(file, bak)) {
            if (!f.exists()) continue
            runCatching { return decode(f.readBytes()) }
        }
        if (exists()) throw StoreCorruptException()
        return BloomData()
    }

    @Synchronized
    fun write(data: BloomData) {
        val bytes = MAGIC + Crypto.seal(keyProvider(), JSON.encodeToString(BloomData.serializer(), data).toByteArray(), MAGIC)
        tmp.writeBytes(bytes)
        if (file.exists()) file.copyTo(bak, overwrite = true)
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
    }

    fun wipe() { file.delete(); bak.delete(); tmp.delete() }

    private fun decode(raw: ByteArray): BloomData {
        require(raw.size > MAGIC.size && raw.copyOf(MAGIC.size).contentEquals(MAGIC)) { "bad magic" }
        val plain = Crypto.open(keyProvider(), raw.copyOfRange(MAGIC.size, raw.size), MAGIC)
        return JSON.decodeFromString(BloomData.serializer(), String(plain))
    }

    class StoreCorruptException : Exception("Bloom's local data could not be decrypted")

    companion object {
        private val MAGIC = "BLM1".toByteArray()
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    }
}
