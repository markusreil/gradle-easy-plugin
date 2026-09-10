package com.mreil.easy.publish.central

import com.mreil.easy.catalogVersionOrDefault
import com.mreil.easy.isRoot
import com.mreil.easy.publish.MAVEN_STAGING_REPO
import com.mreil.easy.publish.isCentralEnabled
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

/**
 * Root-only registration of `publishToMavenCentral` running the JReleaser CLI via `JavaExec`.
 *
 * The CLI jar (`org.jreleaser.cli.Main`) resolves from the resolve-only `jreleaser`
 * configuration at execution time. The deploy task depends on every `mavenStaging`
 * `PublishToMavenRepository` upload task from central-enabled projects (all projects, live)
 * and the generated JReleaser config — deliberately not on the root `publish` lifecycle
 * task, so `publish` itself can depend on the deploy task without a task cycle. Only staging
 * uploads feed the deploy: JReleaser uploads the `stagingRepositories` collection, so
 * other repos (e.g. `sonatypeSnapshots`) must neither gate nor precede it. Either
 * entry point (`publish` or `publishToMavenCentral`) stages everything first;
 * the wiring only runs when any project opts into Maven Central — root-inherited or a
 * single subproject — see [EasyJreleaserPlugin] for the ANY gating rule.
 */
internal object JreleaserDeployWiring {
    fun wire(target: Project) {
        if (!target.isRoot()) return

        val jreleaserConf =
            target.configurations.maybeCreate("jreleaser").apply {
                isCanBeResolved = true
                isCanBeConsumed = false
            }
        if (jreleaserConf.dependencies.none { it.group == "org.jreleaser" && it.name == "jreleaser" }) {
            val version = target.catalogVersionOrDefault("jreleaser", JreleaserVersions.DEFAULT_VERSION)
            target.dependencies.add("jreleaser", "${JreleaserVersions.COORDINATES}:$version")
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
                task.hasStagedUploads.convention(true)
            }

        target.ensureRootPublishTask()
        // Stage first, without going through the root `publish` task (see KDoc). Only the
        // `mavenStaging` uploads feed the JReleaser deploy (it uploads the staging
        // collection); other repos are excluded. A live task collection keeps task
        // avoidance and picks up upload tasks for repositories declared later,
        // regardless of evaluation order. Configured once here — never from inside a
        // task-creation callback (illegal mutation context).
        target.allprojects { project ->
            if (project.isCentralEnabled()) {
                deployProvider.configure { deploy ->
                    deploy.dependsOn(
                        project.tasks
                            .withType(PublishToMavenRepository::class.java)
                            // Null-safe: `repository` is unassigned while implicit upload tasks are
                            // being created, and the spec may be evaluated mid-creation.
                            .matching { it.repository?.name == MAVEN_STAGING_REPO },
                    )
                    // Strip after the staging upload, before deploy (second upload to Central).
                    deploy.dependsOn(project.tasks.withType(StripSignatureChecksumsTask::class.java))
                }
            }
        }
        deployProvider.configure {
            it.dependsOn(target.tasks.named("generateJreleaserConfig"))
        }
        // Empty-everywhere guard: when central is on but no project stages anything
        // (e.g. no publications at all), skip deploy with a warning instead of failing.
        // Resolved once the task graph is final: `whenReady` runs before
        // configuration-cache storage, so the outcome is a plain task input — the task
        // itself only reads its own property, never `taskDependencies` at execution
        // time (unsupported with the configuration cache).
        target.gradle.taskGraph.whenReady { graph ->
            val deploy = deployProvider.get()
            if (graph.hasTask(deploy)) {
                deploy.hasStagedUploads.set(
                    graph.allTasks.any {
                        it is PublishToMavenRepository && it.repository?.name == MAVEN_STAGING_REPO
                    },
                )
            }
        }
        // A single `./gradlew publish` stages, validates and deploys — unconditionally
        // on this edge. Snapshots never reach this wiring at all: `EasyJreleaserPlugin`
        // skips all central wiring for them (via `skipReason`), so `publishToMavenCentral`
        // — and this `publish` dependency — only exist when the version is a release.
        target.ensureRootPublishTask().configure { it.dependsOn(deployProvider) }
    }
}
