package com.mreil.easy.publish

/**
 * Declares which version kind a [MavenRepoSpec] receives.
 *
 * Explicit replacement for the former name-substring convention: the role travels with
 * the spec, so renaming a repository can never silently change what it publishes.
 * No default — an unset role fails fast when repositories are wired.
 */
enum class RepoRole {
    /** Receives snapshot versions only. */
    SNAPSHOT,

    /** Receives release versions only. */
    RELEASE,

    /** Receives every version. */
    NEUTRAL,
}
