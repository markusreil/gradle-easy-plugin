package com.mreil.easy.publish

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPluginEntryPoint
import com.mreil.gradletest.project.evaluate
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.Delete
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EasyPublishStagingTest {
    @Test
    fun `staging repo is added to every enabled project in multi-module build`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val child =
            ProjectBuilder
                .builder()
                .withName("child")
                .withParent(root)
                .build()
        listOf(root, child).forEach {
            it.group = "com.example"
            it.version = "1.0.0"
            it.pluginManager.apply("java-library")
        }
        root.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        child.pluginManager.apply(ProjectPluginEntryPoint::class.java)

        val rootPublish =
            (root.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        rootPublish.enabled.set(true)
        rootPublish.toMavenStaging()

        root.evaluate()
        child.evaluate()

        val rootRepo =
            root.extensions
                .getByType(PublishingExtension::class.java)
                .repositories
                .findByName("mavenStaging") as MavenArtifactRepository
        val childRepo =
            child.extensions
                .getByType(PublishingExtension::class.java)
                .repositories
                .findByName("mavenStaging") as? MavenArtifactRepository
        val rootBuild =
            root.layout.buildDirectory
                .get()
                .asFile.invariantSeparatorsPath
        val childBuild =
            child.layout.buildDirectory
                .get()
                .asFile.invariantSeparatorsPath
        assertSoftly { softly ->
            softly.assertThat(rootRepo.url.toString()).contains(rootBuild)
            // Every module stages into its own build dir; JReleaser deploys the collection.
            softly.assertThat(childRepo).isNotNull()
            softly.assertThat(childRepo?.url.toString()).contains(childBuild)
            softly.assertThat(childRepo?.url.toString()).contains("stagingRepo")
            softly.assertThat(childBuild).isNotEqualTo(rootBuild)
        }
    }

    @Test
    fun `staging repo url resolves under root build directory`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val child =
            ProjectBuilder
                .builder()
                .withName("child")
                .withParent(root)
                .build()
        listOf(root, child).forEach {
            it.group = "com.example"
            it.version = "1.0.0"
            it.pluginManager.apply("java-library")
        }
        root.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        child.pluginManager.apply(ProjectPluginEntryPoint::class.java)

        val rootPublish =
            (root.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        rootPublish.enabled.set(true)
        rootPublish.toMavenStaging("customStaging")

        root.evaluate()
        child.evaluate()

        val repo =
            root.extensions
                .getByType(PublishingExtension::class.java)
                .repositories
                .findByName("mavenStaging") as MavenArtifactRepository
        val rootBuild =
            root.layout.buildDirectory
                .get()
                .asFile.invariantSeparatorsPath
        val childBuild =
            child.layout.buildDirectory
                .get()
                .asFile.invariantSeparatorsPath
        assertSoftly { softly ->
            softly.assertThat(repo.url.toString()).contains(rootBuild)
            softly.assertThat(repo.url.toString()).contains("customStaging")
            softly.assertThat(repo.url.toString()).doesNotContain(childBuild)
            softly.assertThat(childBuild).isNotEqualTo(rootBuild)
        }
    }

    @Test
    fun `child cannot override stagingPath after root defines it`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val child =
            ProjectBuilder
                .builder()
                .withName("child")
                .withParent(root)
                .build()
        listOf(root, child).forEach {
            it.group = "com.example"
            it.version = "1.0.0"
            it.pluginManager.apply("java-library")
        }
        root.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        child.pluginManager.apply(ProjectPluginEntryPoint::class.java)

        val rootPublish =
            (root.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        rootPublish.enabled.set(true)
        rootPublish.toMavenStaging("rootStaging")

        root.evaluate()
        child.evaluate()

        val childPublish =
            (child.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        val ex =
            assertThrows<IllegalStateException> {
                childPublish.toMavenStaging("childStaging")
            }
        assertSoftly { softly ->
            softly.assertThat(ex.message?.lowercase()).contains("cannot")
        }
    }

    @Test
    fun `custom staging path is reflected in repository url`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val publish =
            (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        publish.enabled.set(true)
        publish.toMavenStaging("customStaging")

        project.evaluate()

        val repo =
            project.extensions
                .getByType(PublishingExtension::class.java)
                .repositories
                .findByName("mavenStaging") as MavenArtifactRepository
        assertSoftly { softly ->
            softly.assertThat(repo.url.toString()).contains("customStaging")
            softly.assertThat(repo.url.toString()).contains(
                project.layout.buildDirectory
                    .get()
                    .asFile.invariantSeparatorsPath,
            )
        }
    }

    @Test
    fun `staging upload tasks depend on cleanStagingRepo wiping the staging dir`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPluginEntryPoint::class.java)
        val publish =
            (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        publish.enabled.set(true)
        publish.toMavenStaging()

        project.evaluate()

        val upload = project.tasks.getByName("publishMavenPublicationToMavenStagingRepository")
        val uploadDeps = upload.taskDependencies.getDependencies(upload).map { it.name }
        assertSoftly { softly ->
            softly
                .assertThat(project.tasks.findByName("cleanStagingRepo"))
                .isInstanceOf(Delete::class.java)
            softly.assertThat(uploadDeps).contains("cleanStagingRepo")
        }
    }
}
