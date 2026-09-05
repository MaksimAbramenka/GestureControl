plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.glfw)
    implementation(libs.lwjgl.opengl)
    runtimeOnly(variantOf(libs.lwjgl.core) { classifier("natives-macos-arm64") })
    runtimeOnly(variantOf(libs.lwjgl.glfw) { classifier("natives-macos-arm64") })
    runtimeOnly(variantOf(libs.lwjgl.opengl) { classifier("natives-macos-arm64") })

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Compiles the shared, platform-agnostic native core (core-engine/src/main/cpp -- the same
// scene/render/input sources Android's CMake build and iOS's core-engine-ios both already
// compile) plus this module's own desktop-shim JNI bridge into a single .dylib, mirroring
// core-engine-ios's own hand-written clang++ Gradle task (not CMake) rather than introducing a
// third native build tool into the project. See render/GLCompat.h for how the shared sources stay
// unmodified while still targeting real desktop OpenGL here instead of GLES.
val cppSourceDir = rootDir.resolve("core-engine/src/main/cpp")
val desktopShimDir = project.projectDir.resolve("src/main/cpp/desktop-shim")

val sharedCppSources = listOf(
    "scene/SceneGraph.cpp",
    "input/PointSmoother.cpp",
    "input/OneEuroFilter.cpp",
    "render/StrokeRenderer.cpp",
    "render/RibbonTessellator.cpp",
)

fun runCommand(vararg command: String): Pair<Int, String> {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    return process.waitFor() to output
}

val buildNativeLibDesktop = tasks.register("buildNativeLibDesktop") {
    val outputDir = layout.buildDirectory.dir("nativeLib/desktop")
    val javaHome = System.getProperty("java.home")
    inputs.files(sharedCppSources.map { cppSourceDir.resolve(it) })
    inputs.dir(desktopShimDir)
    inputs.dir(cppSourceDir)
    outputs.dir(outputDir)
    doLast {
        val outDir = outputDir.get().asFile
        outDir.mkdirs()

        fun compile(sourceFile: File): File {
            val objectFile = File(outDir, sourceFile.nameWithoutExtension + ".o")
            val (exitCode, output) = runCommand(
                "clang++", "-std=c++20", "-fPIC", "-O2",
                "-I", cppSourceDir.path,
                "-I", "$javaHome/include",
                "-I", "$javaHome/include/darwin",
                "-c", sourceFile.path,
                "-o", objectFile.path,
            )
            check(exitCode == 0) { "Compiling ${sourceFile.name} failed:\n$output" }
            return objectFile
        }

        val objectFiles = sharedCppSources.map { compile(cppSourceDir.resolve(it)) } +
            compile(desktopShimDir.resolve("DesktopRendererBridge.cpp"))

        val dylibFile = File(outDir, "libgesture_canvas_core_desktop.dylib")
        val (linkExitCode, linkOutput) = runCommand(
            "clang++",
            "-dynamiclib",
            "-framework",
            "OpenGL",
            "-o",
            dylibFile.path,
            *objectFiles.map { it.path }.toTypedArray(),
        )
        check(linkExitCode == 0) { "Linking $dylibFile failed:\n$linkOutput" }
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(buildNativeLibDesktop)
    systemProperty(
        "gesture.canvas.desktop.native.lib",
        layout.buildDirectory.dir(
            "nativeLib/desktop",
        ).get().asFile.resolve("libgesture_canvas_core_desktop.dylib").path,
    )
    // LWJGL/GLFW must create its window/context on the JVM's "main" thread on macOS (Cocoa
    // requirement, unrelated to Gradle) -- the Gradle test worker process's own main thread
    // satisfies this as long as forkEvery/parallel test execution isn't introducing extra worker
    // threads, which the default single-worker-per-test-task setup here doesn't.
    jvmArgs("-XstartOnFirstThread")
}
