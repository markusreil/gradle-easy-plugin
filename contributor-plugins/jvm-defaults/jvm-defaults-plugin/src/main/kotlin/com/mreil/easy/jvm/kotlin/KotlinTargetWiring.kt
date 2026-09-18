package com.mreil.easy.jvm.kotlin

import com.mreil.easy.easyInfo
import com.mreil.easy.jvm.java.TargetCompatibilityWiring
import com.mreil.utils.PropertyResolver
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension

/**
 * Pins Kotlin's `jvmTarget` and `-Xjdk-release` to the version declared via
 * [TargetCompatibilityWiring.PROPERTY_NAME], so the Kotlin bytecode target cannot silently drift
 * with a Kotlin/KGP upgrade and the Java/Kotlin target validation stays satisfied. The Kotlin
 * Gradle types are confined to this file, so [EasyJvmDefaultsKotlinPlugin] carries no compile-time
 * dependency on KGP.
 */
internal object KotlinTargetWiring {
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
        val javaVersion = JavaVersion.toVersion(version)
        target.extensions.getByType(KotlinJvmExtension::class.java).compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
            freeCompilerArgs.add("-Xjdk-release=${javaVersion.majorVersion}")
        }
        target.easyInfo(
            "Kotlin jvmTarget pinned to {} (-Xjdk-release={})",
            javaVersion.toString(),
            javaVersion.majorVersion,
        )
    }
}
