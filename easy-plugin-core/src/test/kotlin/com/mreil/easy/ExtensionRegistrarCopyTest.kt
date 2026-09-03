package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class ExtensionRegistrarCopyTest {
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
    fun `copies values when parent ExtensionAware is provided`() {
        val parentProject = ProjectBuilder.builder().build()
        val childProject = ProjectBuilder.builder().build()

        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
            }

        val parentExt = ExtensionRegistrar.createExtension(parentProject, registry)
        val parentSub = parentExt.extensions.getByType(TestSubExtension::class.java)
        parentSub.enabled.set(true)

        val childExt = ExtensionRegistrar.createExtension(childProject, registry, parentProject)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(childSub.enabled.get()).isTrue()
        }
    }

    @Test
    fun `copies values when parent EasyExtension directly is provided`() {
        val parentProject = ProjectBuilder.builder().build()
        val childProject = ProjectBuilder.builder().build()

        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
            }

        val parentExt = ExtensionRegistrar.createExtension(parentProject, registry)
        val parentSub = parentExt.extensions.getByType(TestSubExtension::class.java)
        parentSub.enabled.set(true)

        val childExt = ExtensionRegistrar.createExtension(childProject, registry, parentExt)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(childSub.enabled.get()).isTrue()
        }
    }

    @Test
    fun `creates EasyExtension on ExtensionAware with parent ExtensionAware copies values`() {
        val parentProject = ProjectBuilder.builder().build()
        val childHolder = ProjectBuilder.builder().build()
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
            }

        val parentExt = ExtensionRegistrar.createExtension(parentProject as ExtensionAware, registry)
        val parentSub = parentExt.extensions.getByType(TestSubExtension::class.java)
        parentSub.enabled.set(true)

        val childTarget: ExtensionAware = childHolder
        val childExt = ExtensionRegistrar.createExtension(childTarget, registry, parentProject as ExtensionAware)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(childSub.enabled.get()).isTrue()
        }
    }

    @Test
    fun `creates EasyExtension on ExtensionAware with parent CanBeCopied copies values`() {
        val parentProject = ProjectBuilder.builder().build()
        val childHolder = ProjectBuilder.builder().build()
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
            }

        val parentExt = ExtensionRegistrar.createExtension(parentProject as ExtensionAware, registry)
        val parentSub = parentExt.extensions.getByType(TestSubExtension::class.java)
        parentSub.enabled.set(true)

        val childTarget: ExtensionAware = childHolder
        val childExt = ExtensionRegistrar.createExtension(childTarget, registry, parentExt as ExtensionAware)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(childSub.enabled.get()).isTrue()
        }
    }

    @Test
    fun `parent convention false is copied to child overriding child default true`() {
        val parentProject = ProjectBuilder.builder().build()
        val childProject = ProjectBuilder.builder().build()
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
            }

        val parentExt = ExtensionRegistrar.createExtension(parentProject, registry)
        parentExt.extensions
            .getByType(TestSubExtension::class.java)
            .enabled
            .set(false)

        val childExt = ExtensionRegistrar.createExtension(childProject, registry, parentProject)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(childSub.enabled.get()).isFalse()
        }
    }

    @Test
    fun `does not copy when parent is null but defaults to true`() {
        val project = ProjectBuilder.builder().build()
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
            }

        val ext = ExtensionRegistrar.createExtension(project, registry, parent = null)
        val sub = ext.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(sub.enabled.get()).isTrue()
        }
    }
}
