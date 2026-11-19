package app.katib.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Envelope encryption:
 *  - A single AES-256-GCM master key lives in Android Keystore (hardware-backed where available,
 *    non-exportable). It only ever wraps small data-encryption keys (DEKs).
 *  - Each audio file and the database passphrase get their own random DEK, wrapped by the master key.
 *  - Destroying the master key ("wipe everything") makes every file and the DB unreadable.
 */
object Crypto {
    private const val ALIAS = "katib_master_v1"
    private const val ANDROID_KS = "AndroidKeyStore"
    private val rng = SecureRandom()

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KS)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }

    /** Wraps a DEK with the Keystore master key. Output = iv(12) || ciphertext+tag. */
    fun wrap(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, masterKey())
        return c.iv + c.doFinal(plain)
    }

    fun unwrap(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, blob, 0, 12))
        return c.doFinal(blob, 12, blob.size - 12)
    }

    fun randomBytes(n: Int) = ByteArray(n).also { rng.nextBytes(it) }

    /** SQLCipher passphrase: random 32 bytes, stored wrapped in no-backup dir. */
    fun dbPassphrase(ctx: Context): ByteArray {
        val f = File(ctx.noBackupFilesDir, "db.key")
        if (f.exists()) return unwrap(f.readBytes())
        val pass = randomBytes(32)
        f.writeBytes(wrap(pass))
        return pass
    }

    /** Destroys the master key. All encrypted data becomes permanently unreadable. */
    fun destroyAll(ctx: Context) {
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
            ks.deleteEntry(ALIAS)
        }
        File(ctx.noBackupFilesDir, "db.key").delete()
    }
}
