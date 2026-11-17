package com.sysadmindoc.alarmclock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.sysadmindoc.alarmclock.data.readiness.ProductionTestAlarmScheduler
import com.sysadmindoc.alarmclock.data.readiness.ProductionTestSessionStore
import com.sysadmindoc.alarmclock.data.readiness.ProductionTestStage
import com.sysadmindoc.alarmclock.service.AlarmService
import com.sysadmindoc.alarmclock.data.local.entity.AlarmIncidentEvent
import com.sysadmindoc.alarmclock.domain.AlarmScheduler
import com.sysadmindoc.alarmclock.service.AlarmFireDismissContract

/**
 * Receives the alarm broadcast from AlarmManager and starts the foreground
 * AlarmService, which handles audio playback, vibration, and launching the
 * full-screen dismiss/snooze UI.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // The B2 diagnostic enters this production receiver but does not
        // reuse a normal alarm's PendingIntent or incident history.
        if (intent.action == ProductionTestAlarmScheduler.ACTION_TEST_FIRE) {
            receiveProductionTest(context, intent)
            return
        }
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        if (alarmId == -1L) return
        val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)
        val fireId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_FIRE_ID)
            ?: AlarmIncidentEvent.fireIdFor(alarmId, scheduledAt)
        val incidents = mutableListOf(
            ReceiverAlarmIncident(
                alarmId = alarmId,
                fireId = fireId,
                scheduledAt = scheduledAt,
                type = AlarmIncidentEvent.TYPE_BROADCAST,
                status = AlarmIncidentEvent.STATUS_RECEIVED,
                reasonCode = "ALARM_MANAGER_DELIVERED",
                source = "AlarmReceiver"
            )
        )
        // Capture the actual screen state at broadcast delivery. The user
        // reported a snooze that did not ring until screen-off; this separates
        // late AlarmManager delivery from service/audio startup problems.
        // Use SCHEDULE type so occurrenceDeliveryCount remains unchanged.
        val interactive = (context.getSystemService(Context.POWER_SERVICE)
            as? PowerManager)?.isInteractive == true
        incidents += ReceiverAlarmIncident(
            alarmId = alarmId,
            fireId = fireId,
            scheduledAt = scheduledAt,
            type = AlarmIncidentEvent.TYPE_SCHEDULE,
            status = AlarmIncidentEvent.STATUS_RECEIVED,
            reasonCode = if (interactive) "DELIVERY_SCREEN_ON" else "DELIVERY_SCREEN_OFF",
            source = "AlarmReceiver"
        )

        val serviceIntent = AlarmFireDismissContract.startServiceIntent(context, alarmId, scheduledAt, fireId)
        val deliveryWakeLock = AlarmDeliveryWakeLock.acquire(context)
        var serviceStartSucceeded = false
        try {
            context.startForegroundService(serviceIntent)
            serviceStartSucceeded = true
            incidents += ReceiverAlarmIncident(
                alarmId = alarmId,
                fireId = fireId,
                scheduledAt = scheduledAt,
                type = AlarmIncidentEvent.TYPE_FOREGROUND_SERVICE,
                status = AlarmIncidentEvent.STATUS_REQUESTED,
                reasonCode = "START_FOREGROUND_SERVICE",
                source = "AlarmReceiver"
            )
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (API 31+) when the app is
            // background-restricted at the exact moment AlarmManager wakes it.
            // The AlarmManager exact-alarm guarantee means this is extremely rare;
            // log for diagnostics and let the system handle retries.
            Log.e("AlarmReceiver", "startForegroundService failed for alarm $alarmId", e)
            incidents += ReceiverAlarmIncident(
                alarmId = alarmId,
                fireId = fireId,
                scheduledAt = scheduledAt,
                type = AlarmIncidentEvent.TYPE_FOREGROUND_SERVICE,
                status = AlarmIncidentEvent.STATUS_FAILED,
                reasonCode = "START_FOREGROUND_SERVICE_FAILED_${e.javaClass.simpleName}",
                source = "AlarmReceiver"
            )
        } finally {
            if (!serviceStartSucceeded) AlarmDeliveryWakeLock.release(deliveryWakeLock)
            recordAlarmIncidentsAsync(context, incidents)
        }
    }

    private fun receiveProductionTest(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val sessionId = intent.getStringExtra(ProductionTestAlarmScheduler.EXTRA_SESSION_ID).orEmpty()
        val fireId = intent.getStringExtra(ProductionTestAlarmScheduler.EXTRA_TEST_FIRE_ID).orEmpty()
        val alarmId = intent.getLongExtra(ProductionTestAlarmScheduler.EXTRA_SOURCE_ALARM_ID, -1L)
        val scheduledAt = intent.getLongExtra(ProductionTestAlarmScheduler.EXTRA_TRIGGER_AT, 0L)
        val proof = ProductionTestSessionStore.read(context, now) ?: return
        val session = proof.session
        if (!session.matchesDelivery(alarmId, sessionId, fireId, scheduledAt, now)) return
        val expected = if (session.nextOccurrence == 0) {
            ProductionTestStage.REGISTERED
        } else {
            ProductionTestStage.SNOOZE_REGISTERED
        }
        if (proof.stage != expected) return
        val next = if (session.nextOccurrence == 0) {
            ProductionTestStage.RECEIVER_DELIVERED
        } else {
            ProductionTestStage.SNOOZE_DELIVERED
        }
        if (!ProductionTestSessionStore.advance(context, sessionId, fireId, next, now)) return
        val serviceIntent = AlarmFireDismissContract.startServiceIntent(
            context, alarmId, scheduledAt, fireId
        ).apply {
            putExtra(AlarmService.EXTRA_PRODUCTION_TEST_SESSION_ID, sessionId)
        }
        val deliveryLock = AlarmDeliveryWakeLock.acquire(context)
        var serviceStartSucceeded = false
        try {
            context.startForegroundService(serviceIntent)
            serviceStartSucceeded = true
        } catch (error: Exception) {
            Log.e("AlarmReceiver", "B2 test service launch failed", error)
            ProductionTestSessionStore.advance(
                context, sessionId, fireId,
                ProductionTestStage.FAILED, System.currentTimeMillis()
            )
        } finally {
            // Mirror the production alarm handoff. An immediate release here
            // lets a dozing phone sleep between broadcast and cold Service
            // startup. Successful starts retain the bounded 15-second lock;
            // Android releases it on timeout without a permanent wakelock.
            if (!serviceStartSucceeded) AlarmDeliveryWakeLock.release(deliveryLock)
        }
    }
}
