package com.sysadmindoc.alarmclock.localization

import android.content.Context
import android.util.AtomicFile
import com.sysadmindoc.alarmclock.util.LocaleSelectionContract
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/** Result never contains private translation strings or file system paths. */
internal data class LanguagePackInstallResult(
    val stored: Boolean,
    val locale: String?,
    val validation: LanguagePackV1Check?,
    val errorCode: String?
)

internal data class LanguagePackReadResult(
    val raw: String,
    val validation: LanguagePackV1Check,
    /** A previous committed valid version, not a silent mutation of current. */
    val recoveredFromPrevious: Boolean
)

/**
 * B1-C1 offline SAF backend. UI chooses a content:// InputStream or OutputStream
 * in B1-U1. No network, user media, Room, AlarmManager, or system locale changes.
 *
 * Validate every input before touching storage, stage one previous valid
 * revision for rollback, then Atomically replace current. Concurrent in-process
 * operations are serialized. Cross-process coordination is a B1-U1 gate.
 */
internal class LanguagePackV1Repository(
    private val directory: File,
    private val validator: LanguagePackV1Validator
) {
    companion object {
        private val processLock = Any()
        fun forApp(context: Context, catalog: LanguageCatalogV1): LanguagePackV1Repository =
            LanguagePackV1Repository(
                File(context.filesDir, "localization-v1/custom-packs"),
                LanguagePackV1Validator(catalog)
            )
    }

    private fun canonicalFile(locale: String, previous: Boolean = false): File {
        val canonical = LocaleSelectionContract.canonicalTag(locale)
            ?: throw LanguagePackProblem("INVALID_LOCALE_TAG")
        require(canonical == locale) { "NONCANONICAL_LOCALE" }
        return File(directory, canonical + if (previous) ".previous.json" else ".json")
    }

    private fun writeAtomically(target: File, raw: String) {
        if (!directory.isDirectory && !directory.mkdirs())
            throw LanguagePackProblem("PACK_DIRECTORY_UNAVAILABLE")
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            output.write(raw.toByteArray(StandardCharsets.UTF_8))
            atomic.finishWrite(output)
        } catch (t: Exception) {
            atomic.failWrite(output)
            throw t
        }
    }

    private fun loadRaw(file: File): String? =
        if (file.isFile) {
            runCatching {
                AtomicFile(file).openRead().use { LanguagePackV1Validator.readBounded(it) }
            }.getOrNull()
        } else null

    private fun checkedRead(locale: String, previous: Boolean): LanguagePackReadResult? {
        val text = loadRaw(canonicalFile(locale, previous)) ?: return null
        val checked = validator.validate(text)
        if (!checked.canInstall || checked.pack?.locale != locale) return null
        return LanguagePackReadResult(text,checked,previous)
    }

    fun load(locale: String): LanguagePackReadResult? = synchronized(processLock) {
        val canonical = LocaleSelectionContract.canonicalTag(locale) ?: return@synchronized null
        checkedRead(canonical,false) ?: checkedRead(canonical,true)
    }

    fun installedLocales(): Set<String> = synchronized(processLock) {
        if (!directory.isDirectory) return@synchronized emptySet()
        directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") &&
                !it.name.endsWith(".previous.json") }
            ?.mapNotNull { candidate ->
                val locale = candidate.name.removeSuffix(".json")
                locale.takeIf { load(it) != null }
            }?.toSet() ?: emptySet()
    }

    /**
     * A rejected pack never modifies existing files. A mechanically-valid but
     * source-stale pack can be retained, but stale entries are not usable.
     * Import failures are reported, not thrown into calling Activities.
     */
    fun importFromStream(input: InputStream): LanguagePackInstallResult {
        val text = try {
            input.use { LanguagePackV1Validator.readBounded(it) }
        } catch (problem: LanguagePackProblem) {
            return LanguagePackInstallResult(false,null,null,problem.code)
        } catch (_: Exception) {
            return LanguagePackInstallResult(false,null,null,"READ_FAILED")
        }
        val check = validator.validate(text)
        val locale = check.pack?.locale
        if (!check.canInstall || locale == null)
            return LanguagePackInstallResult(false,null,check,"VALIDATION_REJECTED")

        return synchronized(processLock) {
            try {
                val existing = checkedRead(locale,false)
                // Never overwrite our previous valid version with corrupt bytes.
                if (existing != null)
                    writeAtomically(canonicalFile(locale,true),existing.raw)
                writeAtomically(canonicalFile(locale),text)
                LanguagePackInstallResult(true,locale,check,null)
            } catch (_: Exception) {
                LanguagePackInstallResult(false,locale,check,"PERSISTENCE_WRITE_FAILED")
            }
        }
    }

    fun importRaw(raw: String): LanguagePackInstallResult =
        importFromStream(ByteArrayInputStream(raw.toByteArray(StandardCharsets.UTF_8)))

    /** SAF CreateDocument supplies the output. Caller owns and closes it. */
    fun exportToStream(locale: String, output: OutputStream): Boolean = synchronized(processLock) {
        val read = load(locale) ?: return@synchronized false
        try {
            output.write(read.raw.toByteArray(StandardCharsets.UTF_8))
            output.flush()
            true
        } catch (_: Exception) { false }
    }

    /** Only a structurally and semantically-source-safe previous pack is restored. */
    fun rollback(locale: String): Boolean = synchronized(processLock) {
        val canonical = LocaleSelectionContract.canonicalTag(locale) ?: return@synchronized false
        val prior = checkedRead(canonical,true) ?: return@synchronized false
        try {
            writeAtomically(canonicalFile(canonical),prior.raw)
            true
        } catch (_: Exception) { false }
    }
}
