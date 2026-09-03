package com.mreil.easy.test.project

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver

/**
 * Injects a fresh [GradleTestProject] into every `GradleTestProject`-typed field and
 * test-method parameter, and deletes its directory afterwards.
 *
 * Usage: `@ExtendWith(GradleTestProjectExtension::class)` with a `lateinit var project: GradleTestProject`.
 */
class GradleTestProjectExtension :
    BeforeEachCallback,
    AfterEachCallback,
    ParameterResolver {
    override fun beforeEach(context: ExtensionContext) {
        val store = context.getStore(NAMESPACE)
        context.testInstance.ifPresent { instance ->
            val fields =
                instance.javaClass.declaredFields.filter { it.type == GradleTestProject::class.java }
            for (field in fields) {
                field.isAccessible = true
                if (field.get(instance) == null) {
                    val project = GradleTestProject()
                    field.set(instance, project)
                    store.put(fieldKey(field), project)
                }
            }
        }
    }

    override fun afterEach(context: ExtensionContext) {
        val store = context.getStore(NAMESPACE)
        // cleanup field-injected projects
        context.testInstance.ifPresent { instance ->
            val fields =
                instance.javaClass.declaredFields.filter { it.type == GradleTestProject::class.java }
            for (field in fields) {
                val key = fieldKey(field)
                val project = store.remove(key, GradleTestProject::class.java)
                project?.cleanup()
            }
        }
        // cleanup parameter-injected projects
        val paramProjects = store.get(PARAM_KEY, MutableList::class.java) as? MutableList<GradleTestProject>
        paramProjects?.forEach { it.cleanup() }
        store.remove(PARAM_KEY)
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean = parameterContext.parameter.type == GradleTestProject::class.java

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any {
        if (parameterContext.parameter.type != GradleTestProject::class.java) {
            throw ParameterResolutionException("Unsupported parameter type")
        }
        val project = GradleTestProject()
        val store = extensionContext.getStore(NAMESPACE)

        @Suppress("UNCHECKED_CAST")
        val list =
            store.getOrComputeIfAbsent(PARAM_KEY) { mutableListOf<GradleTestProject>() }
                as MutableList<GradleTestProject>
        list.add(project)
        return project
    }

    private fun fieldKey(field: java.lang.reflect.Field): String = "field:${field.name}"

    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(GradleTestProjectExtension::class.java)
        private const val PARAM_KEY = "paramProjects"
    }
}
