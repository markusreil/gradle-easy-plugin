/*
 * Settings-scope marker module: registers only `com.mreil.easy.settings` and bundles the
 * settings-scope contributors. The project scope lives in `:easy-plugin`; the two modules never
 * reference each other, so each consumer classloader sees only its own scope (plus the shared core).
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import java.util.zip.ZipFile

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.shadow)
    alias(libs.plugins.plugin.publish)
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":easy-plugin-core"))
    implementation(project(":easy-contributor-api"))
    // auto-collect all settings-scope contributor runtime plugins (APIs are pulled transitively)
    providers
        .provider {
            rootProject.subprojects
                .map { it.path }
                .filter { it.startsWith(":contributor-plugins:") }
                .filter { it.endsWith("-settings-plugin") }
                .filterNot { it.endsWith("-test-plugin") }
                .sorted()
        }.get()
        .forEach { implementation(project(it)) }

    // Third-party runtime dependencies stay ordinary Maven Central dependencies of the published
    // artifact (see `easy-plugin` for the full rationale). kotlin-stdlib is declared explicitly
    // here because, unlike `easy-plugin`, this module has no kotlinx-serialization transitively.
    shadow(libs.commons.configuration2)
    shadow(libs.kotlin.stdlib)
}

gradlePlugin {
    website.set("https://github.com/markusreil/gradle-easy-plugin")
    vcsUrl.set("https://github.com/markusreil/gradle-easy-plugin")

    // The concrete entry point lives in this module, extending the shared base in :easy-plugin-core,
    // so the settings classloader can resolve settings-scope contributors.
    plugins.create("easySettings") {
        id = providers.gradleProperty("plugin.settings").get()
        implementationClass = "com.mreil.easy.SettingsPlugin"
        displayName = "Easy Settings Plugin"
        description =
            "A Gradle plugin framework that simplifies plugin development with modular contributors and convention-based configuration"
        tags.set(listOf("kotlin", "conventions", "plugin-development", "modular"))
    }
}

val internalGroup = project.group.toString()

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""

    // Bundle only modules produced by this build (shared core + settings-scope contributors).
    dependencies {
        include { it.moduleGroup == internalGroup }
    }

    val transformedPaths = listOf("META-INF/services/**", "META-INF/*.kotlin_module")
    filesMatching(transformedPaths) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    inputs.property("transformedPathsDuplicatesStrategy", "$transformedPaths=INCLUDE")

    mergeServiceFiles()
}

// Guards the packaging contract: only in-build modules are bundled, every third-party runtime
// dependency is published, and no project-scope class leaks into the settings jar.
val verifyShadowPackaging =
    tasks.register("verifyShadowPackaging") {
        group = "verification"
        description =
            "Verifies the easy-plugin-settings fat jar bundles only in-build settings-scope modules, " +
            "publishes all third-party runtime dependencies and contains no project-scope classes."

        val runtimeArtifacts =
            configurations.named("runtimeClasspath").flatMap { it.incoming.artifacts.resolvedArtifacts }
        val publishedArtifacts =
            configurations.named("shadow").flatMap { it.incoming.artifacts.resolvedArtifacts }
        val shadowJarFile = tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }

        inputs.file(shadowJarFile).withPropertyName("shadowJar")
        inputs.files(configurations.named("runtimeClasspath")).withPropertyName("runtimeClasspath")
        inputs.files(configurations.named("shadow")).withPropertyName("publishedDependencies")

        doLast {
            fun moduleId(artifact: ResolvedArtifactResult): String? =
                (artifact.id.componentIdentifier as? ModuleComponentIdentifier)
                    ?.let { "${it.group}:${it.module}" }

            val runtimeModules = runtimeArtifacts.get().mapNotNull(::moduleId).toSet()
            val publishedModules = publishedArtifacts.get().mapNotNull(::moduleId).toSet()
            check((runtimeModules - publishedModules).isEmpty()) {
                "Third-party runtime dependencies are missing from the published artifact " +
                    "(declare them on the `shadow` configuration): ${runtimeModules - publishedModules}"
            }

            val entries =
                ZipFile(shadowJarFile.get().asFile).use { zip ->
                    zip
                        .entries()
                        .asSequence()
                        .map { it.name }
                        .toList()
                }
            val testOnlyPrefixes =
                listOf(
                    "com/mreil/gradletest/",
                    "com/mreil/easy/fixtures/",
                    "com/mreil/easy/test/support/",
                )
            val offending =
                entries.filter { name ->
                    name.endsWith(".class") &&
                        (
                            !name.startsWith("com/mreil/") ||
                                testOnlyPrefixes.any { name.startsWith(it) } ||
                                name.contains("TestHarnessPlugin")
                        )
                }
            check(offending.isEmpty()) {
                "The easy-plugin-settings fat jar must bundle only modules built by this build: $offending"
            }

            // D3/D4: shared core is allowed; project-scope plugin/wiring classes are not.
            val projectOnly =
                listOf(
                    "com/mreil/easy/ProjectPlugin.class",
                    "com/mreil/easy/jvm/DefaultEasyJvmDefaultsExtension.class",
                    "com/mreil/easy/jvm/EasyJvmDefaultsContributor.class",
                    "com/mreil/easy/jvm/EasyJvmDefaultsPlugin.class",
                    "com/mreil/easy/jvm/EasyJvmDefaultsKotlinPlugin.class",
                    "com/mreil/easy/jvm/kotlin/DokkaJavadocWiring.class",
                    "com/mreil/easy/jvm/kotlin/KotlinTargetWiring.class",
                    "com/mreil/easy/publish/EasyPublishContributor.class",
                    "com/mreil/easy/semver/EasySemverContributor.class",
                    "com/mreil/easy/codemeta/EasyCodemetaContributor.class",
                    "com/mreil/easy/vcs/EasyVcsContributor.class",
                    "com/mreil/easy/release/EasyReleaseContributor.class",
                    "com/mreil/easy/projectdefaults/EasyProjectDefaultsContributor.class",
                )
            val leaked = entries.filter { it in projectOnly }
            check(leaked.isEmpty()) {
                "The easy-plugin-settings fat jar must not contain project-scope classes: $leaked"
            }
        }
    }

tasks.named<Task>("check") {
    dependsOn(verifyShadowPackaging)
}
