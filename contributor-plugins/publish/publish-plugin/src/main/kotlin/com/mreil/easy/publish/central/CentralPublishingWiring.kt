package com.mreil.easy.publish.central

import com.mreil.easy.publish.DefaultEasyPublishExtension
import com.mreil.easy.publish.EasyPublishPlugin
import com.mreil.easy.publish.MAVEN_STAGING_REPO
import com.mreil.easy.publish.publishExtension
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

/**
 * Per-project wiring for Maven Central publishing preparation.
 *
 * Owns two responsibilities:
 * - **POM validation**: validates generated POMs before staging ([CheckCentralPomsTask]).
 * - **Staging cleanup**: strips unneeded checksums (signature checksums and optional
 *   SHA-256/SHA-512) from the staging dir after the `mavenStaging` upload and before
 *   the JReleaser deploy ([StripSignatureChecksumsTask]). Two-phase pipeline per
 *   project: (1) `maven-publish` stages artifacts into the local staging dir,
 *   (2) `stripSignatureChecksums` removes the checksums Central rejects,
 *   (3) root `publishToMavenCentral` deploys the cleaned staging dirs.
 *   Contrast `cleanStagingRepo`, which runs *before* the staging upload.
 *
 * Both tasks run in every enabled project (see [EasyPublishPlugin]). The wiring only
 * runs when [com.mreil.easy.publish.EasyPublishExtension.toMavenCentral] is set — see
 * [EasyJreleaserPlugin] for the gating rule.
 */
internal object CentralPublishingWiring {
    fun wire(target: Project) {
        val publishExt = target.publishExtension() ?: return
        wirePomCheck(target)
        wireStagingCleanup(target, publishExt)
    }

    private fun wirePomCheck(target: Project) {
        val checkerProvider =
            target.tasks.register(
                "checkCentralPoms",
                CheckCentralPomsTask::class.java,
            ) { task ->
                task.onlyIf("has POM inputs") { check ->
                    (check as CheckCentralPomsTask).pomFiles.files.isNotEmpty()
                }
            }

        // Hook POM tasks eagerly via `withType` + `all`: per docs `all` attaches on
        // registration (realizing as needed), covering existing and future tasks —
        // so no nested afterEvaluate is needed regardless of when publications (and
        // hence their generatePom tasks) are created. `configureEach` would only fire
        // on realization, which may never happen before this wiring is queried
        // (order-dependent unit tests, silent skip on standalone runs).
        target.tasks.withType(GenerateMavenPom::class.java).all { generatePom ->
            checkerProvider.configure { checker ->
                checker.dependsOn(generatePom)
                checker.pomFiles.from(generatePom.destination)
            }
        }
        target.tasks.withType(PublishToMavenRepository::class.java).configureEach { publishTask ->
            publishTask.dependsOn(checkerProvider)
        }
    }

    private fun wireStagingCleanup(
        target: Project,
        publishExt: DefaultEasyPublishExtension,
    ) {
        // staging dir only exists when a staging path is set (toMavenStaging / toMavenCentral)
        publishExt.stagingPath.orNull?.let { path ->
            val stripProvider =
                target.tasks.register("stripSignatureChecksums", StripSignatureChecksumsTask::class.java) { task ->
                    task.group = "verification"
                    task.stagingDir.set(target.layout.buildDirectory.dir(path))
                }
            // Two-phase pipeline: (1) maven-publish stages into the local dir,
            // (2) strip removes Central-rejected checksums, (3) JReleaser deploys.
            // Strip runs AFTER the staging upload (finalizer) and BEFORE the deploy
            // (deploy wiring depends on this task). Contrast cleanStagingRepo, which
            // the staging upload depends on (runs BEFORE). finalizedBy (not an
            // inverted dependsOn) keeps the deferred repository check: the repo is
            // only assigned to the implicit upload task once maven-publish finishes
            // wiring it, so the staging check stays inside a provider realized with
            // the task graph — same mechanism StagingCleanupWiring uses.
            target.tasks.withType(PublishToMavenRepository::class.java).configureEach { upload ->
                upload.finalizedBy(
                    target
                        .provider { upload.repository?.name }
                        .map { repo -> if (repo == MAVEN_STAGING_REPO) listOf(stripProvider) else emptyList() },
                )
            }
        }
    }
}
