package com.mreil.easy.publish

import com.mreil.easy.EasyExtension
import com.mreil.utils.PropertyResolver
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

/**
 * Root-only task wiring for Maven Central deployment via JReleaser.
 *
 * Registers `generateJreleaserConfig` (YAML generation) and `checkCentralPoms` (POM
 * validation), both disabled until `toMavenCentral`. The checker aggregates every
 * `GenerateMavenPom` output across all projects as its inputs, so POM validation runs
 * before any upload. Every `PublishToMavenRepository` task and `generateJreleaserConfig`
 * depend on it, and the root `publish` lifecycle task aggregates all subproject `publish`
 * tasks so a single invocation stages and validates every module JReleaser would deploy.
 */
internal object MavenCentralWiring {
    fun wireJreleaserConfig(
        target: Project,
        propertyResolver: PropertyResolver,
    ) {
        if (target != target.rootProject) return
        val publishExt = publishExtension(target) ?: return

        // Register lazily on root only; disabled until toMavenCentral is true. Uses convention defaults.
        val taskProvider =
            target.tasks.register(
                "generateJreleaserConfig",
                GenerateJreleaserConfigTask::class.java,
            ) { task ->
                task.projectName.convention(target.provider { target.name })
                task.outputFile.convention(
                    target.layout.buildDirectory.file("jreleaser/jreleaser.yml"),
                )
                // Default staging path is "stagingRepo" when central is enabled without explicit staging
                val stagingDirProvider =
                    publishExt.stagingPath
                        .orElse("stagingRepo")
                        .map { path ->
                            target.rootProject.layout.buildDirectory
                                .dir(path)
                                .get()
                                .asFile.invariantSeparatorsPath
                        }
                task.stagingDirectory.convention(stagingDirProvider)
                task.gpgPublicKey.convention(propertyResolver.get("jreleaser.gpg.publicKey").orElse("dummy-gpg-public-key"))
                task.gpgPrivateKey.convention(propertyResolver.get("jreleaser.gpg.privateKey").orElse("dummy-gpg-private-key"))
                task.gpgPassphrase.convention(propertyResolver.get("jreleaser.gpg.passphrase").orElse("dummy-gpg-passphrase"))
                task.mavenCentralUsername.convention(
                    propertyResolver.get("jreleaser.mavencentral.username").orElse("dummy-mavencentral-username"),
                )
                task.mavenCentralPassword.convention(
                    propertyResolver.get("jreleaser.mavencentral.password").orElse("dummy-mavencentral-password"),
                )
                task.onlyIf { publishExt.toMavenCentral.get() }
            }

        fun syncEnabled() {
            taskProvider.configure { it.enabled = publishExt.toMavenCentral.get() }
        }
        target.afterEvaluate { syncEnabled() }
        if (target.state.executed) syncEnabled()
    }

    fun wireCheckCentralPoms(target: Project) {
        if (target != target.rootProject) return
        val publishExt = publishExtension(target) ?: return

        val checkerProvider =
            target.tasks.register(
                "checkCentralPoms",
                CheckCentralPomsTask::class.java,
            ) { task ->
                task.onlyIf { publishExt.toMavenCentral.get() }
            }

        // Live collections: pick up publish tasks and subprojects added later, regardless of evaluation order.
        // GenerateMavenPom tasks are hooked eagerly via `all` (not `configureEach`): they are registered
        // lazily and `configureEach` only fires on realization, which may never happen before this wiring
        // is queried. Only when central is on - otherwise task avoidance is preserved.
        val checkPoms = publishExt.toMavenCentral.get()
        target.allprojects { project ->
            if (checkPoms) {
                project.tasks.withType(GenerateMavenPom::class.java).all { generatePom ->
                    checkerProvider.configure { checker ->
                        checker.dependsOn(generatePom)
                        checker.pomFiles.from(generatePom.destination)
                    }
                }
            }
            project.tasks.withType(PublishToMavenRepository::class.java).configureEach { publishTask ->
                publishTask.dependsOn(checkerProvider)
            }
            project.plugins.withId("maven-publish") {
                if (project != target) {
                    if (target.tasks.findByName("publish") == null) {
                        target.tasks.create("publish") { it.group = "publishing" }
                    }
                    target.tasks.named("publish").configure { it.dependsOn(project.tasks.named("publish")) }
                }
            }
        }
        target.tasks.named("generateJreleaserConfig").configure { it.dependsOn(checkerProvider) }

        fun syncEnabled() {
            checkerProvider.configure { it.enabled = publishExt.toMavenCentral.get() }
        }
        target.afterEvaluate { syncEnabled() }
        if (target.state.executed) syncEnabled()
    }

    private fun publishExtension(target: Project): DefaultEasyPublishExtension? {
        val easy = target.extensions.findByType(EasyExtension::class.java) as? ExtensionAware ?: return null
        return easy.extensions.findByType(EasyPublishExtension::class.java) as? DefaultEasyPublishExtension
    }
}
