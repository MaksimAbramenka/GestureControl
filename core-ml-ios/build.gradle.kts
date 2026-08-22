plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(17)

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain"))
        }
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

// Every MPP*Options/MPP*Result wrapper class (plus NSString, MPPImage) has a paired
// "ClassName+CategoryName.o" archive member (the standard Objective-C category-per-file naming
// convention -- e.g. "MPPHandLandmarkerOptions+Helpers.o", "MPPImage+Utils.o", almost all named
// "+Helpers.o" but not all, confirmed the hard way when MPPImage's "+Utils.o" category was the one
// actually missing on a real device: -[MPPImage(Utils) imageFrameWithError:] never gets called
// with only a hardcoded test stroke or a Simulator run, since the Simulator has no camera) that
// real code calls into at runtime. Objective-C categories create no ordinary linker-visible symbol
// reference, so a normal (non -ObjC) static link silently drops these .o's -- crashing at runtime
// the first time a task actually exercises one ("unrecognized selector", or MediaPipe's own "One
// of copyTo*Proto: methods must be implemented..." assertion). The usual fix is -ObjC, but that
// force-loads *every* ObjC-containing member of MediaPipeTasksCommon.framework, including
// unrelated text-generation modules (text_summarizer, text_proofreader) that need a litert_lm
// library nobody vendored here. Instead, extract every self-contained "ClassName+Category.o"
// member (checked: none reference litert_lm) into one small combined archive and force_load *that*
// directly at the app level (iosApp/project.yml), for the same "K/N doesn't inherit linker flags
// transitively" reason documented elsewhere in this repo.
fun registerExtractHelpersCategoriesTask(taskName: String, frameworkSliceDir: String) =
    tasks.register(taskName) {
        dependsOn(fetchMediaPipeCommon)
        val frameworkBinary = vendorDir.file(
            "MediaPipeTasksCommon/frameworks/MediaPipeTasksCommon.xcframework/$frameworkSliceDir/MediaPipeTasksCommon.framework/MediaPipeTasksCommon",
        ).asFile
        val outputDir = vendorDir.dir("MediaPipeTasksCommon/extracted/$frameworkSliceDir").asFile
        val libFile = File(outputDir, "libMediaPipeHelpers.a")
        outputs.file(libFile)
        doLast {
            outputDir.deleteRecursively()
            outputDir.mkdirs()

            // The simulator slice is a fat (arm64 + x86_64) binary; ar can't read those directly.
            val (infoExit, infoOutput) = runCommand("lipo", "-info", frameworkBinary.path)
            check(infoExit == 0) { "lipo -info failed for $frameworkSliceDir:\n$infoOutput" }
            val arInput = if (infoOutput.contains("Non-fat")) {
                frameworkBinary
            } else {
                val thinFile = File(outputDir, "MediaPipeTasksCommon.arm64")
                val (thinExit, thinOutput) =
                    runCommand("lipo", "-thin", "arm64", frameworkBinary.path, "-output", thinFile.path)
                check(thinExit == 0) { "lipo -thin arm64 failed for $frameworkSliceDir:\n$thinOutput" }
                thinFile
            }

            val (listExit, listOutput) = runCommand("ar", "t", arInput.path)
            check(listExit == 0) { "Listing members failed for $frameworkSliceDir:\n$listOutput" }
            val members = listOutput.lines().map { it.trim() }.filter { it.contains("+") && it.endsWith(".o") }
            check(members.isNotEmpty()) { "No ClassName+Category.o members found in $arInput" }

            for (member in members) {
                val process = ProcessBuilder("ar", "-x", arInput.path, member)
                    .directory(outputDir)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                check(process.waitFor() == 0) { "Extracting $member for $frameworkSliceDir failed:\n$output" }
            }

            val objectFiles = outputDir.listFiles { f -> f.extension == "o" }.orEmpty()
            val (arExit, arOutput) = runCommand(
                "ar",
                "-rcs",
                libFile.path,
                *objectFiles.map { it.path }.toTypedArray(),
            )
            check(arExit == 0) { "Archiving $libFile failed:\n$arOutput" }
        }
    }

val extractHelpersCategoriesSimulator =
    registerExtractHelpersCategoriesTask("extractHelpersCategoriesSimulator", "ios-arm64_x86_64-simulator")
val extractHelpersCategoriesDevice = registerExtractHelpersCategoriesTask("extractHelpersCategoriesDevice", "ios-arm64")

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
