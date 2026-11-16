package com.sysadmindoc.alarmclock.data.readiness

import android.content.Context
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * B2-A durable, isolated test-only proof. Uses noBackupFilesDir rather than
 * Room, real alarm events, playback history, or ordinary shared preferences.
 * No OS PendingIntent or production Service integration is active yet.
 *
 * Each method takes an explicit clock value; a reboot/process restart cannot
 * turn an expired session into a fresh "test passed" event.
 */
internal data class ProductionTestProof(
    val session: ProductionTestSession,
    val stage: ProductionTestStage,
    val observedAtMs: Long
)

internal object ProductionTestSessionStore {
    private const val FORMAT_VERSION = 1
    private const val FILE_NAME = "b2-production-test-proof.v1"
    private val lock = Any()

    private fun target(context: Context): AtomicFile =
        AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    fun writeNew(context: Context, session: ProductionTestSession): Boolean =
        synchronized(lock) {
            if (!session.valid()) return@synchronized false
            // A test cannot silently replace an unfinished test from a
            // previous Activity or service process. Require explicit cancel
            // or let the bounded TTL expire.
            val previous = readRaw(context)
            if (previous != null && previous.session.isActiveAt(session.createdAtMs) &&
                previous.stage !in setOf(
                    ProductionTestStage.DISMISSED,
                    ProductionTestStage.CANCELLED,
                    ProductionTestStage.FAILED
                )
            ) {
                return@synchronized false
            }
            write(context, ProductionTestProof(session, ProductionTestStage.PREPARED, session.createdAtMs))
        }

    fun read(context: Context, nowMs: Long): ProductionTestProof? =
        synchronized(lock) {
            readRaw(context)?.takeIf { it.session.isActiveAt(nowMs) && it.observedAtMs <= nowMs }
        }

    fun advance(
        context: Context,
        sessionId: String,
        incomingFireId: String,
        nextStage: ProductionTestStage,
        nowMs: Long
    ): Boolean = synchronized(lock) {
        val previous = readRaw(context) ?: return@synchronized false
        val session = previous.session
        if (!session.canAccept(sessionId, incomingFireId, nowMs)) return@synchronized false
        if (nowMs < previous.observedAtMs) return@synchronized false
        if (!canAdvanceProductionTestStage(previous.stage, nextStage)) return@synchronized false
        val nextOccurrence = if (nextStage == ProductionTestStage.SNOOZE_REGISTERED) 1
            else session.nextOccurrence
        write(context, ProductionTestProof(
            session = session.copy(nextOccurrence = nextOccurrence),
            stage = nextStage,
            observedAtMs = nowMs
        ))
    }

    fun clear(context: Context) = synchronized(lock) { target(context).delete() }

    private fun write(context: Context, proof: ProductionTestProof): Boolean {
        if (!proof.session.valid()) return false
        val atomic = target(context)
        var stream: java.io.FileOutputStream? = null
        return try {
            val output = atomic.startWrite()
            stream = output
            DataOutputStream(output).apply {
                writeInt(FORMAT_VERSION)
                writeUTF(proof.session.sessionId)
                writeLong(proof.session.sourceAlarmId)
                writeLong(proof.session.createdAtMs)
                writeLong(proof.session.scheduledAtMs)
                writeLong(proof.session.expiresAtMs)
                writeInt(proof.session.nextOccurrence)
                writeInt(proof.stage.ordinal)
                writeLong(proof.observedAtMs)
                flush()
            }
            atomic.finishWrite(output)
            true
        } catch (_: IOException) {
            stream?.let { atomic.failWrite(it) }
            false
        } catch (_: RuntimeException) {
            stream?.let { atomic.failWrite(it) }
            false
        }
    }

    private fun readRaw(context: Context): ProductionTestProof? {
        val atomic = target(context)
        return try {
            DataInputStream(atomic.openRead()).use { input ->
                if (input.readInt() != FORMAT_VERSION) return null
                val session = ProductionTestSession(
                    sessionId = input.readUTF(),
                    sourceAlarmId = input.readLong(),
                    createdAtMs = input.readLong(),
                    scheduledAtMs = input.readLong(),
                    expiresAtMs = input.readLong(),
                    nextOccurrence = input.readInt()
                )
                val stage = ProductionTestStage.entries.getOrNull(input.readInt()) ?: return null
                val observedAt = input.readLong()
                if (!session.valid() || observedAt < session.createdAtMs || observedAt >= session.expiresAtMs) {
                    return null
                }
                ProductionTestProof(session, stage, observedAt)
            }
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }
}
