import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)

    val xcf = XCFrameworkConfig(project, "GestureControlKit")

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "GestureControlKit"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain"))
        }
        iosMain.dependencies {
            implementation(project(":core-engine-ios"))
            implementation(project(":core-ml-ios"))
            implementation(project(":core-camera-ios"))
            implementation(project(":core-ui"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Linker flags aren't inherited from library dependencies in Kotlin/Native -- core-engine-ios and
// core-ml-ios's binaries.all{} linkerOpts only apply to *their own* test binaries, not to this
// module's framework, even though it depends on them. Duplicated here rather than refactored into
// a shared convention plugin for now, to keep each module's already-verified linking setup
// untouched; ok to consolidate later if a third consumer shows up.
val mediaPipeVendorDir = rootDir.resolve("core-ml-ios/vendor")
val coreEngineNativeLibDir = rootDir.resolve("core-engine-ios/build/nativeLib")

val mediaPipeSystemFrameworks = listOf(
    "AudioToolbox", "Accelerate", "CoreMedia", "AssetsLibrary", "CoreFoundation",
    "CoreGraphics", "CoreImage", "QuartzCore", "AVFoundation", "CoreVideo",
    "OpenGLES", "Metal", "Security",
)

kotlin.targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java).configureEach {
    val (frameworkSliceDir, graphLibName, nativeLibTaskName) = when (name) {
        "iosArm64" -> Triple("ios-arm64", "libMediaPipeTasksCommon_device_graph.a", "buildNativeLibIosArm64")
        "iosSimulatorArm64" -> Triple(
            "ios-arm64_x86_64-simulator",
            "libMediaPipeTasksCommon_simulator_graph.a",
            "buildNativeLibIosSimulatorArm64",
        )

        else -> return@configureEach
    }
    val visionFrameworkDir = mediaPipeVendorDir.resolve(
        "MediaPipeTasksVision/frameworks/MediaPipeTasksVision.xcframework/$frameworkSliceDir",
    )
    val commonFrameworkDir = mediaPipeVendorDir.resolve(
        "MediaPipeTasksCommon/frameworks/MediaPipeTasksCommon.xcframework/$frameworkSliceDir",
    )
    val graphLibPath = mediaPipeVendorDir.resolve("MediaPipeTasksCommon/frameworks/graph_libraries/$graphLibName")
    val coreEngineLibDir = coreEngineNativeLibDir.resolve(nativeLibTaskName)

    binaries.all {
        linkerOpts("-F", visionFrameworkDir.path, "-F", commonFrameworkDir.path)
        linkerOpts("-framework", "MediaPipeTasksVision", "-framework", "MediaPipeTasksCommon")
        linkerOpts("-force_load", graphLibPath.path)
        mediaPipeSystemFrameworks.forEach { linkerOpts("-framework", it) }
        linkerOpts("-lc++", "-ObjC")
        linkerOpts("-L", coreEngineLibDir.path, "-lgesture_canvas_core_ios")
        linkerOpts("-framework", "Foundation")
    }
}
