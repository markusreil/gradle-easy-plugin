package com.mreil.easy.publish.central

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.tasks.StopExecutionException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JreleaserPublishTaskTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `buildArgs runs deploy with config file`() {
        val task = createTask()
        task.configFile.set(File(tempDir, "jreleaser.yml"))
        task.projectVersion.set("1.0.0")
        task.dryRun.set(false)

        assertSoftly { softly ->
            softly.assertThat(task.buildArgs()).containsExactly(
                "deploy",
                "-c",
                File(tempDir, "jreleaser.yml").absolutePath,
            )
        }
    }

    @Test
    fun `buildArgs supports dry-run and deployer filter`() {
        val task = createTask()
        task.configFile.set(File(tempDir, "jreleaser.yml"))
        task.projectVersion.set("1.0.0")
        task.dryRun.set(true)
        task.deployerName.set("local-test")

        assertSoftly { softly ->
            softly.assertThat(task.buildArgs()).containsExactly(
                "deploy",
                "-c",
                File(tempDir, "jreleaser.yml").absolutePath,
                "--dry-run",
                "-yn",
                "local-test",
            )
        }
    }

    @Test
    fun `main class defaults to cli entrypoint`() {
        val task = createTask()

        assertSoftly { softly ->
            softly.assertThat(task.mainClass.get()).isEqualTo("org.jreleaser.cli.Main")
        }
    }

    @Test
    fun `ensureStagedUploads skips friendly when nothing staged`() {
        val task = createTask()
        task.hasStagedUploads.set(false)

        assertThatThrownBy { task.ensureStagedUploads() }
            .isInstanceOf(StopExecutionException::class.java)
            .hasMessageContaining("Nothing staged")
    }

    @Test
    fun `ensureStagedUploads passes when staged`() {
        val task = createTask()
        task.hasStagedUploads.set(true)

        task.ensureStagedUploads()
    }

    private fun createTask(): JreleaserPublishTask {
        val project = ProjectBuilder.builder().build()
        return project.tasks.register("deployTest", JreleaserPublishTask::class.java).get()
    }
}
