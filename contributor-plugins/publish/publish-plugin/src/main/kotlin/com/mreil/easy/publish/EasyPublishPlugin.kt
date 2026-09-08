package com.mreil.easy.publish

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.ApplyToSubprojects
import com.mreil.easy.EnabledBy
import com.mreil.easy.isEasyChildEnabled
import com.mreil.easy.publish.central.PomCheckWiring
import com.mreil.easy.publish.central.SigningWiring
import com.mreil.easy.semver.EasySemver
import com.mreil.easy.semver.EasySemverExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
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
 *
 * Maven Central deployment via JReleaser lives in [EasyJreleaserPlugin] (same
 * extension, root-only); this plugin only owns `maven-publish` wiring plus the
 * shared `mavenStaging` file repository consumed by both.
 *
 * Lifecycle:
 *
 * | Phase | What runs | Why |
 * | ----- | --------- | --- |
 * | `init` | `withId + afterEvaluate { ensureDefaultPublication }` | Final plugin set visible, enable-gate deferred |
 * | `afterEnabled` | staging repo, `maven-publish`, `PomCheckWiring` | Eager reads only, no `afterEvaluate` needed |
 */
@EnabledBy(EasyPublishExtension::class)
@ApplyToSubprojects
class EasyPublishPlugin : AbstractEasyProjectPlugin() {
    /**
     * Schedules default `maven` publication creation once `maven-publish` is present.
     *
     * Registered here (not in [afterEnabled]) so no `state.executed` branch is needed:
     * `withId` fires once per application and `afterEvaluate` either defers or runs
     * immediately — exactly once either way. The enabled-gate is read deferred, when
     * `easy { }` configuration is final. Creation itself is deferred past all
     * `plugins {}` applications so the `java-gradle-plugin` check in
     * [ensureDefaultPublication] sees the final plugin set (a `withId` callback can
     * fire before `java-gradle-plugin` is applied in the same block, and an eager
     * check would then incorrectly create a duplicate `maven` publication).
     */
    override fun init(target: Project) {
        target.plugins.withId("maven-publish") {
            target.afterEvaluate {
                if (target.isEasyChildEnabled<EasyPublishExtension>()) {
                    ensureDefaultPublication(target, target.extensions.getByType(PublishingExtension::class.java))
                }
            }
        }
    }

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
            // Signing is handled by the Gradle `signing` plugin (see SigningWiring).
            SigningWiring.wire(target, propertyResolver)
        }
        PomCheckWiring.wire(target)
    }

    /**
     * Configures publications and repositories for the `maven-publish` plugin.
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
        if (target.plugins.hasPlugin(JavaGradlePluginPlugin::class.java)) return
        publishing.publications.findByName("maven")?.let {
            target.logger.lifecycle(
                "Publication 'maven' already exists in project '${target.path}'. " +
                    "The easy-publish plugin creates this publication automatically, so manual creation is not needed.",
            )
        } ?: createDefaultMavenPublication(target, publishing)
    }

    private fun wirePublishToMavenLocal(target: Project) {
        // Eager: only extension values are read (final once afterEnabled runs post-evaluation)
        // and tasks.named is lazy, so no afterEvaluate deferral is needed here.
        if (target.publishExtension()?.toMavenLocal?.get() == true) {
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
        val publishExt = target.publishExtension() ?: return
        requireSemverForSnapshots(target, publishExt)
        val isSnapshot = resolveIsSnapshot(target)
        publishExt.mavenRepos
            .filter { RepoRouting.shouldPublishToRepo(it.name, isSnapshot) }
            .forEach { spec -> publishing.repositories.maven { repo -> spec.configure(target, repo) } }
    }

    /**
     * Snapshot routing ([RepoRouting]) only filters repositories when a semver version is
     * resolvable. Without `easy.semver` every repo — release and snapshot alike — would
     * receive every version, so `toSonatypeSnapshots()` fails fast instead.
     */
    private fun requireSemverForSnapshots(
        target: Project,
        publishExt: DefaultEasyPublishExtension,
    ) {
        if (publishExt.sonatypeSnapshots.get() && !target.isEasyChildEnabled<EasySemverExtension>()) {
            throw GradleException(
                "easy.publish.toSonatypeSnapshots() requires semver for snapshot/release routing. " +
                    "Enable it via easy { semver { enabled.set(true) } }.",
            )
        }
    }

    /**
     * Adds the `mavenStaging` file repository to this project's container, resolving
     * the staging path (inherited from the root/`Settings` configuration) under this
     * project's build directory — every module stages into its own dir, and
     * [EasyJreleaserPlugin] aggregates them into JReleaser's `stagingRepositories`
     * collection for deployment.
     *
     * Runs for every enabled project — unlike publications, the staging repo needs no `java`
     * plugin. Skipped when no staging path is configured or a `mavenStaging` repo already
     * exists (e.g. user-declared, which takes precedence).
     */
    private fun addStagingRepository(target: Project) {
        val publishExt = target.publishExtension() ?: return
        publishExt.stagingPath.orNull?.let { path ->
            if (publishExt.mavenRepos.findByName(MAVEN_STAGING_REPO) == null) {
                val url =
                    target.layout.buildDirectory
                        .dir(path)
                        .get()
                        .asFile.invariantSeparatorsPath
                publishExt.mavenRepo(MAVEN_STAGING_REPO, url)
            }
        }
    }

    private fun resolveIsSnapshot(target: Project): Boolean? =
        RepoRouting.isSnapshot(
            runCatching {
                if (!target.isEasyChildEnabled<EasySemverExtension>()) null else EasySemver.of(target).orNull
            }.getOrNull(),
        )

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
