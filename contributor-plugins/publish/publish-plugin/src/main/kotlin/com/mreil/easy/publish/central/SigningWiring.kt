package com.mreil.easy.publish.central

import com.mreil.easy.publish.publishExtension
import com.mreil.utils.PropertyResolver
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

/**
 * Wires Gradle's `signing` plugin to sign all `MavenPublication`s with in-memory PGP keys.
 *
 * Behavior (see EasyPublishPlugin):
 * - If `signingEnabled` is false -> no-op.
 * - If `signingEnabled` is true and both `jreleaser.gpg.privateKey` (base64) + `jreleaser.gpg.passphrase`
 *   present -> apply `signing`, `useInMemoryPgpKeys`, `sign(publications)`.
 * - Otherwise (enabled but missing one/both) -> apply `signing` + sign wiring but `Sign` tasks fail
 *   at execution with an actionable message suggesting to provide properties or disable.
 *
 * Keys use the exact JReleaser names (`jreleaser.gpg.*`) so existing CI secrets keep working.
 * TODO - move property names to [com.mreil.easy.publish.EasyPublishExtension] later.
 *
 * Validation is at task execution (not configuration) for configuration-cache compatibility
 * (see PropertyResolver docs: no throwing provider in convention).
 */
internal object SigningWiring {
    fun wire(
        target: Project,
        propertyResolver: PropertyResolver,
    ) {
        val publishExt = target.publishExtension() ?: return
        if (!publishExt.signingEnabled.get()) return

        val privateKeyProvider = propertyResolver.get("jreleaser.gpg.privateKey").base64Decode()
        val passphraseProvider = propertyResolver.get("jreleaser.gpg.passphrase")

        // Apply signing plugin eagerly so SigningExtension + Sign tasks are available.
        target.plugins.apply("signing")
        val signing = target.extensions.getByType(SigningExtension::class.java)
        val publishing = target.extensions.getByType(PublishingExtension::class.java)

        // Lazy required flag - always required when signing is enabled (always sign when keys present).
        // Use provider-backed predicate so disabling the extension skips signing.
        signing.setRequired(publishExt.signingEnabled)

        // Configure in-memory keys lazily. Call with orNull at configuration; if absent,
        // the Sign tasks will fail at execution via the doFirst guard below.
        // This keeps convention lazy and CC-compatible (provider tracked).
        // Only configure when both are present; partial is handled by the validation below
        // to surface an actionable message instead of a cryptic NPE from the signing plugin.
        val privateKeyOrNull = privateKeyProvider.orNull
        val passphraseOrNull = passphraseProvider.orNull
        if (privateKeyOrNull != null && passphraseOrNull != null) {
            signing.useInMemoryPgpKeys(privateKeyOrNull, passphraseOrNull)
        }

        // Sign every MavenPublication (includes java + java-gradle-plugin marker publications).
        // Live collection: configureEach covers future publications.
        publishing.publications.configureEach { publication ->
            signing.sign(publication)
        }

        // Execution-time validation for Sign tasks: fail with actionable message if enabled but keys missing.
        // CC-safe: task actions must capture only CC-serializable values. Project objects (`target`,
        // `publishExt`) and the custom `StringProvider` are not serializable (captured provider fields
        // come back null after a cache round-trip), so key presence is resolved eagerly here — provider
        // reads at configuration time are CC-tracked inputs, so changed properties still invalidate the
        // cache — and only plain Booleans reach the action. Logging goes through the task's own logger.
        val signingEnabledValue = publishExt.signingEnabled.get()
        val hasPrivate = privateKeyProvider.orNull != null
        val hasPassphrase = passphraseProvider.orNull != null
        target.tasks.withType(Sign::class.java).configureEach { signTask ->
            signTask.doFirst("validateSigningKeys") { task ->
                if (signingEnabledValue && (!hasPrivate || !hasPassphrase)) {
                    val missing =
                        buildList {
                            if (!hasPrivate) add("jreleaser.gpg.privateKey (base64 armored private key)")
                            if (!hasPassphrase) add("jreleaser.gpg.passphrase")
                        }.joinToString(", ")
                    // GradleException fails the task; logger.warn also emitted for visibility.
                    task.logger.warn(
                        "Signing is enabled (easy.publish.signingEnabled=true) but missing GPG properties: $missing. " +
                            "Provide them via -P/gradle.properties/env (see PropertyResolver) or disable signing with " +
                            "easy { publish { signingEnabled.set(false) } }.",
                    )
                    throw GradleException(
                        "Signing is enabled but missing GPG properties: $missing. " +
                            "Provide jreleaser.gpg.privateKey and jreleaser.gpg.passphrase or set " +
                            "easy.publish.signingEnabled to false.",
                    )
                }
            }
        }
    }
}
