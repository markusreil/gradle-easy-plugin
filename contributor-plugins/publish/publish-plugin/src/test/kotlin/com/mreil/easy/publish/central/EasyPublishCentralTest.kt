package com.mreil.easy.publish.central

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import com.mreil.easy.publish.DefaultEasyPublishExtension
import com.mreil.easy.publish.EasyPublishExtension
import com.mreil.easy.publish.MAVEN_STAGING_REPO
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
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
    fun `toMavenCentral alone creates the default staging repo`() {
        val project = ProjectBuilderHelper.createSingleProject()
        project.publish.enabled.set(true)
        project.publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(project.project)

        val stagingRepo =
            project.project.extensions
                .getByType(PublishingExtension::class.java)
                .repositories
                .findByName(MAVEN_STAGING_REPO) as? MavenArtifactRepository
        assertSoftly { softly ->
            softly.assertThat(project.publish.stagingPath.get()).isEqualTo("stagingRepo")
            softly.assertThat(stagingRepo).isNotNull()
            softly.assertThat(stagingRepo?.url?.toString()).contains("stagingRepo")
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
            softly.assertThat(rootTask?.stagingDirs?.get()).contains(
                root.project.layout.buildDirectory
                    .get()
                    .asFile.invariantSeparatorsPath + "/stagingRepo",
            )
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
            softly.assertThat(task.stagingDirs.get()).containsExactly(
                project.project.layout.buildDirectory
                    .get()
                    .asFile.invariantSeparatorsPath + "/myCustomStaging",
            )
        }
    }

    @Test
    fun `generateJreleaserConfig collects every enabled project's staging dir`() {
        val root = ProjectBuilderHelper.createRootWithChild("root")
        val child = root.child
        root.publish.enabled.set(true)
        root.publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(root.project)
        ProjectBuilderHelper.evaluate(child.project)

        val task = root.project.tasks.getByName("generateJreleaserConfig") as GenerateJreleaserConfigTask
        val rootStaging =
            root.project.layout.buildDirectory
                .get()
                .asFile.invariantSeparatorsPath + "/stagingRepo"
        val childStaging =
            child.project.layout.buildDirectory
                .get()
                .asFile.invariantSeparatorsPath + "/stagingRepo"
        assertSoftly { softly ->
            softly.assertThat(task.stagingDirs.get()).containsExactly(rootStaging, childStaging)
        }
    }

    @Test
    fun `stagingDirsForPublishing omits publication-less projects`() {
        // Java-less root (like a real aggregator): enabled but stages nothing.
        val root = ProjectBuilder.builder().withName("root").build()
        val child =
            ProjectBuilder
                .builder()
                .withName("child")
                .withParent(root)
                .build()
        root.pluginManager.apply(ProjectPlugin::class.java)
        child.pluginManager.apply("java-library")
        child.pluginManager.apply(ProjectPlugin::class.java)
        child.pluginManager.apply("maven-publish")
        val publishing = child.extensions.getByType(PublishingExtension::class.java)
        publishing.publications.create("maven", MavenPublication::class.java) {
            it.from(child.components.getByName("java"))
        }
        publishExtensionOf(root).enabled.set(true)
        publishExtensionOf(child).enabled.set(true)

        val childStaging =
            child.layout.buildDirectory
                .get()
                .asFile.invariantSeparatorsPath + "/stagingRepo"
        assertSoftly { softly ->
            softly
                .assertThat(JreleaserConfigWiring.stagingDirsForPublishing(listOf(root, child)))
                .containsExactly(childStaging)
        }
    }

    private fun publishExtensionOf(project: Project): DefaultEasyPublishExtension =
        (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
            .extensions
            .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension

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
    fun `publish does not directly depend on generateJreleaserConfig`() {
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

    @Test
    fun `checkCentralPoms is registered in every enabled project and enabled with toMavenCentral`() {
        val root = ProjectBuilderHelper.createRootWithChild("root")
        val child = root.child
        root.publish.enabled.set(true)
        root.publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(root.project)
        ProjectBuilderHelper.evaluate(child.project)

        val rootTask = root.project.tasks.findByName("checkCentralPoms") as CheckCentralPomsTask?
        val childTask = child.project.tasks.findByName("checkCentralPoms") as CheckCentralPomsTask?
        assertSoftly { softly ->
            softly.assertThat(rootTask).isNotNull()
            softly.assertThat(childTask).isNotNull()
            softly.assertThat(rootTask?.enabled).isTrue()
            softly.assertThat(childTask?.enabled).isTrue()
        }
    }

    @Test
    fun `checkCentralPoms skips silently without publications`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply(ProjectPlugin::class.java)
        val publish =
            (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyPublishExtension::class.java) as DefaultEasyPublishExtension
        publish.enabled.set(true)
        publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(project)

        val checker = project.tasks.getByName("checkCentralPoms") as CheckCentralPomsTask
        assertSoftly { softly ->
            softly.assertThat(checker.enabled).isTrue()
            softly.assertThat(checker.pomFiles.files).isEmpty()
        }
    }

    @Test
    fun `checkCentralPoms is disabled without toMavenCentral`() {
        val project = ProjectBuilderHelper.createSingleProject()
        project.publish.enabled.set(true)
        project.publish.toMavenStaging()

        ProjectBuilderHelper.evaluate(project.project)

        val task = project.project.tasks.getByName("checkCentralPoms") as CheckCentralPomsTask
        assertSoftly { softly ->
            softly.assertThat(task.enabled).isFalse()
        }
    }

    @Test
    fun `checkCentralPoms depends on generatePom tasks`() {
        val project = ProjectBuilderHelper.createSingleProject()
        project.publish.enabled.set(true)
        project.publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(project.project)

        val checker = project.project.tasks.getByName("checkCentralPoms")
        val deps = checker.taskDependencies.getDependencies(checker).map { it.name }
        assertSoftly { softly ->
            softly.assertThat(deps).contains("generatePomFileForMavenPublication")
        }
    }

    @Test
    fun `publish repository tasks depend on checkCentralPoms`() {
        val project = ProjectBuilderHelper.createSingleProject()
        project.publish.enabled.set(true)
        project.publish.toMavenCentral()
        project.publish.mavenRepo("testRepo", "file:///tmp/test-repo")

        ProjectBuilderHelper.evaluate(project.project)

        val publishTask = project.project.tasks.getByName("publishMavenPublicationToTestRepoRepository")
        val deps = publishTask.taskDependencies.getDependencies(publishTask).map { it.name }
        assertSoftly { softly ->
            softly.assertThat(deps).contains("checkCentralPoms")
        }
    }

    @Test
    fun `root publish aggregates child publish`() {
        val root = ProjectBuilderHelper.createRootWithChild("root")
        val child = root.child
        root.publish.enabled.set(true)
        root.publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(root.project)
        ProjectBuilderHelper.evaluate(child.project)

        val rootPublish = root.project.tasks.getByName("publish")
        val deps = rootPublish.taskDependencies.getDependencies(rootPublish).map { it.path }
        assertSoftly { softly ->
            softly.assertThat(deps).contains(
                child.project.tasks
                    .getByName("publish")
                    .path,
            )
        }
    }

    @Test
    fun `each project's checkCentralPoms covers only its own generatePom tasks`() {
        val root = ProjectBuilderHelper.createRootWithChild("root")
        val child = root.child
        root.publish.enabled.set(true)
        root.publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(root.project)
        ProjectBuilderHelper.evaluate(child.project)

        val rootPomPath =
            root.project.tasks
                .getByName("generatePomFileForMavenPublication")
                .path
        val childPomPath =
            child.project.tasks
                .getByName("generatePomFileForMavenPublication")
                .path
        val rootDeps =
            root.project.tasks
                .getByName("checkCentralPoms")
                .taskDependencies
                .getDependencies(root.project.tasks.getByName("checkCentralPoms"))
                .map { it.path }
        val childDeps =
            child.project.tasks
                .getByName("checkCentralPoms")
                .taskDependencies
                .getDependencies(child.project.tasks.getByName("checkCentralPoms"))
                .map { it.path }
        assertSoftly { softly ->
            softly.assertThat(rootDeps).contains(rootPomPath)
            softly.assertThat(rootDeps).doesNotContain(childPomPath)
            softly.assertThat(childDeps).contains(childPomPath)
            softly.assertThat(childDeps).doesNotContain(rootPomPath)
        }
    }

    @Test
    fun `generateJreleaserConfig depends on checkCentralPoms`() {
        val project = ProjectBuilderHelper.createSingleProject()
        project.publish.enabled.set(true)
        project.publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(project.project)

        val configTask = project.project.tasks.getByName("generateJreleaserConfig")
        val deps = configTask.taskDependencies.getDependencies(configTask).map { it.name }
        assertSoftly { softly ->
            softly.assertThat(deps).contains("checkCentralPoms")
        }
    }

    @Test
    fun `generateJreleaserConfig waits for every project's checkCentralPoms`() {
        val root = ProjectBuilderHelper.createRootWithChild("root")
        val child = root.child
        root.publish.enabled.set(true)
        root.publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(root.project)
        ProjectBuilderHelper.evaluate(child.project)

        val configTask = root.project.tasks.getByName("generateJreleaserConfig")
        val deps = configTask.taskDependencies.getDependencies(configTask).map { it.path }
        assertSoftly { softly ->
            softly.assertThat(deps).contains(
                root.project.tasks
                    .getByName("checkCentralPoms")
                    .path,
                child.project.tasks
                    .getByName("checkCentralPoms")
                    .path,
            )
        }
    }

    @Test
    fun `generateJreleaserConfig carries project version`() {
        val project = ProjectBuilderHelper.createSingleProject()
        project.publish.enabled.set(true)
        project.publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(project.project)

        val task = project.project.tasks.getByName("generateJreleaserConfig") as GenerateJreleaserConfigTask
        assertSoftly { softly ->
            softly.assertThat(task.projectVersion.get()).isEqualTo("1.0.0")
        }
    }

    @Test
    fun `publishToMavenCentral is registered on root only and enabled with toMavenCentral`() {
        val root = ProjectBuilderHelper.createRootWithChild("root")
        val child = root.child
        root.publish.enabled.set(true)
        root.publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(root.project)
        ProjectBuilderHelper.evaluate(child.project)

        val rootTask = root.project.tasks.findByName("publishToMavenCentral") as JreleaserPublishTask?
        val childTask = child.project.tasks.findByName("publishToMavenCentral")
        assertSoftly { softly ->
            softly.assertThat(rootTask).isNotNull()
            softly.assertThat(childTask).isNull()
            softly.assertThat(rootTask?.enabled).isTrue()
            softly.assertThat(rootTask?.mainClass?.get()).isEqualTo("org.jreleaser.cli.Main")
        }
    }

    @Test
    fun `publishToMavenCentral is disabled without toMavenCentral`() {
        val project = ProjectBuilderHelper.createSingleProject()
        project.publish.enabled.set(true)
        project.publish.toMavenStaging()

        ProjectBuilderHelper.evaluate(project.project)

        val task = project.project.tasks.getByName("publishToMavenCentral") as JreleaserPublishTask
        assertSoftly { softly ->
            softly.assertThat(task.enabled).isFalse()
        }
    }

    @Test
    fun `publishToMavenCentral stages uploads and config without depending on publish`() {
        val project = ProjectBuilderHelper.createSingleProject()
        project.publish.enabled.set(true)
        project.publish.toMavenCentral()
        project.publish.toMavenStaging("stagingRepo")
        project.publish.mavenRepo("testRepo", "file:///tmp/test-repo")

        ProjectBuilderHelper.evaluate(project.project)

        val task = project.project.tasks.getByName("publishToMavenCentral")
        val deps = task.taskDependencies.getDependencies(task).map { it.name }
        assertSoftly { softly ->
            softly.assertThat(deps).contains("generateJreleaserConfig")
            // JReleaser deploys the staging collection only — non-staging repos are excluded.
            softly.assertThat(deps).contains("publishMavenPublicationToMavenStagingRepository")
            softly.assertThat(deps).doesNotContain("publishMavenPublicationToTestRepoRepository")
            softly.assertThat(deps).doesNotContain("publish")
        }
    }

    @Test
    fun `publish depends on publishToMavenCentral`() {
        val project = ProjectBuilderHelper.createSingleProject()
        project.publish.enabled.set(true)
        project.publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(project.project)

        val publish = project.project.tasks.getByName("publish")
        val deps = publish.taskDependencies.getDependencies(publish).map { it.name }
        assertSoftly { softly ->
            softly.assertThat(deps).contains("publishToMavenCentral")
        }
    }

    @Test
    fun `publish omits publishToMavenCentral without toMavenCentral`() {
        val project = ProjectBuilderHelper.createSingleProject()
        project.publish.enabled.set(true)
        project.publish.toMavenStaging()

        ProjectBuilderHelper.evaluate(project.project)

        val publish = project.project.tasks.getByName("publish")
        val deps = publish.taskDependencies.getDependencies(publish).map { it.name }
        assertSoftly { softly ->
            softly.assertThat(deps).doesNotContain("publishToMavenCentral")
        }
    }

    @Test
    fun `jreleaser configuration resolves cli dependency`() {
        val project = ProjectBuilderHelper.createSingleProject()
        project.publish.enabled.set(true)
        project.publish.toMavenCentral()

        ProjectBuilderHelper.evaluate(project.project)

        val conf = project.project.configurations.getByName("jreleaser")
        assertSoftly { softly ->
            softly.assertThat(conf.isCanBeResolved).isTrue()
            softly.assertThat(conf.isCanBeConsumed).isFalse()
            softly
                .assertThat(conf.dependencies.map { "${it.group}:${it.name}:${it.version}" })
                .contains("org.jreleaser:jreleaser:1.25.0")
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
