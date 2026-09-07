package com.mreil.easy.publish.central

import com.mreil.easy.publish.EasyPublishPlugin
import com.mreil.easy.publish.publishExtension
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

/**
 * Per-project wiring for `checkCentralPoms` (Maven Central POM validation).
 *
 * Runs in every enabled project (see [EasyPublishPlugin]): the checker takes that
 * project's own `generatePomFile*` outputs as inputs, and every
 * `PublishToMavenRepository` task of the same project depends on it — so invalid
 * POMs fail fast at upload time with module-scoped errors instead of surfacing
 * late in a root aggregator. Projects without POM tasks skip silently via `onlyIf`;
 * the empty-everywhere case is guarded once at deploy time (see [JreleaserDeployWiring]).
 */
internal object PomCheckWiring {
    fun wire(target: Project) {
        val publishExt = target.publishExtension() ?: return

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
}
