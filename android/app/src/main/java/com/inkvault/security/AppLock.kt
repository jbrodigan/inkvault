package com.inkvault.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Opt-in vault lock (Section C1): a biometric prompt with device-credential (PIN/pattern/password)
 * fallback, so it works on any device that has *some* secure lock. Just a gate — no CryptoObject
 * yet; when the encrypted DB (C2) lands, tie the SQLCipher passphrase to a CryptoObject here.
 */
object AppLock {
    private const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /** True only if the device can actually authenticate (a biometric or device credential is set up). */
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Show the system unlock prompt; [onResult] is true on success, false on error/cancel. */
    fun prompt(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
                // onAuthenticationFailed = one bad attempt; the prompt stays up, so we don't act on it.
            },
        )
        // Note: with DEVICE_CREDENTIAL allowed, the API forbids a negative button, so we set none.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock InkVault")
            .setSubtitle("Verify it's you to open your notes")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}
