package com.mreil.easy

import com.mreil.utils.PropertyResolver
import org.gradle.api.Plugin
import org.gradle.api.Project

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
    protected lateinit var propertyResolver: PropertyResolver
        private set

    final override fun apply(target: Project) {
        propertyResolver = PropertyResolver(target.providers)
        init(target)
        val enabledBy = this::class.java.getAnnotation(EnabledBy::class.java)
        if (enabledBy == null) {
            afterEnabled(target)
            return
        }
        val extensionClass = enabledBy.value
        target.afterEvaluate { evaluated ->
            if (evaluated.isExtensionEnabled(extensionClass)) {
                afterEnabled(evaluated)
            }
        }
    }

    override fun init(target: Project) = Unit

    override fun afterEnabled(target: Project) = Unit
}
