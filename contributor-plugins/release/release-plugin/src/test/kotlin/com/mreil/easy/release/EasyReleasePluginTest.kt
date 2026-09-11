package com.mreil.easy.release

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.GradleException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

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
            softly.assertThat(release?.releaseBranchPattern?.get()).isEqualTo("(main|master|rel-.*)")
            softly.assertThat(project.tasks.findByName("release")?.dependsOn).isNotEmpty()
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
}
