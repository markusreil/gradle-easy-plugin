package com.mreil.easy.jvm.kotlin

import com.mreil.easy.easyInfo
import com.mreil.easy.jvm.java.TargetCompatibilityWiring
import com.mreil.utils.PropertyResolver
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Pins Kotlin's `jvmTarget` and `-Xjdk-release` to the version declared via
 * [TargetCompatibilityWiring.PROPERTY_NAME], so the Kotlin bytecode target cannot silently drift
 * with a Kotlin/KGP upgrade and the Java/Kotlin target validation stays satisfied.
 *
 * The plugin is loaded by the settings classloader while KGP is loaded by the project buildscript
 * classloader (a child), so this file must not link against KGP types in bytecode. It therefore
 * accesses KGP reflectively through the `kotlin` extension's own classloader. No KGP API dependency
 * is shipped; KGP >= 1.8 (which exposes `getCompilerOptions()`/`jvmTarget`/`freeCompilerArgs`) is
 * supported, and a clear [GradleException] is raised otherwise.
 */
internal object KotlinTargetWiring {
    private const val KOTLIN_EXTENSION_NAME = "kotlin"
    private const val JVM_EXTENSION_CLASS = "org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension"
    private const val JVM_OPTIONS_CLASS = "org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions"
    private const val JVM_TARGET_CLASS = "org.jetbrains.kotlin.gradle.dsl.JvmTarget"

    internal fun configure(target: Project) {
        val resolver = PropertyResolver(target.providers)
        val version = resolver.get(TargetCompatibilityWiring.PROPERTY_NAME).orNull?.trim()
        if (version == null) {
            target.easyInfo(
                "No Java target version declared via '{}' - leaving the Kotlin jvmTarget in place.",
                TargetCompatibilityWiring.PROPERTY_NAME,
            )
            return
        }
        val kotlinExtension = target.extensions.findByName(KOTLIN_EXTENSION_NAME)
        if (kotlinExtension == null) {
            target.easyInfo("No Kotlin extension present - leaving the Kotlin jvmTarget in place.")
            return
        }
        val javaVersion = JavaVersion.toVersion(version)
        try {
            applyTarget(kotlinExtension, javaVersion)
        } catch (e: ReflectiveOperationException) {
            throw unsupportedKotlinPlugin(version, e)
        } catch (e: IllegalArgumentException) {
            throw unsupportedKotlinPlugin(version, e)
        }
        target.easyInfo(
            "Kotlin jvmTarget pinned to {} (-Xjdk-release={})",
            javaVersion.toString(),
            javaVersion.majorVersion,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyTarget(
        kotlinExtension: Any,
        javaVersion: JavaVersion,
    ) {
        val loader = kotlinExtension.javaClass.classLoader
        val extensionClass = Class.forName(JVM_EXTENSION_CLASS, false, loader)
        val options = extensionClass.getMethod("getCompilerOptions").invoke(kotlinExtension)
        val optionsClass = Class.forName(JVM_OPTIONS_CLASS, false, loader)
        val targetClass = Class.forName(JVM_TARGET_CLASS, false, loader)
        val jvmTarget =
            targetClass
                .getMethod("fromTarget", String::class.java)
                .invoke(null, javaVersion.toString())
        (optionsClass.getMethod("getJvmTarget").invoke(options) as Property<Any>).set(jvmTarget)
        val freeCompilerArgs = optionsClass.getMethod("getFreeCompilerArgs").invoke(options) as ListProperty<String>
        val releaseArg = "-Xjdk-release=${javaVersion.majorVersion}"
        if (!freeCompilerArgs.get().contains(releaseArg)) {
            freeCompilerArgs.add(releaseArg)
        }
    }

    private fun unsupportedKotlinPlugin(
        version: String,
        cause: Throwable,
    ): GradleException =
        GradleException(
            "Failed to apply '${TargetCompatibilityWiring.PROPERTY_NAME}=$version' to Kotlin via " +
                "'$JVM_EXTENSION_CLASS'. The Kotlin Gradle plugin (KGP >= 1.8) is required and must expose " +
                "getCompilerOptions()/getJvmTarget()/getFreeCompilerArgs().",
            cause,
        )
}
