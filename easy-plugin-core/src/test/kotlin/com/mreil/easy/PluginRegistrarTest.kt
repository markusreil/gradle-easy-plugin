package com.mreil.easy

import com.mreil.easy.test.support.loadSettingsPluginId
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.PluginManager
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
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

    private fun registry(
        projectPlugins: Set<KClass<out Plugin<Project>>> = emptySet(),
        settingsPlugins: Set<KClass<out Plugin<Settings>>> = emptySet(),
    ): PluginRegistry =
        mock(PluginRegistry::class.java).apply {
            `when`(getProjectPlugins()).thenReturn(projectPlugins)
            `when`(getSettingsPlugins()).thenReturn(settingsPlugins)
            `when`(getRegisteredExtensions()).thenReturn(emptySet())
        }

    private fun settingsWithPluginManager(appliedPlugins: MutableList<Class<*>>): Settings {
        val pluginManagerMock = mock(PluginManager::class.java)
        val appliedPlugin = mock(AppliedPlugin::class.java)
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val action = invocation.getArgument<Action<AppliedPlugin>>(1)
            action.execute(appliedPlugin)
            null
        }.`when`(pluginManagerMock)
            .withPlugin(eq(SETTINGS_PLUGIN_ID), any())
        doAnswer { appliedPlugins.add(it.getArgument(0)) }
            .`when`(pluginManagerMock)
            .apply(any<Class<Plugin<*>>>())
        val settings = mock(Settings::class.java)
        `when`(settings.pluginManager).thenReturn(pluginManagerMock)
        return settings
    }

    @Test
    fun `applies registered project plugins to project when ProjectPluginEntryPoint is active`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(ProjectPluginEntryPoint::class.java)

        val registry = registry(projectPlugins = setOf(TestProjectPlugin::class))

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
        root.plugins.apply(ProjectPluginEntryPoint::class.java)

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
        root.plugins.apply(ProjectPluginEntryPoint::class.java)

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

        val settingsProxy = settingsWithPluginManager(appliedPlugins)

        val registry = registry(settingsPlugins = setOf(TestSettingsPlugin::class))

        PluginRegistrar.applyPlugins(settingsProxy, registry)

        assertSoftly { softly ->
            softly.assertThat(appliedPlugins).containsExactly(TestSettingsPlugin::class.java)
        }
    }

    @Test
    fun `applies only project plugins to project and only settings plugins to settings when both are registered`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(ProjectPluginEntryPoint::class.java)

        val appliedSettingsPlugins = mutableListOf<Class<*>>()
        val settingsProxy = settingsWithPluginManager(appliedSettingsPlugins)

        val registry = registry(projectPlugins = setOf(TestProjectPlugin::class), settingsPlugins = setOf(TestSettingsPlugin::class))

        PluginRegistrar.applyPlugins(project, registry)
        PluginRegistrar.applyPlugins(settingsProxy, registry)

        assertSoftly { softly ->
            softly.assertThat(project.extensions.extraProperties.has("testProjectPluginApplied")).isTrue()
            softly.assertThat(appliedSettingsPlugins).containsExactly(TestSettingsPlugin::class.java)
        }
    }
}
