package com.mreil.easy

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
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

    private class SimplePluginRegistry : PluginRegistry {
        private val projectPlugins = mutableSetOf<KClass<out Plugin<Project>>>()
        private val settingsPlugins = mutableSetOf<KClass<out Plugin<Settings>>>()
        private val extensions = mutableSetOf<KClass<out EasyPluginExtension>>()

        override fun registerProjectPlugin(pluginClass: KClass<out Plugin<Project>>) {
            projectPlugins.add(pluginClass)
        }

        override fun getProjectPlugins(): Set<KClass<out Plugin<Project>>> = projectPlugins.toSet()

        override fun registerSettingsPlugin(pluginClass: KClass<out Plugin<Settings>>) {
            settingsPlugins.add(pluginClass)
        }

        override fun getSettingsPlugins(): Set<KClass<out Plugin<Settings>>> = settingsPlugins.toSet()

        override fun registerExtension(extensionClass: KClass<out EasyPluginExtension>) {
            extensions.add(extensionClass)
        }

        override fun getRegisteredExtensions(): Set<KClass<out EasyPluginExtension>> = extensions.toSet()
    }

    @Test
    fun `creates EasyExtension and registers child extensions on ExtensionAware`() {
        val project = ProjectBuilder.builder().build()
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
                registerExtension(OtherTestSubExtension::class)
            }

        val extension = ExtensionRegistrar.createExtension(project, registry)

        assertSoftly { softly ->
            softly.assertThat(extension).isNotNull
            softly.assertThat(project.extensions.findByName(EasyExtension.name)).isSameAs(extension)
            softly.assertThat(extension.extensions.findByName("sub")).isInstanceOf(TestSubExtension::class.java)
            softly.assertThat(extension.extensions.findByName("other")).isInstanceOf(OtherTestSubExtension::class.java)
        }
    }

    @Test
    fun `creates EasyExtension directly on ExtensionContainer`() {
        val project = ProjectBuilder.builder().build()
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
            }

        val extension = ExtensionRegistrar.createExtension(project.extensions, registry)

        assertSoftly { softly ->
            softly.assertThat(extension).isNotNull
            softly.assertThat(project.extensions.findByName(EasyExtension.name)).isSameAs(extension)
            softly.assertThat(extension.extensions.findByName("sub")).isInstanceOf(TestSubExtension::class.java)
        }
    }

    @Test
    fun `fails when extension class lacks companion object implementing Named`() {
        val project = ProjectBuilder.builder().build()
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(MissingNamedSubExtension::class)
            }

        assertThatThrownBy {
            ExtensionRegistrar.createExtension(project, registry)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must have a companion object implementing Named")
    }

    @Test
    fun `creates EasyExtension on ExtensionAware delegates to ExtensionContainer overload`() {
        val project = ProjectBuilder.builder().build()
        val target: ExtensionAware = project
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
                registerExtension(OtherTestSubExtension::class)
            }

        val extension = ExtensionRegistrar.createExtension(target, registry)

        assertSoftly { softly ->
            softly.assertThat(extension).isNotNull
            softly.assertThat(target.extensions.findByName(EasyExtension.name)).isSameAs(extension)
            softly.assertThat(extension.extensions.findByName("sub")).isInstanceOf(TestSubExtension::class.java)
            softly.assertThat(extension.extensions.findByName("other")).isInstanceOf(OtherTestSubExtension::class.java)
        }
    }
}
