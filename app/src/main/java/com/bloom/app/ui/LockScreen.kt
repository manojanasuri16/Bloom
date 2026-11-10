package com.bloom.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LockScreen(vm: AppViewModel) {
    val c = Bloom.colors
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateCompat()
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("Enter your PIN") }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun biometric() {
        val act = ctx as? FragmentActivity ?: return
        if (BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) return
        val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(ctx), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { vm.unlock() }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Bloom")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Use PIN").build())
    }
    LaunchedEffect(Unit) { if (s.biometricEnabled) biometric() }
    // Show lockout countdown.
    LaunchedEffect(msg, busy) {
        while (vm.lockedUntil() > System.currentTimeMillis()) {
            msg = "Too many attempts. Try again in ${((vm.lockedUntil() - System.currentTimeMillis()) / 1000 + 1)}s"
            delay(500)
        }
    }

    fun submit(p: String) {
        if (vm.lockedUntil() > System.currentTimeMillis()) { pin = ""; return }
        busy = true
        scope.launch {
            val ok = vm.tryPin(p)
            busy = false
            if (!ok) {
                pin = ""; msg = "Incorrect PIN"
                shake.snapTo(0f)
                shake.animateTo(1f, spring(dampingRatio = 0.25f, stiffness = 500f))
                shake.snapTo(0f)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.cream).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(96.dp))
        Text("Bloom", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(10.dp))
        Text(msg, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        Spacer(Modifier.height(32.dp))
        Row(Modifier.offset { IntOffset((kotlin.math.sin(shake.value * 18f) * (1f - shake.value) * 28f).roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) c.rose else c.hairline))
            }
        }
        Spacer(Modifier.weight(1f))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (s.biometricEnabled) "bio" else "", "0", "del")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(if (k.isEmpty()) androidx.compose.ui.graphics.Color.Transparent else c.surface)
                            .let { if (k.isEmpty()) it else it.bloomClick(!busy) {
                                when (k) {
                                    "bio" -> biometric()
                                    "del" -> pin = pin.dropLast(1)
                                    else -> if (pin.length < 8) pin += k
                                }
                            } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(when (k) { "bio" -> "ID"; "del" -> "⌫"; else -> k }, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).bloomClick(pin.length >= 4 && !busy) { submit(pin) }.padding(16.dp)) {
            Text("Unlock", style = MaterialTheme.typography.labelLarge, color = if (pin.length >= 4) c.rose else c.inkMuted)
        }
    }
}
