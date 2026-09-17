package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
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

    private fun registry(vararg extensions: KClass<out EasyPluginExtension>): PluginRegistry =
        mock(PluginRegistry::class.java).apply {
            `when`(getRegisteredExtensions()).thenReturn(extensions.toSet())
        }

    @Test
    fun `copies values when parent ExtensionAware is provided`() {
        val parentProject = ProjectBuilder.builder().build()
        val childProject = ProjectBuilder.builder().build()

        val registry = registry(TestSubExtension::class)

        val parentExt = ExtensionRegistrar(parentProject, parentProject.providers).createExtension(registry)
        val parentSub = parentExt.extensions.getByType(TestSubExtension::class.java)
        parentSub.enabled.set(true)

        val childExt = ExtensionRegistrar(childProject, childProject.providers).createExtension(registry, parentProject)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(childSub.enabled.get()).isTrue()
        }
    }

    @Test
    fun `copies values when parent EasyExtension directly is provided`() {
        val parentProject = ProjectBuilder.builder().build()
        val childProject = ProjectBuilder.builder().build()

        val registry = registry(TestSubExtension::class)

        val parentExt = ExtensionRegistrar(parentProject, parentProject.providers).createExtension(registry)
        val parentSub = parentExt.extensions.getByType(TestSubExtension::class.java)
        parentSub.enabled.set(true)

        val childExt = ExtensionRegistrar(childProject, childProject.providers).createExtension(registry, parentExt)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(childSub.enabled.get()).isTrue()
        }
    }

    @Test
    fun `creates EasyExtension on ExtensionAware with parent ExtensionAware copies values`() {
        val parentProject = ProjectBuilder.builder().build()
        val childHolder = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class)

        val parentExt = ExtensionRegistrar(parentProject, parentProject.providers).createExtension(registry)
        val parentSub = parentExt.extensions.getByType(TestSubExtension::class.java)
        parentSub.enabled.set(true)

        val childExt = ExtensionRegistrar(childHolder, childHolder.providers).createExtension(registry, parentProject)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(childSub.enabled.get()).isTrue()
        }
    }

    @Test
    fun `creates EasyExtension on ExtensionAware with parent CanBeCopied copies values`() {
        val parentProject = ProjectBuilder.builder().build()
        val childHolder = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class)

        val parentExt = ExtensionRegistrar(parentProject, parentProject.providers).createExtension(registry)
        val parentSub = parentExt.extensions.getByType(TestSubExtension::class.java)
        parentSub.enabled.set(true)

        val childExt = ExtensionRegistrar(childHolder, childHolder.providers).createExtension(registry, parentExt)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(childSub.enabled.get()).isTrue()
        }
    }

    @Test
    fun `parent convention false is copied to child overriding child default true`() {
        val parentProject = ProjectBuilder.builder().build()
        val childProject = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class)

        val parentExt = ExtensionRegistrar(parentProject, parentProject.providers).createExtension(registry)
        parentExt.extensions
            .getByType(TestSubExtension::class.java)
            .enabled
            .set(false)

        val childExt = ExtensionRegistrar(childProject, childProject.providers).createExtension(registry, parentProject)
        val childSub = childExt.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(childSub.enabled.get()).isFalse()
        }
    }

    @Test
    fun `does not copy when parent is null but defaults to true`() {
        val project = ProjectBuilder.builder().build()
        val registry = registry(TestSubExtension::class)

        val ext = ExtensionRegistrar(project, project.providers).createExtension(registry, parent = null)
        val sub = ext.extensions.getByType(TestSubExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(sub.enabled.get()).isTrue()
        }
    }
}
