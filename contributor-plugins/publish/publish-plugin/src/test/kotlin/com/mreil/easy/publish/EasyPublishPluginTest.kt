package com.mreil.easy.publish

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.provider.MissingValueException
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.PublishingExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.createTempDirectory

class EasyPublishPluginTest {
    @Test
    fun `publish task is registered when publish extension enabled`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("publish")).isNotNull()
            softly.assertThat(project.tasks.findByName("publishToMavenLocal")).isNotNull()
        }
    }

    @Test
    fun `fails when group is missing`() {
        val project = ProjectBuilder.builder().build()
        project.group = ""
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        val ex = assertThrows<ProjectConfigurationException> { evaluate(project) }

        assertSoftly { softly ->
            softly.assertThat(ex.cause?.message).contains("Project group must be set")
        }
    }

    @Test
    fun `fails when version is missing`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "unspecified"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        val ex = assertThrows<ProjectConfigurationException> { evaluate(project) }

        assertSoftly { softly ->
            softly.assertThat(ex.cause?.message).contains("Project version must be set")
        }
    }

    @Test
    fun `does not create maven publication when java-gradle-plugin present`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("java-gradle-plugin")
        project.pluginManager.apply(ProjectPlugin::class.java)

        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        evaluate(project)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertSoftly { softly ->
            softly.assertThat(publishing.publications.findByName("maven")).isNull()
            softly.assertThat(publishing.publications.map { it.name }).doesNotContain("maven")
        }
    }

    @Test
    fun `creates maven publication for plain java project`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        evaluate(project)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertSoftly { softly ->
            softly.assertThat(publishing.publications.findByName("maven")).isNotNull()
            softly.assertThat(publishing.publications.map { it.name }).contains("maven")
        }
    }

    @Test
    fun `attaches maven repository declared via extension`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)
        val repoDir = createTempDirectory("repo").toFile().apply { deleteOnExit() }
        publish.mavenRepo("testRepo", repoDir.toURI().toString())

        evaluate(project)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        val repo = publishing.repositories.findByName("testRepo") as MavenArtifactRepository
        assertSoftly { softly ->
            softly.assertThat(repo).isNotNull()
            softly.assertThat(repo.url.toString()).contains("repo")
        }
    }

    @Test
    fun `configures password credentials when enabled`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)
        publish.mavenRepo("secureRepo", "http://localhost:1/repo", true)

        evaluate(project)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        val repo = publishing.repositories.findByName("secureRepo") as MavenArtifactRepository
        assertSoftly { softly ->
            softly.assertThat(repo.url.toString()).isEqualTo("http://localhost:1/repo")
            // Credentials are lazily resolved from `secureRepoUsername`/`secureRepoPassword` Gradle properties.
            // Accessing them without those properties throws MissingValueException – prove the wiring is active.
            val ex = assertThrows<MissingValueException> { repo.credentials }
            softly.assertThat(ex.message).contains("secureRepoUsername").contains("secureRepoPassword")
        }
    }

    @Test
    fun `publish depends on publishToMavenLocal when enabled`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)
        publish.toMavenLocal()

        evaluate(project)

        val publishTask = project.tasks.getByName("publish")
        assertSoftly { softly ->
            softly.assertThat(publishTask.dependsOn.map { it.toString() }).anyMatch { it.contains("publishToMavenLocal") }
        }
    }

    @Test
    fun `publish does not depend on publishToMavenLocal when disabled`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        evaluate(project)

        val publishTask = project.tasks.getByName("publish")
        assertSoftly { softly ->
            softly.assertThat(publishTask.dependsOn.map { it.toString() }).noneMatch { it.contains("publishToMavenLocal") }
        }
    }

    private fun evaluate(project: Project) {
        // Trigger afterEvaluate callbacks registered by AbstractEasyProjectPlugin / EasyPublishPlugin
        val internal = project as ProjectInternal
        internal.evaluate()
    }
}
