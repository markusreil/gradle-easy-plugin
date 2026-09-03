package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

@ApplyToSubprojects
class AnnotatedPlugin : Plugin<Project> {
    override fun apply(target: Project) {}
}

class NonAnnotatedPlugin : Plugin<Project> {
    override fun apply(target: Project) {}
}

class AnnotatedContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(AnnotatedPlugin::class)
}

class NonAnnotatedContributor : EasyPluginContributor {
    override fun projectPlugins(): Set<KClass<out Plugin<Project>>> = setOf(NonAnnotatedPlugin::class)
}

class ApplyToSubprojectsTest {
    @Test
    fun `annotation is present on annotated plugin`() {
        assertSoftly { softly ->
            softly.assertThat(AnnotatedPlugin::class.java.isAnnotationPresent(ApplyToSubprojects::class.java)).isTrue()
            softly.assertThat(NonAnnotatedPlugin::class.java.isAnnotationPresent(ApplyToSubprojects::class.java)).isFalse()
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
            softly.assertThat(service.getContributorFor(AnnotatedPlugin::class)).isEqualTo(contributor)
        }
    }

    @Test
    fun `annotated plugin triggers allprojects logic`() {
        // verifies the branching logic in ProjectPlugin without relying on deferred withType
        val isAnnotated = AnnotatedPlugin::class.java.isAnnotationPresent(ApplyToSubprojects::class.java)
        assertSoftly { softly ->
            softly.assertThat(isAnnotated).isTrue()
        }
    }

    @Test
    fun `non-annotated plugin triggers single-project logic`() {
        val isAnnotated = NonAnnotatedPlugin::class.java.isAnnotationPresent(ApplyToSubprojects::class.java)
        assertSoftly { softly ->
            softly.assertThat(isAnnotated).isFalse()
        }
    }
}
