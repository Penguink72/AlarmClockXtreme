package com.sysadmindoc.alarmclock.domain

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/**
 * Runtime-only checkpoint stored under noBackupFilesDir, never in Room or a
 * manual user backup. Atomic write completes BEFORE the ringing player stops.
 * A source/target fire mismatch, expiry or corrupt URI fails closed.
 */
internal object SnoozeMusicRestoreStore {
    private val lock = Any()

    private fun file(context: Context, alarmId: Long): AtomicFile =
        AtomicFile(File(context.noBackupFilesDir, "u4-snooze-$alarmId.json"))

    fun save(context: Context, checkpoint: SnoozeMusicRestoreCheckpoint): Boolean =
        synchronized(lock) {
            if (!checkpoint.isEligibleFor(
                    checkpoint.alarmId, checkpoint.nextFireId, checkpoint.recordedAtMs
                )) return@synchronized false
            val payload = JSONObject().apply {
                put("alarmId", checkpoint.alarmId)
                put("sourceFireId", checkpoint.sourceFireId)
                put("nextFireId", checkpoint.nextFireId)
                put("uri", checkpoint.uri)
                if (checkpoint.positionMs != null) put("positionMs", checkpoint.positionMs)
                put("playlistId", checkpoint.playlistId ?: JSONObject.NULL)
                put("mode", checkpoint.mode.name)
                put("recordedAtMs", checkpoint.recordedAtMs)
                put("expiresAtMs", checkpoint.expiresAtMs)
            }.toString().toByteArray(Charsets.UTF_8)
            val atomic = file(context, checkpoint.alarmId)
            var output: java.io.FileOutputStream? = null
            try {
                output = atomic.startWrite()
                output.write(payload)
                atomic.finishWrite(output)
                true
            } catch (_: Exception) {
                output?.let { atomic.failWrite(it) }
                false
            }
        }

    fun readEligible(
        context: Context, alarmId: Long, nextFireId: String, nowMs: Long
    ): SnoozeMusicRestoreCheckpoint? = synchronized(lock) {
        if (alarmId <= 0L) return@synchronized null
        val atomic = file(context, alarmId)
        val checkpoint = try {
            val json = JSONObject(
                atomic.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            )
            SnoozeMusicRestoreCheckpoint(
                alarmId = json.getLong("alarmId"),
                sourceFireId = json.getString("sourceFireId"),
                nextFireId = json.getString("nextFireId"),
                uri = json.getString("uri"),
                positionMs = if (json.has("positionMs") && !json.isNull("positionMs")) {
                    json.getLong("positionMs")
                } else null,
                playlistId = if (json.isNull("playlistId")) null
                    else json.optString("playlistId").ifBlank { null },
                mode = SnoozeMusicResumeMode.valueOf(json.getString("mode")),
                recordedAtMs = json.getLong("recordedAtMs"),
                expiresAtMs = json.getLong("expiresAtMs")
            )
        } catch (_: Exception) {
            null
        }
        checkpoint?.takeIf { it.isEligibleFor(alarmId, nextFireId, nowMs) }
    }

    fun clear(context: Context, alarmId: Long) = synchronized(lock) {
        if (alarmId > 0L) file(context, alarmId).delete()
    }
}
