package com.mreil.easy.publish

import com.mreil.easy.test.support.DisableAllEasyPlugins
import com.mreil.easy.test.support.DisableAllEasyPluginsExtension
import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.MavenCoordinates
import com.mreil.gradletest.project.assertj.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

/**
 * Functional test for the Codemeta overlay in published POMs.
 *
 * Publishes with both `publish` and `codemeta` enabled and a real `codemeta.json`,
 * asserting license, developers and SCM connection metadata land in the POM.
 */
@ExtendWith(GradleTestProjectExtension::class, DisableAllEasyPluginsExtension::class)
@DisableAllEasyPlugins
class EasyPublishCodemetaFuncTest {
    lateinit var project: GradleTestProject

    @Test
    fun `pom contains codemeta license developers and scm`() {
        lateinit var repoDir: File
        val projectName = project.projectDir.name
        project.configure {
            repoDir = createDir("repo")
            file(
                "codemeta.json",
                """
                {
                  "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
                  "@type": "SoftwareSourceCode",
                  "name": "demo",
                  "description": "demo description",
                  "version": "1.0.0",
                  "license": "https://spdx.org/licenses/MIT",
                  "codeRepository": "https://github.com/example/demo",
                  "author": [{ "@type": "Person", "givenName": "Ada", "familyName": "Lovelace", "email": "ada@example.com" }]
                }
                """.trimIndent(),
            )
            buildGradle(
                """
                plugins {
                    `java-library`
                    id("com.mreil.easy.test.publish")
                }
                easy {
                    publish {
                        enabled.set(true)
                        signingEnabled.set(false)
                        mavenRepo("testRepo", "${repoDir.invariantSeparatorsPath}")
                    }
                    codemeta { enabled.set(true) }
                }
                """.trimIndent(),
            )
            javaSource()
        }

        project.build("publish", "--info")

        val coordinates = MavenCoordinates(name = projectName)
        assertSoftly { softly ->
            softly
                .assertThat(project)
                .hasPom(repoDir, coordinates)
                .hasText("<name>Ada Lovelace</name>")
                .hasText("<email>ada@example.com</email>")
                .hasText("https://spdx.org/licenses/MIT")
                .hasText("scm:git:https://github.com/example/demo")
        }
    }
}
