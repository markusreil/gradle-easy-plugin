package com.mreil.easy.jvm

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.ApplyToSubprojects
import com.mreil.easy.EnabledBy
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
            ensureJarTask(target, "javadocJar", "withJavadocJar()", JavaPluginExtension::withJavadocJar)
            ToolchainWiring.configure(target)
            TestSuiteWiring.configure(target)
            JacocoWiring.configure(target)
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
