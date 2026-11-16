package com.sysadmindoc.alarmclock.domain

import android.annotation.SuppressLint
import android.content.Context

/**
 * Per-alarm U4 preference. Independent of Room v28 so upgrades cannot discard
 * music playlists. Saved synchronously; manual backup includes this value.
 */
object AlarmSnoozeMusicModeStore {
    private const val PREFS = "alarm_snooze_music_preferences"
    private fun key(alarmId: Long) = "mode_$alarmId"

    fun get(context: Context, alarmId: Long): SnoozeMusicResumeMode {
        if (alarmId <= 0L) return SnoozeMusicResumeMode.NEXT_DIFFERENT
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(alarmId), null)
        return runCatching { SnoozeMusicResumeMode.valueOf(raw.orEmpty()) }
            .getOrDefault(SnoozeMusicResumeMode.NEXT_DIFFERENT)
    }

    @SuppressLint("ApplySharedPref")
    fun save(context: Context, alarmId: Long, mode: SnoozeMusicResumeMode): Boolean {
        if (alarmId <= 0L) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(alarmId), mode.name).commit()
    }
}
