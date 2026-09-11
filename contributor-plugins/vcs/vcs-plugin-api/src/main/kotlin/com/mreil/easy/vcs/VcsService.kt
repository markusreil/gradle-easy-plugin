package com.mreil.easy.vcs

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import javax.inject.Inject

abstract class VcsService
    @Inject
    constructor(
        private val providers: ProviderFactory,
    ) : BuildService<VcsService.Params> {
        interface Params : BuildServiceParameters {
            val rootDir: DirectoryProperty
        }

        private val operations: VcsOperations by lazy {
            if (type() == VcsType.GIT) VcsGit(providers, parameters.rootDir) else VcsNone(providers)
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
                branch = operations.branch().get().takeIf { it.isNotEmpty() },
                clean = operations.isClean().get(),
            )
        }

        fun isClean(): Provider<Boolean> = operations.isClean()

        fun isUpToDateWithRemote(): Provider<Boolean> = operations.isUpToDateWithRemote()

        fun push(): Provider<Boolean> = operations.push()

        fun fetch(): Provider<Boolean> = operations.fetch()

        fun remoteUrl(): Provider<String> = operations.remoteUrl()

        fun currentBranch(): Provider<String> = operations.branch()

        fun currentSha(): Provider<String> = operations.currentSha()
    }
