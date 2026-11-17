package com.sysadmindoc.alarmclock.data.readiness

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.sysadmindoc.alarmclock.receiver.AlarmReceiver

/**
 * B2: schedules a diagnostic with the real AlarmReceiver component but its
 * own PendingIntent identity. Never edits the source alarm's Room row,
 * AlarmRuntimeState, nextTriggerTime, or regular alarm PendingIntent.
 */
internal object ProductionTestAlarmScheduler {
    const val ACTION_TEST_FIRE = "com.sysadmindoc.alarmclock.B2_PRODUCTION_TEST_FIRE"
    const val EXTRA_SESSION_ID = "b2_test_session_id"
    const val EXTRA_TEST_FIRE_ID = "b2_test_fire_id"
    const val EXTRA_SOURCE_ALARM_ID = "b2_test_source_alarm_id"
    const val EXTRA_TRIGGER_AT = "b2_test_trigger_at"
    private const val REQUEST_CODE = 20261
    private const val SNOOZE_MS = 60_000L

    fun schedule(context: Context, sourceAlarmId: Long): Result<ProductionTestSession> = runCatching {
        val manager = requireNotNull(context.getSystemService(AlarmManager::class.java)) {
            "AlarmManager unavailable"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            throw SecurityException("Exact alarm permission required for B2")
        }
        val session = ProductionTestSession.new(sourceAlarmId, System.currentTimeMillis())
        check(ProductionTestSessionStore.writeNew(context, session)) {
            "Another production-path test is active"
        }
        try {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, session.scheduledAtMs,
                pendingIntent(context, session, 0, session.scheduledAtMs)
            )
            check(ProductionTestSessionStore.advance(
                context, session.sessionId, session.fireId(0),
                ProductionTestStage.REGISTERED, System.currentTimeMillis()
            )) { "Test registration proof failed" }
            session
        } catch (error: Exception) {
            manager.cancel(pendingIntent(context, session, 0, session.scheduledAtMs))
            ProductionTestSessionStore.advance(
                context, session.sessionId, session.fireId(0),
                ProductionTestStage.FAILED, System.currentTimeMillis()
            )
            throw error
        }
    }

    /** Called only after the first Media3 onIsPlaying transition was observed. */
    fun scheduleSnooze(context: Context, session: ProductionTestSession): Boolean {
        if (session.nextOccurrence != 0) return false
        val proof = ProductionTestSessionStore.read(context, System.currentTimeMillis()) ?: return false
        if (proof.session.sessionId != session.sessionId ||
            proof.stage != ProductionTestStage.AUDIO_STARTED) return false
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) return false
        val targetAt = System.currentTimeMillis() + SNOOZE_MS
        if (targetAt >= session.expiresAtMs) return false
        val nextPi = pendingIntent(context, session, 1, targetAt)
        return try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetAt, nextPi)
            if (!ProductionTestSessionStore.advance(
                    context, session.sessionId, session.fireId(0),
                    ProductionTestStage.SNOOZE_REGISTERED, System.currentTimeMillis(),
                    nextScheduledAtMs = targetAt
                )) {
                manager.cancel(nextPi)
                false
            } else {
                true
            }
        } catch (_: Exception) {
            manager.cancel(nextPi)
            false
        }
    }

    /** Cancels only B2 PendingIntents; it has no access to normal schedule requests. */
    fun cancel(context: Context) {
        val proof = ProductionTestSessionStore.read(context, System.currentTimeMillis()) ?: return
        val manager = context.getSystemService(AlarmManager::class.java)
        for (occurrence in 0..ProductionTestSession.MAX_TEST_OCCURRENCE) {
            val pi = pendingIntent(context, proof.session, occurrence, proof.session.scheduledAtMs)
            manager?.cancel(pi)
            pi.cancel()
        }
        ProductionTestSessionStore.advance(
            context, proof.session.sessionId, proof.session.fireId(),
            ProductionTestStage.CANCELLED, System.currentTimeMillis()
        )
    }

    private fun pendingIntent(
        context: Context, session: ProductionTestSession, occurrence: Int, atMs: Long
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TEST_FIRE
            // PendingIntent identity includes data URI; the ordinary alarm
            // receiver PI uses a separate action and no B2 namespace.
            data = Uri.parse("acx-b2://session/${session.sessionId}/$occurrence")
            putExtra(EXTRA_SESSION_ID, session.sessionId)
            putExtra(EXTRA_TEST_FIRE_ID, session.fireId(occurrence))
            putExtra(EXTRA_SOURCE_ALARM_ID, session.sourceAlarmId)
            putExtra(EXTRA_TRIGGER_AT, atMs)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
