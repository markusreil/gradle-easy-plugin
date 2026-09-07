package com.mreil.easy.publish.central

import com.mreil.easy.catalogVersionOrDefault
import com.mreil.easy.isRoot
import com.mreil.easy.publish.publishExtension
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

/**
 * Root-only registration of `publishToMavenCentral` running the JReleaser CLI via `JavaExec`.
 *
 * The CLI jar (`org.jreleaser.cli.Main`) resolves from the resolve-only `jreleaser`
 * configuration at execution time. The deploy task depends on every
 * `PublishToMavenRepository` upload task (all projects, live) and the generated
 * JReleaser config — deliberately not on the root `publish` lifecycle task, so
 * `publish` itself can depend on the deploy task without a task cycle. Either
 * entry point (`publish` or `publishToMavenCentral`) stages everything first;
 * a lone `publish` stays deploy-free unless `toMavenCentral` (deploy skipped).
 */
internal object JreleaserDeployWiring {
    fun wire(target: Project) {
        if (!target.isRoot()) return
        val publishExt = target.publishExtension() ?: return

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
                task.onlyIf { publishExt.toMavenCentral.get() }
            }

        target.ensureRootPublishTask()
        // Stage first, without going through the root `publish` task (see KDoc). A live
        // task collection keeps task avoidance and picks up upload tasks for repositories
        // declared later, regardless of evaluation order. Configured once here — never
        // from inside a task-creation callback (illegal mutation context).
        target.allprojects { project ->
            deployProvider.configure { deploy ->
                deploy.dependsOn(project.tasks.withType(PublishToMavenRepository::class.java))
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
                deploy.hasStagedUploads.set(graph.allTasks.any { it is PublishToMavenRepository })
            }
        }
        // A single `./gradlew publish` stages, validates and deploys — but only when
        // central is on. The edge itself (not just the task) is gated: the deploy task's
        // CLI classpath is unresolvable in builds without repositories and would
        // otherwise break configuration-cache storage of every `publish` graph.
        if (publishExt.toMavenCentral.get()) {
            target.ensureRootPublishTask().configure { it.dependsOn(deployProvider) }
        }

        // Eager (see above): no afterEvaluate deferral needed.
        deployProvider.configure { it.enabled = publishExt.toMavenCentral.get() }
    }
}
