package com.bloom.app.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bloom.app.BuildConfig
import com.bloom.app.data.ImportException
import com.bloom.app.data.ImportPreview
import com.bloom.app.data.Portability
import com.bloom.app.domain.Narrative
import com.bloom.app.domain.ThemeMode
import com.bloom.app.security.PinLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(vm: AppViewModel, onShowOnboardingHelp: () -> Unit = {}) {
    val c = Bloom.colors
    val s by vm.settings.collectAsStateCompat()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.update { it.copy(dailyReminder = true) }
    }
    val notifPermPeriod = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.update { it.copy(periodReminder = true) }
    }
    fun needsNotifPermission() = Build.VERSION.SDK_INT >= 33 &&
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))

        Group("Your cycle") {
            Stepper("Typical cycle", s.defaultCycleLength, 21..45, " d") { v -> vm.update { it.copy(defaultCycleLength = v) } }
            Stepper("Typical period", s.defaultPeriodLength, 2..10, " d") { v -> vm.update { it.copy(defaultPeriodLength = v) } }
            Stepper("Luteal phase", s.lutealLength, 9..16, " d") { v -> vm.update { it.copy(lutealLength = v) } }
            Hint("Used until Bloom has learned your own patterns. Luteal phase averages about 12–13 days; temperature logs can personalise it.")
        }

        Group("Appearance") {
            Segmented(listOf("System", "Light", "Dark"), ThemeMode.entries.indexOf(s.themeMode)) { i ->
                vm.update { it.copy(theme = ThemeMode.entries[i].name) }
            }
            ToggleRow("Week starts on Monday", null, s.weekStartsMonday) { v -> vm.update { it.copy(weekStartsMonday = v) } }
            ToggleRow("Temperature in °F", null, s.fahrenheit) { v -> vm.update { it.copy(fahrenheit = v) } }
        }

        Group("Privacy & security") {
            ToggleRow("App lock", if (s.lockEnabled) "PIN required to open Bloom" else "Protect Bloom with a PIN", s.lockEnabled) { on ->
                dialog = if (on) "setpin" else "disablelock"
            }
            if (s.lockEnabled) {
                val bioOk = BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                if (bioOk) ToggleRow("Unlock with biometrics", null, s.biometricEnabled) { v -> vm.update { it.copy(biometricEnabled = v) } }
                ActionRow("Change PIN", null) { dialog = "setpin" }
            }
            ToggleRow("Hide in app switcher", "Blocks screenshots and blanks Bloom in recent apps (off by default)", s.hideInRecents) { v -> vm.update { it.copy(hideInRecents = v) } }
            ToggleRow("Discreet widgets", "Widgets show only a neutral dot — no cycle details", s.discreetWidgets) { v ->
                vm.update { it.copy(discreetWidgets = v) }
            }
        }

        Group("Reminders") {
            ToggleRow("Daily check-in", "A neutral notification at ${"%02d:%02d".format(s.dailyReminderHour, s.dailyReminderMinute)}", s.dailyReminder) { on ->
                if (on && needsNotifPermission()) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.update { it.copy(dailyReminder = on) }
            }
            if (s.dailyReminder) {
                Stepper("Hour", s.dailyReminderHour, 0..23) { v -> vm.update { it.copy(dailyReminderHour = v) } }
            }
            ToggleRow("Period heads-up", "${s.periodReminderDaysBefore} days before the predicted window", s.periodReminder) { on ->
                if (on && needsNotifPermission()) notifPermPeriod.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.update { it.copy(periodReminder = on) }
            }
            if (s.periodReminder) Stepper("Days before", s.periodReminderDaysBefore, 0..7) { v -> vm.update { it.copy(periodReminderDaysBefore = v) } }
            Hint("Reminders are scheduled on your device only. Notification text never mentions your cycle.")
        }

        DataSection(vm)

        Group("About Bloom") {
            Hint("Bloom stores everything encrypted on this phone. It has no account, no analytics, no ads and no internet permission — it is technically unable to send your data anywhere.")
            Hint(Narrative.DISCLAIMER)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        Spacer(Modifier.height(110.dp))
    }

    when (dialog) {
        "setpin" -> PinSetupDialog(vm, onDone = { dialog = null })
        "disablelock" -> PinConfirmDialog(vm, "Turn off app lock", onDone = { ok -> if (ok) vm.disableLock(); dialog = null })
    }
}

@Composable
fun Group(title: String, content: @Composable () -> Unit) {
    val c = Bloom.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        BloomCard { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() } }
    }
}

@Composable
fun Hint(t: String) = Text(t, style = MaterialTheme.typography.bodySmall, color = Bloom.colors.inkMuted, modifier = Modifier.padding(top = 4.dp))

@Composable
fun ToggleRow(label: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Bloom.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.rose, checkedThumbColor = c.onRose,
            uncheckedTrackColor = c.surfaceHigh, uncheckedBorderColor = c.hairline, uncheckedThumbColor = c.inkMuted))
    }
}

@Composable
fun ActionRow(label: String, sub: String?, destructive: Boolean = false, onClick: () -> Unit) {
    val c = Bloom.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bloomClick(onClick = onClick).padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (destructive) androidx.compose.ui.graphics.Color(0xFFD14B5B) else c.ink)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Bloom.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceHigh).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            androidx.compose.foundation.layout.Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) c.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .bloomClick(role = androidx.compose.ui.semantics.Role.Tab) { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { Text(o, style = MaterialTheme.typography.labelLarge, color = if (on) c.ink else c.inkMuted) }
        }
    }
}

// ---- PIN dialogs --------------------------------------------------------------------------------

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter(Char::isDigit).take(8)) }, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PinSetupDialog(vm: AppViewModel, onDone: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val valid = PinLock.isValidFormat(a) && a == b
    AlertDialog(
        onDismissRequest = onDone, title = { Text("Set a PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PinField(a, "New PIN (4–8 digits)") { a = it }
                PinField(b, "Confirm PIN") { b = it }
                if (b.isNotEmpty() && a != b) Text("PINs don't match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Hint("There is no recovery: if you forget your PIN you'll need to erase Bloom's data or restore from a backup.")
            }
        },
        confirmButton = { TextButton({ scope.launch { vm.setPin(a); onDone() } }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onDone) { Text("Cancel") } },
    )
}

@Composable
private fun PinConfirmDialog(vm: AppViewModel, title: String, onDone: (Boolean) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { onDone(false) }, title = { Text(title) },
        text = { Column { PinField(pin, "Current PIN") { pin = it; wrong = false }; if (wrong) Text("Wrong PIN", color = MaterialTheme.colorScheme.error) } },
        confirmButton = {
            TextButton({ scope.launch {
                val ok = withContext(Dispatchers.Default) { PinLock.verify(pin, vm.settings.value) }
                if (ok) onDone(true) else wrong = true
            } }, enabled = pin.length >= 4) { Text("Confirm") }
        },
        dismissButton = { TextButton({ onDone(false) }) { Text("Cancel") } },
    )
}

// ---- Data: export / import / backup / erase ---------------------------------------------------------

@Composable
private fun DataSection(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingText by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }       // "backup" | "erase"
    var importText by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    val stamp = LocalDate.now().toString()

    fun doc() = Portability.toDocument(vm.data.value, BuildConfig.VERSION_NAME, Instant.now().toString())

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val text = pendingText; pendingText = null
        if (uri != null && text != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }.isSuccess }
            if (ok) vm.update { it.copy(lastBackupEpochDay = LocalDate.now().toEpochDay()) }
            message = if (ok) "Saved." else "Couldn't write the file."
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)!!.use { it.readNBytes(32 * 1024 * 1024 + 1) }.also { require(it.size <= 32 * 1024 * 1024) }.toString(Charsets.UTF_8)
                }.getOrNull()
            }
            if (text == null) { message = "Couldn't read that file (or it's too large)."; return@launch }
            if (Portability.isEncrypted(text)) { importText = text; dialog = "password" }
            else runCatching { if (text.trimStart().startsWith("{")) Portability.parseJson(text) else Portability.parseCsv(text) }
                .onSuccess { preview = it }.onFailure { message = (it as? ImportException)?.message ?: "Couldn't read that file." }
        }
    }

    Group("Your data") {
        ActionRow("Export everything (JSON)", "Complete, portable copy of all your logs and settings") {
            pendingText = Portability.toJson(doc()); saveLauncher.launch("bloom-export-$stamp.json")
        }
        ActionRow("Export spreadsheet (CSV)", "One row per day, opens in any spreadsheet app") {
            pendingText = Portability.toCsv(doc()); saveLauncher.launch("bloom-days-$stamp.csv")
        }
        ActionRow("Create encrypted backup", "Password-protected file you can keep anywhere") { dialog = "backup" }
        ActionRow("Import or restore…", "From a Bloom JSON, CSV or encrypted backup") { openLauncher.launch(arrayOf("*/*")) }
        ActionRow("Erase all data", "Permanently deletes everything on this device", destructive = true) { dialog = "erase" }
        vm.settings.value.lastBackupEpochDay?.let { Hint("Last export: ${LocalDate.ofEpochDay(it)}") }
        message?.let { Hint(it) }
    }

    when (dialog) {
        "backup" -> PasswordDialog("Encrypt backup", confirm = true, onDismiss = { dialog = null }) { pw ->
            dialog = null
            scope.launch {
                message = "Encrypting…"
                pendingText = withContext(Dispatchers.Default) { Portability.encrypt(Portability.toJson(doc()), pw.toCharArray()) }
                message = null
                saveLauncher.launch("bloom-backup-$stamp.bloombackup")
            }
        }
        "password" -> PasswordDialog("Backup password", confirm = false, onDismiss = { dialog = null; importText = null }) { pw ->
            val t = importText; dialog = null
            scope.launch {
                runCatching { withContext(Dispatchers.Default) { Portability.parseJson(Portability.decrypt(t!!, pw.toCharArray())) } }
                    .onSuccess { preview = it }.onFailure { message = (it as? ImportException)?.message ?: "Couldn't open the backup." }
                importText = null
            }
        }
        "erase" -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text("Erase everything?") },
            text = { Text("All logs and settings will be permanently deleted from this device. Export a backup first if you might want them again.") },
            confirmButton = { TextButton({ vm.repo.eraseEverything(); dialog = null }) { Text("Erase", color = androidx.compose.ui.graphics.Color(0xFFD14B5B)) } },
            dismissButton = { TextButton({ dialog = null }) { Text("Cancel") } },
        )
    }

    preview?.let { p ->
        var replace by remember(p) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { preview = null }, title = { Text("Import ${p.dayCount} days") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (p.firstDate != null) "From ${p.firstDate} to ${p.lastDate}." else "No entries found.")
                    if (p.skipped > 0) Text("${p.skipped} unreadable entries will be skipped.", style = MaterialTheme.typography.bodySmall)
                    Segmented(listOf("Merge with mine", "Replace mine"), if (replace) 1 else 0) { replace = it == 1 }
                    Hint(if (replace) "Your current logs will be replaced by the file's." else "Days in the file overwrite the same day here; everything else is kept.")
                }
            },
            confirmButton = {
                TextButton({
                    vm.repo.replaceAll(Portability.apply(vm.data.value, p, replace, includeSettings = p.hasSettings && replace))
                    message = "Imported ${p.dayCount} days."; preview = null
                }, enabled = p.dayCount > 0) { Text("Import") }
            },
            dismissButton = { TextButton({ preview = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PasswordDialog(title: String, confirm: Boolean, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val valid = a.length >= 8 && (!confirm || a == b)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(a, { a = it }, label = { Text("Password (8+ characters)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                if (confirm) {
                    OutlinedTextField(b, { b = it }, label = { Text("Confirm password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Hint("Bloom cannot recover this password. Without it the backup can't be opened.")
                }
            }
        },
        confirmButton = { TextButton({ onOk(a) }, enabled = valid) { Text("Continue") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
