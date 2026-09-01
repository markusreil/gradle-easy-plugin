package com.mreil.easy.jvm

import com.mreil.easy.ProjectPlugin
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class EasyJvmDefaultsPluginTest {
    @Test
    fun `configures sources and javadoc jars when java plugin active`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(ProjectPlugin::class.java)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("sourcesJar")).isNotNull()
            softly.assertThat(project.tasks.findByName("javadocJar")).isNotNull()
        }
    }

    @Test
    fun `does not configure when java plugin absent`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)

        assertSoftly { softly ->
            softly.assertThat(project.extensions.findByType(JavaPluginExtension::class.java)).isNull()
        }
    }
}
