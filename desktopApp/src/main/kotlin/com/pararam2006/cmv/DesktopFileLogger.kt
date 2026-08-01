package com.pararam2006.cmv

import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object DesktopFileLogger {
    fun install(): Path? = runCatching {
        val logFile = resolveLogFile()
        Files.createDirectories(logFile.parent)
        rotateIfNeeded(logFile)

        val originalOut = System.out
        val originalErr = System.err
        val fileOutput = SynchronizedOutputStream(
            BufferedOutputStream(FileOutputStream(logFile.toFile(), true)),
        )
        val stdout = PrintStream(
            TeeOutputStream(originalOut, fileOutput),
            true,
            StandardCharsets.UTF_8,
        )
        val stderr = PrintStream(
            TeeOutputStream(originalErr, fileOutput),
            true,
            StandardCharsets.UTF_8,
        )
        System.setOut(stdout)
        System.setErr(stderr)
        Runtime.getRuntime().addShutdownHook(
            Thread(
                {
                    stdout.flush()
                    stderr.flush()
                    fileOutput.close()
                },
                "cmv-log-shutdown",
            ),
        )
        println("[CMV] Desktop log: $logFile")
        logFile
    }.onFailure { failure ->
        System.err.println("[CMV] Unable to initialize desktop file logging: ${failure.message}")
    }.getOrNull()

    private fun resolveLogFile(): Path {
        val stateHome = System.getenv("XDG_STATE_HOME")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".local", "state")
        return stateHome.resolve("custom-music-volume").resolve(LOG_FILE_NAME)
    }

    private fun rotateIfNeeded(logFile: Path) {
        if (Files.exists(logFile) && Files.size(logFile) >= MAX_LOG_SIZE_BYTES) {
            Files.move(
                logFile,
                logFile.resolveSibling("$LOG_FILE_NAME.1"),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private const val LOG_FILE_NAME = "cmv.log"
    private const val MAX_LOG_SIZE_BYTES = 5L * 1024L * 1024L
}

private class SynchronizedOutputStream(
    private val delegate: OutputStream,
) : OutputStream() {
    @Synchronized
    override fun write(value: Int) = delegate.write(value)

    @Synchronized
    override fun write(buffer: ByteArray, offset: Int, length: Int) =
        delegate.write(buffer, offset, length)

    @Synchronized
    override fun flush() = delegate.flush()

    @Synchronized
    override fun close() = delegate.close()
}

private class TeeOutputStream(
    private val console: OutputStream,
    private val file: OutputStream,
) : OutputStream() {
    override fun write(value: Int) {
        console.write(value)
        file.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        console.write(buffer, offset, length)
        file.write(buffer, offset, length)
    }

    override fun flush() {
        console.flush()
        file.flush()
    }
}
