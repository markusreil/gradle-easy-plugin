package com.mreil.easy.publish.central

import com.mreil.easy.ApplyToSubprojects
import com.mreil.easy.EasyExtension
import com.mreil.easy.EnabledBy
import com.mreil.easy.ProjectPlugin
import com.mreil.easy.codemeta.EasyCodemetaExtension
import com.mreil.easy.publish.EasyPublishContributor
import com.mreil.easy.publish.EasyPublishExtension
import com.mreil.easy.publish.EasyPublishPlugin
import com.mreil.easy.publish.publishExtension
import com.mreil.easy.semver.EasySemverExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.ExtensionAware
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class EasyJreleaserPluginTest {
    @Test
    fun `contributor provides publish and jreleaser plugins`() {
        val plugins = EasyPublishContributor().projectPlugins()

        assertThat(plugins).containsExactlyInAnyOrder(
            EasyPublishPlugin::class,
            EasyJreleaserPlugin::class,
        )
    }

    @Test
    fun `jreleaser plugin is gated by the publish extension and root-only`() {
        val jreleaser = EasyJreleaserPlugin::class.java
        val publish = EasyPublishPlugin::class.java

        assertThat(jreleaser.getAnnotation(EnabledBy::class.java)?.value)
            .isEqualTo(EasyPublishExtension::class)
        assertThat(jreleaser.isAnnotationPresent(ApplyToSubprojects::class.java))
            .describedAs("EasyJreleaserPlugin must stay root-only (no fan-out)")
            .isFalse()
        assertThat(publish.isAnnotationPresent(ApplyToSubprojects::class.java)).isTrue()
    }

    /** The codemeta-required guard fires: no JReleaser wiring is registered when
     *  the codemeta extension is present but disabled. */
    @Test
    fun `afterEnabled skips central wiring when codemeta extension is disabled`() {
        val project = singleProject()
        project.publishExtension()!!.apply {
            enabled.set(true)
            toMavenCentral()
        }
        // Disable the codemeta extension that the test classpath registers by default.
        val codemetaExt =
            (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyCodemetaExtension::class.java)
        codemetaExt.enabled.set(false)

        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("checkCentralPoms")).isNull()
            softly.assertThat(project.tasks.findByName("generateJreleaserConfig")).isNull()
            softly.assertThat(project.tasks.findByName("publishToMavenCentral")).isNull()
            // The strip task is wired by CentralPublishingWiring which is skipped too.
            softly.assertThat(project.tasks.findByName("stripSignatureChecksums")).isNull()
        }
    }

    /** The snapshot guard fires: no JReleaser wiring is registered when the
     *  semver extension reports a SNAPSHOT version. */
    @Test
    fun `afterEnabled skips central wiring when semver indicates snapshot version`() {
        val project = singleProject()
        project.publishExtension()!!.apply {
            enabled.set(true)
            toMavenCentral()
        }
        project.version = "1.0.0-SNAPSHOT"

        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("checkCentralPoms")).isNull()
            softly.assertThat(project.tasks.findByName("generateJreleaserConfig")).isNull()
            softly.assertThat(project.tasks.findByName("publishToMavenCentral")).isNull()
        }
    }

    /** Predicate matrix: a stable release version wires central. */
    @Test
    fun `afterEnabled wires central wiring for stable release version`() {
        val project = singleProject()
        project.publishExtension()!!.apply {
            enabled.set(true)
            toMavenCentral()
        }
        project.version = "1.0.0"

        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("checkCentralPoms")).isNotNull()
            softly.assertThat(project.tasks.findByName("generateJreleaserConfig")).isNotNull()
            softly.assertThat(project.tasks.findByName("publishToMavenCentral")).isNotNull()
        }
    }

    /** Predicate matrix: a pre-release like RC1 is not a SNAPSHOT, so it is a deployable release
     *  and central wiring proceeds. */
    @Test
    fun `afterEnabled wires central wiring for RC1 pre-release version`() {
        val project = singleProject()
        project.publishExtension()!!.apply {
            enabled.set(true)
            toMavenCentral()
        }
        project.version = "1.0.0-RC1"

        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("checkCentralPoms")).isNotNull()
            softly.assertThat(project.tasks.findByName("generateJreleaserConfig")).isNotNull()
            softly.assertThat(project.tasks.findByName("publishToMavenCentral")).isNotNull()
        }
    }

    /** Predicate matrix: a 0.x release version is not a SNAPSHOT, so central wiring proceeds. */
    @Test
    fun `afterEnabled wires central wiring for 0-version release`() {
        val project = singleProject()
        project.publishExtension()!!.apply {
            enabled.set(true)
            toMavenCentral()
        }
        project.version = "0.0.105"

        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("checkCentralPoms")).isNotNull()
            softly.assertThat(project.tasks.findByName("generateJreleaserConfig")).isNotNull()
            softly.assertThat(project.tasks.findByName("publishToMavenCentral")).isNotNull()
        }
    }

    /** Predicate matrix: with semver off, the `-SNAPSHOT` suffix fallback still skips central. */
    @Test
    fun `afterEnabled skips central wiring when semver is off and version is a snapshot`() {
        val project = singleProject()
        project.publishExtension()!!.apply {
            enabled.set(true)
            toMavenCentral()
        }
        project.version = "1.0.0-SNAPSHOT"
        semverOf(project).enabled.set(false)

        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("checkCentralPoms")).isNull()
            softly.assertThat(project.tasks.findByName("generateJreleaserConfig")).isNull()
            softly.assertThat(project.tasks.findByName("publishToMavenCentral")).isNull()
        }
    }

    /** Predicate matrix: with semver off, a release version still wires central. */
    @Test
    fun `afterEnabled wires central wiring when semver is off and version is a release`() {
        val project = singleProject()
        project.publishExtension()!!.apply {
            enabled.set(true)
            toMavenCentral()
        }
        project.version = "1.0.0"
        semverOf(project).enabled.set(false)

        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("checkCentralPoms")).isNotNull()
            softly.assertThat(project.tasks.findByName("generateJreleaserConfig")).isNotNull()
            softly.assertThat(project.tasks.findByName("publishToMavenCentral")).isNotNull()
        }
    }

    private fun singleProject(): Project {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        return project
    }

    private fun semverOf(project: Project): EasySemverExtension =
        (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
            .extensions
            .getByType(EasySemverExtension::class.java)

    private fun evaluate(project: Project) {
        (project as ProjectInternal).evaluate()
    }
}
