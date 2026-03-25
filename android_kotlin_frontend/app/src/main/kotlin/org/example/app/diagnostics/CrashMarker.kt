package org.example.app.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File-based "last crash" marker.
 *
 * Motivation: in hosted preview / emulator environments, early startup crashes can appear as
 * a silent auto-close with no visible logs. This marker persists a short error string to app
 * internal storage so the next launch can surface the failure in a dedicated diagnostic screen.
 */
internal object CrashMarker {
    private const val FILE_NAME = "startup_crash_marker.txt"

    // PUBLIC_INTERFACE
    fun writeStartupCrashMarker(context: Context, throwable: Throwable?) {
        /**
         * Persist a short crash marker to internal storage.
         *
         * The marker is best-effort; any exception during writing is ignored to avoid compounding
         * a crash scenario.
         */
        runCatching {
            val file = markerFile(context)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val payload = buildString {
                appendLine("timestamp=$timestamp")
                appendLine("type=${throwable?.javaClass?.name ?: "unknown"}")
                appendLine("message=${throwable?.message ?: "no message"}")
            }
            file.writeText(payload, Charsets.UTF_8)
        }
    }

    // PUBLIC_INTERFACE
    fun readStartupCrashMarker(context: Context): String? {
        /**
         * Read the crash marker if present. Returns null if missing/unreadable.
         */
        return runCatching {
            val file = markerFile(context)
            if (!file.exists()) return@runCatching null
            file.readText(Charsets.UTF_8).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    // PUBLIC_INTERFACE
    fun clearStartupCrashMarker(context: Context) {
        /**
         * Remove the crash marker if it exists.
         */
        runCatching {
            val file = markerFile(context)
            if (file.exists()) file.delete()
        }
    }

    private fun markerFile(context: Context): File = File(context.filesDir, FILE_NAME)
}
