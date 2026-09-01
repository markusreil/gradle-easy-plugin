package com.mreil.easy

import com.mreil.easy.fixtures.DummyPlugin
import com.mreil.easy.fixtures.OtherDummyPlugin
import com.mreil.easy.publish.DefaultEasyPublishExtension
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class PluginRegistryServiceTest {
    private interface DummyExtension : EasyPluginExtension

    private interface OtherDummyExtension : EasyPluginExtension

    private class DummySettingsPlugin : Plugin<Settings> {
        override fun apply(target: Settings) {}
    }

    private class OtherDummySettingsPlugin : Plugin<Settings> {
        override fun apply(target: Settings) {}
    }

    @Test
    fun `ProjectPlugin registers shared service`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.mreil.easy.project")

        val provider =
            project.gradle.sharedServices.registrations
                .getByName(PluginRegistry.NAME)
        val service = provider.service.get() as PluginRegistry
        assertSoftly { softly ->
            softly.assertThat(provider.name).isEqualTo(PluginRegistry.NAME)
            // ServiceLoader auto-discovers EasyPublishPlugin from publish-plugin (impl via @PublicType)
            softly.assertThat(service.getProjectPlugins()).isNotEmpty()
            softly.assertThat(service.getRegisteredExtensions()).contains(DefaultEasyPublishExtension::class)
        }
    }

    @Test
    fun `ProjectPlugin registerIfAbsent is idempotent`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.mreil.easy.project")
        project.plugins.apply("com.mreil.easy.project")

        val count =
            project.gradle.sharedServices.registrations
                .count { it.name == PluginRegistry.NAME }
        assertSoftly { softly -> softly.assertThat(count).isEqualTo(1) }
    }

    @Test
    fun `registerProjectPlugin and getProjectPlugins via shared service`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.mreil.easy.project")

        val service =
            project.gradle.sharedServices.registrations
                .getByName(PluginRegistry.NAME)
                .service
                .get() as PluginRegistry

        assertSoftly { softly ->
            service.registerProjectPlugin(DummyPlugin::class)
            softly.assertThat(service.getProjectPlugins()).contains(DummyPlugin::class)
            service.registerProjectPlugin(OtherDummyPlugin::class)
            softly.assertThat(service.getProjectPlugins()).contains(DummyPlugin::class, OtherDummyPlugin::class)
        }
    }

    @Test
    fun `registerProjectPlugin deduplicates same KClass`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.mreil.easy.project")

        val service =
            project.gradle.sharedServices.registrations
                .getByName(PluginRegistry.NAME)
                .service
                .get() as PluginRegistry

        service.registerProjectPlugin(DummyPlugin::class)
        service.registerProjectPlugin(DummyPlugin::class)

        assertSoftly { softly -> softly.assertThat(service.getProjectPlugins()).contains(DummyPlugin::class) }
    }

    @Test
    fun `getProjectPlugins returns defensive copy`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.mreil.easy.project")

        val service =
            project.gradle.sharedServices.registrations
                .getByName(PluginRegistry.NAME)
                .service
                .get() as PluginRegistry
        service.registerProjectPlugin(DummyPlugin::class)

        val first = service.getProjectPlugins()
        // mutating the returned set must not affect internal state (toSet() guarantees this)
        assertSoftly { softly ->
            softly.assertThat(first).contains(DummyPlugin::class)
            softly.assertThat(service.getProjectPlugins()).contains(DummyPlugin::class)
        }
    }

    @Test
    fun `registerSettingsPlugin and getSettingsPlugins via shared service`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.mreil.easy.project")

        val service =
            project.gradle.sharedServices.registrations
                .getByName(PluginRegistry.NAME)
                .service
                .get() as PluginRegistry

        assertSoftly { softly ->
            service.registerSettingsPlugin(DummySettingsPlugin::class)
            softly.assertThat(service.getSettingsPlugins()).contains(DummySettingsPlugin::class)
            service.registerSettingsPlugin(OtherDummySettingsPlugin::class)
            softly.assertThat(service.getSettingsPlugins()).contains(DummySettingsPlugin::class, OtherDummySettingsPlugin::class)
        }
    }

    @Test
    fun `registerExtension and getRegisteredExtensions via shared service`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.mreil.easy.project")

        val service =
            project.gradle.sharedServices.registrations
                .getByName(PluginRegistry.NAME)
                .service
                .get() as PluginRegistry

        assertSoftly { softly ->
            service.registerExtension(DummyExtension::class)
            softly.assertThat(service.getRegisteredExtensions()).contains(DummyExtension::class)
            service.registerExtension(OtherDummyExtension::class)
            softly.assertThat(service.getRegisteredExtensions()).contains(DummyExtension::class, OtherDummyExtension::class)
        }
    }

    @Test
    fun `registerExtension deduplicates same KClass`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.mreil.easy.project")

        val service =
            project.gradle.sharedServices.registrations
                .getByName(PluginRegistry.NAME)
                .service
                .get() as PluginRegistry

        service.registerExtension(DummyExtension::class)
        service.registerExtension(DummyExtension::class)

        assertSoftly { softly -> softly.assertThat(service.getRegisteredExtensions()).contains(DummyExtension::class) }
    }

    @Test
    fun `getRegisteredExtensions returns defensive copy`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.mreil.easy.project")

        val service =
            project.gradle.sharedServices.registrations
                .getByName(PluginRegistry.NAME)
                .service
                .get() as PluginRegistry
        service.registerExtension(DummyExtension::class)

        val first = service.getRegisteredExtensions()
        assertSoftly { softly ->
            softly.assertThat(first).contains(DummyExtension::class)
            softly.assertThat(service.getRegisteredExtensions()).contains(DummyExtension::class)
        }
    }
}
