package com.sysadmindoc.alarmclock.service

/**
 * A rejected notification snooze can tear down only the FGS that it itself
 * recreated. A succeeding or overlapping alarm must never be stopped by a
 * late coroutine from the previous fire.
 */
internal fun shouldStopRejectedColdSnooze(
    restoredFromColdStart: Boolean,
    requestedAlarmId: Long,
    requestedFireId: String,
    currentAlarmId: Long,
    currentFireId: String,
    accepted: Boolean
): Boolean =
    restoredFromColdStart &&
        requestedAlarmId > 0L &&
        requestedFireId.isNotBlank() &&
        currentAlarmId == requestedAlarmId &&
        currentFireId == requestedFireId &&
        !accepted
