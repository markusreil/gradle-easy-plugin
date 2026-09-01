package com.mreil.easy.publish

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Spec for a single Maven repository declared via [EasyPublishExtension.mavenRepo].
 *
 * Implements [Named] so it can live in a [NamedDomainObjectContainer]; the container
 * element name is fixed at construction time and returned by [getName].
 */
abstract class MavenRepoSpec
    @Inject
    constructor(
        private val repoName: String,
    ) : Named {
        override fun getName(): String = repoName

        /** The repository URL. [EasyPublishPlugin.configureMavenRepositories] resolves it against the project. */
        abstract val url: Property<String>

        /**
         * Whether this repository requires password credentials. When enabled, Gradle
         * resolves the username/password from the repository's credentials properties.
         */
        abstract val passwordCredentials: Property<Boolean>

        init {
            passwordCredentials.convention(false)
        }

        /**
         * Configures [repo] from this spec (name, URL, and optional password credentials).
         *
         * Centralizes the `publishing.repositories.maven {}` wiring so
         * [EasyPublishPlugin] stays thin.
         */
        fun configure(
            target: Project,
            repo: MavenArtifactRepository,
        ) {
            repo.name = name
            repo.setUrl(target.uri(url.get()))
            if (passwordCredentials.get()) {
                repo.credentials(PasswordCredentials::class.java)
            }
        }
    }
