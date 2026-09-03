package com.mreil.easy

import com.mreil.easy.test.loadSettingsPluginId
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.PluginManager
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

private val SETTINGS_PLUGIN_ID: String by lazy { loadSettingsPluginId() }

class PluginRegistrarTest {
    class TestProjectPlugin : Plugin<Project> {
        override fun apply(project: Project) {
            project.extensions.extraProperties.set("testProjectPluginApplied", true)
        }
    }

    @ApplyToSubprojects
    class TestSubprojectPlugin : Plugin<Project> {
        override fun apply(project: Project) {
            project.extensions.extraProperties.set("testSubprojectPluginApplied", true)
        }
    }

    class TestSettingsPlugin : Plugin<Settings> {
        override fun apply(settings: Settings) {
            // applied to settings
        }
    }

    class SubprojectContributor : EasyPluginContributor {
        override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(TestSubprojectPlugin::class)
    }

    class NonSubprojectContributor : EasyPluginContributor {
        override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(TestProjectPlugin::class)
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
    fun `applies registered project plugins to project when ProjectPlugin is active`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(ProjectPlugin::class.java)

        val registry =
            SimplePluginRegistry().apply {
                registerProjectPlugin(TestProjectPlugin::class)
            }

        PluginRegistrar.applyPlugins(project, registry)

        assertSoftly { softly ->
            softly.assertThat(project.extensions.extraProperties.has("testProjectPluginApplied")).isTrue()
            softly.assertThat(project.extensions.extraProperties.get("testProjectPluginApplied")).isEqualTo(true)
        }
    }

    @Test
    fun `applies to subprojects when plugin is annotated with ApplyToSubprojects`() {
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
        root.plugins.apply(ProjectPlugin::class.java)

        val service =
            root.gradle.sharedServices
                .registerIfAbsent(
                    PluginRegistry.NAME,
                    PluginRegistryService::class.java,
                ).get()

        val contributor = SubprojectContributor()
        service.registerProjectPlugin(TestSubprojectPlugin::class)
        val field = PluginRegistryService::class.java.getDeclaredField("pluginToContributor")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(service) as MutableMap<KClass<out Plugin<*>>, EasyPluginContributor>)[TestSubprojectPlugin::class] = contributor

        PluginRegistrar.applyPlugins(root, service)

        assertSoftly { softly ->
            softly.assertThat(root.extensions.extraProperties.has("testSubprojectPluginApplied")).isTrue()
            softly.assertThat(sub1.extensions.extraProperties.has("testSubprojectPluginApplied")).isTrue()
            softly.assertThat(sub2.extensions.extraProperties.has("testSubprojectPluginApplied")).isTrue()
        }
    }

    @Test
    fun `does not apply to subprojects when plugin is not annotated with ApplyToSubprojects`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val sub =
            ProjectBuilder
                .builder()
                .withName("sub")
                .withParent(root)
                .build()
        root.plugins.apply(ProjectPlugin::class.java)

        val service =
            root.gradle.sharedServices
                .registerIfAbsent(
                    PluginRegistry.NAME,
                    PluginRegistryService::class.java,
                ).get()

        val contributor = NonSubprojectContributor()
        service.registerProjectPlugin(TestProjectPlugin::class)
        val field = PluginRegistryService::class.java.getDeclaredField("pluginToContributor")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(service) as MutableMap<KClass<out Plugin<*>>, EasyPluginContributor>)[TestProjectPlugin::class] = contributor

        PluginRegistrar.applyPlugins(root, service)

        assertSoftly { softly ->
            softly.assertThat(root.extensions.extraProperties.has("testProjectPluginApplied")).isTrue()
            softly.assertThat(sub.extensions.extraProperties.has("testProjectPluginApplied")).isFalse()
        }
    }

    @Test
    fun `applies registered settings plugins to settings when settings plugin is active`() {
        val appliedPlugins = mutableListOf<Class<*>>()

        val pluginManagerProxy =
            Proxy.newProxyInstance(
                PluginManager::class.java.classLoader,
                arrayOf(PluginManager::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "withPlugin" -> {
                        val id = args[0] as String

                        @Suppress("UNCHECKED_CAST")
                        val action = args[1] as Action<AppliedPlugin>
                        if (id == SETTINGS_PLUGIN_ID) {
                            val appliedPlugin =
                                Proxy.newProxyInstance(
                                    AppliedPlugin::class.java.classLoader,
                                    arrayOf(AppliedPlugin::class.java),
                                ) { _, _, _ -> null } as AppliedPlugin
                            action.execute(appliedPlugin)
                        }
                        null
                    }
                    "apply" -> {
                        val type = args[0] as Class<*>
                        appliedPlugins.add(type)
                        null
                    }
                    else -> null
                }
            } as PluginManager

        val settingsProxy =
            Proxy.newProxyInstance(
                Settings::class.java.classLoader,
                arrayOf(Settings::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getPluginManager" -> pluginManagerProxy
                    else -> null
                }
            } as Settings

        val registry =
            SimplePluginRegistry().apply {
                registerSettingsPlugin(TestSettingsPlugin::class)
            }

        PluginRegistrar.applyPlugins(settingsProxy, registry)

        assertSoftly { softly ->
            softly.assertThat(appliedPlugins).containsExactly(TestSettingsPlugin::class.java)
        }
    }

    @Test
    fun `applies only project plugins to project and only settings plugins to settings when both are registered`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(ProjectPlugin::class.java)

        val appliedSettingsPlugins = mutableListOf<Class<*>>()
        val pluginManagerProxy =
            Proxy.newProxyInstance(
                PluginManager::class.java.classLoader,
                arrayOf(PluginManager::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "withPlugin" -> {
                        val id = args[0] as String

                        @Suppress("UNCHECKED_CAST")
                        val action = args[1] as Action<AppliedPlugin>
                        if (id == SETTINGS_PLUGIN_ID) {
                            val appliedPlugin =
                                Proxy.newProxyInstance(
                                    AppliedPlugin::class.java.classLoader,
                                    arrayOf(AppliedPlugin::class.java),
                                ) { _, _, _ -> null } as AppliedPlugin
                            action.execute(appliedPlugin)
                        }
                        null
                    }
                    "apply" -> {
                        val type = args[0] as Class<*>
                        appliedSettingsPlugins.add(type)
                        null
                    }
                    else -> null
                }
            } as PluginManager

        val settingsProxy =
            Proxy.newProxyInstance(
                Settings::class.java.classLoader,
                arrayOf(Settings::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getPluginManager" -> pluginManagerProxy
                    else -> null
                }
            } as Settings

        val registry =
            SimplePluginRegistry().apply {
                registerProjectPlugin(TestProjectPlugin::class)
                registerSettingsPlugin(TestSettingsPlugin::class)
            }

        PluginRegistrar.applyPlugins(project, registry)
        PluginRegistrar.applyPlugins(settingsProxy, registry)

        assertSoftly { softly ->
            softly.assertThat(project.extensions.extraProperties.has("testProjectPluginApplied")).isTrue()
            softly.assertThat(appliedSettingsPlugins).containsExactly(TestSettingsPlugin::class.java)
        }
    }
}
