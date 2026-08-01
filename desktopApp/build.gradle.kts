import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
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
            packageName = "com.pararam2006.cmv"
            packageVersion = "1.1.0"
        }
    }
}