package com.bloom.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.bloom.app.domain.ThemeMode
import com.bloom.app.ui.AppViewModel
import com.bloom.app.ui.Bloom
import com.bloom.app.ui.BloomTheme
import com.bloom.app.ui.CalendarScreen
import com.bloom.app.ui.InsightsScreen
import com.bloom.app.ui.LockScreen
import com.bloom.app.ui.LogSheet
import com.bloom.app.ui.OnboardingScreen
import com.bloom.app.ui.SettingsScreen
import com.bloom.app.ui.TodayScreen
import com.bloom.app.ui.bloomClick
import com.bloom.app.ui.collectAsStateCompat
import java.time.LocalDate

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()
    private var stoppedAt = 0L
    private val openLog = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplash()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openLog.value = intent?.getBooleanExtra("open_log", false) == true
        setContent {
            val s by vm.settings.collectAsStateCompat()
            val dark = when (s.themeMode) { ThemeMode.SYSTEM -> null; ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
            // Block screenshots / blank the recents thumbnail while enabled.
            LaunchedEffect(s.hideInRecents) {
                if (s.hideInRecents) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            BloomTheme(dark) {
                val locked by vm.locked.collectAsStateCompat()
                Box(Modifier.fillMaxSize().background(Bloom.colors.cream)) {
                    when {
                        vm.loadError -> DataError()
                        !s.onboardingDone -> OnboardingScreen(vm) {}
                        else -> Shell(vm, openLog.value) { openLog.value = false }
                    }
                    Crossfade(locked && s.lockEnabled, animationSpec = tween(250), label = "lock") { l -> if (l) LockScreen(vm) }
                }
            }
        }
    }

    private fun installSplash() { installSplashScreen() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_log", false)) openLog.value = true
    }

    override fun onStop() { super.onStop(); stoppedAt = System.currentTimeMillis() }

    override fun onStart() {
        super.onStart()
        vm.refreshToday()
        // Re-lock after 30 s in the background.
        if (stoppedAt != 0L && System.currentTimeMillis() - stoppedAt > 30_000) vm.lockIfEnabled()
    }
}

@Composable
private fun DataError() {
    val c = Bloom.colors
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text("Bloom couldn't unlock its data", style = MaterialTheme.typography.headlineMedium)
        Text("Android's secure key store no longer has the key for this data (this can happen after some system resets). " +
            "Your data has not been deleted, but it can't be read. Restore from a backup after reinstalling, or clear app data to start fresh.",
            style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.padding(top = 12.dp))
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.WaterDrop), CALENDAR("Calendar", Icons.Rounded.CalendarMonth),
    INSIGHTS("Insights", Icons.Rounded.Insights), SETTINGS("Settings", Icons.Rounded.Settings)
}

@Composable
private fun Shell(vm: AppViewModel, openLogRequested: Boolean, consumed: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var logDate by remember { mutableStateOf<LocalDate?>(null) }
    val locked by vm.locked.collectAsStateCompat()
    LaunchedEffect(openLogRequested, locked) {
        if (openLogRequested && !locked) { logDate = LocalDate.now(); tab = Tab.TODAY; consumed() }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(tab, label = "tab", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { t ->
            when (t) {
                Tab.TODAY -> TodayScreen(vm) { logDate = vm.today.value }
                Tab.CALENDAR -> CalendarScreen(vm) { logDate = it }
                Tab.INSIGHTS -> InsightsScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
        // Soft scroll-edge fade instead of a hard divider where content slides under the floating bar.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(130.dp).background(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, Bloom.colors.cream))))
        TabBar(tab, Modifier.align(Alignment.BottomCenter)) { tab = it }
    }
    logDate?.let { LogSheet(vm, it) { logDate = null } }
}

@Composable
private fun TabBar(selected: Tab, modifier: Modifier, onSelect: (Tab) -> Unit) {
    val c = Bloom.colors
    Row(
        modifier.navigationBarsPadding().padding(bottom = 12.dp).shadow(14.dp, RoundedCornerShape(30.dp), ambientColor = c.rose, spotColor = c.rose)
            .clip(RoundedCornerShape(30.dp)).background(c.surface)
            .border(1.dp, c.hairline, RoundedCornerShape(30.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == selected
            Row(
                Modifier.clip(RoundedCornerShape(24.dp)).background(if (on) c.roseSoft else androidx.compose.ui.graphics.Color.Transparent)
                    .bloomClick(role = androidx.compose.ui.semantics.Role.Tab) { onSelect(t) }
                    .padding(horizontal = if (on) 16.dp else 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(t.icon, t.label, tint = if (on) c.rose else c.inkMuted, modifier = Modifier.padding(0.dp))
                if (on) Text(t.label, style = MaterialTheme.typography.labelLarge, color = c.rose)
            }
        }
    }
}
