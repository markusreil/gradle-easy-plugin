package com.mreil.easy.release

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.EnabledBy
import com.mreil.easy.findEasyChild
import com.mreil.easy.isRoot
import com.mreil.easy.semver.EasySemver
import com.mreil.easy.vcs.EasyVcs
import com.mreil.utils.hasGroup
import com.mreil.utils.hasVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

@EnabledBy(EasyReleaseExtension::class)
abstract class EasyReleasePlugin : AbstractEasyProjectPlugin() {
    @get:Inject
    abstract val listenerRegistry: BuildEventsListenerRegistry

    override fun afterEnabled(target: Project) {
        if (!target.isRoot()) return
        val release = target.findEasyChild<EasyReleaseExtension, DefaultEasyReleaseExtension>() ?: return
        val vcs = EasyVcs.of(target)
        registerService(target)
        val check =
            target.tasks.register("preReleaseCheck", PreReleaseCheckTask::class.java) {
                it.group = "release"
                it.description = "Verifies release readiness and resolves versions; gates release."
                it.releaseBranchPattern.set(release.releaseBranchPattern)
                it.hasGroup.set(target.hasGroup())
                it.hasVersion.set(target.hasVersion())
                it.branch.set(vcs.flatMap { service -> service.currentBranch() })
                it.clean.set(vcs.flatMap { service -> service.isClean() })
                it.upToDate.set(vcs.flatMap { service -> service.isUpToDateWithRemote() })
                it.commitSha.set(vcs.flatMap { service -> service.currentSha() })
            }
        target.tasks.register("release", Task::class.java) {
            it.group = "release"
            it.description = "Release wiring task (other release tasks attach here)."
            it.dependsOn(check)
        }
        target.tasks.configureEach {
            if (it.name != "preReleaseCheck" && it.group == "release") {
                it.dependsOn(check)
            }
        }
    }

    private fun registerService(target: Project) {
        val semver = EasySemver.of(target)
        val semverRelease = semver.map { it.withClearedPreRelease().toString() }
        val semverNext = semver.map { it.withIncPatch().withPreRelease("SNAPSHOT").toString() }
        val releaseService =
            target.gradle.sharedServices.registerIfAbsent("release", ReleaseStateService::class.java) {
                it.parameters.apply {
                    releaseVersion.set(propertyResolver.get(RELEASE_VERSION_PROPERTY).orElse(semverRelease))
                    nextVersion.set(propertyResolver.get(NEXT_VERSION_PROPERTY).orElse(semverNext))
                    projectName.set(target.name)
                    currentVersion.set(target.version.toString())
                }
            }
        listenerRegistry.onTaskCompletion(releaseService)
    }

    companion object {
        const val RELEASE_VERSION_PROPERTY = "easy.release.version"
        const val NEXT_VERSION_PROPERTY = "easy.release.nextVersion"
    }
}
