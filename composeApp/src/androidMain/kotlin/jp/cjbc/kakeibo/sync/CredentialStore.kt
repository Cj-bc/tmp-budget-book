package jp.cjbc.kakeibo.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val PREFERENCES_NAME = "kakeibo-credentials"
private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "kakeibo-credentials"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val TAG_BITS = 128

/**
 * Secrets kept in the app's own preferences, each encrypted on its own with a key in the Android
 * Keystore, which never leaves it. A secret that cannot be decrypted, say because the key was
 * lost, reads as absent.
 */
internal class CredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(name: String): String? {
        val stored = preferences.getString(name, null) ?: return null
        return runCatching {
            val (iv, encrypted) = stored.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(encrypted).decodeToString()
        }.onFailure { System.err.println("Sync: could not decrypt $name: $it") }.getOrNull()
    }

    /** Stores [value] for [name], or removes it if `null`. */
    fun write(name: String, value: String?) {
        val stored = value?.let {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            listOf(cipher.iv, cipher.doFinal(it.encodeToByteArray())).joinToString(":") { bytes ->
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }
        preferences.edit().apply { if (stored == null) remove(name) else putString(name, stored) }.apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as SecretKey?)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }
}
