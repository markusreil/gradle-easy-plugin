package com.mreil.easy.vcs

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class EasyVcsPluginTest {
    @TempDir
    lateinit var tempDir: Path

    private fun project(dir: Path): Project {
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        return project
    }

    private fun vcsService(project: Project): VcsService {
        val registration =
            project.gradle.sharedServices.registrations
                .findByName("vcs")
                ?: error("vcs service not registered")
        @Suppress("UNCHECKED_CAST")
        return registration.service.get() as VcsService
    }

    private fun git(
        dir: Path,
        vararg args: String,
    ): String {
        val process = ProcessBuilder(listOf("git", *args)).directory(dir.toFile()).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $output" }
        return output
    }

    @Test
    fun `remote url resolves origin https and strips git suffix`() {
        git(tempDir, "init")
        git(tempDir, "remote", "add", "origin", "https://github.com/example/repo.git")
        val service = vcsService(project(tempDir))

        assertSoftly { softly ->
            softly.assertThat(service.remoteUrl().get()).isEqualTo("https://github.com/example/repo")
        }
    }

    @Test
    fun `remote url normalizes ssh form to https`() {
        git(tempDir, "init")
        git(tempDir, "remote", "add", "origin", "git@github.com:example/repo.git")
        val service = vcsService(project(tempDir))

        assertSoftly { softly ->
            softly.assertThat(service.remoteUrl().get()).isEqualTo("https://github.com/example/repo")
        }
    }

    @Test
    fun `remote url is empty for none type`() {
        val service = vcsService(project(tempDir))

        assertSoftly { softly ->
            softly.assertThat(service.remoteUrl().get()).isEmpty()
        }
    }

    @Test
    fun `detects git when git dir present`() {
        tempDir.resolve(".git").toFile().mkdirs()
        val service = vcsService(project(tempDir))

        assertSoftly { softly ->
            softly.assertThat(service.type()).isEqualTo(VcsType.GIT)
        }
    }

    @Test
    fun `detects none when no git dir present`() {
        val service = vcsService(project(tempDir))

        assertSoftly { softly ->
            softly.assertThat(service.type()).isEqualTo(VcsType.NONE)
        }
    }

    @Test
    fun `none info defaults branch and clean`() {
        val service = vcsService(project(tempDir))

        assertSoftly { softly ->
            softly.assertThat(service.info().type).isEqualTo(VcsType.NONE)
            softly.assertThat(service.info().branch).isNull()
            softly.assertThat(service.info().clean).isTrue()
        }
    }

    @Test
    fun `current sha is empty for none type`() {
        val service = vcsService(project(tempDir))

        assertSoftly { softly ->
            softly.assertThat(service.type()).isEqualTo(VcsType.NONE)
            softly.assertThat(service.currentSha().get()).isEmpty()
        }
    }

    @Test
    fun `service registered in init even when disabled`() {
        val project = project(tempDir)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        easy.extensions
            .getByType(EasyVcsExtension::class.java)
            .enabled
            .set(false)

        assertSoftly { softly ->
            softly
                .assertThat(
                    project.gradle.sharedServices.registrations
                        .findByName("vcs"),
                ).isNotNull()
        }
    }
}
