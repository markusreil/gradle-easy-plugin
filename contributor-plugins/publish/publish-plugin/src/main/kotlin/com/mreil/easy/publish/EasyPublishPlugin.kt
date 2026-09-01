package com.mreil.easy.publish

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.EasyExtension
import com.mreil.easy.EnabledBy
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.Publication
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
class EasyPublishPlugin : AbstractEasyProjectPlugin() {
    /** Applies `maven-publish` once the project has the `java` plugin. */
    override fun afterEnabled(target: Project) {
        if (target.plugins.hasPlugin("java")) {
            target.plugins.apply("maven-publish")
            target.plugins.withId("maven-publish") {
                withMavenPublish(target)
            }
        }
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
     * Every publication is normalised by [configureMavenPublication] (live via
     * `configureEach`), and repositories declared in the extension are attached by
     * [configureMavenRepositories].
     */
    private fun withMavenPublish(target: Project) {
        val publishing = target.extensions.getByType(PublishingExtension::class.java)

        fun ensureDefaultPublication() {
            if (!target.plugins.hasPlugin(JavaGradlePluginPlugin::class.java) &&
                publishing.publications.findByName("maven") == null
            ) {
                createDefaultMavenPublication(target, publishing)
            }
        }

        // Defer to afterEvaluate so all `plugins {}` have been applied; run immediately
        // if already evaluated (e.g., harness `afterEvaluate` already fired).
        target.afterEvaluate { ensureDefaultPublication() }
        if (target.state.executed) ensureDefaultPublication()

        // this collection is live and will configure elements that are in the future
        publishing.publications.configureEach {
            configureMavenPublication(target, it)
        }
        configureMavenRepositories(target, publishing)
    }

    /**
     * Attaches every [MavenRepoSpec] declared in [EasyPublishExtension.mavenRepos]
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
        val publishExt = easy.extensions.findByType(EasyPublishExtension::class.java) ?: return
        publishExt.mavenRepos.forEach { spec ->
            publishing.repositories.maven { repo -> spec.configure(target, repo) }
        }
    }

    /**
     * Normalises a [Publication] to a consistent, publishable shape.
     *
     * For regular [MavenPublication]s the group/artifactId/version are taken from the
     * project (failing if `group`/`version` are unset), while plugin marker publications
     * keep their marker coordinates and only have the version enforced. In both cases a
     * POM is populated with name, description, license and SCM metadata, and version
     * mapping is set up to resolve versions from the runtime classpath.
     */
    private fun configureMavenPublication(
        target: Project,
        publication: Publication,
    ) {
        if (publication !is MavenPublication) return
        val isPluginMarker = publication.name.endsWith("PluginMarkerMaven")
        configurePublicationCoordinates(target, publication, isPluginMarker)
        configurePublicationPom(target, publication)
        configurePublicationVersionMapping(publication)
    }

    private fun configurePublicationCoordinates(
        target: Project,
        publication: MavenPublication,
        isPluginMarker: Boolean,
    ) {
        if (!isPluginMarker) {
            val group = target.group.toString()
            if (group.isEmpty() || group == "unspecified") {
                error("Project group must be set for publication ${publication.name} (e.g. group = \"com.example\")")
            }
            if (publication.groupId.isNullOrEmpty() || publication.groupId == "unspecified") {
                publication.groupId = group
            }
            if (publication.artifactId.isNullOrEmpty()) {
                publication.artifactId = target.name
            }
        }
        val version = target.version.toString()
        if (version.isEmpty() || version == "unspecified") {
            val hint = if (isPluginMarker) "plugin marker publication" else "publication"
            error("Project version must be set for $hint ${publication.name} (e.g. version = \"1.0.0\")")
        }
        if (publication.version.isNullOrEmpty() || publication.version == "unspecified") {
            publication.version = version
        }
    }

    private fun configurePublicationPom(
        target: Project,
        publication: MavenPublication,
    ) {
        publication.pom { pom ->
            pom.name.set(target.name)
            pom.description.set(target.description ?: "Published via EasyPublishPlugin")
            pom.url.set("https://github.com/mreil/gradle-easy-plugin-new")
            pom.licenses { licenses ->
                licenses.license { license ->
                    license.name.set("MIT")
                    license.url.set("https://opensource.org/licenses/MIT")
                }
            }
            pom.scm { scm ->
                scm.url.set("https://github.com/mreil/gradle-easy-plugin-new")
            }
        }
    }

    private fun configurePublicationVersionMapping(publication: MavenPublication) {
        publication.versionMapping { mapping ->
            mapping.usage("java-api") { it.fromResolutionOf("runtimeClasspath") }
            mapping.usage("java-runtime") { it.fromResolutionResult() }
        }
        publication.suppressPomMetadataWarningsFor("java-api")
        publication.suppressPomMetadataWarningsFor("java-runtime")
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
