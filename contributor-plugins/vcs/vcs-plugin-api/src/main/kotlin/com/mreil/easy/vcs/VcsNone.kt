package com.mreil.easy.vcs

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

@Suppress("TooManyFunctions")
internal class VcsNone(
    private val providers: ProviderFactory,
) : VcsOperations {
    override fun remoteUrl(): Provider<String> = providers.provider { "" }

    override fun branch(): Provider<String> = providers.provider { "" }

    override fun currentSha(): Provider<String> = providers.provider { "" }

    override fun isClean(): Provider<Boolean> = providers.provider { true }

    override fun isUpToDateWithRemote(): Provider<Boolean> = providers.provider { true }

    override fun push(): Provider<Boolean> = providers.provider { true }

    override fun push(tag: String): Provider<Boolean> = providers.provider { true }

    override fun fetch(): Provider<Boolean> = providers.provider { false }

    override fun addAndCommit(
        paths: List<String>,
        message: String,
    ): Provider<Boolean> = providers.provider { true }

    override fun tag(name: String): Provider<Boolean> = providers.provider { true }

    override fun isTracked(path: String): Provider<Boolean> = providers.provider { true }
}
