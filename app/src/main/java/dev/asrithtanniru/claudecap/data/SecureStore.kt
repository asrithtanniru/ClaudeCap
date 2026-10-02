package dev.asrithtanniru.claudecap.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts prefs values with an Android Keystore AES-GCM key. Never log the plaintext. */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun putString(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv.size.toByte().let { ivLen ->
            ByteArray(1 + cipher.iv.size + ciphertext.size).also { out ->
                out[0] = ivLen
                System.arraycopy(cipher.iv, 0, out, 1, cipher.iv.size)
                System.arraycopy(ciphertext, 0, out, 1 + cipher.iv.size, ciphertext.size)
            }
        }
        prefs.edit { putString(key, Base64.encodeToString(payload, Base64.NO_WRAP)) }
    }

    fun getString(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val ivLen = payload[0].toInt()
        val iv = payload.copyOfRange(1, 1 + ivLen)
        val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "secure_store"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "claude_usage_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        const val KEY_COOKIE = "cookie"
        const val KEY_USER_AGENT = "user_agent"
        const val KEY_ORG_ID = "org_id"
    }
}
