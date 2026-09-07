package com.mreil.easy.publish.central

import com.mreil.easy.publish.central.JreleaserYaml.Config
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test

class JreleaserDeployersTest {
    @Test
    fun `central config resolves only the release deployer`() {
        val deployers = deployersFor(config())

        assertSoftly { softly ->
            softly.assertThat(deployers.map { it.section }).containsExactly("mavenCentral")
            softly.assertThat(deployers.map { it.name }).containsExactly("sonatype")
            softly.assertThat((deployers[0] as MavenCentralDeployer).active).isEqualTo("RELEASE")
        }
    }

    @Test
    fun `nexus config demotes central and appends nexus3`() {
        val deployers = deployersFor(config().copy(nexusUrl = "http://localhost:8081", nexusUsername = "u", nexusPassword = "p"))

        assertSoftly { softly ->
            softly.assertThat(deployers.map { it.section }).containsExactly("mavenCentral", "nexus3")
            softly.assertThat((deployers[0] as MavenCentralDeployer).active).isEqualTo("NEVER")
            softly.assertThat((deployers[1] as Nexus3TestDeployer).url).isEqualTo("http://localhost:8081")
        }
    }

    @Test
    fun `each deployer renders its unique keys plus shared keys`() {
        assertSoftly { softly ->
            softly.assertThat(MavenCentralDeployer("RELEASE", listOf("build/stagingRepo"), "u", "p").toMap()).containsKeys(
                "active",
                "url",
                "stagingRepositories",
                "username",
                "password",
            )
            softly
                .assertThat(
                    Nexus3TestDeployer("http://localhost:8081", listOf("build/stagingRepo"), "u", "p").toMap(),
                ).containsEntry("authorization", "BASIC")
        }
    }

    @Test
    fun `stagingRepositories lists every module staging dir`() {
        val deployers = deployersFor(config().copy(stagingDirs = listOf("root/build/stagingRepo", "child/build/stagingRepo")))

        assertSoftly { softly ->
            deployers.forEach { deployer ->
                softly.assertThat(deployer.toMap()["stagingRepositories"]).isEqualTo(
                    listOf("root/build/stagingRepo", "child/build/stagingRepo"),
                )
            }
        }
    }

    private fun config(): Config =
        Config(
            projectName = "demo",
            projectVersion = "1.0.0",
            projectGroupId = "com.example",
            stagingDirs = listOf("build/stagingRepo"),
            mavenCentralUsername = "central-user",
            mavenCentralPassword = "central-pass",
        )
}
