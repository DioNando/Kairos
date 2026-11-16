package com.kairos.app.ui.security

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Empreinte / visage **ou** code de déverrouillage de l'appareil. BIOMETRIC_WEAK | DEVICE_CREDENTIAL
 * est la seule combinaison avec code acceptée dès Android 9 ; le code sert de repli quand la biométrie
 * échoue ou n'est pas configurée.
 */
private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

private fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

/** Résultat d'une demande d'authentification. */
enum class AuthResult { SUCCESS, CANCELLED, UNAVAILABLE }

/** true si l'appareil est protégé (biométrie ou code) : sinon aucune authentification n'est possible. */
fun canAuthenticate(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Demande à l'utilisateur de prouver son identité (empreinte ou code de l'appareil).
 * [UNAVAILABLE] si l'appareil n'a ni biométrie ni code : à l'appelant de prévoir une confirmation de
 * repli plutôt que d'enfermer l'utilisateur.
 */
@Composable
fun rememberDeviceAuth(title: String, subtitle: String, onResult: (AuthResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest = rememberUpdatedState(onResult)
    return remember(context, title, subtitle) {
        {
            val activity = context.findActivity() as? FragmentActivity
            if (activity == null || !canAuthenticate(context)) {
                latest.value(AuthResult.UNAVAILABLE)
            } else {
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            latest.value(AuthResult.SUCCESS)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            latest.value(AuthResult.CANCELLED)
                        }
                    },
                )
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setAllowedAuthenticators(AUTHENTICATORS)
                        .build(),
                )
            }
        }
    }
}

/** Bloque captures d'écran et aperçu des apps récentes tant que [active] (clé visible à l'écran). */
@Composable
fun SecureWindow(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active) {
        val window = context.findActivity()?.window
        if (active) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
