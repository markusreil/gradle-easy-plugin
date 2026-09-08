package com.mreil.easy.publish

import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Delete

/**
 * Registers a `cleanStagingRepo` task that wipes a project's staging dir before the
 * `mavenStaging` uploads write into it.
 *
 * JReleaser deploys *every* file found under `stagingRepositories` — it never filters
 * stale artifacts from a previous version (e.g. a leftover `1.2.3-SNAPSHOT` next to a
 * fresh `1.2.3`), which fails the Central deploy. Gradle's `maven-publish` only
 * overwrites same-named files, so without cleanup the dir accumulates across builds.
 * Wiping right before the upload tasks (not on the deploy task, which runs after the
 * uploads) keeps the staging dir pristine per publish run.
 *
 * Only the plugin-managed dir (`buildDirectory`/{stagingPath}, the same path listed in
 * the generated JReleaser `stagingRepositories`) is deleted; a user-declared
 * `mavenStaging` repo with a custom URL is an escape hatch the plugin does not manage.
 */
internal object StagingCleanupWiring {
    fun wire(target: Project) {
        val publishExt = target.publishExtension() ?: return
        publishExt.stagingPath.orNull?.let { path ->
            val cleanTask =
                target.tasks.register("cleanStagingRepo", Delete::class.java) { clean ->
                    clean.group = "publishing"
                    clean.description =
                        "Deletes $path before Maven staging uploads (JReleaser deploys every file it finds)"
                    // Provider-based, resolved at execution: CC-safe, no `project` capture.
                    clean.delete(target.layout.buildDirectory.dir(path))
                }
            // Live collection: covers upload tasks created later, e.g. for the staging repo
            // attached by configureMavenRepositories after this wiring runs. The repository
            // is only assigned to the implicit upload task once maven-publish finishes
            // wiring it — later than this configureEach action fires — so the staging check
            // is deferred into a provider realized with the task graph, when the deploy
            // wiring's own repository matching also succeeds.
            target.tasks.withType(PublishToMavenRepository::class.java).configureEach { upload ->
                upload.dependsOn(
                    target
                        .provider { upload.repository?.name }
                        .map { repo -> if (repo == MAVEN_STAGING_REPO) listOf(cleanTask) else emptyList() },
                )
            }
        }
    }
}
