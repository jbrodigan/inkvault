package com.inkvault.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * One secret encrypted at rest with an AES-256-GCM key held in the Android Keystore (hardware-backed
 * when the device has a TEE/StrongBox). The key never leaves the Keystore and the plaintext never
 * touches disk — only {iv : ciphertext} is persisted, in a private SharedPreferences. This backs the
 * "enter once, stored encrypted" pen password: it stays on-device and is never sent anywhere.
 *
 * Native platform crypto on purpose. androidx.security:security-crypto (EncryptedSharedPreferences)
 * was deprecated by Jetpack in 2024, and this needs exactly one key and one value — the JDK crypto
 * APIs + Keystore cover it with no extra dependency. Note: one secret per [name]; if multiple
 * pens ever need distinct passwords, key the stored entries by MAC instead of a fixed [name].
 */
class SecretStore(context: Context, private val name: String) {

    private val prefs =
        context.applicationContext.getSharedPreferences("inkvault_secrets", Context.MODE_PRIVATE)
    private val alias = "inkvault_secret_$name"
    private val atKey = "${name}_saved_at" // when the secret was last stored (for expiry)

    /** The stored plaintext, or null if nothing is stored (or it can't be decrypted any more). */
    fun get(): String? {
        val blob = prefs.getString(name, null) ?: return null
        return runCatching {
            val parts = blob.split(":")
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            Cipher.getInstance(TRANSFORM).run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                String(doFinal(ct), Charsets.UTF_8)
            }
        }.getOrNull()
    }

    /** Encrypt and persist [value], replacing anything stored before. Records the save time. */
    fun set(value: String) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ct, Base64.NO_WRAP)
        prefs.edit().putString(name, blob).putLong(atKey, System.currentTimeMillis()).apply()
    }

    /** Forget the secret (e.g. the user disabled it, or turned off "remember"). */
    fun clear() = prefs.edit().remove(name).remove(atKey).apply()

    /** Whether a secret is currently stored — cheap, no decryption. */
    fun has(): Boolean = prefs.contains(name)

    /** Whether a secret is stored AND was saved within the last [days] (i.e. not expired). */
    fun savedWithinDays(days: Int): Boolean {
        val savedAt = prefs.getLong(atKey, 0L)
        if (savedAt <= 0L) return false
        return System.currentTimeMillis() - savedAt < days * 24L * 60 * 60 * 1000
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
