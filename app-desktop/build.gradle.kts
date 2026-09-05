plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core-ui"))
    implementation(project(":core-engine-desktop"))
    implementation(project(":core-ml-desktop"))
    implementation(compose.desktop.currentOs)

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.opengl)
    implementation(libs.lwjgl3.awt) {
        // lwjgl3-awt's own published POM references an unresolved `${lwjgl.natives}` Maven
        // property for its transitive LWJGL dependency (an internal-build-only property that
        // doesn't resolve for downstream consumers) -- excluded since this module already
        // declares LWJGL explicitly with the right natives classifier above.
        exclude(group = "org.lwjgl", module = "lwjgl")
    }
    runtimeOnly(variantOf(libs.lwjgl.core) { classifier("natives-macos-arm64") })
    runtimeOnly(variantOf(libs.lwjgl.opengl) { classifier("natives-macos-arm64") })
}

compose.desktop {
    application {
        mainClass = "com.gesturecontrol.appdesktop.MainKt"
    }
}

// NativeDesktopEngine locates the .dylib via this same system property in core-engine-desktop's
// own test task (see that module's build.gradle.kts) -- the "run" task the Compose Desktop plugin
// registers needs it too, since it's a separate JavaExec invocation that doesn't inherit it.
val coreEngineDesktop = project(":core-engine-desktop")

// The Compose Desktop plugin registers its "run" task lazily (inside its own afterEvaluate), so
// this has to wait for that too rather than configuring it eagerly here.
afterEvaluate {
    tasks.named<JavaExec>("run") {
        dependsOn("${coreEngineDesktop.path}:buildNativeLibDesktop")
        systemProperty(
            "gesture.canvas.desktop.native.lib",
            coreEngineDesktop.layout.buildDirectory.dir("nativeLib/desktop").get().asFile
                .resolve("libgesture_canvas_core_desktop.dylib").path,
        )
        // Same sidecar paths VerifySidecarMain (core-ml-desktop) already resolves relative to
        // rootDir -- system properties rather than program args, since Compose Desktop's
        // `application {}` entry point convention doesn't thread args through cleanly.
        systemProperty("gesture.canvas.sidecar.python", rootDir.resolve("hand-tracking-sidecar/venv/bin/python3").path)
        systemProperty(
            "gesture.canvas.sidecar.script",
            rootDir.resolve("hand-tracking-sidecar/hand_tracking_sidecar.py").path,
        )
        systemProperty("gesture.canvas.sidecar.model", rootDir.resolve("app/src/main/assets/hand_landmarker.task").path)
    }
}
