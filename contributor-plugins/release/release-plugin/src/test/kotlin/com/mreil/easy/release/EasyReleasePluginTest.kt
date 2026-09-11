package com.mreil.easy.release

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import org.assertj.core.api.SoftAssertions.assertSoftly
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
    }
}
