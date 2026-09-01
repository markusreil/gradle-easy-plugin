package com.mreil.easy.publish

import com.mreil.easy.CanBeEnabled
import com.mreil.easy.EasyPluginExtension
import com.mreil.easy.Named
import org.gradle.api.Action

/**
 * Public API for the `easy.publish` extension.
 *
 * Declared under the `easy` extension and gated by [CanBeEnabled], so the
 * [EasyPublishPlugin][com.mreil.easy.publish.EasyPublishPlugin] only activates when this extension is
 * explicitly created/enabled.
 *
 * Use [mavenRepo] to declare named Maven repositories to publish to. The underlying
 * [NamedDomainObjectContainer][org.gradle.api.NamedDomainObjectContainer] of [MavenRepoSpec] is
 * implementation-internal and not exposed in the public API.
 */
interface EasyPublishExtension :
    EasyPluginExtension,
    CanBeEnabled {
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

    companion object : Named {
        override val name: String = "publish"
    }
}
