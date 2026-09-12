package com.mreil.easy.vcs

import org.gradle.api.provider.Provider

@Suppress("TooManyFunctions")
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

    /**
     * Reports whether a tag named [name] already exists.
     *
     * Used to gate release-time tag creation: a collision surfaces only at execution otherwise
     * (`git tag <name>` fails, or `git push --atomic` is rejected). Failing fast at the gate
     * with a clear message is the difference between "version already released" and a raw
     * `git` error.
     */
    fun hasTag(name: String): Provider<Boolean>

    /**
     * Reports whether [path] is tracked by the VCS index.
     *
     * Used to gate mutations on tracked files (e.g. release version rewrites): an untracked or
     * ignored file survives `git reset --hard` on rollback, so writes to it cannot be undone.
     * Absolute paths are resolved against the VCS root directory.
     */
    fun isTracked(path: String): Provider<Boolean>
}
