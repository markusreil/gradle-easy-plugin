package com.mreil.easy.vcs

import org.gradle.api.provider.Provider

internal interface VcsOperations {
    fun remoteUrl(): Provider<String>

    fun branch(): Provider<String>

    fun isClean(): Provider<Boolean>

    fun isUpToDateWithRemote(): Provider<Boolean>

    fun push(): Provider<Boolean>

    fun fetch(): Provider<Boolean>
}
