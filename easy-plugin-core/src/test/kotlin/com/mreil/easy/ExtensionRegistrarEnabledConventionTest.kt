package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetSystemProperty
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.reflect.KClass

class ExtensionRegistrarEnabledConventionTest {
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

    abstract class TestMissingConventionExtension :
        EasyPluginExtension,
        CanBeEnabled {
        companion object : Named {
            override val name: String = "missing"
        }
    }

    private fun registry(vararg extensions: KClass<out EasyPluginExtension>): PluginRegistry =
        mock(PluginRegistry::class.java).apply {
            `when`(getRegisteredExtensions()).thenReturn(extensions.toSet())
        }

    @Test
    fun `enabled convention defaults to true without system property`() {
        val project = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class)

        val ext = ExtensionRegistrar(project, project.providers).createExtension(registry)
        val sub = ext.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(sub.enabled.get()).isTrue()
            softly.assertThat(sub.isEnabled()).isTrue()
        }
    }

    @Test
    @SetSystemProperty(key = "easy.disableAllPlugins", value = "true")
    fun `enabled convention is false when disableAllPlugins is true`() {
        val project = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class)

        val ext = ExtensionRegistrar(project, project.providers).createExtension(registry)
        val sub = ext.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(sub.enabled.get()).isFalse()
            softly.assertThat(sub.isEnabled()).isFalse()
        }
    }

    @Test
    @SetSystemProperty(key = "easy.disableAllPlugins", value = "true")
    fun `explicit enabled set true overrides convention false`() {
        val project = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class)

        val ext = ExtensionRegistrar(project, project.providers).createExtension(registry)
        val sub = ext.extensions.getByType(TestSubExtension::class.java)
        // late override, as done in build script easy { sub { enabled.set(true) } }
        sub.enabled.set(true)

        assertSoftly { softly ->
            softly.assertThat(sub.enabled.get()).isTrue()
            softly.assertThat(sub.isEnabled()).isTrue()
        }
    }

    @Test
    fun `explicit enabled set false overrides convention true`() {
        val project = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class)

        val ext = ExtensionRegistrar(project, project.providers).createExtension(registry)
        val sub = ext.extensions.getByType(TestSubExtension::class.java)
        sub.enabled.set(false)

        assertSoftly { softly ->
            softly.assertThat(sub.enabled.get()).isFalse()
            softly.assertThat(sub.isEnabled()).isFalse()
        }
    }

    @Test
    fun `convention true on parent overrides child convention true via copy`() {
        // Child default would be true, but parent with explicit false should be copied
        val parent = ProjectBuilder.builder().build()
        val child = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class)

        val parentExt = ExtensionRegistrar(parent, parent.providers).createExtension(registry)
        parentExt.extensions
            .getByType(TestSubExtension::class.java)
            .enabled
            .set(false)

        val childExt = ExtensionRegistrar(child, child.providers).createExtension(registry, parent)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(childSub.enabled.get()).isFalse()
        }
    }

    @Test
    @SetSystemProperty(key = "easy.disableAllPlugins", value = "true")
    fun `disableAll convention false is copied to child via parent`() {
        val parent = ProjectBuilder.builder().build()
        val child = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class)

        val parentExt = ExtensionRegistrar(parent, parent.providers).createExtension(registry)
        // parent already has convention false due to system prop

        val childExt = ExtensionRegistrar(child, child.providers).createExtension(registry, parent)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)

        // Even though child would get convention false itself, explicit test of copy path:
        // child inherits disabled state
        assertSoftly { softly ->
            softly
                .assertThat(
                    parentExt.extensions
                        .getByType(TestSubExtension::class.java)
                        .enabled
                        .get(),
                ).isFalse()
            softly.assertThat(childSub.enabled.get()).isFalse()
        }
    }

    @Test
    fun `late set after copy still wins over copied convention`() {
        val parent = ProjectBuilder.builder().build()
        val child = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class)

        val parentExt = ExtensionRegistrar(parent, parent.providers).createExtension(registry)
        parentExt.extensions
            .getByType(TestSubExtension::class.java)
            .enabled
            .set(false)

        val childExt = ExtensionRegistrar(child, child.providers).createExtension(registry, parent)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)
        // late re-enable in child (easy { sub { enabled.set(true) } })
        childSub.enabled.set(true)

        assertSoftly { softly ->
            softly.assertThat(childSub.enabled.get()).isTrue()
        }
    }

    @Test
    fun `fails when extension does not provide convention for enabled`() {
        val project = ProjectBuilder.builder().build()
        val registry = registry(TestMissingConventionExtension::class)

        org.assertj.core.api.Assertions
            .assertThatThrownBy {
                ExtensionRegistrar(project, project.providers).createExtension(registry)
            }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("must provide a convention for 'enabled'")
            .hasMessageContaining("missing")
    }

    @Test
    @SetSystemProperty(key = "easy.disableAllPlugins", value = "true")
    fun `fails even when disableAllPlugins is true if convention missing`() {
        val project = ProjectBuilder.builder().build()
        val registry = registry(TestMissingConventionExtension::class)

        org.assertj.core.api.Assertions
            .assertThatThrownBy {
                ExtensionRegistrar(project, project.providers).createExtension(registry)
            }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("must provide a convention for 'enabled'")
            .hasMessageContaining("missing")
    }
}
