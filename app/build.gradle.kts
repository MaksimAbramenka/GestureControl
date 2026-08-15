import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.gesturecontrol.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.gesturecontrol"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core-camera"))
    implementation(project(":core-ml"))
    implementation(project(":core-ml:gesture-classifier"))
    implementation(project(":core-engine"))
    implementation(project(":core-ui"))
    implementation(project(":core-voice"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.camerax.core)
    implementation(libs.timber)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val voiceModelFileName = "functiongemma-270m-mobile-actions.litertlm"
val voiceModelLocalFile = rootProject.file("ml/models/mobile_actions_q8_ekv1024.litertlm")
val voiceModelDeviceDir = "/sdcard/Android/data/com.gesturecontrol/files/models"
val voiceModelDevicePath = "$voiceModelDeviceDir/$voiceModelFileName"

fun runCommand(vararg command: String): Pair<Int, String> {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    return process.waitFor() to output
}

tasks.register("pushVoiceModel") {
    group = "install"
    description = "Pushes the LiteRT-LM voice-command model to the device if it isn't already there."
    doLast {
        if (!voiceModelLocalFile.exists()) {
            logger.lifecycle(
                "Voice model not found at ${voiceModelLocalFile.path} -- skipping push. " +
                    "Voice commands will report unavailable. See README's \"Voice commands model\" section.",
            )
            return@doLast
        }

        val (statExitValue, statOutput) = runCommand("adb", "shell", "stat", "-c%s", voiceModelDevicePath)
        val remoteSize = if (statExitValue == 0) statOutput.trim().toLongOrNull() else null

        if (remoteSize == voiceModelLocalFile.length()) {
            logger.lifecycle("Voice model already on device (size matches), skipping push.")
            return@doLast
        }

        logger.lifecycle("Pushing voice model to device (~280MB, this may take a minute)...")
        runCommand("adb", "shell", "mkdir", "-p", voiceModelDeviceDir)
        val (pushExitValue, pushOutput) = runCommand("adb", "push", voiceModelLocalFile.path, voiceModelDevicePath)
        if (pushExitValue != 0) {
            logger.warn("Voice model push failed:\n$pushOutput")
        } else {
            logger.lifecycle("Voice model pushed successfully.")
        }
    }
}

afterEvaluate {
    tasks.findByName("installDebug")?.finalizedBy("pushVoiceModel")
}
