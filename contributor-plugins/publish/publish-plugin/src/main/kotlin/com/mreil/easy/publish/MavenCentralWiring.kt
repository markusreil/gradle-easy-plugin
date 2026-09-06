package com.mreil.easy.publish

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.mreil.easy.EasyExtension
import com.mreil.utils.PropertyResolver
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

/**
 * Root-only task wiring for Maven Central deployment via JReleaser.
 *
 * Registers `generateJreleaserConfig` (YAML generation), `checkCentralPoms` (POM
 * validation) and `publishToMavenCentral` (JReleaser `deploy`), all disabled until
 * `toMavenCentral`. The checker aggregates every `GenerateMavenPom` output across all
 * projects as its inputs, so POM validation runs before any upload. Every
 * `PublishToMavenRepository` task and `generateJreleaserConfig` depend on it, and the
 * root `publish` lifecycle task aggregates all subproject `publish` tasks so a single
 * invocation stages and validates every module JReleaser would deploy.
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
                task.projectVersion.convention(target.provider { target.version.toString() })
                task.projectGroupId.convention(target.provider { target.group.toString() })
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
                // StringProvider is a decorating wrapper Gradle cannot consume directly;
                // map() unwraps to the underlying provider (absent stays absent for @Optional).
                task.nexusUrl.convention(propertyResolver.get("jreleaser.testNexusUrl").map { it })
                task.nexusUsername.convention(
                    propertyResolver.get("jreleaser.nexus.username").orElse("dummy-nexus-username"),
                )
                task.nexusPassword.convention(
                    propertyResolver.get("jreleaser.nexus.password").orElse("dummy-nexus-password"),
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
                        target.tasks.register("publish") { it.group = "publishing" }
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

    /**
     * Registers `publishToMavenCentral` running the JReleaser CLI via `JavaExec`.
     *
     * The CLI jar (`org.jreleaser.cli.Main`) resolves from the resolve-only `jreleaser`
     * configuration at execution time. Depends on the root `publish` lifecycle task
     * (ensuring it exists first, mirroring the aggregation wiring) and the generated
     * JReleaser config.
     */
    fun wireJreleaserDeploy(target: Project) {
        if (target != target.rootProject) return
        val publishExt = publishExtension(target) ?: return

        val jreleaserConf =
            target.configurations.maybeCreate("jreleaser").apply {
                isCanBeResolved = true
                isCanBeConsumed = false
            }
        if (jreleaserConf.dependencies.none { it.group == "org.jreleaser" && it.name == "jreleaser" }) {
            target.dependencies.add("jreleaser", "${JreleaserVersions.COORDINATES}:${JreleaserVersions.CLI}")
        }

        val deployProvider =
            target.tasks.register(
                "publishToMavenCentral",
                JreleaserPublishTask::class.java,
            ) { task ->
                task.jreleaserClasspath.from(jreleaserConf)
                task.configFile.convention(
                    target.tasks
                        .named("generateJreleaserConfig", GenerateJreleaserConfigTask::class.java)
                        .flatMap { it.outputFile },
                )
                task.projectVersion.convention(target.provider { target.version.toString() })
                task.dryRun.convention(false)
                task.onlyIf { publishExt.toMavenCentral.get() }
            }

        if (target.tasks.findByName("publish") == null) {
            target.tasks.register("publish") { it.group = "publishing" }
        }
        deployProvider.configure {
            it.dependsOn(target.tasks.named("publish"))
            it.dependsOn(target.tasks.named("generateJreleaserConfig"))
        }

        fun syncEnabled() {
            deployProvider.configure { it.enabled = publishExt.toMavenCentral.get() }
        }
        target.afterEvaluate { syncEnabled() }
        if (target.state.executed) syncEnabled()
    }

    /**
     * Resolved JReleaser config values (plain data, no Gradle types).
     *
     * [GenerateJreleaserConfigTask] keeps the lazy `@Input` properties and maps them
     * to this at execution time; tests construct it directly without a Project.
     */
    data class Config(
        val projectName: String,
        val projectVersion: String,
        val projectGroupId: String,
        val stagingDir: String,
        val gpgPublicKey: String,
        val gpgPrivateKey: String,
        val gpgPassphrase: String,
        val mavenCentralUsername: String,
        val mavenCentralPassword: String,
        val nexusUrl: String? = null,
        val nexusUsername: String = "",
        val nexusPassword: String = "",
    )

    internal fun buildYaml(config: Config): String {
        val fullConfig =
            linkedMapOf(
                "project" to
                    linkedMapOf(
                        "name" to config.projectName,
                        "version" to config.projectVersion,
                        "languages" to
                            linkedMapOf(
                                // artifactId defaults to the project name; groupId is required
                                // (deployer namespaces and artifact matching default to it).
                                "java" to linkedMapOf("groupId" to config.projectGroupId),
                            ),
                    ),
                "signing" to signingBlock(config.gpgPublicKey, config.gpgPrivateKey, config.gpgPassphrase),
                "deploy" to
                    linkedMapOf(
                        "maven" to
                            LinkedHashMap<String, Any>().also { maven ->
                                deployersFor(config).forEach { deployer ->
                                    maven[deployer.section] = mapOf(deployer.name to deployer.toMap())
                                }
                            },
                    ),
            )
        val yaml = yamlMapper.writeValueAsString(fullConfig)
        return "# Generated by EasyPublishPlugin — JReleaser config for Maven Central\n" + yaml
    }

    private fun signingBlock(
        gpgPublicKey: String,
        gpgPrivateKey: String,
        gpgPassphrase: String,
    ): Map<String, Any> =
        linkedMapOf(
            "active" to "ALWAYS",
            "armored" to true,
            "pgp" to
                linkedMapOf(
                    "active" to "ALWAYS",
                    "armored" to true,
                    "publicKey" to gpgPublicKey,
                    "secretKey" to gpgPrivateKey,
                    "passphrase" to gpgPassphrase,
                ),
        )

    private val yamlMapper: ObjectMapper =
        ObjectMapper(
            YAMLFactory
                .builder()
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build(),
        ).registerKotlinModule()
}
