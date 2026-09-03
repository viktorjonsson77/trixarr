package org.jellyfin.androidtv.seerr

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SeerrAuthStore {
	fun readSession(profileKey: String): String?
	fun writeSession(profileKey: String, cookie: String)
	fun clearSession(profileKey: String)
	fun clearAll()
}

class KeystoreSeerrAuthStore(context: Context) : SeerrAuthStore {
	private val preferences = context.getSharedPreferences("seerr_secure_sessions", Context.MODE_PRIVATE)
	private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

	private fun key(): SecretKey {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			throw UnsupportedOperationException("Secure Seerr sessions require Android 6.0 or newer")
		}
		(keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
		return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
			init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
			generateKey()
		}
	}

	override fun readSession(profileKey: String): String? = runCatching {
		val stored = preferences.getString(profileKey, null) ?: return null
		val bytes = Base64.decode(stored, Base64.NO_WRAP)
		val iv = bytes.copyOfRange(0, IV_BYTES)
		val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
		String(cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)), Charsets.UTF_8)
	}.getOrNull()

	override fun writeSession(profileKey: String, cookie: String) {
		val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
		val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(cookie.toByteArray()), Base64.NO_WRAP)
		preferences.edit { putString(profileKey, encoded) }
	}

	override fun clearSession(profileKey: String) { preferences.edit { remove(profileKey) } }
	override fun clearAll() { preferences.edit { clear() } }

	private companion object { const val KEY_ALIAS = "jellyfintv_seerr_sessions_v1"; const val TRANSFORMATION = "AES/GCM/NoPadding"; const val IV_BYTES = 12 }
}
