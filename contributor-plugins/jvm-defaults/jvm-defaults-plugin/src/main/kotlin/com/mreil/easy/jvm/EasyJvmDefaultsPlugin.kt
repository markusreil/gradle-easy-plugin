package com.mreil.easy.jvm

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.ApplyToSubprojects
import com.mreil.easy.EnabledBy
import com.mreil.easy.jvm.java.TargetCompatibilityWiring
import com.mreil.easy.jvm.java.ToolchainWiring
import com.mreil.easy.jvm.kotlin.DokkaJavadocWiring
import com.mreil.easy.notifyRedundantConfig
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

/** JVM defaults plugin contributed via [EasyJvmDefaultsContributor]. */
@ApplyToSubprojects
@EnabledBy(EasyJvmDefaultsExtension::class)
class EasyJvmDefaultsPlugin : AbstractEasyProjectPlugin() {
    override fun afterEnabled(target: Project) {
        ReportAggregationWiring.configureRootAggregation(target)
        target.pluginManager.withPlugin("java") {
            ensureJarTask(target, "sourcesJar", "withSourcesJar()", JavaPluginExtension::withSourcesJar)
            val javadocJarCreated =
                ensureJarTask(target, "javadocJar", "withJavadocJar()", JavaPluginExtension::withJavadocJar)
            if (javadocJarCreated) {
                // Only rewire a javadocJar the plugin created; a manually configured one is left
                // untouched (see ensureJarTask's migration hint).
                target.pluginManager.withPlugin(DokkaJavadoc.PLUGIN_ID) {
                    DokkaJavadocWiring.configureJavadocJar(target)
                }
            }
            ToolchainWiring.configure(target)
            TargetCompatibilityWiring.configure(target)
            TestSuiteWiring.configure(target)
            JacocoWiring.configure(target)
        }
    }

    private fun ensureJarTask(
        target: Project,
        taskName: String,
        configSnippet: String,
        enable: JavaPluginExtension.() -> Unit,
    ): Boolean {
        if (target.tasks.findByName(taskName) == null) {
            target.extensions.getByType(JavaPluginExtension::class.java).enable()
            return true
        }
        target.notifyRedundantConfig(taskName, configSnippet)
        return false
    }
}
