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
    }
}

val cppSourceDir = rootDir.resolve("core-engine/src/main/cpp")
val cppSources = listOf(
    "scene/SceneGraph.cpp",
    "input/PointSmoother.cpp",
    "input/OneEuroFilter.cpp",
    "render/StrokeRenderer.cpp",
    "render/RibbonTessellator.cpp",
    "ios-shim/GestureCanvasBridge.cpp",
)
val objcppSources = listOf(
    "ios-shim/EaglContext.mm",
)

fun runCommand(vararg command: String): Pair<Int, String> {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    return process.waitFor() to output
}

fun registerBuildNativeLibTask(taskName: String, sdk: String, deploymentTargetFlag: String) = tasks.register(taskName) {
    val outputDir = layout.buildDirectory.dir("nativeLib/$taskName")
    inputs.files((cppSources + objcppSources).map { cppSourceDir.resolve(it) })
    inputs.dir(cppSourceDir)
    outputs.dir(outputDir)
    doLast {
        val outDir = outputDir.get().asFile
        outDir.mkdirs()

        fun compile(relativeSource: String, extraFlags: List<String>): File {
            val sourceFile = cppSourceDir.resolve(relativeSource)
            val objectFile = File(outDir, sourceFile.nameWithoutExtension + ".o")
            val (exitCode, output) = runCommand(
                "xcrun", "--sdk", sdk, "clang++",
                "-arch", "arm64", "-std=c++20", deploymentTargetFlag,
                *extraFlags.toTypedArray(),
                "-I", cppSourceDir.path,
                "-c", sourceFile.path,
                "-o", objectFile.path,
            )
            check(exitCode == 0) { "Compiling $relativeSource for $sdk failed:\n$output" }
            return objectFile
        }

        val objectFiles = cppSources.map { compile(it, emptyList()) } +
            objcppSources.map { compile(it, listOf("-fobjc-arc")) }

        val libFile = File(outDir, "libgesture_canvas_core_ios.a")
        val (arExitCode, arOutput) = runCommand(
            "ar",
            "rcs",
            libFile.path,
            *objectFiles.map { it.path }.toTypedArray(),
        )
        check(arExitCode == 0) { "Archiving $libFile failed:\n$arOutput" }
    }
}

val buildNativeLibIosArm64 = registerBuildNativeLibTask("buildNativeLibIosArm64", "iphoneos", "-mios-version-min=13.0")
val buildNativeLibIosSimulatorArm64 =
    registerBuildNativeLibTask("buildNativeLibIosSimulatorArm64", "iphonesimulator", "-mios-simulator-version-min=13.0")

kotlin.targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java).configureEach {
    val (buildTask, libDirName) = when (name) {
        "iosArm64" -> buildNativeLibIosArm64 to "buildNativeLibIosArm64"
        "iosSimulatorArm64" -> buildNativeLibIosSimulatorArm64 to "buildNativeLibIosSimulatorArm64"
        else -> return@configureEach
    }
    binaries.all {
        linkerOpts("-framework", "OpenGLES", "-framework", "Foundation")
    }
    compilations.getByName("main") {
        cinterops {
            create("gestureCanvasBridge") {
                defFile(project.file("src/nativeInterop/cinterop/GestureCanvasBridge.def"))
                includeDirs(cppSourceDir, cppSourceDir.resolve("ios-shim"))
                extraOpts(
                    "-libraryPath",
                    layout.buildDirectory.dir("nativeLib/$libDirName").get().asFile.path,
                    "-staticLibrary",
                    "libgesture_canvas_core_ios.a",
                )
                tasks.named(interopProcessingTaskName) { dependsOn(buildTask) }
            }
        }
    }
}
