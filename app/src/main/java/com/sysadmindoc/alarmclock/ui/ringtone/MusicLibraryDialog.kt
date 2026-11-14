package com.sysadmindoc.alarmclock.ui.ringtone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sysadmindoc.alarmclock.R
import java.util.Date

@Composable
fun MusicLibraryDialog(
    onDismiss: () -> Unit,
    viewModel: MusicLibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSources by remember { mutableStateOf(false) }
    var showPool by remember { mutableStateOf(false) }
    var showDurationChoices by remember { mutableStateOf(false) }
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.autoScanIfNeeded() }

    LaunchedEffect(viewModel) {
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.autoScanIfNeeded()
        } else {
            // Ask once. Subsequent visits still scan previously granted SAF
            // sources without repeatedly demanding device-media permission.
            val prefs = context.getSharedPreferences("music_library_permission", 0)
            if (!prefs.getBoolean("requested_audio_permission", false)) {
                prefs.edit().putBoolean("requested_audio_permission", true).apply()
                permissionLauncher.launch(permission)
            } else {
                viewModel.autoScanIfNeeded()
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.music_library_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(
                        R.string.music_library_media_counts,
                        state.count,
                        state.audioCount,
                        state.videoCount
                    )
                )
                // Stacked full-width actions: three side-by-side buttons were
                // clipped on narrow phones, especially with zh-TW / ja labels.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.scanning,
                        onClick = { showSources = true }
                    ) {
                        Text(stringResource(R.string.music_library_sources_button))
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.scanning,
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, permission) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.scan()
                            } else {
                                permissionLauncher.launch(permission)
                            }
                        }
                    ) {
                        if (state.scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp).size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            stringResource(
                                if (state.scanning) R.string.music_library_scanning
                                else R.string.music_library_scan
                            )
                        )
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.scanning,
                        onClick = { showPool = true }
                    ) {
                        Text(stringResource(R.string.music_library_pool_manage))
                    }
                }
                Text(
                    stringResource(R.string.music_library_auto_pool_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(
                        enabled = !state.scanning && !state.loadingPool,
                        onClick = { showDurationChoices = true }
                    ) {
                        Text(
                            if (state.minimumAutoDurationSeconds == 0) {
                                stringResource(R.string.music_library_auto_no_minimum)
                            } else {
                                stringResource(
                                    R.string.music_library_auto_minimum,
                                    state.minimumAutoDurationSeconds
                                )
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = showDurationChoices,
                        onDismissRequest = { showDurationChoices = false }
                    ) {
                        MusicAutoPoolPolicy.availableMinimumSeconds.forEach { seconds ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (seconds == 0) {
                                            stringResource(R.string.music_library_auto_no_minimum)
                                        } else {
                                            stringResource(R.string.music_library_auto_minimum, seconds)
                                        }
                                    )
                                },
                                onClick = {
                                    showDurationChoices = false
                                    viewModel.updateMinimumAutoDuration(seconds)
                                }
                            )
                        }
                    }
                }
                if (state.status.isNotBlank()) {
                    Text(state.status, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        java.time.format.DateTimeFormatter
                            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                            .withLocale(context.resources.configuration.locales[0])
                            .format(state.selectedDate),
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(
                        onClick = {
                            val date = state.selectedDate
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    viewModel.loadHistory(java.time.LocalDate.of(year, month + 1, day))
                                },
                                date.year,
                                date.monthValue - 1,
                                date.dayOfMonth
                            ).show()
                        }
                    ) {
                        Text(stringResource(R.string.music_library_choose_date))
                    }
                }
                if (state.history.isEmpty()) {
                    Text(stringResource(R.string.music_library_no_history))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.history, key = { it.id }) { entry ->
                            val time = android.text.format.DateFormat.getTimeFormat(context).format(
                                Date(entry.startedAt)
                            )
                            Text(
                                stringResource(
                                    R.string.music_library_history_row,
                                    entry.playOrder,
                                    time,
                                    entry.title
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.music_library_close))
            }
        }
    )

    if (showSources) {
        MusicLibrarySourcesDialog(
            viewModel = viewModel,
            onDismiss = { showSources = false }
        )
    }
    if (showPool) {
        MusicPoolDialog(
            viewModel = viewModel,
            onDismiss = { showPool = false }
        )
    }
}
