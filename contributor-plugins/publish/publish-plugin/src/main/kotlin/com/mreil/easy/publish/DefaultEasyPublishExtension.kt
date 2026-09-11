package com.mreil.easy.publish

import com.mreil.easy.CopyMode
import com.mreil.easy.PublicType
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.Property

internal const val SONATYPE_SNAPSHOTS_REPO = "sonatypeSnapshots"
internal const val SONATYPE_SNAPSHOTS_URL = "https://central.sonatype.com/repository/maven-snapshots/"
internal const val MAVEN_STAGING_REPO = "mavenStaging"

/**
 * Internal implementation of [EasyPublishExtension].
 *
 * Holds the live [mavenRepos] container and implements [mavenRepo] by delegating to it.
 * This type is not part of the public API — consumers should reference [EasyPublishExtension]
 * from `publish-plugin-api`.
 */
@PublicType(EasyPublishExtension::class)
abstract class DefaultEasyPublishExtension : EasyPublishExtension {
    init {
        enabled.convention(true)
        toMavenLocal.convention(false)
        toMavenCentral.convention(false)
        sonatypeSnapshots.convention(false)
        // Signing is opt-in: staging-only / local / snapshot publishing skips it.
        // [toMavenCentral] turns it on because the JReleaser deploy verifies every artifact
        // is signed, so consumers must sign before deploy.
        signingEnabled.convention(false)
    }

    abstract val mavenRepos: NamedDomainObjectContainer<MavenRepoSpec>

    @get:CopyMode(CopyMode.Mode.READ_ONLY)
    abstract val stagingPath: Property<String>

    override fun toMavenStaging(path: String) {
        stagingPath.set(path)
    }

    abstract val toMavenLocal: Property<Boolean>

    override fun toMavenLocal() {
        toMavenLocal.set(true)
    }

    // DEEP (inheritable convention, overridable per-project): a single subproject may opt
    // into Maven Central from its own script while the root stays unset. READ_ONLY would
    // disallow the per-project override.
    abstract val toMavenCentral: Property<Boolean>

    /**
     * Enables the JReleaser Central deploy. Unless another staging repo was already chosen,
     * also stages to the default `build/stagingRepo` — a Central deploy needs something to
     * upload, and JReleaser builds its `stagingRepositories` list from this. Also turns on
     * `signingEnabled` because the JReleaser deploy verifies every artifact is signed;
     * callers who want to opt out can set `signingEnabled.set(false)` after calling this.
     */
    override fun toMavenCentral() {
        toMavenCentral.set(true)
        signingEnabled.set(true)
        if (stagingPath.orNull == null) {
            // Best-effort default staging: a subproject inheriting READ_ONLY staging from the
            // root (e.g. when the root is central) cannot change it here — that is intended, and
            // the wiring falls back to the `stagingRepo` default when the path stays unset.
            runCatching { toMavenStaging() }
        }
    }

    @get:CopyMode(CopyMode.Mode.READ_ONLY)
    abstract val sonatypeSnapshots: Property<Boolean>

    override fun toSonatypeSnapshots() {
        sonatypeSnapshots.set(true)
        if (mavenRepos.findByName(SONATYPE_SNAPSHOTS_REPO) == null) {
            mavenRepo(SONATYPE_SNAPSHOTS_REPO, SONATYPE_SNAPSHOTS_URL, true)
        }
    }

    abstract override val signingEnabled: Property<Boolean>

    override fun mavenRepo(
        name: String,
        action: Action<MavenRepoSpec>,
    ) {
        mavenRepos.create(name, action)
    }

    override fun mavenRepo(
        name: String,
        url: String,
        withPasswordCredentials: Boolean,
    ) {
        mavenRepos.create(name) {
            it.url.set(url)
            it.passwordCredentials.set(withPasswordCredentials)
        }
    }
}
