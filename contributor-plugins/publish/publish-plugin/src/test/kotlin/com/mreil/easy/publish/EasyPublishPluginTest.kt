package com.mreil.easy.publish

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPluginEntryPoint
import com.mreil.gradletest.project.evaluate
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.GradleException
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.provider.MissingValueException
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.PublishingExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junitpioneer.jupiter.ClearEnvironmentVariable
import kotlin.io.path.createTempDirectory

// Portal/Central wiring reads credentials from the environment first: clear them so
// credential-absence cases genuinely see none, regardless of CI secrets being present.
@ClearEnvironmentVariable(key = "JRELEASER_GPG_PRIVATE_KEY")
@ClearEnvironmentVariable(key = "JRELEASER_GPG_PASSPHRASE")
@ClearEnvironmentVariable(key = "GRADLE_PUBLISH_KEY")
@ClearEnvironmentVariable(key = "GRADLE_PUBLISH_SECRET")
@ClearEnvironmentVariable(key = "JRELEASER_MAVENCENTRAL_USERNAME")
@ClearEnvironmentVariable(key = "JRELEASER_MAVENCENTRAL_PASSWORD")
@ClearEnvironmentVariable(key = "JRELEASER_NEXUS_USERNAME")
@ClearEnvironmentVariable(key = "JRELEASER_NEXUS_PASSWORD")
class EasyPublishPluginTest {
    @Test
    fun `publish task is registered when publish extension enabled`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        project.evaluate()

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
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        val ex = assertThrows<ProjectConfigurationException> { project.evaluate() }

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
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        val ex = assertThrows<ProjectConfigurationException> { project.evaluate() }

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
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)

        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        project.evaluate()

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
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        project.evaluate()

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
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)
        val repoDir = createTempDirectory("repo").toFile().apply { deleteOnExit() }
        publish.mavenRepo("testRepo", repoDir.toURI().toString(), RepoRole.NEUTRAL)

        project.evaluate()

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
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)
        publish.mavenRepo("secureRepo", "http://localhost:1/repo", RepoRole.NEUTRAL, true)

        project.evaluate()

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
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)
        publish.deployToMavenLocal()

        project.evaluate()

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
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        project.evaluate()

        val publishTask = project.tasks.getByName("publish")
        assertSoftly { softly ->
            softly.assertThat(publishTask.dependsOn.map { it.toString() }).noneMatch { it.contains("publishToMavenLocal") }
        }
    }

    @Test
    fun `deployToPortal defaults to false`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        publish.enabled.set(true)

        assertSoftly { softly ->
            softly.assertThat(publish.deployToPortal.get()).isFalse()
        }
    }

    @Test
    fun `publish depends on publishPlugins when deployToPortal enabled on release with credentials`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)
        publish.deployToPortal()
        project.tasks.register("publishPlugins")

        val oldKey = System.getProperty("gradle.publish.key")
        val oldSecret = System.getProperty("gradle.publish.secret")
        try {
            System.setProperty("gradle.publish.key", "test-key")
            System.setProperty("gradle.publish.secret", "test-secret")
            project.evaluate()
            val publishTask = project.tasks.named("publish").get()
            assertSoftly { softly ->
                softly.assertThat(publishTask.dependsOn.map { it.toString() }).anyMatch { it.contains("publishPlugins") }
            }
        } finally {
            if (oldKey != null) System.setProperty("gradle.publish.key", oldKey) else System.clearProperty("gradle.publish.key")
            if (oldSecret != null) System.setProperty("gradle.publish.secret", oldSecret) else System.clearProperty("gradle.publish.secret")
        }
    }

    @Test
    fun `publish does not depend on publishPlugins when deployToPortal enabled but project is not a plugin project`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)
        publish.deployToPortal()

        project.evaluate()

        val publishTask = project.tasks.named("publish").get()
        assertSoftly { softly ->
            softly.assertThat(publishTask.dependsOn.map { it.toString() }).noneMatch { it.contains("publishPlugins") }
        }
    }

    @Test
    fun `deployToPortal does not fail when project has no publish task`() {
        // Root-like project: no java plugin, so maven-publish is never applied and there is
        // no `publish` task. deployToPortal() is a build-wide toggle that inherits to every
        // project, so wiring must not throw "Task with name 'publish' not found".
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)
        publish.deployToPortal()

        project.evaluate()

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("publish")).isNull()
        }
    }

    @Test
    fun `publish throws when deployToPortal enabled but credentials missing`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)
        publish.deployToPortal()
        project.tasks.register("publishPlugins")

        val oldKey = System.getProperty("gradle.publish.key")
        val oldSecret = System.getProperty("gradle.publish.secret")
        try {
            System.clearProperty("gradle.publish.key")
            System.clearProperty("gradle.publish.secret")
            project.evaluate()
            org.assertj.core.api.Assertions
                .assertThatThrownBy {
                    project.tasks.named("publish").get()
                }.cause()
                .isInstanceOf(GradleException::class.java)
                .hasMessageContaining("gradle.publish.key")
        } finally {
            if (oldKey != null) System.setProperty("gradle.publish.key", oldKey)
            if (oldSecret != null) System.setProperty("gradle.publish.secret", oldSecret)
        }
    }

    @Test
    fun `publish does not throw when snapshot with deployToPortal enabled`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0-SNAPSHOT"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)
        publish.deployToPortal()

        project.evaluate()

        val publishTask = project.tasks.named("publish").get()
        assertSoftly { softly ->
            softly.assertThat(publishTask.dependsOn.map { it.toString() }).noneMatch { it.contains("publishPlugins") }
        }
    }

    @Test
    fun `publish does not throw when deployToPortal not enabled`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        val publish = easy.extensions.getByType(EasyPublishExtension::class.java)
        publish.enabled.set(true)

        project.evaluate()

        val publishTask = project.tasks.named("publish").get()
        assertSoftly { softly ->
            softly.assertThat(publishTask).isNotNull()
        }
    }
}
