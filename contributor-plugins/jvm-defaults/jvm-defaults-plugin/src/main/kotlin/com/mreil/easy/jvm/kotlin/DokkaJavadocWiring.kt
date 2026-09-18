package com.mreil.easy.jvm.kotlin

import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

/**
 * Dynamically applies Dokka's Javadoc plugin to Kotlin projects and rewires the `javadocJar` to
 * Dokka's publication output. Dokka is referenced only by plugin id and marker
 * coordinate (no compile/runtime dependency is added to any module); the marker dependency is added
 * to the **root** project's buildscript classpath before it is evaluated and resolved from the
 * build's existing buildscript repositories, and subprojects inherit that classloader (equivalent
 * to the root `plugins { id(...) apply false }` idiom).
 */
internal object DokkaJavadocWiring {
    /** Dokka Javadoc Gradle plugin id. */
    const val PLUGIN_ID = "org.jetbrains.dokka-javadoc"

    /** Gradle plugin marker coordinates for [PLUGIN_ID]. */
    const val MARKER = "org.jetbrains.dokka-javadoc:org.jetbrains.dokka-javadoc.gradle.plugin"

    /** Task producing the Dokka publication Javadoc output. */
    const val PUBLICATION_JAVADOC_TASK = "dokkaGeneratePublicationJavadoc"

    /**
     * Applies [PLUGIN_ID] once the Kotlin JVM plugin is present and, when [includeClasspath] is
     * true, adds the Dokka marker to [project]'s buildscript classpath first. Called from a
     * settings-level `beforeProject` hook only when `easy.jvmDefaults.dokkaJavadoc(...)` opted in.
     *
     * The marker is resolved from the build's existing buildscript repositories (the build must
     * declare e.g. `gradlePluginPortal()`/`mavenCentral()` in its root `buildscript { }`). Providing
     * sensible defaults is a follow-up. Only the root project passes `includeClasspath = true`;
     * subprojects inherit the root buildscript classloader, so they still register (and get) the
     * KGP-gated apply without a per-project classpath entry.
     */
    @Suppress("ForbiddenComment")
    fun inject(
        project: Project,
        version: String,
        includeClasspath: Boolean,
    ) {
        if (includeClasspath) {
            // TODO: provide sensible default buildscript repositories (or reuse the build's
            //  pluginManagement repositories) as a separate default-plugin step; for now the build
            //  must declare them itself.
            project.buildscript.dependencies.add("classpath", "$MARKER:$version")
        }
        project.pluginManager.withPlugin(EasyJvmDefaultsKotlinPlugin.KOTLIN_JVM_PLUGIN) {
            project.pluginManager.apply(PLUGIN_ID)
        }
    }

    /**
     * Lazily adds Dokka's publication Javadoc output to the `javadocJar` task.
     *
     * Only Kotlin-only projects are supported. For mixed Java+Kotlin sources the stock `javadoc`
     * output is added before Dokka's, so [DuplicatesStrategy.EXCLUDE] makes the layout deterministic
     * (the stock file wins on collision) instead of leaving it undefined; mixed-source javadoc jars
     * are still unsupported.
     */
    fun configureJavadocJar(project: Project) {
        val dokkaJavadoc = project.tasks.named(PUBLICATION_JAVADOC_TASK)
        project.tasks.withType(Jar::class.java).matching { it.name == "javadocJar" }.configureEach {
            it.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            it.from(dokkaJavadoc)
        }
    }
}
