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
import java.nio.file.Files

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
            softly.assertThat(project.tasks.findByName("postReleasePush")).isNotNull()
            softly.assertThat(release?.releaseBranchPattern?.get()).isEqualTo("(main|master|rel-.*)")
            softly.assertThat(release?.preReleaseCommitMessage?.get()).isEqualTo("Set version for release: \$v")
            softly.assertThat(release?.tagTemplate?.get()).isEqualTo("v\$v")
            softly.assertThat(release?.postReleaseCommitMessage?.get()).isEqualTo("Set new version after release: \$v")
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
    fun `preReleaseCheck fails when version file is untracked`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0"
        (project as ProjectInternal).evaluate()
        val check = project.tasks.getByName("preReleaseCheck") as PreReleaseCheckTask
        check.branch.set("main")
        check.clean.set(true)
        check.upToDate.set(true)
        check.versionFile.set(project.layout.projectDirectory.file("version.txt"))
        check.versionFileTracked.set(false)

        assertThatThrownBy { check.check() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("version file")
            .hasMessageContaining("is not tracked by git")
    }

    @Test
    fun `preReleaseCheck passes when version file is tracked`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0"
        (project as ProjectInternal).evaluate()
        val check = project.tasks.getByName("preReleaseCheck") as PreReleaseCheckTask
        check.branch.set("main")
        check.clean.set(true)
        check.upToDate.set(true)
        check.versionFile.set(project.layout.projectDirectory.file("gradle.properties"))
        check.versionFileTracked.set(true)

        check.check()
    }

    @Test
    fun `preReleaseCheck fails when release tag already exists`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0"
        (project as ProjectInternal).evaluate()
        val check = project.tasks.getByName("preReleaseCheck") as PreReleaseCheckTask
        check.branch.set("main")
        check.clean.set(true)
        check.upToDate.set(true)
        check.tagName.set("v1.0.0")
        check.tagExists.set(true)

        assertThatThrownBy { check.check() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("release tag 'v1.0.0' already exists")
    }

    @Test
    fun `preReleaseCheck passes when release tag does not exist`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0"
        (project as ProjectInternal).evaluate()
        val check = project.tasks.getByName("preReleaseCheck") as PreReleaseCheckTask
        check.branch.set("main")
        check.clean.set(true)
        check.upToDate.set(true)
        check.tagName.set("v1.0.0")
        check.tagExists.set(false)

        check.check()
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
            softly.assertThat(state.tagName()).isEqualTo("v1.0.0")
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
    fun `release service resolves custom tag template from the extension`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.2.3-SNAPSHOT"
        project.extensions
            .getByType(EasyExtension::class.java)
            .extensions
            .getByType(EasyReleaseExtension::class.java)
            .tagTemplate
            .set("release-\$v")
        (project as ProjectInternal).evaluate()
        val state = releaseStateOf(project)
        assertSoftly { softly ->
            softly.assertThat(state.releaseVersion().get()).isEqualTo("1.2.3")
            softly.assertThat(state.tagName()).isEqualTo("release-1.2.3")
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

    @Test
    fun `postReleasePush is gated and runs after preReleaseTag`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("postReleasePush") as PostReleasePushTask
        assertSoftly { softly ->
            softly.assertThat(task.group).isEqualTo("release")
            softly.assertThat(task.commitMessageTemplate.get()).isEqualTo("Set new version after release: \$v")
            softly.assertThat(task.taskDependencies.getDependencies(task)).contains(
                project.tasks.getByName("preReleaseCheck"),
                project.tasks.getByName("preReleaseTag"),
            )
        }
    }

    @Test
    fun `postReleasePush bumps to next version and commits`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0-SNAPSHOT"
        registerVcsService(project)
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("postReleasePush") as PostReleasePushTask
        val versionFile = File(project.projectDir, "gradle.properties")
        versionFile.writeText("group=com.example\nversion=1.0.0-SNAPSHOT\n")
        task.versionFile.set(versionFile)

        task.push()

        assertSoftly { softly ->
            softly.assertThat(versionFile.readText()).contains("version=1.0.1-SNAPSHOT\n")
            softly.assertThat(versionFile.readText()).contains("group=com.example")
        }
    }

    @Test
    fun `postReleasePush is a no-op when version file already at next version`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.group = "com.example"
        project.version = "1.0.0-SNAPSHOT"
        registerVcsService(project)
        (project as ProjectInternal).evaluate()
        val task = project.tasks.getByName("postReleasePush") as PostReleasePushTask
        val original = "group=com.example\nversion=1.0.1-SNAPSHOT\n"
        val versionFile = File(project.projectDir, "gradle.properties")
        versionFile.writeText(original)
        task.versionFile.set(versionFile)

        task.push()

        assertThat(versionFile.readText()).isEqualTo(original)
    }

    @Test
    fun `postReleasePush fails without resolved next version`() {
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
        val task = project.tasks.getByName("postReleasePush") as PostReleasePushTask
        task.versionFile.set(File(project.projectDir, "gradle.properties"))

        assertThatThrownBy { task.push() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining(EasyReleasePlugin.NEXT_VERSION_PROPERTY)
    }

    @Test
    fun `rollback resets to gate commit and deletes tag created after gate`() {
        val repo = gitRepo()
        val versionFile = File(repo, "gradle.properties")
        versionFile.writeText("group=com.example\nversion=1.0.0-SNAPSHOT\n")
        gitAddCommit(repo, "initial")
        val gateSha = gitOutput(repo, "rev-parse", "HEAD")

        val state = releaseStateWithRepo(repo)
        state.recordCommitSha(gateSha)
        state.recordReleaseVersion("1.0.0")

        versionFile.writeText("group=com.example\nversion=1.0.0\n")
        runGit(repo, "add", "gradle.properties")
        runGit(repo, "commit", "-m", "Set version for release: 1.0.0")
        runGit(repo, "tag", "v1.0.0")

        state.rollback("postReleasePush")

        assertSoftly { softly ->
            softly.assertThat(gitOutput(repo, "rev-parse", "HEAD")).isEqualTo(gateSha)
            softly.assertThat(gitRef(repo, "v1.0.0")).isNull()
            softly.assertThat(versionFile.readText()).contains("version=1.0.0-SNAPSHOT")
        }
    }

    @Test
    fun `rollback keeps a pre-existing tag pointing at the gate commit`() {
        val repo = gitRepo()
        val versionFile = File(repo, "gradle.properties")
        versionFile.writeText("group=com.example\nversion=1.0.0-SNAPSHOT\n")
        runGit(repo, "add", "-A")
        runGit(repo, "commit", "-m", "initial")
        val gateSha = gitOutput(repo, "rev-parse", "HEAD")
        runGit(repo, "tag", "v1.0.0")

        val state = releaseStateWithRepo(repo)
        state.recordCommitSha(gateSha)
        state.recordReleaseVersion("1.0.0")

        versionFile.writeText("group=com.example\nversion=1.0.0\n")
        runGit(repo, "add", "gradle.properties")
        runGit(repo, "commit", "-m", "Set version for release: 1.0.0")

        state.rollback("preReleaseTag")

        assertSoftly { softly ->
            softly.assertThat(gitOutput(repo, "rev-parse", "HEAD")).isEqualTo(gateSha)
            softly.assertThat(gitRef(repo, "v1.0.0")).isEqualTo(gateSha)
            softly.assertThat(versionFile.readText()).contains("version=1.0.0-SNAPSHOT")
        }
    }

    @Test
    fun `rollback trigger matches full task paths not simple names`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0-SNAPSHOT"
        project.pluginManager.apply(ProjectPlugin::class.java)
        (project as ProjectInternal).evaluate()
        val state = releaseStateOf(project)

        assertThat(state.parameters.releaseTaskPaths.get()).containsExactlyInAnyOrder(
            ":preReleaseCheck",
            ":preReleaseCommit",
            ":preReleaseTag",
            ":postReleasePush",
            ":release",
        )
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

    private fun releaseStateWithRepo(repo: File): ReleaseStateService {
        val project = ProjectBuilder.builder().withProjectDir(repo).build()
        project.group = "com.example"
        project.version = "1.0.0-SNAPSHOT"
        project.pluginManager.apply(ProjectPlugin::class.java)
        (project as ProjectInternal).evaluate()
        return releaseStateOf(project)
    }

    private fun gitRepo(): File =
        Files.createTempDirectory("rollback-repo-").toFile().apply {
            runGit(this, "init", "-b", "main")
            runGit(this, "config", "user.email", "test@example.com")
            runGit(this, "config", "user.name", "Test")
        }

    private fun gitAddCommit(
        dir: File,
        message: String,
    ) {
        runGit(dir, "add", "-A")
        runGit(dir, "commit", "-m", message)
    }

    private fun gitRef(
        dir: File,
        ref: String,
    ): String? {
        val process =
            ProcessBuilder(listOf("git", "rev-parse", "--verify", "--quiet", "$ref^{commit}"))
                .directory(dir)
                .redirectErrorStream(true)
                .start()
        val output =
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
        return output.takeIf { process.waitFor() == 0 }
    }

    private fun gitOutput(
        dir: File,
        vararg args: String,
    ): String {
        val process =
            ProcessBuilder(listOf("git") + args.toList())
                .directory(dir)
                .redirectErrorStream(true)
                .start()
        val output =
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed with exit code $process.exitValue()" }
        return output
    }

    private fun runGit(
        dir: File,
        vararg args: String,
    ) {
        val process =
            ProcessBuilder(listOf("git") + args.toList())
                .directory(dir)
                .redirectErrorStream(true)
                .start()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed with exit code $process.exitValue()" }
    }
}
