package com.pararam2006.cmv.platform.linux

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxAutostartManagerTest {
    @Test
    fun writesBackgroundDesktopEntryAndDoesNotRewriteUnchangedFile() {
        val directory = createTempDirectory("cmv-autostart-test")
        try {
            val manager = LinuxAutostartManager(directory)
            val launcher = Path.of("/opt/Custom Music Volume/bin/cmv")

            assertTrue(manager.ensureInstalled(launcher))
            val entry = Files.readString(
                directory.resolve(LinuxAutostartManager.ENTRY_FILE_NAME),
            )

            assertTrue(entry.contains("Exec=\"/opt/Custom Music Volume/bin/cmv\" --background"))
            assertTrue(entry.contains("X-GNOME-Autostart-enabled=true"))
            assertFalse(manager.ensureInstalled(launcher))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun escapesDesktopExecSpecialCharacters() {
        val directory = createTempDirectory("cmv-autostart-escape-test")
        try {
            val entry = LinuxAutostartManager(directory).desktopEntry(
                Path.of("/opt/CMV `${'$'}HOME`/cmv"),
            )

            assertTrue(entry.contains("Exec=\"/opt/CMV \\`\\\$HOME\\`/cmv\" --background"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
