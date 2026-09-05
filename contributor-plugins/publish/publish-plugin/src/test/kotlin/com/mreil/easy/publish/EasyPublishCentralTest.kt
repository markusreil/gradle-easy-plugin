package com.mreil.easy.publish

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.ExtensionAware
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EasyPublishCentralTest {
    @Test
    fun `toMavenCentral is inherited read-only and child cannot change staging after root central`() {
        val root = ProjectBuilderHelper.createRootWithChild("root")
        val child = root.child
        val rootPublish = root.publish
        rootPublish.enabled.set(true)
        rootPublish.toMavenCentral()
        rootPublish.toMavenStaging("rootCentralStaging")

        ProjectBuilderHelper.evaluate(root.project)
        ProjectBuilderHelper.evaluate(child.project)

        assertSoftly { softly ->
            softly.assertThat(rootPublish.toMavenCentral.get()).isTrue()
            softly.assertThat(rootPublish.stagingPath.get()).isEqualTo("rootCentralStaging")
            softly.assertThat(child.publish.toMavenCentral.get()).isTrue()
            softly.assertThat(child.publish.stagingPath.get()).isEqualTo("rootCentralStaging")
        }

        val ex =
            assertThrows<IllegalStateException> {
                child.publish.toMavenStaging("childStaging")
            }
        assertSoftly { softly ->
            softly.assertThat(ex.message?.lowercase()).contains("cannot")
        }
    }

    @Test
    fun `generateJreleaserConfig is registered on root when toMavenCentral is set`() {
        val root = ProjectBuilderHelper.createRootWithChild("root")
        val child = root.child
        val rootPublish = root.publish
        rootPublish.enabled.set(true)
        rootPublish.toMavenCentral()

        ProjectBuilderHelper.evaluate(root.project)
        ProjectBuilderHelper.evaluate(child.project)

        val rootTask = root.project.tasks.findByName("generateJreleaserConfig") as GenerateJreleaserConfigTask?
        val childTask = child.project.tasks.findByName("generateJreleaserConfig")
        assertSoftly { softly ->
            softly.assertThat(rootTask).isNotNull()
            softly.assertThat(childTask).isNull()
            softly.assertThat(rootTask?.enabled).isTrue()
            softly
                .assertThat(
                    rootTask
                        ?.outputFile
                        ?.get()
                        ?.asFile
                        ?.invariantSeparatorsPath,
                ).contains("build/jreleaser/jreleaser.yml")
            softly.assertThat(rootTask?.stagingDirectory?.get()).contains("build/stagingRepo")
        }
    }

    @Test
    fun `generateJreleaserConfig respects custom staging path`() {
        val project = ProjectBuilderHelper.createSingleProject()
        val publish = project.publish
        publish.enabled.set(true)
        publish.toMavenCentral()
        publish.toMavenStaging("myCustomStaging")

        ProjectBuilderHelper.evaluate(project.project)

        val task = project.project.tasks.getByName("generateJreleaserConfig") as GenerateJreleaserConfigTask
        assertSoftly { softly ->
            softly.assertThat(task.stagingDirectory.get()).contains("myCustomStaging")
            softly.assertThat(task.stagingDirectory.get()).doesNotContain("build/stagingRepo")
        }
    }

    @Test
    fun `generateJreleaserConfig is skipped when toMavenCentral not set`() {
        val project = ProjectBuilderHelper.createSingleProject()
        val publish = project.publish
        publish.enabled.set(true)
        publish.toMavenStaging()

        ProjectBuilderHelper.evaluate(project.project)

        val task = project.project.tasks.getByName("generateJreleaserConfig") as GenerateJreleaserConfigTask
        // disabled via onlyIf + syncEnabled
        assertSoftly { softly ->
            softly.assertThat(task.enabled).isFalse()
        }
    }

    @Test
    fun `publish does not depend on generateJreleaserConfig yet`() {
        val project = ProjectBuilderHelper.createSingleProject()
        val publish = project.publish
        publish.enabled.set(true)
        publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(project.project)

        val deps =
            project.project.tasks
                .named("publish")
                .get()
                .dependsOn
                .map { it.toString() }
        assertSoftly { softly ->
            softly.assertThat(deps.any { it.contains("generateJreleaserConfig") }).isFalse()
        }
    }
}

private object ProjectBuilderHelper {
    data class ProjectWithChild(
        val project: Project,
        val child: ChildProject,
        val publish: DefaultEasyPublishExtension,
    )

    data class ChildProject(
        val project: Project,
        val publish: DefaultEasyPublishExtension,
    )

    data class SingleProject(
        val project: Project,
        val publish: DefaultEasyPublishExtension,
    )

    fun createRootWithChild(rootName: String): ProjectWithChild {
        val root =
            org.gradle.testfixtures.ProjectBuilder
                .builder()
                .withName(rootName)
                .build()
        val child =
            org.gradle.testfixtures.ProjectBuilder
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
        val childPublish =
            (child.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        return ProjectWithChild(root, ChildProject(child, childPublish), rootPublish)
    }

    fun createSingleProject(): SingleProject {
        val project =
            org.gradle.testfixtures.ProjectBuilder
                .builder()
                .build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        val publish =
            (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        return SingleProject(project, publish)
    }

    fun evaluate(project: Project) {
        val internal = project as ProjectInternal
        internal.evaluate()
    }
}
