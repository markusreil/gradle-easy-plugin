package com.mreil.easy.publish

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import com.mreil.easy.semver.EasySemverExtension
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.PublishingExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class SonatypeSnapshotsTest {
    @Test
    fun `toSonatypeSnapshots creates snapshot repo with credentials`() {
        val project = createProject()
        val publish = publishOf(project)
        publish.enabled.set(true)
        publish.toSonatypeSnapshots()

        val spec = publish.mavenRepos.findByName(SONATYPE_SNAPSHOTS_REPO)
        assertSoftly { softly ->
            softly.assertThat(publish.sonatypeSnapshots.get()).isTrue()
            softly.assertThat(spec).isNotNull()
            softly.assertThat(spec?.url?.get()).isEqualTo(SONATYPE_SNAPSHOTS_URL)
            softly.assertThat(spec?.passwordCredentials?.get()).isTrue()
        }
    }

    @Test
    fun `toSonatypeSnapshots is idempotent and keeps manual declaration`() {
        val project = createProject()
        val publish = publishOf(project)
        publish.enabled.set(true)
        publish.mavenRepo(SONATYPE_SNAPSHOTS_REPO, "https://example.com/custom-snapshots", true)
        publish.toSonatypeSnapshots()
        publish.toSonatypeSnapshots()

        assertSoftly { softly ->
            softly.assertThat(publish.mavenRepos.map { it.name }.count { it == SONATYPE_SNAPSHOTS_REPO }).isEqualTo(1)
            softly
                .assertThat(
                    publish.mavenRepos
                        .getByName(SONATYPE_SNAPSHOTS_REPO)
                        .url
                        .get(),
                ).isEqualTo("https://example.com/custom-snapshots")
        }
    }

    @Test
    fun `toSonatypeSnapshots attaches snapshot repo and filters release repos for SNAPSHOT version without semver`() {
        val project = createProject(version = "1.0.0-SNAPSHOT")
        val publish = publishOf(project)
        publish.enabled.set(true)
        // semver defaults to enabled when on the classpath — disable to simulate "not active"
        // (absent extension behaves identically via isEasyChildEnabled).
        semverOf(project).enabled.set(false)
        publish.toSonatypeSnapshots()
        publish.mavenRepo("myRelease", "https://example.com/releases")

        evaluate(project)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        val snapshotsRepo =
            publishing.repositories.findByName(SONATYPE_SNAPSHOTS_REPO) as MavenArtifactRepository?
        assertSoftly { softly ->
            softly.assertThat(snapshotsRepo).isNotNull()
            softly.assertThat(snapshotsRepo?.url?.toString()).isEqualTo(SONATYPE_SNAPSHOTS_URL)
            softly.assertThat(publishing.repositories.findByName("myRelease")).isNull()
        }
    }

    @Test
    fun `toSonatypeSnapshots is skipped and release repos attach for release version without semver`() {
        val project = createProject(version = "1.0.0")
        val publish = publishOf(project)
        publish.enabled.set(true)
        semverOf(project).enabled.set(false)
        publish.toSonatypeSnapshots()
        publish.mavenRepo("myRelease", "https://example.com/releases")

        evaluate(project)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertSoftly { softly ->
            softly.assertThat(publishing.repositories.findByName(SONATYPE_SNAPSHOTS_REPO)).isNull()
            softly.assertThat(publishing.repositories.findByName("myRelease")).isNotNull()
        }
    }

    @Test
    fun `sonatypeSnapshots repo is attached for snapshots when semver enabled`() {
        val project = createProject(version = "1.0.0-SNAPSHOT")
        val publish = publishOf(project)
        publish.enabled.set(true)
        semverOf(project).enabled.set(true)
        publish.toSonatypeSnapshots()

        evaluate(project)

        val repo =
            project.extensions
                .getByType(PublishingExtension::class.java)
                .repositories
                .findByName(SONATYPE_SNAPSHOTS_REPO) as MavenArtifactRepository?
        assertSoftly { softly ->
            softly.assertThat(repo).isNotNull()
            softly.assertThat(repo?.url?.toString()).isEqualTo(SONATYPE_SNAPSHOTS_URL)
        }
    }

    @Test
    fun `sonatypeSnapshots repo is skipped for releases`() {
        val project = createProject(version = "1.0.0")
        val publish = publishOf(project)
        publish.enabled.set(true)
        semverOf(project).enabled.set(true)
        publish.toSonatypeSnapshots()

        evaluate(project)

        val repo =
            project.extensions
                .getByType(PublishingExtension::class.java)
                .repositories
                .findByName(SONATYPE_SNAPSHOTS_REPO)
        assertSoftly { softly ->
            softly.assertThat(repo).isNull()
        }
    }

    private fun createProject(version: String = "1.0.0"): Project {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = version
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        return project
    }

    private fun publishOf(project: Project): DefaultEasyPublishExtension =
        (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
            .extensions
            .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension

    private fun semverOf(project: Project): EasySemverExtension =
        (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
            .extensions
            .getByType(EasySemverExtension::class.java)

    private fun evaluate(project: Project) {
        val internal = project as ProjectInternal
        internal.evaluate()
    }
}
