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
}

private fun initGitWithRemote(project: GradleTestProject) {
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
