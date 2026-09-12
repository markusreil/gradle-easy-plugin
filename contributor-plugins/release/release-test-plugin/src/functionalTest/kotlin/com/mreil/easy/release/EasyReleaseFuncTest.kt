package com.mreil.easy.release

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.nio.file.Files

@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
@Suppress("TooManyFunctions")
class EasyReleaseFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `release succeeds without vcs`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val result = project.build("release")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(result.output).contains("Releasing")
            softly.assertThat(result.output).contains("release version 1.0.0")
            softly.assertThat(result.output).contains("next version 1.0.1-SNAPSHOT")
        }
    }

    @Test
    fun `release succeeds on clean git tree up to date with remote`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val result = project.build("release")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(result.output).contains("release version 1.0.0")
            softly.assertThat(result.output).contains("next version 1.0.1-SNAPSHOT")
        }
    }

    @Test
    fun `system properties override semver versions`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
            systemProperty("easy.release.version", "9.9.9")
            systemProperty("easy.release.nextVersion", "9.9.10-SNAPSHOT")
        }

        val result = project.build("release")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(result.output).contains("release version 9.9.9")
            softly.assertThat(result.output).contains("next version 9.9.10-SNAPSHOT")
        }
    }

    @Test
    fun `preReleaseCommit writes release version and commits only the version file`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val result = project.build("preReleaseCommit")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(project.file("gradle.properties").readText()).contains("version=1.0.0")
            softly.assertThat(gitLastMessage(project)).isEqualTo("Set version for release: 1.0.0")
            softly.assertThat(gitChangedFiles(project)).containsExactly("gradle.properties")
        }
    }

    @Test
    fun `preReleaseCommit uses custom version file and message template`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release {
                        enabled.set(true)
                        versionFile.set(layout.projectDirectory.file("version.txt"))
                        preReleaseCommitMessage.set("Release \${'$'}v is out")
                    }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
            file("version.txt", "version=1.0.0-SNAPSHOT")
        }
        initGitWithRemote(project)

        val result = project.build("preReleaseCommit")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(project.file("version.txt").readText()).contains("version=1.0.0")
            softly.assertThat(gitLastMessage(project)).isEqualTo("Release 1.0.0 is out")
            softly.assertThat(gitChangedFiles(project)).containsExactly("version.txt")
        }
    }

    @Test
    fun `preReleaseCommit is gated by preReleaseCheck`() {
        project.configure {
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)
        project.file("uncommitted.txt", "dirty")

        val result = project.buildAndFail("preReleaseCommit")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("working tree is dirty")
        }
    }

    @Test
    fun `preReleaseCommit without vcs updates the version file`() {
        project.configure {
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val result = project.build("preReleaseCommit")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(project.file("gradle.properties").readText()).contains("version=1.0.0")
        }
    }

    @Test
    fun `preReleaseTag tags the release commit with release version`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val result = project.build("preReleaseTag")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(result.output).contains("Created git tag v1.0.0.")
            softly.assertThat(gitLastMessage(project)).isEqualTo("Set version for release: 1.0.0")
            softly.assertThat(gitRevParse(project, "v1.0.0")).isEqualTo(gitRevParse(project, "HEAD"))
        }
    }

    @Test
    fun `preReleaseTag uses custom tag template`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release {
                        enabled.set(true)
                        tagTemplate.set("release-\${'$'}v")
                    }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val result = project.build("preReleaseTag")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(result.output).contains("Created git tag release-1.0.0.")
            softly.assertThat(gitRevParse(project, "release-1.0.0")).isEqualTo(gitRevParse(project, "HEAD"))
        }
    }

    @Test
    fun `preReleaseTag without vcs is a no-op`() {
        project.configure {
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val result = project.build("preReleaseTag")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
        }
    }

    @Test
    fun `postReleasePush bumps to next version, commits and pushes commit and tag atomically`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        val remote = initGitWithRemote(project)

        val result = project.build("postReleasePush")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(result.output).contains("Pushed commit and tag v1.0.0.")
            softly.assertThat(project.file("gradle.properties").readText()).contains("version=1.0.1-SNAPSHOT")
            softly.assertThat(gitLastMessage(project)).isEqualTo("Set new version after release: 1.0.1-SNAPSHOT")
            softly
                .assertThat(gitOutput(remote.absolutePath, "rev-parse", "v1.0.0"))
                .isEqualTo(gitRevParse(project, "v1.0.0"))
            softly
                .assertThat(gitOutput(remote.absolutePath, "log", "--format=%s"))
                .contains("Set new version after release: 1.0.1-SNAPSHOT")
        }
    }

    @Test
    fun `postReleasePush uses custom commit message template`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release {
                        enabled.set(true)
                        postReleaseCommitMessage.set("Post \${'$'}v")
                    }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val result = project.build("postReleasePush")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(gitLastMessage(project)).isEqualTo("Post 1.0.1-SNAPSHOT")
        }
    }

    @Test
    fun `postReleasePush without vcs bumps the version file`() {
        project.configure {
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val result = project.build("postReleasePush")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(project.file("gradle.properties").readText()).contains("version=1.0.1-SNAPSHOT")
        }
    }

    @Test
    fun `failed push rolls back to gate commit and deletes the release tag`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        val remote = initGitWithRemote(project)
        val conflictSha = advanceRemote(remote)
        val gateSha = gitRevParse(project, "HEAD")

        val result = project.buildAndFail("release")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("Rolling back local changes")
            softly.assertThat(result.output).contains("Deleted release tag v1.0.0.")
            softly.assertThat(gitRevParse(project, "HEAD")).isEqualTo(gateSha)
            softly.assertThat(gitTagExists(project, "v1.0.0")).isFalse()
            softly.assertThat(project.file("gradle.properties").readText()).contains("version=1.0.0-SNAPSHOT")
            softly.assertThat(gitOutput(remote.absolutePath, "rev-parse", "refs/heads/main")).isEqualTo(conflictSha)
            softly.assertThat(gitRefExists(remote.absolutePath, "v1.0.0")).isFalse()
        }
    }

    @Test
    fun `pre-existing release tag survives rollback`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)
        runGit(project.projectDir.absolutePath, "tag", "v1.0.0")
        val gateSha = gitRevParse(project, "HEAD")

        val result = project.buildAndFail("release")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("Rolling back local changes")
            softly.assertThat(gitRevParse(project, "HEAD")).isEqualTo(gateSha)
            softly.assertThat(gitTagExists(project, "v1.0.0")).isTrue()
            softly.assertThat(gitRevParse(project, "v1.0.0")).isEqualTo(gateSha)
            softly.assertThat(project.file("gradle.properties").readText()).contains("version=1.0.0-SNAPSHOT")
        }
    }

    @Test
    fun `no rollback when the gate fails on a dirty tree`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)
        project.file("uncommitted.txt", "dirty")
        val headSha = gitRevParse(project, "HEAD")

        val result = project.buildAndFail("release")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("working tree is dirty")
            softly.assertThat(result.output).doesNotContain("Rolling back")
            softly.assertThat(gitRevParse(project, "HEAD")).isEqualTo(headSha)
            softly.assertThat(gitTagExists(project, "v1.0.0")).isFalse()
            softly.assertThat(project.file("uncommitted.txt").readText()).isEqualTo("dirty")
        }
    }

    @Test
    fun `successful release performs no rollback`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release { enabled.set(true) }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)

        val result = project.build("release")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("BUILD SUCCESSFUL")
            softly.assertThat(result.output).doesNotContain("Rolling back")
            softly.assertThat(gitTagExists(project, "v1.0.0")).isTrue()
            softly.assertThat(project.file("gradle.properties").readText()).contains("version=1.0.1-SNAPSHOT")
        }
    }

    @Test
    fun `preReleaseCheck fails when version file is untracked`() {
        project.configure {
            file(".gitignore", ".gradle/\nbuild/\n")
            project.version = "1.0.0-SNAPSHOT"
            buildGradle(
                """
                plugins {
                    id("com.mreil.easy.test.release")
                }
                version = "1.0.0-SNAPSHOT"
                easy {
                    release {
                        enabled.set(true)
                        versionFile.set(layout.projectDirectory.file("version.txt"))
                    }
                    vcs { enabled.set(true) }
                    semver { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }
        initGitWithRemote(project)
        // Write version.txt AFTER the initial commit so it remains untracked in git's index.
        project.file("version.txt", "version=1.0.0-SNAPSHOT\n")

        val result = project.buildAndFail("preReleaseCheck")

        assertSoftly { softly ->
            softly.assertThat(result.output).contains("is not tracked by git")
            softly.assertThat(gitTagExists(project, "v1.0.0")).isFalse()
            softly.assertThat(project.file("version.txt").readText()).contains("version=1.0.0-SNAPSHOT")
        }
    }
}

private fun initGitWithRemote(project: GradleTestProject): File {
    project.file("build.gradle.kts")
    runGit(project.projectDir.absolutePath, "init", "-b", "main")
    runGit(project.projectDir.absolutePath, "config", "user.email", "test@example.com")
    runGit(project.projectDir.absolutePath, "config", "user.name", "Test")
    runGit(project.projectDir.absolutePath, "add", "-A")
    runGit(project.projectDir.absolutePath, "commit", "-m", "initial")
    val remoteDir = Files.createTempDirectory("git-remote-").toFile()
    runGit(remoteDir.absolutePath, "init", "--bare")
    runGit(project.projectDir.absolutePath, "remote", "add", "origin", remoteDir.absolutePath)
    runGit(project.projectDir.absolutePath, "push", "-u", "origin", "main")
    return remoteDir
}

/**
 * Advances the bare [remote] with a commit the local repo does not know about (via a throwaway
 * clone), so the next `git push` from the project is a non-fast-forward that gets rejected.
 */
private fun advanceRemote(remote: File): String {
    val cloneDir = Files.createTempDirectory("rollback-clone-").toFile()
    runGit(remote.parentFile.absolutePath, "clone", remote.absolutePath, cloneDir.absolutePath)
    runGit(cloneDir.absolutePath, "config", "user.email", "test@example.com")
    runGit(cloneDir.absolutePath, "config", "user.name", "Test")
    runGit(cloneDir.absolutePath, "commit", "--allow-empty", "-m", "remote-only commit")
    runGit(cloneDir.absolutePath, "push", "origin", "main")
    return gitOutput(remote.absolutePath, "rev-parse", "refs/heads/main")
}

private fun runGit(
    workDir: String,
    vararg args: String,
) {
    val process =
        ProcessBuilder(listOf("git") + args.toList())
            .directory(File(workDir))
            .redirectErrorStream(true)
            .start()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "git ${args.joinToString(" ")} failed with exit code $exitCode" }
}

private fun gitLastMessage(project: GradleTestProject): String = gitOutput(project.projectDir.absolutePath, "log", "-1", "--format=%s")

private fun gitRevParse(
    project: GradleTestProject,
    ref: String,
): String = gitOutput(project.projectDir.absolutePath, "rev-parse", ref)

private fun gitChangedFiles(project: GradleTestProject): List<String> =
    gitOutput(project.projectDir.absolutePath, "show", "--name-only", "--format=", "HEAD")
        .lines()
        .filter { it.isNotBlank() }

private fun gitTagExists(
    project: GradleTestProject,
    tag: String,
): Boolean = gitRefExists(project.projectDir.absolutePath, tag)

private fun gitRefExists(
    workDir: String,
    ref: String,
): Boolean {
    val process =
        ProcessBuilder(listOf("git", "rev-parse", "--verify", "--quiet", "$ref^{commit}"))
            .directory(File(workDir))
            .redirectErrorStream(true)
            .start()
    return process.waitFor() == 0
}

private fun gitOutput(
    workDir: String,
    vararg args: String,
): String {
    val process =
        ProcessBuilder(listOf("git") + args.toList())
            .directory(File(workDir))
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed with exit code $process.exitValue()" }
    return output.trim()
}
