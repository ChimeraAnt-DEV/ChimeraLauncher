package org.chimeramc.client.core.minecraft

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.jvm.JvmStatic

/**
 * Persistent on-disk log for the game-launch pipeline.
 *
 * Every log line that goes through [MinecraftRuntimePreparer.ProgressListener.onLog] (or
 * mirrors of `Log.w/e` during native-library loading() is appended, in real time, to
 * `context.getExternalFilesDir(null)/launch_debug.log` — a plain text file opened in append
 * mode and flushed after every write, so it survives even if the process is killed or
 * restarted before the in-memory LogcatOverlay can be viewed.

 * Thread-safe: appends are serialized through a single writer; no buffering is left
 * on disk (writer never wraps the OS buffer beyond one logical write, and flushes
 * per call(, so once this function returns, the line is durable.
 */
object LaunchLog {

    private val lock = Any()
    private var writer: PrintWriter? = null
    private var file: File? = null

    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)



    /** Lazily opens the append-mode writer (creates the parent dir if needed). */
    private fun writer(context: Context): PrintWriter? {
        synchronized (lock) {
            if (writer != null) return writer
            return try {
                val dir = context.getExternalFilesDir(null)
                    ?: context.filesDir
                val target = File(dir, "launch_debug.log")
                target.parentFile?.mkdirs()
                val w = PrintWriter(FileWriter(target, true), true) // second boolean: autoFlush
                writer = w
                file = target
                w
            } catch (_: Throwable) {
                null
            }
        }
    }

    /** Appends a plain line (no timestamp prefix( — used for raw onLog messages. */
    @JvmStatic
    fun append(context: Context, message: String) {
        synchronized (lock) {
            val w = writer(context) ?: return
            try {
                w.append(timestampFormat.format(Date())).append(' ').append(message).append('\n')
                w.flush()
            } catch (_: Throwable) {}
        }
    }

    /** Appends a logcat-style line: `MM-DD HH:MM:SS.mmm pid tid I/Tag  : msg`. */
    @JvmStatic
    fun appendLogcat(context: Context, level: Char, tag: String, message: String) {
        synchronized (lock) {
            val w = writer(context) ?: return
            try {
                w.append(timestampFormat.format(Date()))
                    .append(' ')
                    .append(android.os.Process.myPid().toString())
                    .append(' ')
                    .append(android.os.Process.myTid().toString())
                    .append(' ')
                    .append(level).append('/').append(tag).append(": ").append(message).append('\n')
                w.flush()
            } catch (_: Throwable) {}
        }
    }

    /** The log file itself (may be null before the first write or if external storage unavailable). */
    @JvmStatic
    fun resolveFile(context: Context): File? {
        synchronized (lock) {
            if (file != null) return file
            return writer(context)?.let { file }
        }
    }

    /** Reads the full historical file content,or null if unavailable/missing. */
    @JvmStatic
    fun readAll(context: Context): String? {
        val f = resolveFile(context) ?: return null
        return try {
            f.readText()
        } catch (_: Throwable) {
            null
        }
    }
}