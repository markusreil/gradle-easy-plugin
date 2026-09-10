package com.mreil.easy.codemeta

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import com.mreil.easy.vcs.EasyVcsExtension
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.ExtensionAware
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class EasyCodemetaPluginTest {
    @TempDir
    lateinit var tempDir: Path

    private fun project(dir: Path): Project {
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        return project
    }

    private fun generateTask(project: Project): GenerateCodemetaTask =
        project.tasks.named("generateCodemeta", GenerateCodemetaTask::class.java).get()

    private fun evaluate(project: Project) = (project as ProjectInternal).evaluate()

    private fun git(
        dir: Path,
        vararg args: String,
    ) {
        val process = ProcessBuilder(listOf("git", *args)).directory(dir.toFile()).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $output" }
    }

    private fun disableVcs(project: Project) {
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        easy.extensions
            .getByType(EasyVcsExtension::class.java)
            .enabled
            .set(false)
    }

    @Test
    fun `generate task uses vcs remote url when vcs enabled`() {
        git(tempDir, "init")
        git(tempDir, "remote", "add", "origin", "https://github.com/example/repo.git")
        val project = project(tempDir)
        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(generateTask(project).codeRepository.get()).isEqualTo("https://github.com/example/repo")
        }
    }

    @Test
    fun `generate task falls back to placeholder when vcs disabled`() {
        val project = project(tempDir)
        disableVcs(project)
        evaluate(project)

        assertSoftly { softly ->
            softly
                .assertThat(generateTask(project).codeRepository.get())
                .isEqualTo("TODO: Add codeRepository - e.g. https://github.com/mreil/gradle-easy-plugin-new")
        }
    }

    @Test
    fun `generate task falls back to placeholder without git remote`() {
        val project = project(tempDir)
        evaluate(project)

        assertSoftly { softly ->
            softly
                .assertThat(generateTask(project).codeRepository.get())
                .isEqualTo("TODO: Add codeRepository - e.g. https://github.com/mreil/gradle-easy-plugin-new")
        }
    }
}
