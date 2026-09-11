package com.mreil.easy.vcs

import org.gradle.api.provider.Provider

internal interface VcsOperations {
    fun remoteUrl(): Provider<String>

    fun branch(): Provider<String>

    fun currentSha(): Provider<String>

    fun isClean(): Provider<Boolean>

    fun isUpToDateWithRemote(): Provider<Boolean>

    fun push(): Provider<Boolean>

    /**
     * Pushes the current branch and the named tag in a single atomic operation.
     */
    fun push(tag: String): Provider<Boolean>

    fun fetch(): Provider<Boolean>

    fun addAndCommit(
        paths: List<String>,
        message: String,
    ): Provider<Boolean>

    fun tag(name: String): Provider<Boolean>
}
