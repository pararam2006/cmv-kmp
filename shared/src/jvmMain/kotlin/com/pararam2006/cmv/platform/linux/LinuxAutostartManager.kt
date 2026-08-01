package com.pararam2006.cmv.platform.linux

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class LinuxAutostartManager(
    private val autostartDirectory: Path = defaultAutostartDirectory(),
) {
    fun ensureInstalled(launcherPath: Path): Boolean {
        require(launcherPath.isAbsolute) { "Autostart launcher path must be absolute" }

        val entry = desktopEntry(launcherPath.normalize())
        val target = autostartDirectory.resolve(ENTRY_FILE_NAME)
        if (Files.isRegularFile(target) && Files.readString(target) == entry) return false

        Files.createDirectories(autostartDirectory)
        val temporary = Files.createTempFile(autostartDirectory, ".$ENTRY_FILE_NAME.", ".tmp")
        try {
            Files.writeString(temporary, entry)
            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return true
    }

    internal fun desktopEntry(launcherPath: Path): String = buildString {
        appendLine("[Desktop Entry]")
        appendLine("Type=Application")
        appendLine("Version=1.0")
        appendLine("Name=Custom Music Volume")
        append("Exec=")
        append(quoteExecArgument(launcherPath.toString()))
        appendLine(" --background")
        appendLine("Terminal=false")
        appendLine("X-GNOME-Autostart-enabled=true")
        appendLine("StartupNotify=false")
    }

    companion object {
        const val ENTRY_FILE_NAME = "com.pararam2006.cmv.desktop"

        fun packagedLauncherPath(): Path? =
            System.getProperty("jpackage.app-path")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?.takeIf(Path::isAbsolute)

        private fun defaultAutostartDirectory(): Path {
            val configHome = System.getenv("XDG_CONFIG_HOME")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: Path.of(System.getProperty("user.home"), ".config")
            return configHome.resolve("autostart")
        }

        private fun quoteExecArgument(value: String): String = buildString {
            append('"')
            value.forEach { character ->
                if (character == '\\' || character == '"' || character == '`' || character == '$') {
                    append('\\')
                }
                append(character)
            }
            append('"')
        }
    }
}
