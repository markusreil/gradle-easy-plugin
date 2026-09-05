package com.mreil.easy

import com.mreil.utils.PropertyResolver
import org.gradle.api.Project

/**
 * Shared helper for migration hints that notify users about redundant manual configuration.
 *
 * Hints are logged with [org.gradle.api.logging.Logger.lifecycle] per project and can be disabled
 * via `easy.migrationHintsEnabled=false` (environment variable, system or Gradle property).
 */
object MigrationHints {
    const val PROPERTY_NAME = "easy.migrationHintsEnabled"

    fun isEnabled(target: Project): Boolean =
        PropertyResolver(target.providers)
            .get(PROPERTY_NAME)
            .map { it.toBoolean() }
            .orElse(true)
            .get()

    fun notifyRedundantConfig(
        target: Project,
        what: String,
        manualSnippet: String = what,
    ) {
        if (!isEnabled(target)) return
        target.logger.lifecycle(
            "[easy] [{}] '{}' is already configured manually. " +
                "The Easy plugin handles this automatically - consider removing '{}' from your build file.",
            target.path,
            what,
            manualSnippet,
        )
    }
}

/**
 * Logs a consistent migration hint when [what] was already configured manually.
 *
 * No-op when `easy.migrationHintsEnabled=false`.
 */
fun Project.notifyRedundantConfig(
    what: String,
    manualSnippet: String = what,
) = MigrationHints.notifyRedundantConfig(this, what, manualSnippet)
