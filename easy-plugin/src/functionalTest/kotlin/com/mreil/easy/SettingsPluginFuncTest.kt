package com.mreil.easy

import com.mreil.gradletest.project.GradleTestProject
import com.mreil.gradletest.project.GradleTestProjectExtension
import com.mreil.gradletest.project.assertj.assertSoftly
import com.mreil.gradletest.project.probeTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(GradleTestProjectExtension::class)
class SettingsPluginFuncTest {
    lateinit var project: GradleTestProject

    private fun GradleTestProject.stageCodemetaJson() {
        // pre-create codemeta.json so generateCodemeta (auto-wired into every task) does not fail the build
        file(
            "codemeta.json",
            """
            {
              "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
              "@type": "SoftwareSourceCode",
              "name": "test",
              "description": "test",
              "version": "1.0.0"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `settings plugin creates settings-scope easy extension and does not push to root project`() {
        val extensionProbe =
            probeTask("verifyNoRootExtension") {
                expect("ROOT_HAS_NO_EASY", "project.extensions.findByName(\"easy\") == null", "true")
            }
        project.configure {
            settings(
                """
                plugins {
                    id("com.mreil.easy.settings")
                }
                check(extensions.findByName("easy") is com.mreil.easy.EasySettingsExtension) {
                    "expected the settings-scope easy root to be EasySettingsExtension"
                }
                """.trimIndent(),
            )
            buildGradle(
                """
                ${extensionProbe.script()}
                """.trimIndent(),
            )
        }

        val result = project.build("verifyNoRootExtension")

        assertSoftly { softly ->
            extensionProbe.assertOutput(softly, result.output)
        }
    }

    @Test
    fun `settings and project scopes register distinct registry services`() {
        // TestKit loads both plugin IDs in one classloader, so this asserts the wiring/names are
        // distinct (two registrations, two instances), not the cross-classloader behaviour itself.
        val servicesProbe =
            probeTask("verifyRegistries") {
                prelude(
                    "val registrations = project.gradle.sharedServices.registrations",
                    "val projectService = registrations.getByName(PluginRegistry.NAME).service.get()",
                    "val settingsService = registrations.getByName(PluginRegistry.SETTINGS_NAME).service.get()",
                )
                expect("HAS_PROJECT_EASY", "project.extensions.findByName(\"easy\") is EasyExtension", "true")
                expect("HAS_PROJECT_REGISTRY", "registrations.findByName(PluginRegistry.NAME) != null", "true")
                expect("HAS_SETTINGS_REGISTRY", "registrations.findByName(PluginRegistry.SETTINGS_NAME) != null", "true")
                expect("DISTINCT_REGISTRY_SERVICES", "projectService !== settingsService", "true")
            }
        project.configure {
            stageCodemetaJson()
            settings(
                """
                plugins {
                    id("com.mreil.easy.settings")
                }
                """.trimIndent(),
            )
            buildGradle(
                """
                import com.mreil.easy.EasyExtension
                import com.mreil.easy.PluginRegistry

                plugins {
                    id("com.mreil.easy.project")
                }

                ${servicesProbe.script()}
                """.trimIndent(),
            )
        }

        val result = project.build("verifyRegistries")

        assertSoftly { softly ->
            servicesProbe.assertOutput(softly, result.output)
        }
    }
}
