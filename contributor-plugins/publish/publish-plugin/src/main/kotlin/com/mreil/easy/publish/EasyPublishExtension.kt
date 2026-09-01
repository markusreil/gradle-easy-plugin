package com.mreil.easy.publish

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer

/**
 * The `easy.publish` extension. Declared under the `easy` extension and gated by
 * [CanBeEnabled], so the [EasyPublishPlugin] only activates when this extension is
 * explicitly created/enabled.
 *
 * Use [mavenRepo] to declare named Maven repositories to publish to.
 */
abstract class EasyPublishExtension :
    EasyPluginExtension,
    CanBeEnabled {
    /**
     * Named collection of Maven repositories to publish to. Each entry is created via
     * [mavenRepo]. The collection is live: repositories are attached to the
     * `maven-publish` extension lazily, so elements added here are picked up even if
     * `publishing {}` was already configured.
     */
    abstract val mavenRepos: NamedDomainObjectContainer<MavenRepoSpec>

    /**
     * Declares a named Maven repository to publish to.
     *
     * @param name the repository name (also used as the Gradle repository `name`).
     * @param action configures the created [MavenRepoSpec] (e.g. `url` and `passwordCredentials`).
     */
    fun mavenRepo(
        name: String,
        action: Action<MavenRepoSpec>,
    ) {
        mavenRepos.create(name, action)
    }

    companion object : Named {
        override val name: String = "publish"
    }
}
