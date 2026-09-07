package com.mreil.easy.publish

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named
import org.gradle.api.Action
import org.gradle.api.provider.Property

/**
 * Public API for the `easy.publish` extension.
 *
 * Declared under the `easy` extension and gated by [CanBeEnabled], so the
 * [EasyPublishPlugin][com.mreil.easy.publish.EasyPublishPlugin] only activates when this extension is
 * explicitly enabled via `enabled.set(true)` — it is **disabled by default**
 * (`DefaultEasyPublishExtension` conventions `enabled` to `false`).
 *
 * Use [mavenRepo] to declare named Maven repositories to publish to. The underlying
 * [NamedDomainObjectContainer][org.gradle.api.NamedDomainObjectContainer] of [MavenRepoSpec] is
 * implementation-internal and not exposed in the public API.
 */
interface EasyPublishExtension :
    EasyPluginExtension,
    CanBeEnabled {
    /**
     * Enable maven publishing to a repository relative to the current project's build directory.
     */
    fun toMavenStaging(path: String = "stagingRepo")

    fun toMavenLocal()

    fun toMavenCentral()

    /**
     * Publishes snapshots directly to Sonatype's snapshot repository via `maven-publish`
     * (parallel, no JReleaser round-trip).
     *
     * Creates the `sonatypeSnapshots` repository
     * (`https://central.sonatype.com/repository/maven-snapshots/` with standard
     * `sonatypeSnapshotsUsername`/`sonatypeSnapshotsPassword` credentials) unless it
     * already exists, so a manual `mavenRepo("sonatypeSnapshots", ...)` declaration
     * keeps working. Requires `easy.semver` to be enabled for snapshot/release
     * routing — fails fast otherwise.
     */
    fun toSonatypeSnapshots()

    val signingEnabled: Property<Boolean>

    /**
     * Declares a named Maven repository to publish to.
     *
     * @param name the repository name (also used as the Gradle repository `name`).
     * @param action configures the created [MavenRepoSpec] (e.g. `url` and `passwordCredentials`).
     */
    fun mavenRepo(
        name: String,
        action: Action<MavenRepoSpec>,
    )

    /**
     * Convenience overload for declaring a Maven repository.
     *
     * Builds the underlying [MavenRepoSpec] in the extension, setting [MavenRepoSpec.url]
     * and optionally [MavenRepoSpec.passwordCredentials] without exposing the spec type.
     *
     * @param name the repository name.
     * @param url the repository URL.
     * @param withPasswordCredentials whether to enable password credentials (default `false`).
     */
    fun mavenRepo(
        name: String,
        url: String,
        withPasswordCredentials: Boolean = false,
    )

    companion object : Named {
        override val name: String = "publish"
    }
}
