package com.mreil.easy.publish

import com.mreil.easy.CopyMode
import com.mreil.easy.PublicType
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.Property

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
        enabled.convention(false)
        toMavenLocal.convention(false)
        toMavenCentral.convention(false)
        signingEnabled.convention(true)
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

    @get:CopyMode(CopyMode.Mode.READ_ONLY)
    abstract val toMavenCentral: Property<Boolean>

    override fun toMavenCentral() {
        toMavenCentral.set(true)
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
