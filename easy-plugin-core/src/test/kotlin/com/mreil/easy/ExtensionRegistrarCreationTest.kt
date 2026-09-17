package com.mreil.easy

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.reflect.KClass

class ExtensionRegistrarCreationTest {
    abstract class TestSubExtension :
        EasyPluginExtension,
        CanBeEnabled {
        init {
            enabled.convention(true)
        }

        companion object : Named {
            override val name: String = "sub"
        }
    }

    abstract class OtherTestSubExtension : EasyPluginExtension {
        abstract val message: Property<String>

        companion object : Named {
            override val name: String = "other"
        }
    }

    abstract class MissingNamedSubExtension : EasyPluginExtension

    private fun registry(vararg extensions: KClass<out EasyPluginExtension>): PluginRegistry =
        mock(PluginRegistry::class.java).apply {
            `when`(getRegisteredExtensions()).thenReturn(extensions.toSet())
        }

    @Test
    fun `creates EasyExtension and registers child extensions on ExtensionAware`() {
        val project = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class, OtherTestSubExtension::class)

        val extension = ExtensionRegistrar(project, project.providers).createExtension(registry)

        assertSoftly { softly ->
            softly.assertThat(extension).isNotNull
            softly.assertThat(project.extensions.findByName(EasyExtension.name)).isSameAs(extension)
            softly.assertThat(extension.extensions.findByName("sub")).isInstanceOf(TestSubExtension::class.java)
            softly.assertThat(extension.extensions.findByName("other")).isInstanceOf(OtherTestSubExtension::class.java)
        }
    }

    @Test
    fun `creates EasyExtension on ExtensionAware target`() {
        val project = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class)

        val extension = ExtensionRegistrar(project, project.providers).createExtension(registry)

        assertSoftly { softly ->
            softly.assertThat(extension).isNotNull
            softly.assertThat(project.extensions.findByName(EasyExtension.name)).isSameAs(extension)
            softly.assertThat(extension.extensions.findByName("sub")).isInstanceOf(TestSubExtension::class.java)
        }
    }

    @Test
    fun `fails when extension class lacks companion object implementing Named`() {
        val project = ProjectBuilder.builder().build()
        val registry = registry(MissingNamedSubExtension::class)

        assertThatThrownBy {
            ExtensionRegistrar(project, project.providers).createExtension(registry)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must have a companion object implementing Named")
    }

    @Test
    fun `creates EasyExtension on ExtensionAware reference`() {
        val project = ProjectBuilder.builder().build()
        val target: ExtensionAware = project
        val registry = registry(TestSubExtension::class, OtherTestSubExtension::class)

        val extension = ExtensionRegistrar(target, project.providers).createExtension(registry)

        assertSoftly { softly ->
            softly.assertThat(extension).isNotNull
            softly.assertThat(target.extensions.findByName(EasyExtension.name)).isSameAs(extension)
            softly.assertThat(extension.extensions.findByName("sub")).isInstanceOf(TestSubExtension::class.java)
            softly.assertThat(extension.extensions.findByName("other")).isInstanceOf(OtherTestSubExtension::class.java)
        }
    }
}
