package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import kotlin.reflect.KClass

/**
 * Base for [Project] plugins that want eager [init] and lazy [afterEnabled] handling.
 *
 * [init] is called eagerly during [apply] and may register extensions, tasks or apply other plugins.
 * [afterEnabled] is called only when the extension referenced by [EnabledBy] is enabled
 * (or immediately if no [EnabledBy] is present). The check is deferred via [Project.afterEvaluate]
 * so `easy { ... }` configuration is respected without forcing every plugin to handle [CanBeEnabled].
 */
abstract class AbstractEasyProjectPlugin :
    Plugin<Project>,
    PluginLifecycle<Project> {
    final override fun apply(target: Project) {
        init(target)
        val enabledBy = this::class.java.getAnnotation(EnabledBy::class.java)
        if (enabledBy == null) {
            afterEnabled(target)
            return
        }
        val extensionClass = enabledBy.value
        target.afterEvaluate { evaluated ->
            if (isExtensionEnabled(evaluated, extensionClass)) {
                afterEnabled(evaluated)
            }
        }
    }

    override fun init(target: Project) = Unit

    override fun afterEnabled(target: Project) = Unit

    @Suppress("ReturnCount")
    private fun isExtensionEnabled(
        target: Project,
        extensionClass: KClass<out EasyPluginExtension>,
    ): Boolean {
        val easy = target.extensions.findByName(EasyExtension.name) as? ExtensionAware ?: return false
        val extName = Named.extensionName(extensionClass)
        val ext = easy.extensions.findByName(extName) ?: return false
        val holder =
            ext as? CanBeEnabled ?: error(
                "Extension ${extensionClass.qualifiedName} referenced by @EnabledBy must implement CanBeEnabled",
            )
        return holder.isEnabled()
    }
}
