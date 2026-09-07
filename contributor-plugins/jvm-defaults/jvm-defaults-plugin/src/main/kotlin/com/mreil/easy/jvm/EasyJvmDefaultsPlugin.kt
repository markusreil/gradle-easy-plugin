package com.mreil.easy.jvm

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.ApplyToSubprojects
import com.mreil.easy.notifyRedundantConfig
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

/** JVM defaults plugin contributed via [EasyJvmDefaultsContributor]. */
@ApplyToSubprojects
class EasyJvmDefaultsPlugin : AbstractEasyProjectPlugin() {
    override fun afterEnabled(target: Project) {
        target.pluginManager.withPlugin("java") {
            ensureJarTask(target, "sourcesJar", "withSourcesJar()", JavaPluginExtension::withSourcesJar)
            ensureJarTask(target, "javadocJar", "withJavadocJar()", JavaPluginExtension::withJavadocJar)
        }
    }

    private fun ensureJarTask(
        target: Project,
        taskName: String,
        configSnippet: String,
        enable: JavaPluginExtension.() -> Unit,
    ) {
        if (target.tasks.findByName(taskName) == null) {
            target.extensions.getByType(JavaPluginExtension::class.java).enable()
        } else {
            target.notifyRedundantConfig(taskName, configSnippet)
        }
    }
}
