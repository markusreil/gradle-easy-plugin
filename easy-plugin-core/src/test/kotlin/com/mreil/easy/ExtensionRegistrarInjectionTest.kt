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

class ExtensionRegistrarInjectionTest {
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
    fun `does not inject to subprojects when called via ExtensionAware reference`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val sub =
            ProjectBuilder
                .builder()
                .withName("sub")
                .withParent(root)
                .build()
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
            }

        val target: ExtensionAware = root
        ExtensionRegistrar.createExtension(target, registry)

        assertSoftly { softly ->
            softly.assertThat(sub.extensions.findByName(EasyExtension.name)).isNull()
        }
    }

    @Test
    fun `injects EasyExtension copies to subprojects when created on root Project`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val sub1 =
            ProjectBuilder
                .builder()
                .withName("sub1")
                .withParent(root)
                .build()
        val sub2 =
            ProjectBuilder
                .builder()
                .withName("sub2")
                .withParent(root)
                .build()
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
                registerExtension(OtherTestSubExtension::class)
            }

        val rootExt = ExtensionRegistrar.createExtensionWithSubprojects(root, registry)

        assertSoftly { softly ->
            softly.assertThat(root.extensions.findByName(EasyExtension.name)).isSameAs(rootExt)
            softly.assertThat(sub1.extensions.findByName(EasyExtension.name)).isNotNull
            softly.assertThat(sub2.extensions.findByName(EasyExtension.name)).isNotNull
            val sub1Easy = sub1.extensions.getByName(EasyExtension.name) as ExtensionAware
            val sub2Easy = sub2.extensions.getByName(EasyExtension.name) as ExtensionAware
            softly.assertThat(sub1Easy.extensions.findByName("sub")).isInstanceOf(TestSubExtension::class.java)
            softly.assertThat(sub2Easy.extensions.findByName("other")).isInstanceOf(OtherTestSubExtension::class.java)
        }
    }

    @Test
    fun `injects copies with parent values to subprojects`() {
        val parentHolder = ProjectBuilder.builder().build()
        val parentRegistry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
            }
        val parentExt = ExtensionRegistrar.createExtension(parentHolder as ExtensionAware, parentRegistry)
        parentExt.extensions
            .getByType(TestSubExtension::class.java)
            .enabled
            .set(true)

        val root = ProjectBuilder.builder().withName("root").build()
        val sub =
            ProjectBuilder
                .builder()
                .withName("sub")
                .withParent(root)
                .build()
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
            }

        val rootExt = ExtensionRegistrar.createExtensionWithSubprojects(root, registry, parentHolder as ExtensionAware)
        val subEasy = sub.extensions.getByName(EasyExtension.name) as ExtensionAware
        val subSub = subEasy.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly
                .assertThat(
                    rootExt.extensions
                        .getByType(TestSubExtension::class.java)
                        .enabled
                        .get(),
                ).isTrue()
            softly.assertThat(subSub.enabled.get()).isTrue()
        }
    }

    @Test
    fun `does not overwrite existing extension in subprojects`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val sub =
            ProjectBuilder
                .builder()
                .withName("sub")
                .withParent(root)
                .build()
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
            }

        val subRegistry = SimplePluginRegistry().apply { registerExtension(TestSubExtension::class) }
        val existing = ExtensionRegistrar.createExtensionWithSubprojects(sub, subRegistry)
        existing.extensions
            .getByType(TestSubExtension::class.java)
            .enabled
            .set(false)

        val rootExt = ExtensionRegistrar.createExtensionWithSubprojects(root, registry)
        rootExt.extensions
            .getByType(TestSubExtension::class.java)
            .enabled
            .set(true)

        val subSub =
            (sub.extensions.getByName(EasyExtension.name) as ExtensionAware)
                .extensions
                .getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(subSub.enabled.get()).isFalse()
        }
    }

    @Test
    fun `does not inject to subprojects when called on non-root Project`() {
        val registry =
            SimplePluginRegistry().apply {
                registerExtension(TestSubExtension::class)
            }

        val freshRoot = ProjectBuilder.builder().withName("freshRoot").build()
        val freshSub1 =
            ProjectBuilder
                .builder()
                .withName("freshSub1")
                .withParent(freshRoot)
                .build()
        val freshSub2 =
            ProjectBuilder
                .builder()
                .withName("freshSub2")
                .withParent(freshRoot)
                .build()

        ExtensionRegistrar.createExtensionWithSubprojects(freshSub1, registry)

        assertSoftly { softly ->
            softly.assertThat(freshSub1.extensions.findByName(EasyExtension.name)).isNotNull
            softly.assertThat(freshRoot.extensions.findByName(EasyExtension.name)).isNull()
            softly.assertThat(freshSub2.extensions.findByName(EasyExtension.name)).isNull()
        }
    }
}
