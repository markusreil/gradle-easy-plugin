package com.mreil.easy.release

import com.mreil.easy.ProjectPlugin
import com.mreil.easy.vcs.VcsService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import java.io.File

class EasyReleaseLifecycleTest {
    @Test
    fun `releaseLifecycle service is registered with semver derived release version`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.2.3-SNAPSHOT"
        (project as ProjectInternal).evaluate()

        val service = releaseLifecycleOf(project)

        assertThat(service.releaseVersion().get()).isEqualTo("1.2.3")
    }

    @Test
    fun `releaseLifecycle service honors release version system property override`() {
        System.setProperty(EasyReleasePlugin.RELEASE_VERSION_PROPERTY, "9.9.9")
        try {
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply(ProjectPlugin::class.java)
            project.group = "com.example"
            project.version = "1.2.3-SNAPSHOT"
            (project as ProjectInternal).evaluate()

            val service = releaseLifecycleOf(project)

            assertThat(service.releaseVersion().get()).isEqualTo("9.9.9")
        } finally {
            System.clearProperty(EasyReleasePlugin.RELEASE_VERSION_PROPERTY)
        }
    }

    @Test
    fun `beforePreReleaseCommit attaches a doFirst action with the resolved release version`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0"
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("preReleaseCommit") as PreReleaseCommitTask
        val actionCountBefore = task.actions.size
        val recorded = mutableListOf<String>()

        EasyRelease.beforePreReleaseCommit(project) { v ->
            v.orNull?.let(recorded::add)
            emptyList()
        }

        assertSoftly { softly ->
            softly.assertThat(task.actions.size).isEqualTo(actionCountBefore + 1)
            softly.assertThat(releaseLifecycleOf(project).releaseVersion().get()).isEqualTo("1.0.0")
        }
    }

    @Test
    fun `beforePreReleaseCommit collects listener files into additionalFiles`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0"
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("preReleaseCommit") as PreReleaseCommitTask
        val extraFile = File(project.projectDir, "extra.txt").apply { writeText("initial") }

        EasyRelease.beforePreReleaseCommit(project) { _ -> listOf(extraFile) }
        task.actions.first().execute(task)

        assertThat(task.additionalFiles.get()).containsExactly(extraFile.absolutePath)
    }

    @Test
    fun `preReleaseCommit commits version file and contributed extra files`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        registerVcsService(project)
        project.group = "com.example"
        project.version = "1.0.0-SNAPSHOT"
        val versionFile = File(project.projectDir, "gradle.properties").apply { writeText("version=1.0.0-SNAPSHOT\n") }
        val extraFile = File(project.projectDir, "changelog.md").apply { writeText("# initial\n") }
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("preReleaseCommit") as PreReleaseCommitTask

        EasyRelease.beforePreReleaseCommit(
            project,
            EasyRelease.writeToFileListener(extraFile, prefix = "release: "),
        )
        task.actions.first().execute(task)
        task.commit()

        assertSoftly { softly ->
            softly.assertThat(versionFile.readText()).isEqualTo("version=1.0.0\n")
            softly.assertThat(extraFile.readText()).isEqualTo("release: 1.0.0")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerVcsService(project: Project) {
        project.gradle.sharedServices.registerIfAbsent("vcs", VcsService::class.java) {
            it.parameters.rootDir.set(project.layout.projectDirectory)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun releaseLifecycleOf(project: Project): ReleaseLifecycleService {
        val registration =
            project.gradle.sharedServices.registrations
                .findByName(EasyRelease.SERVICE_NAME)
                as BuildServiceRegistration<ReleaseLifecycleService, *>
        return registration.service.get()
    }
}
