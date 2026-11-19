package mx.edu.utez.wallet.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class BiometricBinding(val grantId: String, val token: String, val userId: String,
    val username: String, val demo: Boolean, val serverUrl: String, val expiresAt: Long)

/** Stores a device grant, never a password. Decryption requires a fresh strong biometric. */
class BiometricAccess(private val context: Context) {
    private val preferences = context.getSharedPreferences("biometric-access-v1", Context.MODE_PRIVATE)
    private var prompt: BiometricPrompt? = null
    fun hasEntry() = preferences.contains("encrypted")
    fun username(): String = preferences.getString("username", "").orEmpty()
    fun grantId(): String? = preferences.getString("grantId", null)
    fun isFor(userId: String, demo: Boolean, serverUrl: String): Boolean = hasEntry() &&
        preferences.getString("userId", null) == userId && preferences.getBoolean("demo", true) == demo &&
        (demo || preferences.getString("serverUrl", null) == serverUrl.trimEnd('/'))
    fun availability(): Pair<Boolean, String> = when (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> true to "Usa la huella o la biometría segura de este teléfono."
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false to "Primero registra una huella en los ajustes de tu teléfono."
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> false to "El sensor no está disponible por el momento. Usa tu contraseña."
        else -> false to "Este dispositivo no ofrece biometría fuerte compatible. Puedes usar tu contraseña."
    }
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun deleteKey(alias: String?) { if (alias != null) runCatching { keyStore().deleteEntry(alias) } }
    fun clear() {
        val alias = preferences.getString("alias", null)
        preferences.edit().clear().commit()
        deleteKey(alias)
    }
    private fun cipher() = Cipher.getInstance("AES/GCM/NoPadding")
    private fun key(alias: String): SecretKey = (keyStore().getKey(alias, null) as? SecretKey)
        ?: throw IllegalStateException("El acceso biométrico cambió. Inicia sesión con contraseña y actívalo de nuevo.")
    private fun createKey(alias: String): SecretKey {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        else @Suppress("DEPRECATION") builder.setUserAuthenticationValidityDurationSeconds(-1)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(builder.build()); generateKey() }
    }
    private fun authenticate(activity: FragmentActivity, title: String, cipher: Cipher,
        success: (Cipher) -> Unit, failure: (String) -> Unit) {
        prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                try { success(requireNotNull(result.cryptoObject?.cipher)) }
                catch (_: Exception) { failure("No se pudo guardar o recuperar el acceso biométrico. Usa tu contraseña e inténtalo de nuevo.") }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                failure(if (errorCode in listOf(BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED))
                    "Autenticación cancelada. Puedes usar tu contraseña." else errString.toString())
            }
            // Failed attempts stay inside Android's prompt; no session is created here.
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title)
            .setSubtitle("UTEZ Wallet · biometría del dispositivo")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancelar").build()
        requireNotNull(prompt).authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
    fun enroll(activity: FragmentActivity, binding: BiometricBinding, success: () -> Unit, failure: (String) -> Unit) {
        val alias = "utez_wallet_biometric_${UUID.randomUUID()}"
        try {
            val crypto = cipher().apply { init(Cipher.ENCRYPT_MODE, createKey(alias)) }
            authenticate(activity, "Activar acceso biométrico", crypto, { authenticated ->
                val payload = JSONObject().put("grantId", binding.grantId).put("token", binding.token)
                    .put("userId", binding.userId).put("username", binding.username).put("demo", binding.demo)
                    .put("serverUrl", binding.serverUrl.trimEnd('/')).put("expiresAt", binding.expiresAt)
                    .toString().toByteArray(Charsets.UTF_8)
                val encrypted = try { authenticated.doFinal(payload) } finally { payload.fill(0) }
                val oldAlias = preferences.getString("alias", null)
                check(preferences.edit().clear().putString("alias", alias)
                    .putString("encrypted", Base64.getEncoder().encodeToString(encrypted))
                    .putString("iv", Base64.getEncoder().encodeToString(authenticated.iv))
                    .putString("username", binding.username).putString("userId", binding.userId)
                    .putString("grantId", binding.grantId).putBoolean("demo", binding.demo)
                    .putString("serverUrl", binding.serverUrl.trimEnd('/')).commit())
                deleteKey(oldAlias); success()
            }, { message -> deleteKey(alias); failure(message) })
        } catch (_: Exception) { deleteKey(alias); failure("No se pudo activar la biometría. Comprueba que tengas una huella registrada.") }
    }
    fun unlock(activity: FragmentActivity, success: (BiometricBinding) -> Unit, failure: (String) -> Unit) {
        try {
            val alias = requireNotNull(preferences.getString("alias", null))
            val iv = Base64.getDecoder().decode(requireNotNull(preferences.getString("iv", null)))
            val encrypted = Base64.getDecoder().decode(requireNotNull(preferences.getString("encrypted", null)))
            val crypto = cipher().apply { init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(128, iv)) }
            authenticate(activity, "Entrar a UTEZ Wallet", crypto, { authenticated ->
                val plaintext = authenticated.doFinal(encrypted)
                val payload = try { JSONObject(plaintext.toString(Charsets.UTF_8)) } finally { plaintext.fill(0) }
                val binding = BiometricBinding(payload.getString("grantId"), payload.getString("token"),
                    payload.getString("userId"), payload.getString("username"), payload.getBoolean("demo"),
                    payload.getString("serverUrl"), payload.getLong("expiresAt"))
                if (System.currentTimeMillis() >= binding.expiresAt) {
                    clear(); failure("El acceso biométrico venció. Inicia sesión con tu contraseña.")
                } else success(binding)
            }, failure)
        } catch (_: KeyPermanentlyInvalidatedException) {
            clear(); failure("La biometría del teléfono cambió. Inicia sesión con contraseña y actívala de nuevo.")
        } catch (_: Exception) { clear(); failure("No se pudo recuperar el acceso. Inicia sesión con tu contraseña.") }
    }
}
