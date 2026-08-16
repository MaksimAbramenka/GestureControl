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
    "ios-shim/GestureCanvasBridge.cpp",
)

fun runCommand(vararg command: String): Pair<Int, String> {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    return process.waitFor() to output
}

fun registerBuildNativeLibTask(taskName: String, sdk: String) = tasks.register(taskName) {
    val outputDir = layout.buildDirectory.dir("nativeLib/$taskName")
    inputs.files(cppSources.map { cppSourceDir.resolve(it) })
    inputs.dir(cppSourceDir)
    outputs.dir(outputDir)
    doLast {
        val outDir = outputDir.get().asFile
        outDir.mkdirs()

        val objectFiles = cppSources.map { relativeSource ->
            val sourceFile = cppSourceDir.resolve(relativeSource)
            val objectFile = File(outDir, sourceFile.nameWithoutExtension + ".o")
            val (exitCode, output) = runCommand(
                "xcrun", "--sdk", sdk, "clang++",
                "-arch", "arm64", "-std=c++20",
                "-I", cppSourceDir.path,
                "-c", sourceFile.path,
                "-o", objectFile.path,
            )
            check(exitCode == 0) { "Compiling $relativeSource for $sdk failed:\n$output" }
            objectFile
        }

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

val buildNativeLibIosArm64 = registerBuildNativeLibTask("buildNativeLibIosArm64", "iphoneos")
val buildNativeLibIosSimulatorArm64 = registerBuildNativeLibTask("buildNativeLibIosSimulatorArm64", "iphonesimulator")

kotlin.targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java).configureEach {
    val (buildTask, libDirName) = when (name) {
        "iosArm64" -> buildNativeLibIosArm64 to "buildNativeLibIosArm64"
        "iosSimulatorArm64" -> buildNativeLibIosSimulatorArm64 to "buildNativeLibIosSimulatorArm64"
        else -> return@configureEach
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
