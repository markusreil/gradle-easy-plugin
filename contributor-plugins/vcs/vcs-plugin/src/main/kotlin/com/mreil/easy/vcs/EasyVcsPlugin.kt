package com.mreil.easy.vcs

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.EnabledBy
import com.mreil.easy.isRoot
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
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
        // Service is injected via @ServiceReference; VCS detection happens in the
        // action at execution time, so no wiring is needed here.
        target.tasks.register("vcsStatus", VcsStatusTask::class.java)
    }
}

/**
 * Prints the detected VCS type, branch and clean state.
 *
 * Resolves the shared [VcsService] via [ServiceReference] and queries it inside
 * the action. VCS detection shells out to git, which is only legal at execution
 * time for the configuration cache, so the service is never realized during
 * configuration.
 */
abstract class VcsStatusTask : DefaultTask() {
    @get:ServiceReference("vcs")
    abstract val vcs: Property<VcsService>

    @TaskAction
    fun printStatus() {
        val info = vcs.get().info()
        logger.lifecycle("VCS type=${info.type}, branch=${info.branch ?: "n/a"}, clean=${info.clean}")
    }
}
