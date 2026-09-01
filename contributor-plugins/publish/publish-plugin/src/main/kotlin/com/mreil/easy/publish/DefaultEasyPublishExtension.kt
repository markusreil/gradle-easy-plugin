package com.mreil.easy.publish

import com.mreil.easy.PublicType
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer

/**
 * Internal implementation of [EasyPublishExtension].
 *
 * Holds the live [mavenRepos] container and implements [mavenRepo] by delegating to it.
 * This type is not part of the public API — consumers should reference [EasyPublishExtension]
 * from `publish-plugin-api`.
 */
@PublicType(EasyPublishExtension::class)
abstract class DefaultEasyPublishExtension : EasyPublishExtension {
    abstract val mavenRepos: NamedDomainObjectContainer<MavenRepoSpec>

    override fun mavenRepo(
        name: String,
        action: Action<MavenRepoSpec>,
    ) {
        mavenRepos.create(name, action)
    }
}
