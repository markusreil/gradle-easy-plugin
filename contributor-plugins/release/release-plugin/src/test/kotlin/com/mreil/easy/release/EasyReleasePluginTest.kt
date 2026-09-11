package com.mreil.easy.release

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import com.mreil.easy.semver.EasySemverExtension
import com.mreil.easy.vcs.VcsService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import java.io.File

class EasyReleasePluginTest {
    @Test
    fun `release extension is registered`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)

        val easy = project.extensions.getByType(EasyExtension::class.java)
        val release = easy.extensions.findByType(EasyReleaseExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(release).isNotNull()
            softly.assertThat(project.plugins.findPlugin(EasyReleasePlugin::class.java)).isNotNull()
        }
        (project as ProjectInternal).evaluate()
        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("release")).isNotNull()
            softly.assertThat(project.tasks.findByName("preReleaseCheck")).isNotNull()
            softly.assertThat(project.tasks.findByName("preReleaseCommit")).isNotNull()
            softly.assertThat(project.tasks.findByName("preReleaseTag")).isNotNull()
            softly.assertThat(release?.releaseBranchPattern?.get()).isEqualTo("(main|master|rel-.*)")
            softly.assertThat(release?.preReleaseCommitMessage?.get()).isEqualTo("Set version for release: \$v")
            softly.assertThat(release?.tagTemplate?.get()).isEqualTo("v\$v")
            softly.assertThat(project.tasks.findByName("release")?.dependsOn).isNotEmpty()
        }
    }

    @Test
    fun `every release task runs after preReleaseCheck`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        (project as ProjectInternal).evaluate()
        val check = project.tasks.getByName("preReleaseCheck")
        val gated = project.tasks.filter { it.group == "release" && it.name != "preReleaseCheck" }
        assertSoftly { softly ->
            softly.assertThat(gated).isNotEmpty()
            gated.forEach {
                softly.assertThat(it.taskDependencies.getDependencies(it)).contains(check)
            }
        }
    }

    @Test
    fun `preReleaseCheck fails when version missing`() {
        val check = checkTask(group = "com.example", version = null)
        assertThatThrownBy { check.check() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("project version is not set")
    }

    @Test
    fun `preReleaseCheck fails when group missing`() {
        val check = checkTask(group = null, version = "1.0.0")
        assertThatThrownBy { check.check() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("project group is not set")
    }

    @Test
    fun `preReleaseCheck fails on dirty tree`() {
        val check = checkTask(clean = false)
        assertThatThrownBy { check.check() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("working tree is dirty")
    }

    @Test
    fun `preReleaseCheck fails when behind remote`() {
        val check = checkTask(upToDate = false)
        assertThatThrownBy { check.check() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("branch is behind remote")
    }

    @Test
    fun `preReleaseCheck fails on branch pattern mismatch`() {
        val check = checkTask(branch = "feature-x")
        assertThatThrownBy { check.check() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("branch 'feature-x' does not match release pattern")
    }

    @Test
    fun `preReleaseCheck passes on blank branch`() {
        val check = checkTask(branch = "")
        check.check()
    }

    @Test
    fun `preReleaseCheck collects all failures`() {
        val check = checkTask(group = null, version = null, branch = "feature-x", clean = false, upToDate = false)
        assertThatThrownBy { check.check() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("project group is not set")
            .hasMessageContaining("project version is not set")
            .hasMessageContaining("working tree is dirty")
            .hasMessageContaining("branch is behind remote")
            .hasMessageContaining("branch 'feature-x' does not match release pattern")
    }

    @Test
    fun `preReleaseCheck uses system property override`() {
        System.setProperty(EasyReleasePlugin.RELEASE_VERSION_PROPERTY, "9.9.9")
        System.setProperty(EasyReleasePlugin.NEXT_VERSION_PROPERTY, "9.9.10-SNAPSHOT")
        try {
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply(ProjectPlugin::class.java)
            (project as ProjectInternal).evaluate()
            val check = project.tasks.getByName("preReleaseCheck") as PreReleaseCheckTask
            val state = releaseStateOf(project)
            assertSoftly { softly ->
                softly.assertThat(state.releaseVersion().get()).isEqualTo("9.9.9")
                softly.assertThat(state.nextVersion().get()).isEqualTo("9.9.10-SNAPSHOT")
            }
        } finally {
            System.clearProperty(EasyReleasePlugin.RELEASE_VERSION_PROPERTY)
            System.clearProperty(EasyReleasePlugin.NEXT_VERSION_PROPERTY)
        }
    }

    @Test
    fun `preReleaseCheck fails without version or semver`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0"
        val easy = project.extensions.getByType(EasyExtension::class.java)
        easy.extensions
            .getByType(EasySemverExtension::class.java)
            .enabled
            .set(false)
        (project as ProjectInternal).evaluate()
        val check = project.tasks.getByName("preReleaseCheck") as PreReleaseCheckTask
        assertThatThrownBy { check.check() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining(EasyReleasePlugin.RELEASE_VERSION_PROPERTY)
    }

    @Test
    fun `preReleaseCheck derives release from snapshot version`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.2.3-SNAPSHOT"
        (project as ProjectInternal).evaluate()
        val state = releaseStateOf(project)
        assertSoftly { softly ->
            softly.assertThat(state.releaseVersion().get()).isEqualTo("1.2.3")
            softly.assertThat(state.nextVersion().get()).isEqualTo("1.2.4-SNAPSHOT")
        }
    }

    @Test
    fun `preReleaseCheck records state on success`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0"
        (project as ProjectInternal).evaluate()
        val check = project.tasks.getByName("preReleaseCheck") as PreReleaseCheckTask
        check.commitSha.set("abc123")
        check.check()
        val state = releaseStateOf(project)
        assertSoftly { softly ->
            softly.assertThat(state.releaseVersion().get()).isEqualTo("1.0.0")
            softly.assertThat(state.nextVersion().get()).isEqualTo("1.0.1-SNAPSHOT")
            softly.assertThat(state.projectName().get()).isEqualTo(project.name)
            softly.assertThat(state.commitSha().orNull).isEqualTo("abc123")
        }
    }

    @Test
    fun `preReleaseCheck does not record state on failure`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.version = "1.0.0"
        (project as ProjectInternal).evaluate()
        val check = project.tasks.getByName("preReleaseCheck") as PreReleaseCheckTask
        assertThatThrownBy { check.check() }.isInstanceOf(GradleException::class.java)
        val state = releaseStateOf(project)
        assertSoftly { softly ->
            softly.assertThat(state.commitSha().orNull.isNullOrBlank()).isTrue()
        }
    }

    @Test
    fun `preReleaseCommit is gated and defaults to root gradle properties`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("preReleaseCommit") as PreReleaseCommitTask
        assertSoftly { softly ->
            softly.assertThat(task.group).isEqualTo("release")
            softly
                .assertThat(task.versionFile.get().asFile)
                .isEqualTo(
                    project.layout.projectDirectory
                        .file("gradle.properties")
                        .asFile,
                )
            softly.assertThat(task.commitMessageTemplate.get()).isEqualTo("Set version for release: \$v")
            softly.assertThat(task.taskDependencies.getDependencies(task)).contains(
                project.tasks.getByName("preReleaseCheck"),
            )
        }
    }

    @Test
    fun `preReleaseCommit updates version file without vcs`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0-SNAPSHOT"
        registerVcsService(project)
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("preReleaseCommit") as PreReleaseCommitTask
        val versionFile = File(project.projectDir, "gradle.properties")
        versionFile.writeText("group=com.example\nversion=1.0.0-SNAPSHOT\n")
        task.versionFile.set(versionFile)

        task.commit()

        assertSoftly { softly ->
            softly.assertThat(versionFile.readText()).contains("version=1.0.0\n")
            softly.assertThat(versionFile.readText()).contains("group=com.example")
        }
    }

    @Test
    fun `preReleaseCommit is a no-op when version file already at release version`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0"
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("preReleaseCommit") as PreReleaseCommitTask
        val original = "group=com.example\nversion=1.0.0\n"
        val versionFile = File(project.projectDir, "gradle.properties")
        versionFile.writeText(original)
        task.versionFile.set(versionFile)

        task.commit()

        assertThat(versionFile.readText()).isEqualTo(original)
    }

    @Test
    fun `preReleaseCommit fails without resolved release version`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0"
        project.extensions
            .getByType(EasyExtension::class.java)
            .extensions
            .getByType(EasySemverExtension::class.java)
            .enabled
            .set(false)
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("preReleaseCommit") as PreReleaseCommitTask
        task.versionFile.set(File(project.projectDir, "gradle.properties"))

        assertThatThrownBy { task.commit() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining(EasyReleasePlugin.RELEASE_VERSION_PROPERTY)
    }

    @Test
    fun `preReleaseTag is gated and runs after preReleaseCommit`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("preReleaseTag") as PreReleaseTagTask
        assertSoftly { softly ->
            softly.assertThat(task.group).isEqualTo("release")
            softly.assertThat(task.tagTemplate.get()).isEqualTo("v\$v")
            softly.assertThat(task.taskDependencies.getDependencies(task)).contains(
                project.tasks.getByName("preReleaseCheck"),
                project.tasks.getByName("preReleaseCommit"),
            )
        }
    }

    @Test
    fun `preReleaseTag is a no-op without vcs`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0-SNAPSHOT"
        registerVcsService(project)
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("preReleaseTag") as PreReleaseTagTask

        task.tag()
    }

    @Test
    fun `preReleaseTag fails without resolved release version`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0"
        project.extensions
            .getByType(EasyExtension::class.java)
            .extensions
            .getByType(EasySemverExtension::class.java)
            .enabled
            .set(false)
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("preReleaseTag") as PreReleaseTagTask

        assertThatThrownBy { task.tag() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining(EasyReleasePlugin.RELEASE_VERSION_PROPERTY)
    }

    @Suppress("UNCHECKED_CAST")
    private fun releaseStateOf(project: Project): ReleaseStateService {
        val registration =
            project.gradle.sharedServices.registrations
                .findByName("release")
                as BuildServiceRegistration<ReleaseStateService, *>
        return registration.service.get()
    }

    private fun checkTask(
        group: String? = "com.example",
        version: String? = "1.0.0",
        branch: String = "main",
        clean: Boolean = true,
        upToDate: Boolean = true,
    ): PreReleaseCheckTask {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        if (group != null) project.group = group
        if (version != null) project.version = version
        (project as ProjectInternal).evaluate()
        val check = project.tasks.getByName("preReleaseCheck") as PreReleaseCheckTask
        check.branch.set(branch)
        check.clean.set(clean)
        check.upToDate.set(upToDate)
        return check
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerVcsService(project: Project) {
        project.gradle.sharedServices.registerIfAbsent("vcs", VcsService::class.java) {
            it.parameters.rootDir.set(project.layout.projectDirectory)
        }
    }
}
