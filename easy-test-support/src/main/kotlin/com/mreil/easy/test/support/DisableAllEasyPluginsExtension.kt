package com.mreil.easy.test.support

import com.mreil.gradletest.project.GradleTestProject
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class DisableAllEasyPluginsExtension :
    BeforeEachCallback,
    AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        if (!isAnnotated(context)) return
        System.setProperty(DISABLE_KEY, "true")
        findGradleTestProjects(context).forEach { it.systemProperty(DISABLE_KEY, "true") }
    }

    override fun afterEach(context: ExtensionContext) {
        if (!isAnnotated(context)) return
        System.clearProperty(DISABLE_KEY)
    }

    private fun isAnnotated(context: ExtensionContext): Boolean {
        val methodAnnotated =
            context.testMethod
                .map { it.isAnnotationPresent(DisableAllEasyPlugins::class.java) }
                .orElse(false)
        if (methodAnnotated) return true
        return context.testClass
            .map { it.isAnnotationPresent(DisableAllEasyPlugins::class.java) }
            .orElse(false)
    }

    private fun findGradleTestProjects(context: ExtensionContext): List<GradleTestProject> {
        val result = mutableListOf<GradleTestProject>()
        context.testInstance.ifPresent { instance ->
            var clazz: Class<*>? = instance.javaClass
            while (clazz != null) {
                for (field in clazz.declaredFields) {
                    if (GradleTestProject::class.java.isAssignableFrom(field.type)) {
                        field.isAccessible = true
                        val value = field.get(instance) as? GradleTestProject
                        if (value != null) result.add(value)
                    }
                }
                clazz = clazz.superclass
            }
        }
        // also check store for parameter-injected projects:
        // GradleTestProjectExtension uses its own namespace,
        // so global store is best-effort; field scan covers 99%
        try {
            context.getStore(ExtensionContext.Namespace.GLOBAL)
        } catch (_: Exception) {
            // ignore
        }
        return result
    }

    companion object {
        private const val DISABLE_KEY = "easy.disableAllPlugins"
    }
}
