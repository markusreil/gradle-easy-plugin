package com.mreil.easy.jvm

import com.mreil.easy.EasyExtension
import com.mreil.easy.ProjectPlugin
import com.mreil.gradletest.project.evaluate
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.jvm.JvmComponentDependencies
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Provider
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.AggregateTestReport
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.base.TestingExtension
import org.gradle.testing.jacoco.plugins.JacocoCoverageReport
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File
import java.nio.file.Files
import java.util.Optional

class EasyJvmDefaultsPluginTest {
    @Test
    fun `registers jvmDefaults extension enabled by default`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)

        val extension =
            (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyJvmDefaultsExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(extension.enabled.get()).isTrue()
        }
    }

    @Test
    fun `configures sources and javadoc jars when java plugin active`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.evaluate()

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("sourcesJar")).isNotNull()
            softly.assertThat(project.tasks.findByName("javadocJar")).isNotNull()
        }
    }

    @Test
    fun `keeps manually configured sources and javadoc jars`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
        javaExtension.withSourcesJar()
        javaExtension.withJavadocJar()

        project.pluginManager.apply(ProjectPlugin::class.java)
        project.evaluate()

        assertSoftly { softly ->
            softly.assertThat(project.tasks.findByName("sourcesJar")).isNotNull()
            softly.assertThat(project.tasks.findByName("javadocJar")).isNotNull()
        }
    }

    @Test
    fun `does not configure when java plugin absent`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.evaluate()

        assertSoftly { softly ->
            softly.assertThat(project.extensions.findByType(JavaPluginExtension::class.java)).isNull()
        }
    }

    @Test
    fun `pins java toolchain to declared version`() {
        System.setProperty(ToolchainWiring.PROPERTY_NAME, "17")
        try {
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply("java")
            project.pluginManager.apply(ProjectPlugin::class.java)
            project.evaluate()

            val languageVersion =
                project.extensions
                    .getByType(JavaPluginExtension::class.java)
                    .toolchain.languageVersion.orNull

            assertSoftly { softly ->
                softly.assertThat(languageVersion).isEqualTo(JavaLanguageVersion.of(17))
            }
        } finally {
            System.clearProperty(ToolchainWiring.PROPERTY_NAME)
        }
    }

    @Test
    fun `does not pin toolchain when property absent`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.evaluate()

        val languageVersion =
            project.extensions
                .getByType(JavaPluginExtension::class.java)
                .toolchain.languageVersion.orNull

        assertSoftly { softly ->
            softly.assertThat(languageVersion).isNull()
        }
    }

    @Test
    fun `configureTestSuites enabled by default`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ProjectPlugin::class.java)

        val extension =
            (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyJvmDefaultsExtension::class.java)

        assertSoftly { softly ->
            softly.assertThat(extension.configureTestSuites.get()).isTrue()
            softly.assertThat(extension.jacocoEnabled.get()).isTrue()
        }
    }

    @Test
    fun `applies jacoco and wires functional suite execution data into the report`() {
        val project = ProjectBuilder.builder().build()
        project.file("src/functionalTest/kotlin").mkdirs()
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.evaluate()

        val functionalTest = project.tasks.named("functionalTest").get()
        val check = project.tasks.named("check").get()
        val report = project.tasks.named("jacocoTestReport", JacocoReport::class.java).get()

        assertSoftly { softly ->
            softly.assertThat(report.executionData.files).anyMatch { it.name == "functionalTest.exec" }
            softly.assertThat(report.taskDependencies.getDependencies(report)).contains(functionalTest)
            softly.assertThat(check.taskDependencies.getDependencies(check)).contains(report)
        }
    }

    @Test
    fun `adds the project's main output to auto-configured functional suites`() {
        val project = ProjectBuilder.builder().build()
        project.file("src/functionalTest/kotlin").mkdirs()
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.evaluate()

        val mainOutput =
            project.extensions
                .getByType(SourceSetContainer::class.java)
                .getByName("main")
                .output
                .files
        val suiteImplementation =
            project.configurations
                .getByName("functionalTestImplementation")
                .dependencies
                .filterIsInstance<FileCollectionDependency>()
                .flatMap { it.files.toList() }

        assertSoftly { softly ->
            softly.assertThat(mainOutput).isNotEmpty()
            softly.assertThat(suiteImplementation).containsExactlyInAnyOrderElementsOf(mainOutput)
        }
    }

    @Test
    fun `does not apply jacoco when disabled`() {
        val project = ProjectBuilder.builder().build()
        project.file("src/functionalTest/kotlin").mkdirs()
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        jvmDefaultsExtension(project).jacocoEnabled.set(false)
        project.evaluate()

        assertSoftly { softly ->
            softly.assertThat(project.plugins.hasPlugin("jacoco")).isFalse()
            softly.assertThat(project.tasks.findByName("jacocoTestReport")).isNull()
        }
    }

    @Test
    fun `classifies test and star Test directories`() {
        val project = ProjectBuilder.builder().build()
        listOf(
            "src/test/java",
            "src/test/kotlin",
            "src/functionalTest/java",
            "src/integrationTest/kotlin",
            "src/main/java",
            "src/testFixtures/java",
            "src/latest/java",
        ).forEach { project.file(it).mkdirs() }

        val suites = project.findTestSuites().associateBy { it.name }

        assertSoftly { softly ->
            softly.assertThat(suites.keys).containsExactlyInAnyOrder("test", "functionalTest", "integrationTest")
            softly.assertThat(suites.getValue("test").type).isEqualTo(TestSuiteType.UNIT)
            softly.assertThat(suites.getValue("functionalTest").type).isEqualTo(TestSuiteType.FUNCTIONAL)
            softly.assertThat(suites.getValue("integrationTest").type).isEqualTo(TestSuiteType.FUNCTIONAL)
        }
    }

    @Test
    fun `returns no test suites when none exist`() {
        val project = ProjectBuilder.builder().build()

        assertSoftly { softly ->
            softly.assertThat(project.findTestSuites()).isEmpty()
        }
    }

    @Test
    fun `registers functional test suite from source directory`() {
        val project = ProjectBuilder.builder().build()
        project.file("src/functionalTest/kotlin").mkdirs()
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.evaluate()

        val suites = project.extensions.getByType(TestingExtension::class.java).suites

        assertSoftly { softly ->
            softly.assertThat(suites.findByName("functionalTest")).isInstanceOf(JvmTestSuite::class.java)
        }
    }

    @Test
    fun `registers no functional suite when only unit sources exist`() {
        val project = ProjectBuilder.builder().build()
        project.file("src/test/kotlin").mkdirs()
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.evaluate()

        val suites = project.extensions.getByType(TestingExtension::class.java).suites

        assertSoftly { softly ->
            softly.assertThat(suites.findByName("test")).isNotNull()
            softly.assertThat(suites.findByName("functionalTest")).isNull()
        }
    }

    @Test
    fun `wires functional suite into check`() {
        val project = ProjectBuilder.builder().build()
        project.file("src/functionalTest/kotlin").mkdirs()
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.evaluate()

        val check = project.tasks.named("check").get()
        val functionalTest = project.tasks.named("functionalTest").get()

        assertSoftly { softly ->
            softly.assertThat(check.taskDependencies.getDependencies(check)).contains(functionalTest)
        }
    }

    @Test
    fun `mirrors gradle plugin classpath wiring on auto-configured functional suites`() {
        val project = ProjectBuilder.builder().build()
        project.file("src/functionalTest/kotlin").mkdirs()
        project.pluginManager.apply("java-gradle-plugin")
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.evaluate()

        val configurations = project.configurations
        val metadataConsumer = project.tasks.named("functionalTest").get()
        val testSourceSets =
            project.extensions
                .getByType(GradlePluginDevelopmentExtension::class.java)
                .testSourceSets
                .map { it.name }

        assertSoftly { softly ->
            softly
                .assertThat(testSourceSets)
                .describedAs("gradlePlugin.testSourceSets")
                .contains("functionalTest")
            // Gradle wires the built-in `test` suite itself; the plugin mirrors that for
            // `functionalTest`, which Gradle misses because it is registered after evaluation.
            softly
                .assertThat(configurations.getByName("testImplementation").hasGradleTestKit())
                .describedAs("testImplementation TestKit (provided by Gradle)")
                .isTrue()
            softly
                .assertThat(configurations.getByName("functionalTestImplementation").hasGradleTestKit())
                .describedAs("functionalTestImplementation TestKit (added by the plugin)")
                .isTrue()
            softly
                .assertThat(configurations.getByName("testImplementation").hasGradleApi())
                .describedAs("testImplementation gradleApi (provided by Gradle)")
                .isTrue()
            softly
                .assertThat(configurations.getByName("functionalTestImplementation").hasGradleApi())
                .describedAs("functionalTestImplementation gradleApi (added by the plugin)")
                .isTrue()
            softly
                .assertThat(configurations.getByName("testRuntimeOnly").hasPluginUnderTestMetadata(metadataConsumer))
                .describedAs("testRuntimeOnly metadata (provided by Gradle)")
                .isTrue()
            softly
                .assertThat(
                    configurations.getByName("functionalTestRuntimeOnly").hasPluginUnderTestMetadata(metadataConsumer),
                ).describedAs("functionalTestRuntimeOnly metadata (added by the plugin)")
                .isTrue()
        }
    }

    @Test
    fun `does not register functional suite when disabled`() {
        val project = ProjectBuilder.builder().build()
        project.file("src/functionalTest/kotlin").mkdirs()
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        val extension =
            (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
                .extensions
                .getByType(EasyJvmDefaultsExtension::class.java)
        extension.configureTestSuites.set(false)
        project.evaluate()

        val suites = project.extensions.getByType(TestingExtension::class.java).suites

        assertSoftly { softly ->
            softly.assertThat(suites.findByName("functionalTest")).isNull()
        }
    }

    @Test
    fun `registers root aggregate report for auto-configured functional suite`() {
        val build = functionalSuiteBuild(aggregateTestReports = true)

        val report =
            build.root.extensions
                .getByType(ReportingExtension::class.java)
                .reports
                .findByName("functionalTestAggregateTestReport")
        val check =
            build.root.tasks
                .named("check")
                .get()

        assertSoftly { softly ->
            softly.assertThat(report).isInstanceOf(AggregateTestReport::class.java)
            softly.assertThat((report as AggregateTestReport).testSuiteName.get()).isEqualTo("functionalTest")
            softly.assertThat(check.taskDependencies.getDependencies(check)).contains(report.reportTask.get())
        }
    }

    @Test
    fun `adds the functional suite project to the root aggregation`() {
        val build = functionalSuiteBuild(aggregateTestReports = true)

        val aggregated =
            build.root.configurations
                .getByName("testReportAggregation")
                .dependencies
                .filterIsInstance<ProjectDependency>()
                .map { it.path }

        assertSoftly { softly ->
            softly.assertThat(aggregated).contains(build.functional.path)
        }
    }

    @Test
    fun `does not register root aggregate report without the root aggregation plugin`() {
        val build = functionalSuiteBuild(aggregateTestReports = false)

        assertSoftly { softly ->
            softly.assertThat(build.root.tasks.findByName("functionalTestAggregateTestReport")).isNull()
        }
    }

    @Test
    fun `registers root code coverage report for auto-configured functional suite`() {
        val build = functionalSuiteBuild(aggregateTestReports = false)

        val report =
            build.root.extensions
                .getByType(ReportingExtension::class.java)
                .reports
                .findByName("functionalTestCodeCoverageReport")
        val check =
            build.root.tasks
                .named("check")
                .get()

        assertSoftly { softly ->
            softly.assertThat(report).isInstanceOf(JacocoCoverageReport::class.java)
            softly.assertThat((report as JacocoCoverageReport).testSuiteName.get()).isEqualTo("functionalTest")
            softly.assertThat(check.taskDependencies.getDependencies(check)).contains(report.reportTask.get())
        }
    }

    @Test
    fun `adds the functional suite project to the root jacoco aggregation`() {
        val build = functionalSuiteBuild(aggregateTestReports = false)

        val aggregated =
            build.root.configurations
                .getByName("jacocoAggregation")
                .dependencies
                .filterIsInstance<ProjectDependency>()
                .map { it.path }

        assertSoftly { softly ->
            softly.assertThat(aggregated).contains(build.functional.path)
        }
    }

    @Test
    fun `does not apply jacoco aggregation when disabled on the root`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("base")
        project.pluginManager.apply(ProjectPlugin::class.java)
        jvmDefaultsExtension(project).jacocoEnabled.set(false)
        project.evaluate()

        assertSoftly { softly ->
            softly.assertThat(project.plugins.hasPlugin("jacoco-report-aggregation")).isFalse()
        }
    }

    @Test
    fun `configures the framework-provided unit suite with framework and catalog dependencies`() {
        val project = ProjectBuilder.builder().build()
        project.extensions.add(VersionCatalogsExtension::class.java, "testCatalog", project.versionCatalog())
        val (suite, collector) = suiteCapturingDependencies()
        `when`(suite.name).thenReturn("test")

        TestSuiteWiring.configureUnitSuite(project, suite)

        val captor = argumentCaptor<Provider<MinimalExternalModuleDependency>>()
        verify(suite).useJUnitJupiter("9.9.9-jupiter")
        verify(collector, times(4)).add(captor.capture())
        assertSoftly { softly ->
            softly
                .assertThat(captor.allValues.map { it.orNull?.coordinates() })
                .containsExactlyInAnyOrder(
                    "org.assertj:assertj-core:9.9.9-catalog",
                    "org.junit-pioneer:junit-pioneer:9.9.9-pioneer",
                    "org.junit.jupiter:junit-jupiter-params:9.9.9-params",
                    "org.mockito:mockito-core:9.9.9-mockito",
                )
            softly
                .assertThat(mockingDetails(suite).invocations.map { it.method.name })
                .doesNotContain("getTargets", "getSources")
        }
    }

    @Test
    fun `does not add mockito to functional suites from the libs catalog`() {
        val project = ProjectBuilder.builder().build()
        project.extensions.add(VersionCatalogsExtension::class.java, "testCatalog", project.versionCatalog())
        val (suite, collector) = suiteCapturingDependencies()

        TestSuiteWiring.addCatalogDependencies(project, suite, CATALOG_FUNCTIONAL_TEST_DEPENDENCIES)

        val captor = argumentCaptor<Provider<MinimalExternalModuleDependency>>()
        verify(collector, times(3)).add(captor.capture())
        assertSoftly { softly ->
            softly
                .assertThat(captor.allValues.map { it.orNull?.coordinates() })
                .containsExactlyInAnyOrder(
                    "org.assertj:assertj-core:9.9.9-catalog",
                    "org.junit-pioneer:junit-pioneer:9.9.9-pioneer",
                    "org.junit.jupiter:junit-jupiter-params:9.9.9-params",
                )
        }
    }

    @Test
    fun `pins junit jupiter to the version from the libs catalog`() {
        val project = ProjectBuilder.builder().build()
        project.extensions.add(VersionCatalogsExtension::class.java, "testCatalog", project.versionCatalog())

        val suite = mock(JvmTestSuite::class.java)
        `when`(suite.name).thenReturn("functionalTest")

        TestSuiteWiring.configureTestFramework(project, suite)

        verify(suite).useJUnitJupiter("9.9.9-jupiter")
    }

    @Test
    fun `does not add catalog test dependencies when the libs catalog is absent`() {
        val project = ProjectBuilder.builder().build()
        project.file("src/functionalTest/kotlin").mkdirs()
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.evaluate()

        val functionalDeps =
            project.configurations
                .getByName("functionalTestImplementation")
                .dependencies
        val unitDeps =
            project.configurations
                .getByName("testImplementation")
                .dependencies

        assertSoftly { softly ->
            softly
                .assertThat(functionalDeps.map { it.group })
                .doesNotContain("org.assertj", "org.junit-pioneer", "org.mockito")
            softly.assertThat(functionalDeps.map { it.name }).doesNotContain("junit-jupiter-params")
            softly
                .assertThat(unitDeps.map { it.group })
                .doesNotContain("org.assertj", "org.junit-pioneer", "org.mockito")
            softly.assertThat(unitDeps.map { it.name }).doesNotContain("junit-jupiter-params")
        }
    }

    private fun functionalSuiteBuild(aggregateTestReports: Boolean): TestBuild {
        val rootDir = Files.createTempDirectory("jvm-defaults-root-").toFile()
        val root = ProjectBuilder.builder().withProjectDir(rootDir).build()
        root.pluginManager.apply("base")
        if (aggregateTestReports) root.pluginManager.apply("test-report-aggregation")

        val functionalDir = File(rootDir, "functional").apply { mkdirs() }
        val functional =
            ProjectBuilder
                .builder()
                .withParent(root)
                .withProjectDir(functionalDir)
                .build()
        functional.file("src/functionalTest/kotlin").mkdirs()
        functional.pluginManager.apply("java-library")

        root.pluginManager.apply(ProjectPlugin::class.java)

        root.evaluate()
        functional.evaluate()
        return TestBuild(root, functional)
    }

    private fun Project.versionCatalog(): VersionCatalogsExtension {
        val catalog = mock(VersionCatalog::class.java)
        `when`(catalog.findLibrary(anyString())).thenReturn(Optional.empty())
        listOf(
            "assertj-core" to "org.assertj:assertj-core:9.9.9-catalog",
            "junit-pioneer" to "org.junit-pioneer:junit-pioneer:9.9.9-pioneer",
            "junit-jupiter-params" to "org.junit.jupiter:junit-jupiter-params:9.9.9-params",
            "junit-jupiter" to "org.junit.jupiter:junit-jupiter:9.9.9-jupiter",
            "mockito-core" to "org.mockito:mockito-core:9.9.9-mockito",
        ).forEach { (alias, coordinates) ->
            val (group, name, version) = coordinates.split(':')
            val dependency = mock(MinimalExternalModuleDependency::class.java)
            `when`(dependency.group).thenReturn(group)
            `when`(dependency.name).thenReturn(name)
            `when`(dependency.version).thenReturn(version)
            val versionConstraint = mock(VersionConstraint::class.java)
            `when`(versionConstraint.requiredVersion).thenReturn(version)
            `when`(dependency.versionConstraint).thenReturn(versionConstraint)
            val provider: Provider<MinimalExternalModuleDependency> = providers.provider { dependency }
            `when`(catalog.findLibrary(alias)).thenReturn(Optional.of(provider))
        }
        val extension = mock(VersionCatalogsExtension::class.java)
        `when`(extension.find(anyString())).thenReturn(Optional.empty())
        `when`(extension.find("libs")).thenReturn(Optional.of(catalog))
        return extension
    }

    private fun jvmDefaultsExtension(project: Project): EasyJvmDefaultsExtension =
        (project.extensions.getByType(EasyExtension::class.java) as ExtensionAware)
            .extensions
            .getByType(EasyJvmDefaultsExtension::class.java)

    private fun MinimalExternalModuleDependency.coordinates(): String = "$group:$name:$version"

    private fun Configuration.fileCollectionFiles(): List<File> =
        dependencies.flatMap { (it as? FileCollectionDependency)?.files?.toList().orEmpty() }

    private fun Configuration.hasGradleTestKit(): Boolean = fileCollectionFiles().any { it.name.startsWith("gradle-test-kit") }

    private fun Configuration.hasGradleApi(): Boolean = fileCollectionFiles().any { it.name.startsWith("gradle-api") }

    private fun Configuration.hasPluginUnderTestMetadata(consumer: Task): Boolean =
        dependencies.any { dependency ->
            (dependency as? FileCollectionDependency)
                ?.files
                ?.buildDependencies
                ?.getDependencies(consumer)
                ?.any { it.name == "pluginUnderTestMetadata" } == true
        }

    private fun suiteCapturingDependencies(): Pair<JvmTestSuite, DependencyCollector> {
        val collector = mock(DependencyCollector::class.java)
        val dependencies = mock(JvmComponentDependencies::class.java)
        `when`(dependencies.implementation).thenReturn(collector)
        val suite = mock(JvmTestSuite::class.java)
        `when`(suite.dependencies).thenReturn(dependencies)
        return suite to collector
    }

    private inline fun <reified T : Any> argumentCaptor(): ArgumentCaptor<T> = ArgumentCaptor.forClass(T::class.java)

    private data class TestBuild(
        val root: Project,
        val functional: Project,
    )

    @Test
    fun `leaves the framework-provided functional suite description intact`() {
        val project = ProjectBuilder.builder().build()
        project.file("src/functionalTest/kotlin").mkdirs()
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(ProjectPlugin::class.java)
        project.evaluate()

        val task = project.tasks.named("functionalTest").get()

        assertSoftly { softly ->
            softly.assertThat(task.description).isEqualTo("Runs the functional test suite.")
        }
    }
}
