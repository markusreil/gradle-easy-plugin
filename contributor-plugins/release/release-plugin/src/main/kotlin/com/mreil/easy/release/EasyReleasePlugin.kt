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

@EnabledBy(EasyReleaseExtension::class)
class EasyReleasePlugin : AbstractEasyProjectPlugin() {
    override fun init(target: Project) {
    }

    override fun afterEnabled(target: Project) {
        if (!target.isRoot()) return
        val release = target.findEasyChild<EasyReleaseExtension, DefaultEasyReleaseExtension>() ?: return
        val vcs = EasyVcs.of(target)
        val semver = EasySemver.of(target)
        val semverRelease = semver.map { it.withClearedPreRelease().toString() }
        val semverNext = semver.map { it.withIncPatch().withPreRelease("SNAPSHOT").toString() }
        val releaseVersion = propertyResolver.get(RELEASE_VERSION_PROPERTY).orElse(semverRelease)
        val nextVersion = propertyResolver.get(NEXT_VERSION_PROPERTY).orElse(semverNext)
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
                it.projectName.set(target.name)
                it.currentVersion.set(target.version.toString())
                it.releaseVersion.set(releaseVersion)
                it.nextVersion.set(nextVersion)
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

    companion object {
        const val RELEASE_VERSION_PROPERTY = "easy.release.version"
        const val NEXT_VERSION_PROPERTY = "easy.release.nextVersion"
    }
}
