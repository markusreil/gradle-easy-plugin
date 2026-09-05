package com.mreil.easy.publish

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.PublishingExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EasyPublishStagingTest {
    @Test
    fun `staging repo is only added to root in multi-module build`() {
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
        root.pluginManager.apply(ProjectPlugin::class.java)
        child.pluginManager.apply(ProjectPlugin::class.java)

        val rootPublish =
            (root.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        rootPublish.enabled.set(true)
        rootPublish.toMavenStaging()

        evaluate(root)
        evaluate(child)

        val rootRepo =
            root.extensions
                .getByType(PublishingExtension::class.java)
                .repositories
                .findByName("mavenStaging")
        val childRepo =
            child.extensions
                .getByType(PublishingExtension::class.java)
                .repositories
                .findByName("mavenStaging")
        assertSoftly { softly ->
            softly.assertThat(rootRepo).isNotNull()
            softly.assertThat(rootRepo?.name).isEqualTo("mavenStaging")
            softly.assertThat(childRepo).isNull()
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
        root.pluginManager.apply(ProjectPlugin::class.java)
        child.pluginManager.apply(ProjectPlugin::class.java)

        val rootPublish =
            (root.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        rootPublish.enabled.set(true)
        rootPublish.toMavenStaging("customStaging")

        evaluate(root)
        evaluate(child)

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
        root.pluginManager.apply(ProjectPlugin::class.java)
        child.pluginManager.apply(ProjectPlugin::class.java)

        val rootPublish =
            (root.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        rootPublish.enabled.set(true)
        rootPublish.toMavenStaging("rootStaging")

        evaluate(root)
        evaluate(child)

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
        project.pluginManager.apply(ProjectPlugin::class.java)
        val publish =
            (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        publish.enabled.set(true)
        publish.toMavenStaging("customStaging")

        evaluate(project)

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

    private fun evaluate(project: Project) {
        val internal = project as org.gradle.api.internal.project.ProjectInternal
        internal.evaluate()
    }
}
