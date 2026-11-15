package com.sysadmindoc.alarmclock.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.alarmclock.R
import com.sysadmindoc.alarmclock.data.local.entity.PlaybackHistoryEntry
import com.sysadmindoc.alarmclock.service.ReplayUiState
import com.sysadmindoc.alarmclock.ui.ringtone.MusicMediaKind
import com.sysadmindoc.alarmclock.ui.ringtone.SupportedMediaFile
import com.sysadmindoc.alarmclock.ui.theme.AccentRed
import com.sysadmindoc.alarmclock.ui.theme.DismissGreen
import com.sysadmindoc.alarmclock.ui.theme.TextMuted
import com.sysadmindoc.alarmclock.ui.theme.TextPrimary
import com.sysadmindoc.alarmclock.ui.theme.TextSecondary

fun humanReadableReplayError(context: Context, error: ReplayUiState.Error): String {
    val reason = error.reason
    return when {
        reason.startsWith("ALARM_ACTIVE") -> {
            context.getString(R.string.replay_error_alarm_active)
        }
        reason.startsWith("HISTORY_NOT_FOUND") -> {
            context.getString(R.string.replay_error_history_not_found)
        }
        reason.contains("cleanup:") -> {
            val mainPart = reason.substringBefore("; cleanup:").trim()
            when (mainPart) {
                "ALARM_ACTIVE" -> context.getString(R.string.replay_error_alarm_active)
                "HISTORY_NOT_FOUND" -> context.getString(R.string.replay_error_history_not_found)
                else -> context.getString(R.string.replay_error_playback_failed)
            }
        }
        else -> {
            context.getString(R.string.replay_error_playback_failed)
        }
    }
}

fun resolveMimeType(context: Context, uri: Uri, title: String?): String {
    val typeFromResolver = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    if (!typeFromResolver.isNullOrBlank()) return typeFromResolver
    val kind = SupportedMediaFile.classify(title ?: uri.lastPathSegment, null)
    return if (kind == MusicMediaKind.VIDEO) "video/*" else "audio/*"
}

fun openInExternalApp(context: Context, uriString: String, title: String? = null): Result<Unit> {
    return runCatching {
        val uri = Uri.parse(uriString)
        val mimeType = resolveMimeType(context, uri, title)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(
            viewIntent,
            title ?: context.getString(R.string.replay_open_in_external)
        ).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

fun formatPlaybackTime(context: Context, timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    val isToday = android.text.format.DateUtils.isToday(timestampMillis)
    val timeStr = DateFormat.getTimeFormat(context).format(java.util.Date(timestampMillis))
    return if (isToday) {
        timeStr
    } else {
        val dateStr = DateFormat.getDateFormat(context).format(java.util.Date(timestampMillis))
        "$dateStr $timeStr"
    }
}

@Composable
fun RecentPlaybackCard(
    recentHistory: List<PlaybackHistoryEntry>,
    replayState: ReplayUiState,
    onPlayHistory: (Long) -> Unit,
    onPauseReplay: () -> Unit,
    onResumeReplay: () -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentReplayState = replayState
    AppSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.recent_playback_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                TextButton(
                    onClick = onViewAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.recent_playback_view_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (recentHistory.isEmpty()) {
                Text(
                    text = stringResource(R.string.recent_playback_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                val latest = recentHistory.first()
                val isLatestActive = when (currentReplayState) {
                    is ReplayUiState.Resolving -> currentReplayState.historyId == latest.id
                    is ReplayUiState.Preparing -> currentReplayState.historyId == latest.id
                    is ReplayUiState.Playing -> currentReplayState.historyId == latest.id
                    is ReplayUiState.Paused -> currentReplayState.historyId == latest.id
                    is ReplayUiState.Error -> currentReplayState.historyId == latest.id
                    ReplayUiState.Idle -> false
                }

                RecentTrackPrimaryItem(
                    entry = latest,
                    isActive = isLatestActive,
                    replayState = currentReplayState,
                    onPlay = { onPlayHistory(latest.id) },
                    onPause = onPauseReplay,
                    onResume = onResumeReplay
                )

                if (recentHistory.size > 1) {
                    val additional = recentHistory.drop(1).take(2)
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    additional.forEach { entry ->
                        val isEntryActive = when (currentReplayState) {
                            is ReplayUiState.Resolving -> currentReplayState.historyId == entry.id
                            is ReplayUiState.Preparing -> currentReplayState.historyId == entry.id
                            is ReplayUiState.Playing -> currentReplayState.historyId == entry.id
                            is ReplayUiState.Paused -> currentReplayState.historyId == entry.id
                            is ReplayUiState.Error -> currentReplayState.historyId == entry.id
                            ReplayUiState.Idle -> false
                        }
                        RecentTrackSubItem(
                            entry = entry,
                            isActive = isEntryActive,
                            replayState = currentReplayState,
                            onPlay = { onPlayHistory(entry.id) },
                            onPause = onPauseReplay,
                            onResume = onResumeReplay
                        )
                    }
                }
            }

            if (currentReplayState is ReplayUiState.Error) {
                val errorMsg = humanReadableReplayError(context, currentReplayState)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentRed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentRed,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentTrackPrimaryItem(
    entry: PlaybackHistoryEntry,
    isActive: Boolean,
    replayState: ReplayUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val context = LocalContext.current
    val currentReplayState = replayState
    val timeLabel = formatPlaybackTime(context, entry.startedAt)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.primary else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (isActive) {
                    when (currentReplayState) {
                        is ReplayUiState.Resolving, is ReplayUiState.Preparing -> {
                            Text(
                                text = stringResource(R.string.replay_status_preparing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is ReplayUiState.Playing -> {
                            Text(
                                text = stringResource(R.string.replay_status_playing),
                                style = MaterialTheme.typography.labelSmall,
                                color = DismissGreen
                            )
                        }
                        is ReplayUiState.Paused -> {
                            Text(
                                text = stringResource(R.string.replay_status_paused),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        else -> {}
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (isActive && currentReplayState is ReplayUiState.Playing) {
                        onPause()
                    } else if (isActive && currentReplayState is ReplayUiState.Paused) {
                        onResume()
                    } else {
                        onPlay()
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                if (isActive && (currentReplayState is ReplayUiState.Resolving || currentReplayState is ReplayUiState.Preparing)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isActive && currentReplayState is ReplayUiState.Playing) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = stringResource(R.string.replay_action_pause),
                        tint = DismissGreen
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = if (isActive && currentReplayState is ReplayUiState.Paused) {
                            stringResource(R.string.replay_action_resume)
                        } else {
                            stringResource(R.string.replay_action_play)
                        },
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Box {
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.replay_more_options),
                        tint = TextSecondary
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.replay_open_in_external)) },
                        onClick = {
                            menuExpanded = false
                            val result = openInExternalApp(context, entry.uri, entry.title)
                            if (result.isFailure) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.replay_open_external_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentTrackSubItem(
    entry: PlaybackHistoryEntry,
    isActive: Boolean,
    replayState: ReplayUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val context = LocalContext.current
    val currentReplayState = replayState
    val timeLabel = formatPlaybackTime(context, entry.startedAt)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (isActive) {
                    when (currentReplayState) {
                        is ReplayUiState.Resolving, is ReplayUiState.Preparing -> {
                            Text(
                                text = stringResource(R.string.replay_status_preparing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is ReplayUiState.Playing -> {
                            Text(
                                text = stringResource(R.string.replay_status_playing),
                                style = MaterialTheme.typography.labelSmall,
                                color = DismissGreen
                            )
                        }
                        is ReplayUiState.Paused -> {
                            Text(
                                text = stringResource(R.string.replay_status_paused),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        else -> {}
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (isActive && currentReplayState is ReplayUiState.Playing) {
                        onPause()
                    } else if (isActive && currentReplayState is ReplayUiState.Paused) {
                        onResume()
                    } else {
                        onPlay()
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                if (isActive && (currentReplayState is ReplayUiState.Resolving || currentReplayState is ReplayUiState.Preparing)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isActive && currentReplayState is ReplayUiState.Playing) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = stringResource(R.string.replay_action_pause),
                        tint = DismissGreen
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = if (isActive && currentReplayState is ReplayUiState.Paused) {
                            stringResource(R.string.replay_action_resume)
                        } else {
                            stringResource(R.string.replay_action_play)
                        },
                        tint = if (isActive) MaterialTheme.colorScheme.primary else TextSecondary
                    )
                }
            }

            Box {
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.replay_more_options),
                        tint = TextMuted
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.replay_open_in_external)) },
                        onClick = {
                            menuExpanded = false
                            val result = openInExternalApp(context, entry.uri, entry.title)
                            if (result.isFailure) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.replay_open_external_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryTrackRow(
    entry: PlaybackHistoryEntry,
    isActive: Boolean,
    replayState: ReplayUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentReplayState = replayState
    val timeStr = DateFormat.getTimeFormat(context).format(java.util.Date(entry.startedAt))

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${entry.playOrder}. ",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.primary else TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (isActive) {
                        when (currentReplayState) {
                            is ReplayUiState.Resolving, is ReplayUiState.Preparing -> {
                                Text(
                                    text = stringResource(R.string.replay_status_preparing),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is ReplayUiState.Playing -> {
                                Text(
                                    text = stringResource(R.string.replay_status_playing),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DismissGreen
                                )
                            }
                            is ReplayUiState.Paused -> {
                                Text(
                                    text = stringResource(R.string.replay_status_paused),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            is ReplayUiState.Error -> {
                                Text(
                                    text = humanReadableReplayError(context, currentReplayState),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentRed
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (isActive && currentReplayState is ReplayUiState.Playing) {
                            onPause()
                        } else if (isActive && currentReplayState is ReplayUiState.Paused) {
                            onResume()
                        } else {
                            onPlay()
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isActive && (currentReplayState is ReplayUiState.Resolving || currentReplayState is ReplayUiState.Preparing)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (isActive && currentReplayState is ReplayUiState.Playing) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = stringResource(R.string.replay_action_pause),
                            tint = DismissGreen
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = if (isActive && currentReplayState is ReplayUiState.Paused) {
                                stringResource(R.string.replay_action_resume)
                            } else {
                                stringResource(R.string.replay_action_play)
                            },
                            tint = if (isActive) MaterialTheme.colorScheme.primary else TextSecondary
                        )
                    }
                }

                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.replay_more_options),
                            tint = TextMuted
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.replay_open_in_external)) },
                            onClick = {
                                menuExpanded = false
                                val result = openInExternalApp(context, entry.uri, entry.title)
                                if (result.isFailure) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.replay_open_external_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.OpenInNew, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
    }
}
