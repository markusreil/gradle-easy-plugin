package com.mreil.easy.vcs

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class VcsService : BuildService<VcsService.Params> {
    interface Params : BuildServiceParameters {
        val rootDir: DirectoryProperty
    }

    fun type(): VcsType {
        val gitDir =
            parameters.rootDir
                .get()
                .asFile
                .resolve(".git")
        return if (gitDir.exists()) VcsType.GIT else VcsType.NONE
    }

    fun info(): VcsInfo {
        val vcsType = type()
        return VcsInfo(
            type = vcsType,
            branch = if (vcsType == VcsType.GIT) branch() else null,
            clean = isClean(),
        )
    }

    fun isClean(): Boolean = if (type() == VcsType.GIT) git("status", "--porcelain").output.isEmpty() else true

    fun isUpToDateWithRemote(): Boolean {
        if (type() != VcsType.GIT) return true
        val result = git("rev-list", "--count", "HEAD..@{u}")
        return result.exit == 0 && result.output.firstOrNull()?.trim() == "0"
    }

    fun push(): Boolean = type() == VcsType.GIT && git("push").exit == 0

    fun fetch(): Boolean = type() == VcsType.GIT && git("fetch").exit == 0

    fun remoteUrl(): String? = if (type() == VcsType.GIT) VcsGit.remoteUrl(parameters.rootDir) else null

    private fun branch(): String? = git("rev-parse", "--abbrev-ref", "HEAD").output.firstOrNull()

    private fun git(vararg args: String): ProcessResult = VcsGit.run(parameters.rootDir, *args)
}
