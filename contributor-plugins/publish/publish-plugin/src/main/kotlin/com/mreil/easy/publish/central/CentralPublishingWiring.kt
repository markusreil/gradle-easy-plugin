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
 * - **Staging cleanup**: strips unnecessary checksums (signature checksums and optional
 *   SHA-256/SHA-512) from the staged repo after upload and before the JReleaser deploy
 *   ([StripSignatureChecksumsTask]).
 *
 * Both tasks run in every enabled project (see [EasyPublishPlugin]).
 */
internal object CentralPublishingWiring {
    fun wire(target: Project) {
        val publishExt = target.publishExtension() ?: return
        wirePomCheck(target, publishExt)
        wireStagingCleanup(target, publishExt)
    }

    private fun wirePomCheck(
        target: Project,
        publishExt: DefaultEasyPublishExtension,
    ) {
        val checkerProvider =
            target.tasks.register(
                "checkCentralPoms",
                CheckCentralPomsTask::class.java,
            ) { task ->
                task.onlyIf { publishExt.toMavenCentral.get() }
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
        // Only when central is on - the eager realization cost is accepted there,
        // otherwise task avoidance is fully preserved.
        if (publishExt.toMavenCentral.get()) {
            target.tasks.withType(GenerateMavenPom::class.java).all { generatePom ->
                checkerProvider.configure { checker ->
                    checker.dependsOn(generatePom)
                    checker.pomFiles.from(generatePom.destination)
                }
            }
        }
        target.tasks.withType(PublishToMavenRepository::class.java).configureEach { publishTask ->
            publishTask.dependsOn(checkerProvider)
        }

        // Eager: only an extension value is read (final once afterEnabled runs
        // post-evaluation), so no afterEvaluate deferral is needed.
        checkerProvider.configure { it.enabled = publishExt.toMavenCentral.get() }
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
                    task.onlyIf { publishExt.toMavenCentral.get() }
                }
            stripProvider.configure { it.enabled = publishExt.toMavenCentral.get() }
            // Strip must run AFTER the project's staging upload writes the checksums,
            // and deploy wiring makes it run BEFORE the JReleaser deploy.
            target.tasks.withType(PublishToMavenRepository::class.java).configureEach { upload ->
                upload.dependsOn(
                    target
                        .provider { upload.repository?.name }
                        .map { repo -> if (repo == MAVEN_STAGING_REPO) listOf(stripProvider) else emptyList() },
                )
            }
        }
    }
}
