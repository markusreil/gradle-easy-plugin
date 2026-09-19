package com.mreil.easy.jvm.kotlin

import com.mreil.easy.jvm.DokkaJavadoc
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

/**
 * Rewires a plugin-created `javadocJar` to Dokka's publication Javadoc output.
 *
 * Only Kotlin-only projects are supported. For mixed Java+Kotlin sources the stock `javadoc`
 * output is added before Dokka's, so [DuplicatesStrategy.EXCLUDE] makes the layout deterministic
 * (the stock file wins on collision) instead of leaving it undefined; mixed-source javadoc jars
 * are still unsupported.
 */
internal object DokkaJavadocWiring {
    fun configureJavadocJar(project: Project) {
        val dokkaJavadoc = project.tasks.named(DokkaJavadoc.PUBLICATION_JAVADOC_TASK)
        project.tasks.withType(Jar::class.java).matching { it.name == "javadocJar" }.configureEach {
            it.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            it.from(dokkaJavadoc)
        }
    }
}
