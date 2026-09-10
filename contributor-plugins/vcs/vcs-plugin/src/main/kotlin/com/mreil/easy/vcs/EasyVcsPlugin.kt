package com.mreil.easy.vcs

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.EnabledBy
import com.mreil.easy.isRoot
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Easy plugin that exposes the shared [VcsService] and a `vcsStatus` diagnostic task.
 *
 * The service is registered eagerly in [init] (apply time) so other contributors
 * can resolve it regardless of evaluation order; the task is registered lazily in
 * [afterEnabled] behind the enabled flag.
 */
@EnabledBy(EasyVcsExtension::class)
class EasyVcsPlugin : AbstractEasyProjectPlugin() {
    override fun init(target: Project) {
        if (!target.isRoot()) return
        target.gradle.sharedServices.registerIfAbsent("vcs", VcsService::class.java) {
            it.parameters.rootDir.set(target.layout.projectDirectory)
        }
    }

    override fun afterEnabled(target: Project) {
        if (!target.isRoot()) return
        target.tasks.register("vcsStatus", VcsStatusTask::class.java) { task ->
            task.status.set(
                EasyVcs.of(target).map { vcs ->
                    val info = vcs.info()
                    "VCS type=${info.type}, branch=${info.branch ?: "n/a"}, clean=${info.clean}"
                },
            )
        }
    }
}

/**
 * Prints the detected VCS type, branch and clean state.
 *
 * Captures only a [Property] provider (no [Project] at execution) to stay
 * configuration-cache compatible.
 */
abstract class VcsStatusTask : DefaultTask() {
    @get:Input
    abstract val status: Property<String>

    @TaskAction
    fun printStatus() {
        logger.lifecycle(status.get())
    }
}
