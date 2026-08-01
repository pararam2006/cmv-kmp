This is a Kotlin Multiplatform project targeting Android, Desktop (JVM).

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`

### Ubuntu desktop

The desktop runtime reads media sessions through MPRIS and controls the default
PipeWire/PulseAudio-compatible sink through the event-driven `libpulse` client API.
PipeWire is supported through its standard `pipewire-pulse` compatibility server;
no helper script is required. The runtime subscribes to default-sink, volume, mute,
and active-port changes. `wpctl` (provided by WirePlumber) is retained only as a
startup fallback when `libpulse` or its server is unavailable. Select the media
applications to track in Settings before starting playback.

- Build a Debian package: `./gradlew :desktopApp:packageDeb`
- The installed application adds itself to the current user's XDG autostart directory
  after its first launch and starts hidden in the system tray on subsequent logins.
- Starting it again from the applications menu opens the existing window.
- Run the real integration tests: `CMV_LIVE_PULSE_TEST=1 ./gradlew :shared:jvmTest`
  for libpulse, or `CMV_LIVE_MPRIS_TEST=1 ./gradlew :shared:jvmTest` for the
  currently running MPRIS players.

Desktop debugging does not use Android Logcat. Run `./gradlew :desktopApp:run` to
see stdout and stack traces in the terminal, follow an installed application with
`journalctl --user -f | grep CMV`, or inspect the persistent log at
`~/.local/state/custom-music-volume/cmv.log` (`$XDG_STATE_HOME` is respected). The
file is rotated to `cmv.log.1` after reaching 5 MiB.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…