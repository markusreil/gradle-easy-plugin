package com.mreil.easy.release

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

interface EasyReleaseExtension :
    EasyPluginExtension,
    CanBeEnabled {
    companion object : Named {
        override val name: String = "release"
    }

    /**
     * Regex matched against the current VCS branch to decide release readiness.
     */
    val releaseBranchPattern: Property<String>

    /**
     * File whose `version=` line [preReleaseCommit] rewrites to the release version.
     *
     * Defaults to `<rootDir>/gradle.properties`.
     */
    val versionFile: RegularFileProperty

    /**
     * Commit message template for [preReleaseCommit].
     *
     * The placeholder `$v` is replaced with the release version.
     */
    val preReleaseCommitMessage: Property<String>
}
