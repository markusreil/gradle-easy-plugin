package com.mreil.easy.jvm

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import com.mreil.gradletest.project.probeTask
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class EasyJvmDefaultsPluginFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `sources and javadoc jars created together with main plugin`() {
        project.configure {
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.jvm")
                }
                easy {
                    jvmDefaults { enabled.set(true) }
                }
                """.trimIndent(),
            )
        }

        val result =
            project.build("sourcesJar", "javadocJar")

        val libs =
            project
                .file("build/libs")
                .listFiles()
                ?.map { it.name }
                .orEmpty()
        assertSoftly { softly ->
            softly.assertThat(result.output).contains("sourcesJar")
            softly.assertThat(libs.any { it.contains("sources") }).isTrue()
            softly.assertThat(libs.any { it.contains("javadoc") }).isTrue()
        }
    }

    @Test
    fun `adds catalog test dependencies and pins junit jupiter from the libs catalog`() {
        val catalogProbe =
            probeTask("verifyCatalog") {
                prelude(CATALOG_DEPENDENCY_QUERIES)
                expect("ASSERTJ_VERSION", "assertj?.version", "9.9.9-catalog")
                expect("JUNIT_PIONEER_VERSION", "junitPioneer?.version", "9.9.9-pioneer")
                expect("JUPITER_PARAMS_VERSION", "jupiterParams?.version", "9.9.9-params")
                expect("JUPITER_VERSION", "junitJupiter?.version", "9.9.9-jupiter")
                expect("UNIT_MOCKITO_VERSION", "unitMockito?.version", "9.9.9-mockito")
                expect("UNIT_ASSERTJ_VERSION", "unitAssertj?.version", "9.9.9-catalog")
                expectAbsent("FUNCTIONAL_MOCKITO_VERSION", "functionalMockito?.version", "9.9.9-mockito")
            }
        project.configure {
            file(
                "gradle/libs.versions.toml",
                """
                [libraries]
                assertj-core = { module = "org.assertj:assertj-core", version = "9.9.9-catalog" }
                junit-pioneer = { module = "org.junit-pioneer:junit-pioneer", version = "9.9.9-pioneer" }
                junit-jupiter-params = { module = "org.junit.jupiter:junit-jupiter-params", version = "9.9.9-params" }
                junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version = "9.9.9-jupiter" }
                mockito-core = { module = "org.mockito:mockito-core", version = "9.9.9-mockito" }
                """.trimIndent(),
            )
            file("src/functionalTest/kotlin/Placeholder.kt", "package com.example\n")
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.jvm")
                }
                easy {
                    jvmDefaults { enabled.set(true) }
                }
                ${catalogProbe.script()}
                """.trimIndent(),
            )
        }

        val result = project.build("verifyCatalog")

        assertSoftly { softly ->
            catalogProbe.assertOutput(softly, result.output)
        }
    }

    @Test
    fun `aligns java and kotlin targets with java target version`() {
        val targetProbe =
            probeTask("verifyTargets") {
                prelude(TARGET_PROBE_PRELUDE)
                expect("JAVA_TARGET_COMPATIBILITY", "javaExtension.targetCompatibility.toString()", "11")
                expect("JAVA_RELEASE", "compileJava.options.release.orNull", "11")
                expect("KOTLIN_JVM_TARGET", "compileKotlin.compilerOptions.jvmTarget.get().target", "11")
                expect(
                    "KOTLIN_JDK_RELEASE",
                    "compileKotlin.compilerOptions.freeCompilerArgs.get().single { it.startsWith(\"-Xjdk-release=\") }",
                    "-Xjdk-release=11",
                )
            }
        project.systemProperty("java.targetVersion", "11")
        project.configure {
            buildGradle(targetBuild(targetProbe.script()))
        }

        val result = project.build("verifyTargets")

        assertSoftly { softly ->
            targetProbe.assertOutput(softly, result.output)
        }
    }

    @Test
    fun `leaves kotlin and java targets untouched without target version`() {
        val targetProbe =
            probeTask("verifyTargets") {
                prelude(TARGET_PROBE_PRELUDE)
                expect("JAVA_RELEASE_ABSENT", "compileJava.options.release.orNull == null", "true")
                expect(
                    "KOTLIN_JDK_RELEASE_ABSENT",
                    "compileKotlin.compilerOptions.freeCompilerArgs.get().none { it.startsWith(\"-Xjdk-release=\") }",
                    "true",
                )
            }
        project.configure {
            buildGradle(targetBuild(targetProbe.script()))
        }

        val result = project.build("verifyTargets")

        assertSoftly { softly ->
            targetProbe.assertOutput(softly, result.output)
        }
    }

    private fun targetBuild(probeScript: String): String =
        """
        plugins {
            `java-library`
            id("org.jetbrains.kotlin.jvm")
            id("com.mreil.easy.test.jvm")
        }
        repositories { mavenCentral() }
        easy {
            jvmDefaults { enabled.set(true) }
        }
        $probeScript
        """.trimIndent()

    @Test
    fun `aggregates auto-configured functional suites at the root`() {
        project.configure {
            settings("include(\"child\")")
            buildGradle(
                """
                plugins {
                    // No test-report-aggregation here: jvm-defaults applies it automatically.
                    `base`
                    id("com.mreil.easy.test.jvm")
                }
                easy {
                    jvmDefaults { enabled.set(true) }
                }
                """.trimIndent(),
            )
            createChild {
                buildGradle(
                    """
                    plugins {
                        `java-library`
                    }
                    repositories { mavenCentral() }
                    """.trimIndent(),
                )
                file(
                    "src/functionalTest/java/PlaceholderTest.java",
                    """
                    package com.example;

                    import org.junit.jupiter.api.Test;

                    class PlaceholderTest {
                        @Test
                        void passes() {}
                    }
                    """.trimIndent(),
                )
            }
        }

        val first = project.build("--configuration-cache", "functionalTestAggregateTestReport")
        val second = project.build("--configuration-cache", "functionalTestAggregateTestReport")

        assertSoftly { softly ->
            softly.assertThat(first.output).contains("Configuration cache entry stored")
            softly.assertThat(first.output).doesNotContain("problems were found")
            softly.assertThat(second.output).contains("Configuration cache entry reused")
            softly.assertThat(second.output).doesNotContain("problems were found")
            softly
                .assertThat(second.task(":child:functionalTest")?.outcome)
                .isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE, TaskOutcome.UP_TO_DATE)
            softly
                .assertThat(second.task(":functionalTestAggregateTestReport")?.outcome)
                .isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE, TaskOutcome.UP_TO_DATE)
            softly
                .assertThat(project.file("build/reports/tests/functionalTest/aggregated-results/index.html"))
                .exists()
        }
    }

    @Test
    fun `aggregates functional suite coverage at the root`() {
        configureFunctionalCoverageProject()

        val first = project.build("--configuration-cache", "functionalTestCodeCoverageReport")
        val second = project.build("--configuration-cache", "functionalTestCodeCoverageReport")
        val reportXml =
            project.file("build/reports/jacoco/functionalTestCodeCoverageReport/functionalTestCodeCoverageReport.xml")
        val covered =
            Regex(
                """class name="com/example/Placeholder".*?<counter type="INSTRUCTION" missed="\d+" covered="(\d+)"""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(reportXml.readText())?.groupValues?.get(1)?.toInt()

        assertSoftly { softly ->
            softly.assertThat(first.output).contains("Configuration cache entry stored")
            softly.assertThat(first.output).doesNotContain("problems were found")
            softly.assertThat(second.output).contains("Configuration cache entry reused")
            softly.assertThat(second.output).doesNotContain("problems were found")
            softly
                .assertThat(second.task(":functionalTestCodeCoverageReport")?.outcome)
                .isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE, TaskOutcome.UP_TO_DATE)
            softly.assertThat(reportXml).exists()
            softly.assertThat(covered).isNotNull().isGreaterThan(0)
        }
    }

    @Test
    fun `aggregates the built-in unit suite at the root automatically`() {
        configureUnitCoverageProject()

        val first = project.build("--configuration-cache", "testAggregateTestReport", "testCodeCoverageReport")
        val second = project.build("--configuration-cache", "testAggregateTestReport", "testCodeCoverageReport")
        val reportXml = project.file("build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml")
        val covered =
            Regex(
                """class name="com/example/UnitPlaceholder".*?<counter type="INSTRUCTION" missed="\d+" covered="(\d+)"""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(reportXml.readText())?.groupValues?.get(1)?.toInt()

        assertSoftly { softly ->
            softly.assertThat(first.output).contains("Configuration cache entry stored")
            softly.assertThat(first.output).doesNotContain("problems were found")
            softly.assertThat(second.output).contains("Configuration cache entry reused")
            softly.assertThat(second.output).doesNotContain("problems were found")
            softly
                .assertThat(second.task(":child:test")?.outcome)
                .isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE, TaskOutcome.UP_TO_DATE)
            softly
                .assertThat(second.task(":testAggregateTestReport")?.outcome)
                .isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE, TaskOutcome.UP_TO_DATE)
            softly
                .assertThat(second.task(":testCodeCoverageReport")?.outcome)
                .isIn(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE, TaskOutcome.UP_TO_DATE)
            softly.assertThat(project.file("build/reports/tests/test/aggregated-results/index.html")).exists()
            softly.assertThat(reportXml).exists()
            softly.assertThat(covered).isNotNull().isGreaterThan(0)
        }
    }

    /**
     * The same `base` root/harness setup as [configureFunctionalCoverageProject], but the child's
     * test lives in the built-in `test` suite, so coverage can only come from that suite.
     */
    private fun configureUnitCoverageProject() {
        project.configure {
            settings("include(\"child\")")
            buildGradle(
                """
                plugins {
                    // No test-report-aggregation / jacoco-report-aggregation: jvm-defaults applies them.
                    `base`
                    id("com.mreil.easy.test.jvm")
                }
                repositories { mavenCentral() }
                easy {
                    jvmDefaults { enabled.set(true) }
                }
                """.trimIndent(),
            )
            createChild {
                buildGradle(
                    """
                    plugins {
                        `java-library`
                    }
                    repositories { mavenCentral() }
                    """.trimIndent(),
                )
                file(
                    "src/main/java/com/example/UnitPlaceholder.java",
                    """
                    package com.example;

                    public class UnitPlaceholder {
                        public int onlyUnit() { return 2; }
                    }
                    """.trimIndent(),
                )
                file(
                    "src/test/java/com/example/UnitPlaceholderTest.java",
                    """
                    package com.example;

                    import org.junit.jupiter.api.Test;

                    class UnitPlaceholderTest {
                        @Test
                        void passes() { new UnitPlaceholder().onlyUnit(); }
                    }
                    """.trimIndent(),
                )
            }
        }
    }

    /**
     * A `base` root with the harness plugin plus a `java-library` child whose only test is a
     * functional suite exercising a main class, so coverage can only come from that suite.
     */
    private fun configureFunctionalCoverageProject() {
        project.configure {
            settings("include(\"child\")")
            buildGradle(
                """
                plugins {
                    `base`
                    id("com.mreil.easy.test.jvm")
                }
                repositories { mavenCentral() }
                easy {
                    jvmDefaults { enabled.set(true) }
                }
                """.trimIndent(),
            )
            createChild {
                buildGradle(
                    """
                    plugins {
                        `java-library`
                    }
                    repositories { mavenCentral() }
                    """.trimIndent(),
                )
                file(
                    "src/main/java/com/example/Placeholder.java",
                    """
                    package com.example;

                    public class Placeholder {
                        public int onlyFunctional() { return 1; }
                    }
                    """.trimIndent(),
                )
                file(
                    "src/functionalTest/java/com/example/PlaceholderFuncTest.java",
                    """
                    package com.example;

                    import org.junit.jupiter.api.Test;

                    class PlaceholderFuncTest {
                        @Test
                        void passes() { new Placeholder().onlyFunctional(); }
                    }
                    """.trimIndent(),
                )
            }
        }
    }
}

private const val TARGET_PROBE_PRELUDE =
    "val javaExtension = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)\n" +
        "val compileJava = project.tasks.named(\"compileJava\", org.gradle.api.tasks.compile.JavaCompile::class.java).get()\n" +
        "val compileKotlin = project.tasks.named(\"compileKotlin\", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).get()"

private const val CATALOG_DEPENDENCY_QUERIES =
    "val cfg = project.configurations.getByName(\"functionalTestImplementation\")\n" +
        "val assertj = cfg.dependencies.find { it.group == \"org.assertj\" }\n" +
        "val junitPioneer = cfg.dependencies.find { it.group == \"org.junit-pioneer\" }\n" +
        "val jupiterParams = cfg.dependencies.find { it.group == \"org.junit.jupiter\" && it.name == \"junit-jupiter-params\" }\n" +
        "val functionalMockito = cfg.dependencies.find { it.group == \"org.mockito\" }\n" +
        "val unitCfg = project.configurations.getByName(\"testImplementation\")\n" +
        "val unitMockito = unitCfg.dependencies.find { it.group == \"org.mockito\" }\n" +
        "val unitAssertj = unitCfg.dependencies.find { it.group == \"org.assertj\" }\n" +
        "val junitJupiter = cfg.dependencies.find { it.name == \"junit-jupiter\" }"
