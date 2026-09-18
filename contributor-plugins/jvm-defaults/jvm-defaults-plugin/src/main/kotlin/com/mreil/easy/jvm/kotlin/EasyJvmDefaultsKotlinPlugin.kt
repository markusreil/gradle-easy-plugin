package com.mreil.easy.jvm.kotlin

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.ApplyToSubprojects
import com.mreil.easy.EnabledBy
import com.mreil.easy.jvm.EasyJvmDefaultsExtension
import org.gradle.api.Project

/**
 * Kotlin-side companion of [EasyJvmDefaultsPlugin], isolated so the main plugin carries no
 * compile-time dependency on KGP. It is only applied when the Kotlin JVM plugin is present and
 * pins Kotlin's `jvmTarget`/`-Xjdk-release` to `java.targetVersion`, so a Kotlin upgrade cannot
 * silently change the bytecode target or break the Java/Kotlin target validation.
 */
@ApplyToSubprojects
@EnabledBy(EasyJvmDefaultsExtension::class)
class EasyJvmDefaultsKotlinPlugin : AbstractEasyProjectPlugin() {
    override fun afterEnabled(target: Project) {
        target.pluginManager.withPlugin(KOTLIN_JVM_PLUGIN) {
            KotlinTargetWiring.configure(target)
        }
    }

    companion object {
        /** Plugin id of the Kotlin JVM Gradle plugin. */
        const val KOTLIN_JVM_PLUGIN = "org.jetbrains.kotlin.jvm"
    }
}
