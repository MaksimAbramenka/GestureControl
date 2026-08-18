plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(17)

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        iosTest {
            kotlin.srcDir(layout.buildDirectory.dir("generated/testConstants"))
        }
    }
}

// Generates the absolute path to the checked-in hand-tracking model as a Kotlin constant, rather
// than hardcoding a path into a committed test file -- computed from the actual project location
// at build time so it works on any machine, not just the one that wrote it.
val generateTestConstants = tasks.register("generateTestConstants") {
    val outputDir = layout.buildDirectory.dir("generated/testConstants/com/gesturecontrol/core/ml/ios")
    val modelPath = rootProject.file("app/src/main/assets/hand_landmarker.task").absolutePath
    outputs.dir(layout.buildDirectory.dir("generated/testConstants"))
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        File(dir, "TestConstants.kt").writeText(
            """
            package com.gesturecontrol.core.ml.ios

            internal const val TEST_HAND_LANDMARKER_MODEL_PATH = "$modelPath"

            """.trimIndent(),
        )
    }
}

tasks.matching { it.name.startsWith("compileTestKotlin") }.configureEach {
    dependsOn(generateTestConstants)
}

// MediaPipeTasksVision/Common aren't published in a form Kotlin/Native's Gradle plugin can resolve
// as a dependency -- they're CocoaPods-distributed static frameworks whose real linking recipe
// (force-loading a ~500MB-1GB "graph" static lib, plus ten system frameworks) is normally injected
// by CocoaPods' own build-config generation. Fetched and cached here instead of committed --
// MediaPipeTasksCommon's graph libraries alone are ~1.3GB combined (device + simulator).
val vendorDir = layout.projectDirectory.dir("vendor")
val mediaPipeVisionUrl = "https://dl.google.com/cpdc/20260727-225049/MediaPipeTasksVision-1.0.0.tar.gz"
val mediaPipeCommonUrl = "https://dl.google.com/cpdc/20260727-225047/MediaPipeTasksCommon-1.0.0.tar.gz"

fun runCommand(vararg command: String): Pair<Int, String> {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    return process.waitFor() to output
}

fun registerVendorFetchTask(taskName: String, url: String, destDirName: String, markerRelativePath: String) =
    tasks.register(taskName) {
        doLast {
            val destDir = vendorDir.dir(destDirName).asFile
            val marker = File(destDir, markerRelativePath)
            if (marker.exists()) {
                logger.lifecycle("$destDirName already present, skipping download.")
                return@doLast
            }
            destDir.mkdirs()
            val tarFile = File(destDir, "download.tar.gz")
            logger.lifecycle("Downloading $destDirName from $url (large, this can take a while)...")
            val (curlExit, curlOut) = runCommand("curl", "-sL", "-o", tarFile.path, url)
            check(curlExit == 0) { "Download of $destDirName failed:\n$curlOut" }
            logger.lifecycle("Extracting $destDirName...")
            val (tarExit, tarOut) = runCommand("tar", "xzf", tarFile.path, "-C", destDir.path)
            check(tarExit == 0) { "Extracting $destDirName failed:\n$tarOut" }
            tarFile.delete()
        }
    }

val fetchMediaPipeVision = registerVendorFetchTask(
    "fetchMediaPipeVision",
    mediaPipeVisionUrl,
    "MediaPipeTasksVision",
    "frameworks/MediaPipeTasksVision.xcframework/ios-arm64/MediaPipeTasksVision.framework/Info.plist",
)
val fetchMediaPipeCommon = registerVendorFetchTask(
    "fetchMediaPipeCommon",
    mediaPipeCommonUrl,
    "MediaPipeTasksCommon",
    "frameworks/MediaPipeTasksCommon.xcframework/ios-arm64/MediaPipeTasksCommon.framework/Info.plist",
)

val systemFrameworks = listOf(
    "AudioToolbox", "Accelerate", "CoreMedia", "AssetsLibrary", "CoreFoundation",
    "CoreGraphics", "CoreImage", "QuartzCore", "AVFoundation", "CoreVideo",
    // Not in MediaPipeTasksCommon's declared podspec frameworks, but its GPU/network/crypto code
    // calls into these directly -- CocoaPods apps get them for free since virtually every iOS app
    // already links them; a bare Kotlin/Native binary doesn't, so they must be added explicitly.
    "OpenGLES", "Metal", "Security",
)

kotlin.targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java).configureEach {
    val (frameworkSliceDir, graphLibName) = when (name) {
        "iosArm64" -> "ios-arm64" to "libMediaPipeTasksCommon_device_graph.a"
        "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator" to "libMediaPipeTasksCommon_simulator_graph.a"
        else -> return@configureEach
    }
    val visionFrameworkDir = vendorDir.dir(
        "MediaPipeTasksVision/frameworks/MediaPipeTasksVision.xcframework/$frameworkSliceDir",
    ).asFile
    val commonFrameworkDir = vendorDir.dir(
        "MediaPipeTasksCommon/frameworks/MediaPipeTasksCommon.xcframework/$frameworkSliceDir",
    ).asFile
    val graphLibPath = vendorDir.dir("MediaPipeTasksCommon/frameworks/graph_libraries").file(graphLibName).asFile

    binaries.all {
        linkerOpts("-F", visionFrameworkDir.path, "-F", commonFrameworkDir.path)
        linkerOpts("-framework", "MediaPipeTasksVision", "-framework", "MediaPipeTasksCommon")
        linkerOpts("-force_load", graphLibPath.path)
        systemFrameworks.forEach { linkerOpts("-framework", it) }
        linkerOpts("-lc++")
        // Objective-C categories (e.g. MediaPipe's internal NSString<->std::string bridging) need
        // this to be properly registered at runtime when linked from a static library -- without
        // it, category methods can crash with "unrecognized selector" even though the containing
        // object file is definitely linked in via -force_load.
        linkerOpts("-ObjC")
    }

    compilations.getByName("main") {
        cinterops {
            create("mediaPipeTasksVision") {
                defFile(project.file("src/nativeInterop/cinterop/MediaPipeTasksVision.def"))
                compilerOpts("-F", visionFrameworkDir.path, "-F", commonFrameworkDir.path)
                tasks.named(interopProcessingTaskName) {
                    dependsOn(fetchMediaPipeVision, fetchMediaPipeCommon)
                }
            }
        }
    }
}
