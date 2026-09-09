package com.mreil.easy.jvm

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class EasyJvmDefaultsPluginTest {
    @Test
    fun `registers jvmDefaults extension enabled by default`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)

        val extension =
            (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyJvmDefaultsExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(extension.enabled.get()).isTrue()
        }
    }

    @Test
    fun `configures sources and javadoc jars when java plugin active`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(ProjectPlugin::class.java)
        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("sourcesJar")).isNotNull()
            softly.assertThat(project.tasks.findByName("javadocJar")).isNotNull()
        }
    }

    @Test
    fun `keeps manually configured sources and javadoc jars`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
        javaExtension.withSourcesJar()
        javaExtension.withJavadocJar()

        project.pluginManager.apply(ProjectPlugin::class.java)
        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("sourcesJar")).isNotNull()
            softly.assertThat(project.tasks.findByName("javadocJar")).isNotNull()
        }
    }

    @Test
    fun `does not configure when java plugin absent`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        evaluate(project)

        assertSoftly { softly ->
            softly.assertThat(project.extensions.findByType(JavaPluginExtension::class.java)).isNull()
        }
    }

    @Test
    fun `pins java toolchain to declared version`() {
        System.setProperty(EasyJvmDefaultsPlugin.PROPERTY_NAME, "17")
        try {
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply("java")
            project.pluginManager.apply(ProjectPlugin::class.java)
            evaluate(project)

            val languageVersion =
                project.extensions
                    .getByType(JavaPluginExtension::class.java)
                    .toolchain.languageVersion.orNull

            assertSoftly { softly ->
                softly.assertThat(languageVersion).isEqualTo(JavaLanguageVersion.of(17))
            }
        } finally {
            System.clearProperty(EasyJvmDefaultsPlugin.PROPERTY_NAME)
        }
    }

    @Test
    fun `does not pin toolchain when property absent`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(ProjectPlugin::class.java)
        evaluate(project)

        val languageVersion =
            project.extensions
                .getByType(JavaPluginExtension::class.java)
                .toolchain.languageVersion.orNull

        assertSoftly { softly ->
            softly.assertThat(languageVersion).isNull()
        }
    }

    private fun evaluate(project: Project) {
        // Trigger afterEvaluate callbacks registered by AbstractEasyProjectPlugin
        val internal = project as ProjectInternal
        internal.evaluate()
    }
}
