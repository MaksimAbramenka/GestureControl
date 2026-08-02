plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

val ktlintChainWrapThreshold =
    "ktlint_chain_method_rule_force_multiline_when_chain_operator_count_greater_or_equal_than" to "6"

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(
                mapOf(
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    ktlintChainWrapThreshold,
                ),
            )
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**/*.kts")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(mapOf(ktlintChainWrapThreshold))
    }
}
