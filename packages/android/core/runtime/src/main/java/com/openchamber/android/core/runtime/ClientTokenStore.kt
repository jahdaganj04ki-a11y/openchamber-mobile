package com.openchamber.android.core.runtime

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
 * The client token, kept in the Android Keystore.
 *
 * The Keystore holds the key, not the token: the token is encrypted with a
 * key that never leaves the secure hardware, and the ciphertext sits in the
 * app's private preferences. The token is the only credential the app holds,
 * and it is worthless outside this phone, because the server binds loopback.
 */
class ClientTokenStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): String? {
        val stored = preferences.getString(TOKEN_KEY, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, stored.decodeBase64Iv()))
            val plaintext = cipher.doFinal(stored.decodeCiphertext())
            String(plaintext, Charsets.UTF_8).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun write(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(TOKEN_KEY, cipher.iv.encodeBase64() + ":" + ciphertext.encodeBase64())
            .apply()
    }

    fun clear() {
        preferences.edit().remove(TOKEN_KEY).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun String.decodeBase64Iv(): ByteArray =
        Base64.decode(substringBefore(':'), Base64.NO_WRAP)

    private fun String.decodeCiphertext(): ByteArray =
        Base64.decode(substringAfter(':'), Base64.NO_WRAP)

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    companion object {
        private const val PREFERENCES_NAME = "openchamber.client"
        private const val TOKEN_KEY = "client.token"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "openchamber.client-token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
