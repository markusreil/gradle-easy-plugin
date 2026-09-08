package com.mreil.easy.publish.central

import com.mreil.easy.isEnabled
import com.mreil.easy.isRoot
import com.mreil.easy.publish.publishExtension
import com.mreil.utils.PropertyResolver
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension

/**
 * Root-only registration of `generateJreleaserConfig` (JReleaser YAML generation).
 *
 * Registered lazily with convention defaults; disabled until `toMavenCentral`.
 * Config generation explicitly waits for every project's `checkCentralPoms`
 * (see [PomCheckWiring]) so POM validation stays a separate step before any
 * config is generated for upload.
 */
internal object JreleaserConfigWiring {
    @Suppress("LongMethod")
    fun wire(
        target: Project,
        propertyResolver: PropertyResolver,
    ) {
        if (!target.isRoot()) return
        val publishExt = target.publishExtension() ?: return

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
                // Per-project staging dirs, resolved eagerly: extension values are final once
                // afterEnabled runs post-evaluation, and inheritance is live provider linkage
                // (see ExtensionCopier), so root-set values are visible here. Only enabled
                // projects are included (mirroring addStagingRepository); a child disabling
                // itself in its own later-evaluated script may still contribute a dangling
                // entry — same tolerance as the previous single shared dir.
                // Default staging path is "stagingRepo" when central is enabled without explicit staging.
                val stagingDirs =
                    target.allprojects
                        .sortedBy { it.path }
                        .mapNotNull { stagingDirFor(it) }
                        .distinct()
                task.stagingDirs.convention(stagingDirs)
                task.mavenCentralUsername.convention(
                    propertyResolver.get(JreleaserVersions.PROPERTY_MAVENCENTRAL_USERNAME),
                )
                task.mavenCentralPassword.convention(
                    propertyResolver.get(JreleaserVersions.PROPERTY_MAVENCENTRAL_PASSWORD),
                )
                task.nexusUrl.convention(propertyResolver.get(JreleaserVersions.PROPERTY_TEST_NEXUS_URL))
                task.nexusUsername.convention(
                    propertyResolver.get(JreleaserVersions.PROPERTY_NEXUS_USERNAME),
                )
                task.nexusPassword.convention(
                    propertyResolver.get(JreleaserVersions.PROPERTY_NEXUS_PASSWORD),
                )
                task.onlyIf { publishExt.toMavenCentral.get() }
            }

        // Eager: only an extension value is read (final once afterEnabled runs
        // post-evaluation), so no afterEvaluate deferral is needed.
        taskProvider.configure { it.enabled = publishExt.toMavenCentral.get() }

        // Explicit validation step (kept separate by design): config generation waits
        // for every project's POM check. Live collection — no eager realization, picks
        // up checks registered later regardless of evaluation order.
        target.allprojects { project ->
            taskProvider.configure { it.dependsOn(project.tasks.withType(CheckCentralPomsTask::class.java)) }
        }

        // Phantom-dir correction (see stagingDirsForPublishing): runs only when generation
        // is really scheduled, so eager unit-test assertions and previews without
        // publications are untouched.
        target.gradle.taskGraph.whenReady { graph ->
            val generate = taskProvider.get()
            if (graph.hasTask(generate)) {
                generate.stagingDirs.set(stagingDirsForPublishing(target.allprojects))
            }
        }
    }

    /**
     * Staging dirs of projects that actually publish something.
     *
     * Publication-less containers (a java-less root, intermediate dirs) stage nothing —
     * their dirs are never created and JReleaser fails on the first missing
     * `stagingRepository`. Publications are final once the task graph is ready.
     */
    internal fun stagingDirsForPublishing(projects: Iterable<Project>): List<String> =
        projects
            .sortedBy { it.path }
            .filter { it.hasMavenPublications() }
            .mapNotNull { stagingDirFor(it) }
            .distinct()

    private fun Project.hasMavenPublications(): Boolean =
        extensions.findByType(PublishingExtension::class.java)?.publications?.isNotEmpty() == true

    private fun stagingDirFor(project: Project): String? =
        project
            .publishExtension()
            ?.takeIf { it.isEnabled() }
            ?.let { ext ->
                val path = ext.stagingPath.orNull ?: "stagingRepo"
                project.layout.buildDirectory
                    .dir(path)
                    .get()
                    .asFile.invariantSeparatorsPath
            }
}
