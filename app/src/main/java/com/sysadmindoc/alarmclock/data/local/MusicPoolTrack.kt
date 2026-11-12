package com.sysadmindoc.alarmclock.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.sysadmindoc.alarmclock.data.local.entity.MusicTrack

data class MusicPoolTrack(
    @Embedded val track: MusicTrack,
    @ColumnInfo(name = "membershipState") val membershipState: String?
)
