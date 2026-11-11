package com.sysadmindoc.alarmclock.ui.ringtone

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import com.sysadmindoc.alarmclock.R

@Composable
fun MusicLibraryDialog(
    onDismiss: () -> Unit,
    viewModel: MusicLibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.scan() }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                RingtoneFolderStore.addFolder(context, uri)
            }.onSuccess {
                viewModel.scan()
            }.onFailure { error ->
                viewModel.reportStatus(
                    "Could not keep access to that folder: ${error.message.orEmpty()}"
                )
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
                Text(stringResource(R.string.music_library_track_count, state.count))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
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
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        }
                        Text(
                            stringResource(
                                if (state.scanning) R.string.music_library_scanning
                                else R.string.music_library_scan
                            )
                        )
                    }
                    OutlinedButton(
                        enabled = !state.scanning,
                        onClick = { folderLauncher.launch(null) }
                    ) {
                        Text(stringResource(R.string.music_library_add_folder))
                    }
                }
                if (state.status.isNotBlank()) {
                    Text(state.status, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(state.selectedDate.toString(), style = MaterialTheme.typography.titleSmall)
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
                            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(
                                Date(entry.startedAt)
                            )
                            Text("${entry.playOrder}. $time — ${entry.title}")
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
}
