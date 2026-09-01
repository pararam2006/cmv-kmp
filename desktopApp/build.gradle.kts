import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.navigation)
    implementation(libs.compose.lifecycleRuntime)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
}

compose.desktop {
    application {
        mainClass = "com.pararam2006.cmv.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            modules("jdk.security.auth")
            packageName = "Custom Music Volume"
            packageVersion = "1.2.0"
            description = "Learns and applies per-track system volume on selected media players"
            vendor = "Custom Music Volume"

            linux {
                shortcut = true
                packageName = "custom-music-volume"
                appCategory = "AudioVideo"
                menuGroup = "AudioVideo"
                debMaintainer = "Andrey"
                iconFile.set(project.file("../androidApp/src/main/ic_launcher-playstore.png"))
            }
        }
    }
}

tasks.withType<AbstractJPackageTask>().configureEach {
    if (targetFormat == TargetFormat.Deb) {
        freeArgs.addAll(
            "--linux-package-deps",
            "libpulse0,wireplumber",
        )
    }
}
