package com.mreil.easy.publish.central

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * JReleaser JSON model and rendering for Maven Central deployment.
 *
 * [GenerateJreleaserConfigTask] keeps the lazy `@Input` properties and maps them
 * to [Config] at execution time; tests construct it directly without a Project.
 * The rendered file is `jreleaser.json` (JReleaser detects the format by extension).
 */
internal object JreleaserJson {
    /**
     * Resolved JReleaser config values (plain data, no Gradle types).
     */
    data class Config(
        val projectName: String,
        val projectVersion: String,
        val projectGroupId: String,
        val stagingDirs: List<String>,
        val mavenCentralUsername: String,
        val mavenCentralPassword: String,
        val nexusUrl: String? = null,
        val nexusUsername: String = "",
        val nexusPassword: String = "",
    )

    internal fun buildJson(config: Config): String {
        val fullConfig =
            buildJsonObject {
                putJsonObject("project") {
                    put("name", config.projectName)
                    put("version", config.projectVersion)
                    putJsonObject("languages") {
                        // artifactId defaults to the project name; groupId is required
                        // (deployer namespaces and artifact matching default to it).
                        putJsonObject("java") { put("groupId", config.projectGroupId) }
                    }
                }
                putJsonObject("deploy") {
                    putJsonObject("maven") {
                        deployersFor(config).forEach { deployer ->
                            putJsonObject(deployer.section) {
                                put(deployer.name, deployer.toJson())
                            }
                        }
                    }
                }
            }
        return format.encodeToString(JsonObject.serializer(), fullConfig)
    }

    private val format =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
}
