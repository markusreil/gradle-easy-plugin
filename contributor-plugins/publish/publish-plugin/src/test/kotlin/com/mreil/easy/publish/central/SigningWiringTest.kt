package com.mreil.easy.publish.central

import com.mreil.easy.ProjectPlugin
import com.mreil.easy.publish.MAVEN_STAGING_REPO
import com.mreil.easy.publish.publishExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.plugins.signing.Sign
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import org.junit.jupiter.api.Test as JUnitTest

/**
 * Unit tests for [SigningWiring] — proves the wiring registers an actionable
 * `doFirst` on every `Sign` task that fires when signing is enabled but the
 * GPG properties are missing.
 *
 * The full lifecycle is covered by `SigningFuncTest`; this file gives JaCoCo
 * coverage of the closure body and a fast feedback path for the error message.
 */
class SigningWiringTest {
    @TempDir
    lateinit var stagingDir: File

    @JUnitTest
    fun `sign task doFirst throws actionable GradleException when keys are missing`() {
        val project = projectWithPublicationAndSigning(signingEnabled = true)

        val signTask = firstSignTask(project)

        // The doFirst registered by SigningWiring sits at the front of the action list.
        val validateAction = signTask.actions.first()

        assertThatThrownBy { validateAction.execute(signTask) }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("Signing is enabled but missing GPG properties")
            .hasMessageContaining("signingEnabled")
    }

    @JUnitTest
    fun `sign task doFirst lists every missing gpg property`() {
        val project = projectWithPublicationAndSigning(signingEnabled = true)

        val signTask = firstSignTask(project)
        val validateAction = signTask.actions.first()

        assertThatThrownBy { validateAction.execute(signTask) }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("jreleaser.gpg.privateKey")
            .hasMessageContaining("jreleaser.gpg.passphrase")
    }

    @JUnitTest
    fun `sign task doFirst is a no-op when keys are present`() {
        // Provide both keys via system properties (PropertyResolver reads them there).
        // PropertyResolver normalizes "jreleaser.gpg.privateKey" to "jreleaser.gpg.private.key".
        val privateKey = "ZmFrZS1rZXk=" // base64("fake-key")
        val passphrase = "test-passphrase"
        System.setProperty("jreleaser.gpg.privateKey", privateKey)
        System.setProperty("jreleaser.gpg.passphrase", passphrase)
        try {
            val project = projectWithPublicationAndSigning(signingEnabled = true)

            val signTask = firstSignTask(project)
            val validateAction = signTask.actions.first()

            assertThatCode { validateAction.execute(signTask) }.doesNotThrowAnyException()
        } finally {
            System.clearProperty("jreleaser.gpg.privateKey")
            System.clearProperty("jreleaser.gpg.passphrase")
        }
    }

    @JUnitTest
    fun `sign task doFirst is a no-op when signing is disabled even without keys`() {
        val project = projectWithPublicationAndSigning(signingEnabled = false)

        // When signing is disabled, the wiring short-circuits before registering a doFirst,
        // so the Sign task may not exist; if it does (no wiring ran), the action is absent.
        val signTasks = project.tasks.withType(Sign::class.java).toList()
        assertSoftly { softly ->
            softly.assertThat(signTasks).isEmpty()
        }
    }

    /**
     * Builds a project with `java-library` + `maven-publish`, a Maven publication,
     * and the publish extension enabled with `signingEnabled` as requested. Order
     * matters: signingEnabled must be set BEFORE the first `evaluate()` because
     * `EasyPublishPlugin.afterEnabled` (fired by `afterEvaluate`) calls
     * `SigningWiring.wire` itself.
     */
    private fun projectWithPublicationAndSigning(signingEnabled: Boolean): Project {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("maven-publish")
        // ProjectPlugin registers the publish extension via SPI - apply it before
        // accessing publishExtension() (which would otherwise throw UnknownDomainObjectException).
        project.pluginManager.apply(ProjectPlugin::class.java)

        val publishExt = project.publishExtension()!!
        publishExt.enabled.set(true)
        publishExt.signingEnabled.set(signingEnabled)
        publishExt.toMavenStaging()
        evaluate(project)

        // Ensure a Maven publication exists so signing.sign() has something to sign.
        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        if (publishing.publications.findByName("maven") == null) {
            publishing.publications.create("maven", MavenPublication::class.java) {
                it.from(project.components.getByName("java"))
            }
        }

        // Force the staging repo to exist so PublishToMavenRepository tasks are realized
        // and the wiring's `configureEach { signTask -> ... }` actually fires.
        project.extensions
            .getByType(PublishingExtension::class.java)
            .let { ext ->
                if (ext.repositories.findByName(MAVEN_STAGING_REPO) == null) {
                    ext.repositories.maven { repo ->
                        repo.name = MAVEN_STAGING_REPO
                        repo.url = stagingDir.toURI()
                    }
                }
            }

        // Realize PublishToMavenRepository so the Sign tasks (added by signing.sign) are wired.
        project.tasks.withType(PublishToMavenRepository::class.java).toList()
        // Realize Sign tasks via their `actions` accessor.
        project.tasks.withType(Sign::class.java).toList()
        return project
    }

    private fun firstSignTask(project: Project): Sign {
        val tasks = project.tasks.withType(Sign::class.java).toList()
        assertThat(tasks).`as`("at least one Sign task must be registered").isNotEmpty
        return tasks.first()
    }

    private fun evaluate(project: Project) {
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
    }
}
