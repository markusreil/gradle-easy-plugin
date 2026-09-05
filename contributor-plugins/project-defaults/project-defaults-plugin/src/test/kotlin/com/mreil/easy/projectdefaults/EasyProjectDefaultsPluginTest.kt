package com.mreil.easy.projectdefaults

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.ExtensionAware
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EasyProjectDefaultsPluginTest {
    @Test
    fun `applies base plugin to project`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "1.0.0"
        project.pluginManager.apply(ProjectPlugin::class.java)

        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("clean")).isNotNull()
            softly.assertThat(project.tasks.findByName("build")).isNotNull()
            softly.assertThat(project.tasks.findByName("check")).isNotNull()
        }
    }

    @Test
    fun `fails when group is missing`() {
        val project = ProjectBuilder.builder().build()
        project.group = ""
        project.version = "1.0.0"
        project.pluginManager.apply(ProjectPlugin::class.java)

        val ex = assertThrows<ProjectConfigurationException> { evaluate(project) }

        assertSoftly { softly ->
            softly.assertThat(ex.cause?.message).contains("Project group must be set")
        }
    }

    @Test
    fun `fails when version is missing`() {
        val project = ProjectBuilder.builder().build()
        project.group = "com.example"
        project.version = "unspecified"
        project.pluginManager.apply(ProjectPlugin::class.java)

        val ex = assertThrows<ProjectConfigurationException> { evaluate(project) }

        assertSoftly { softly ->
            softly.assertThat(ex.cause?.message).contains("Project version must be set")
        }
    }

    @Test
    fun `still applies base plugin when extension disabled`() {
        val project = ProjectBuilder.builder().build()
        project.group = ""
        project.version = "unspecified"
        project.pluginManager.apply(ProjectPlugin::class.java)
        val easy = project.extensions.getByType(EasyExtension::class.java) as ExtensionAware
        easy.extensions
            .getByType(EasyProjectDefaultsExtension::class.java)
            .enabled
            .set(false)

        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("clean")).isNotNull()
        }
    }

    private fun evaluate(project: Project) {
        // Trigger afterEvaluate callbacks registered by AbstractEasyProjectPlugin
        val internal = project as ProjectInternal
        internal.evaluate()
    }
}
