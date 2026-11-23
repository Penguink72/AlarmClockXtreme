package com.sysadmindoc.alarmclock.ui.languagelab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sysadmindoc.alarmclock.R
import com.sysadmindoc.alarmclock.ui.settings.Lp2PreviewText
import com.sysadmindoc.alarmclock.util.LocaleRuntimeRouting
import com.sysadmindoc.alarmclock.util.LocaleRuntimeTextBridge
import com.sysadmindoc.alarmclock.util.LocaleContextTextAdapter
import com.sysadmindoc.alarmclock.ui.theme.AlarmClockXtremeTheme

/** Explicit Debug-only LP2-A settings adapter, no alarm scheduling side effects. */
class Lp2SettingsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlarmClockXtremeTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Lp2SettingsPreviewScreen(onClose = ::finish)
                }
            }
        }
    }
}

@Composable
private fun Lp2SettingsPreviewScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val initial = remember { Lp2PreviewText.options(context) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var primary by remember { mutableStateOf(initial.primary) }
    var secondary by remember { mutableStateOf(initial.secondary) }
    var dual by remember { mutableStateOf(initial.dual) }
    var installed by remember {
        mutableStateOf(runCatching { Lp2PreviewText.available(context) }.getOrDefault(emptyMap()))
    }
    var status by remember { mutableStateOf("intro") }
    var detail by remember { mutableStateOf("") }
    fun apply() {
        val applied = runCatching {
            Lp2PreviewText.configure(
                context,
                Lp2PreviewText.Options(enabled = enabled, primary = primary, secondary = secondary, dual = dual)
            )
        }
        status = if (applied.getOrNull() == true) "saved" else "error"
        detail = applied.exceptionOrNull()?.message ?: Lp2PreviewText.lastError().orEmpty()
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = runCatching {
                val stream = requireNotNull(context.contentResolver.openInputStream(uri))
                Lp2PreviewText.importFromStream(context, stream)
            }
            result.onSuccess {
                installed = Lp2PreviewText.available(context)
                primary = it.locale
                enabled = true
                apply()
                if (status != "error") status = "imported"
            }.onFailure {
                status = "error"
                detail = it.message.orEmpty()
            }
        }
    }
    Scaffold(topBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.lp2a_header), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onClose) { Text(stringResource(R.string.lp2a_back)) }
        }
    }) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.lp2a_warning), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.lp2a_revision), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text(stringResource(R.string.lp2a_import))
                }
                OutlinedButton(onClick = {
                    enabled = false
                    apply()
                }) {
                    Text(stringResource(R.string.lp2a_disable))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = enabled, onCheckedChange = {
                    enabled = it
                    apply()
                })
                Text(stringResource(R.string.lp2a_enabled))
            }
            Lp2LocaleSelector(
                title = stringResource(R.string.lp2a_primary),
                selected = primary,
                available = installed,
                onSelect = { primary = it; apply() }
            )
            Lp2LocaleSelector(
                title = stringResource(R.string.lp2a_secondary),
                selected = secondary,
                available = installed,
                onSelect = { secondary = it; apply() }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = dual, onCheckedChange = {
                    dual = it
                    apply()
                })
                Text(stringResource(R.string.lp2a_dual))
            }
            Text(
                stringResource(R.string.lp2a_count, Lp2PreviewText.pilotKeys.size),
                style = MaterialTheme.typography.titleMedium
            )
            Text(installed.entries.joinToString("   ") { it.key + " " + it.value + "/200" })
            HorizontalDivider()
            val currentStatus = when (status) {
                "imported" -> stringResource(R.string.lp2a_imported)
                "saved" -> stringResource(R.string.lp2a_saved)
                "error" -> stringResource(R.string.lp2a_error) + " " + detail
                else -> stringResource(R.string.lp2a_intro)
            }
            Text(currentStatus, style = MaterialTheme.typography.bodySmall,
                color = if (status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.lp2a_sample_label), style = MaterialTheme.typography.titleSmall)
            for (key in Lp2PreviewText.pilotKeys.take(6)) {
                Text(key, style = MaterialTheme.typography.labelSmall)
                val first = runCatching { Lp2PreviewText.resolveForDebug(key, primary, context) }.getOrNull()
                val second = if (dual) runCatching {
                    Lp2PreviewText.resolveForDebug(key, secondary, context)
                }.getOrNull() else null
                if (first != null) Text(first.value + if (first.fallback) " [EN fallback]" else "")
                if (second != null) Text(second.value + if (second.fallback) " [EN fallback]" else "",
                    color = MaterialTheme.colorScheme.secondary)
            }
            // L13-C: real Android resource lookup in a debug-only, read-only
            // surface. A native fallback is NOT an approved zh-TW/ja translation;
            // no call to LocaleManager.setApplicationLocales or DataStore.
            HorizontalDivider()
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
            for (localeTag in listOf("en", "zh-TW", "ja")) {
                val title = LocaleRuntimeTextBridge.previewBuiltin(
                    context, localeTag, R.string.notif_alarm_title
                )
                val dismiss = LocaleRuntimeTextBridge.previewBuiltin(
                    context, localeTag, R.string.notif_dismiss_action
                )
                val decision = LocaleRuntimeRouting.builtinDecision(localeTag)
                val snooze = LocaleContextTextAdapter.formatted(
                    context, decision, R.string.notif_snooze_action, 10
                )
                Text(localeTag, style = MaterialTheme.typography.labelLarge)
                Text(title.value + " · " + snooze.value + " · " + dismiss.value)
                Text(title.origin, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
            OutlinedButton(onClick = {
                context.startActivity(Intent(context, Lp1LanguageLabActivity::class.java))
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.lp2a_open_legacy))
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.lp2a_return_to_settings))
            }
        }
    }
}

@Composable
private fun Lp2LocaleSelector(
    title: String,
    selected: String,
    available: Map<String, Int>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { expanded = true }) { Text(selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            available.keys.forEach { locale ->
                DropdownMenuItem(
                    text = { Text(locale + "  " + available.getValue(locale) + "/200") },
                    onClick = { expanded = false; onSelect(locale) }
                )
            }
        }
    }
}
