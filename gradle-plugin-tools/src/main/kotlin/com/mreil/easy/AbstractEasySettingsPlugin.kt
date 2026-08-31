package com.mreil.easy

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ExtensionAware
import kotlin.reflect.KClass

/**
 * Base for [Settings] plugins that want eager [init] and lazy [afterEnabled] handling.
 *
 * [init] is called eagerly during [apply]. [afterEnabled] is deferred via [Settings.gradle]
 * `settingsEvaluated` so `easy { ... }` in `settings.gradle.kts` is respected.
 */
abstract class AbstractEasySettingsPlugin :
    Plugin<Settings>,
    PluginLifecycle<Settings> {
    final override fun apply(target: Settings) {
        init(target)
        val enabledBy = this::class.java.getAnnotation(EnabledBy::class.java)
        if (enabledBy == null) {
            afterEnabled(target)
            return
        }
        val extensionClass = enabledBy.value
        try {
            target.gradle.settingsEvaluated {
                if (isExtensionEnabled(target, extensionClass)) {
                    afterEnabled(target)
                }
            }
        } catch (_: Exception) {
            if (isExtensionEnabled(target, extensionClass)) {
                afterEnabled(target)
            }
        }
    }

    override fun init(target: Settings) = Unit

    override fun afterEnabled(target: Settings) = Unit

    @Suppress("ReturnCount")
    private fun isExtensionEnabled(
        target: Settings,
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
