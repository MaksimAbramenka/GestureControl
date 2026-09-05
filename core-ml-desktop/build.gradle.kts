plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.gesturecontrol.core.ml.desktop.VerifySidecarMainKt"
}

// Stage 4's own manual-verification harness (see the project plan, §6b Stage 4) needs the
// sidecar's venv/script/model paths, which only make sense relative to the repo root -- not
// this module's own project directory, which is JavaExec's default working directory.
tasks.named<JavaExec>("run") {
    args(
        rootDir.resolve("hand-tracking-sidecar/venv/bin/python3").path,
        rootDir.resolve("hand-tracking-sidecar/hand_tracking_sidecar.py").path,
        rootDir.resolve("app/src/main/assets/hand_landmarker.task").path,
    )
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
