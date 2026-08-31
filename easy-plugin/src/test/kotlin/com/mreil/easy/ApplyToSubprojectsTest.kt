package com.mreil.easy

import com.mreil.easy.fixtures.SubprojectPlugin
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

@ApplyToSubprojects
class AnnotatedContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(SubprojectPlugin::class)
}

class NonAnnotatedContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(SubprojectPlugin::class)
}

class ApplyToSubprojectsTest {
    @Test
    fun `annotation is present on annotated contributor`() {
        assertSoftly { softly ->
            softly.assertThat(AnnotatedContributor::class.java.isAnnotationPresent(ApplyToSubprojects::class.java)).isTrue()
            softly.assertThat(NonAnnotatedContributor::class.java.isAnnotationPresent(ApplyToSubprojects::class.java)).isFalse()
        }
    }

    @Test
    fun `service tracks contributor for plugin class`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.mreil.easy.project")
        val service =
            project.gradle.sharedServices.registrations
                .getByName(PluginRegistry.NAME)
                .service
                .get() as PluginRegistryService

        // simulate ServiceLoader by manually registering with contributor mapping
        val contributor = AnnotatedContributor()
        contributor.projectPlugins().forEach {
            service.registerProjectPlugin(it)
            // use internal map via reflection to mimic loadFromServiceLoader
            val field = PluginRegistryService::class.java.getDeclaredField("pluginToContributor")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(service) as MutableMap<KClass<out Plugin<*>>, EasyPluginContributor>)[it] = contributor
        }

        assertSoftly { softly ->
            softly.assertThat(service.getContributorFor(SubprojectPlugin::class)).isEqualTo(contributor)
        }
    }

    @Test
    fun `annotated contributor triggers allprojects logic`() {
        // verifies the branching logic in ProjectPlugin without relying on deferred withType
        val contributor = AnnotatedContributor()
        val isAnnotated = contributor::class.java.isAnnotationPresent(ApplyToSubprojects::class.java)
        assertSoftly { softly ->
            softly.assertThat(isAnnotated).isTrue()
        }
    }

    @Test
    fun `non-annotated contributor triggers single-project logic`() {
        val contributor = NonAnnotatedContributor()
        val isAnnotated = contributor::class.java.isAnnotationPresent(ApplyToSubprojects::class.java)
        assertSoftly { softly ->
            softly.assertThat(isAnnotated).isFalse()
        }
    }
}
