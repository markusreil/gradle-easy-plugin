package com.mreil.easy.publish

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.ApplyToSubprojects
import com.mreil.easy.EasyExtension
import com.mreil.easy.EnabledBy
import com.mreil.easy.isExtensionEnabled
import com.mreil.easy.semver.EasySemver
import com.mreil.easy.semver.EasySemverExtension
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin

/**
 * Easy plugin that wraps Gradle's `maven-publish` plugin to publish a project's
 * artifacts to Maven repositories.
 *
 * It is contributed and applied via the easy plugin infrastructure:
 * - Discovered through [EasyPublishContributor] using the [java.util.ServiceLoader] SPI.
 * - Gated behind the [EasyPublishExtension] `easy.publish` extension, so it only
 *   becomes active when that extension is enabled (see [EnabledBy]).
 *
 * When applied to a project that has the `java` plugin, it applies `maven-publish`,
 * creates a default `maven` publication from the `java` component (unless the
 * `java-gradle-plugin` plugin is already publishing its own publications and
 * plugin markers), applies consistent POM metadata and version mapping, and wires
 * up any Maven repositories declared via [EasyPublishExtension.mavenRepo].
 */
@EnabledBy(EasyPublishExtension::class)
@ApplyToSubprojects
class EasyPublishPlugin : AbstractEasyProjectPlugin() {
    /** Applies `maven-publish` once the project has the `java` plugin. */
    override fun afterEnabled(target: Project) {
        // Staging is a publish concern, not a java concern: every enabled project stages into
        // the shared root staging dir (see addStagingRepository), whether or not it has publications.
        addStagingRepository(target)
        if (target.plugins.hasPlugin("java")) {
            target.plugins.apply("maven-publish")
            target.plugins.withId("maven-publish") {
                withMavenPublish(target)
            }
        }
        MavenCentralWiring.wireJreleaserConfig(target, propertyResolver)
        MavenCentralWiring.wireCheckCentralPoms(target)
        MavenCentralWiring.wireJreleaserDeploy(target)
    }

    /**
     * Configures publications and repositories for the `maven-publish` plugin.
     *
     * A default `maven` publication is created from the `java` component unless the
     * `java-gradle-plugin` plugin is present (it creates its own publications and
     * plugin markers). The creation is deferred to `afterEvaluate` + `findByName("maven")`
     * guard to avoid a `withId("maven-publish")` ordering race: `withId` can fire before
     * `java-gradle-plugin` is applied in the same `plugins {}` block, so an eager
     * `hasPlugin` check would incorrectly create a duplicate `maven` publication.
     *
     * Every publication is normalised by [MavenPublicationConfigurer] (live via
     * `configureEach`), and repositories declared in the extension are attached by
     * [configureMavenRepositories].
     *
     * The container `mavenRepos` lives on the internal [DefaultEasyPublishExtension] implementation
     * and is not part of the public [EasyPublishExtension] API.
     */
    private fun withMavenPublish(target: Project) {
        val publishing = target.extensions.getByType(PublishingExtension::class.java)

        // The only existence check after the fact: defer to afterEvaluate so all `plugins {}`
        // have been applied before probing for a user-defined 'maven' publication; run
        // immediately if already evaluated. Either/or: a late-registered afterEvaluate
        // action fires immediately, so doing both would run twice (and the second run would
        // find the self-created publication and log a spurious warning).
        if (target.state.executed) {
            ensureDefaultPublication(target, publishing)
        } else {
            target.afterEvaluate { ensureDefaultPublication(target, publishing) }
        }

        // this collection is live and will configure elements that are in the future
        publishing.publications.configureEach {
            MavenPublicationConfigurer.configure(target, it)
        }
        configureMavenRepositories(target, publishing)
        wirePublishToMavenLocal(target)
    }

    private fun ensureDefaultPublication(
        target: Project,
        publishing: PublishingExtension,
    ) {
        val hasJavaGradlePlugin = target.plugins.hasPlugin(JavaGradlePluginPlugin::class.java)
        val mavenPublication = publishing.publications.findByName("maven")

        if (!hasJavaGradlePlugin && mavenPublication != null) {
            target.logger.lifecycle(
                "Publication 'maven' already exists in project '${target.path}'. " +
                    "The easy-publish plugin creates this publication automatically, so manual creation is not needed.",
            )
        }

        if (!hasJavaGradlePlugin && mavenPublication == null) {
            createDefaultMavenPublication(target, publishing)
        }
    }

    private fun wirePublishToMavenLocal(target: Project) {
        val easy = target.extensions.findByType(EasyExtension::class.java) as? ExtensionAware
        val publishExt = easy?.extensions?.findByType(EasyPublishExtension::class.java) as? DefaultEasyPublishExtension

        // Eager: only extension values are read (final once afterEnabled runs post-evaluation)
        // and tasks.named is lazy, so no afterEvaluate deferral is needed here — unlike
        // ensureDefaultPublication, nothing checks for after-the-fact existence.
        if (publishExt?.toMavenLocal?.get() == true) {
            target.tasks.named("publish").configure { it.dependsOn("publishToMavenLocal") }
        }
    }

    /**
     * Attaches every [MavenRepoSpec] declared in the internal [DefaultEasyPublishExtension.mavenRepos]
     * to [PublishingExtension.repositories] as a `maven` repository.
     *
     * Credentials are only configured when [MavenRepoSpec.passwordCredentials] is
     * enabled, in which case Gradle resolves them from the `-P<name>Username` /
     * `-P<name>Password` project properties (or credentials in `~/.gradle/gradle.properties`).
     */
    private fun configureMavenRepositories(
        target: Project,
        publishing: PublishingExtension,
    ) {
        val easy = target.extensions.findByType(EasyExtension::class.java) as? ExtensionAware ?: return
        val publishExt = easy.extensions.findByType(EasyPublishExtension::class.java) as? DefaultEasyPublishExtension ?: return
        val isSnapshot = resolveIsSnapshot(target)
        publishExt.mavenRepos.forEach { spec ->
            if (!shouldPublishToRepo(spec.name, isSnapshot)) return@forEach
            publishing.repositories.maven { repo -> spec.configure(target, repo) }
        }
    }

    /**
     * Adds the shared `mavenStaging` file repository to this project's container, resolving
     * the staging path (inherited from the root/`Settings` configuration) under the root
     * build directory so every module stages into the same directory JReleaser deploys.
     *
     * Runs for every enabled project — unlike publications, the staging repo needs no `java`
     * plugin. Skipped when no staging path is configured or a `mavenStaging` repo already
     * exists (e.g. user-declared, which takes precedence).
     */
    private fun addStagingRepository(target: Project) {
        val easy = target.extensions.findByType(EasyExtension::class.java) as? ExtensionAware
        val publishExt = easy?.extensions?.findByType(EasyPublishExtension::class.java) as? DefaultEasyPublishExtension
        val path = publishExt?.stagingPath?.orNull
        if (publishExt != null && path != null && publishExt.mavenRepos.findByName("mavenStaging") == null) {
            val url =
                target.rootProject.layout.buildDirectory
                    .dir(path)
                    .get()
                    .asFile.invariantSeparatorsPath
            publishExt.mavenRepo("mavenStaging", url)
        }
    }

    private fun resolveIsSnapshot(target: Project): Boolean? {
        val semver =
            runCatching {
                if (!target.isExtensionEnabled(EasySemverExtension::class)) null else EasySemver.of(target).orNull
            }.getOrNull()
        return semver?.let { !it.isStable }
    }

    private fun shouldPublishToRepo(
        repoName: String,
        isSnapshot: Boolean?,
    ): Boolean {
        if (isSnapshot == null) return true
        val lower = repoName.lowercase()
        val isReleaseRepo = lower.contains("release")
        val isSnapshotRepo = lower.contains("snapshot")
        return when {
            isSnapshot -> !isReleaseRepo
            else -> !isSnapshotRepo
        }
    }

    /**
     * Creates the default `maven` publication backed by the project's `java` component.
     */
    private fun createDefaultMavenPublication(
        target: Project,
        publishing: PublishingExtension,
    ) {
        publishing.publications.create("maven", MavenPublication::class.java) { publication ->
            publication.from(target.components.getByName("java"))
        }
    }
}
