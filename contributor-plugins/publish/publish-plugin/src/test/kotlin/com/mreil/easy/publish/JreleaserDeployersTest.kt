package com.mreil.easy.publish

import com.mreil.easy.publish.MavenCentralWiring.Config
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test

class JreleaserDeployersTest {
    @Test
    fun `central config resolves two deployers in order`() {
        val deployers = deployersFor(config())

        assertSoftly { softly ->
            softly.assertThat(deployers.map { it.section }).containsExactly("mavenCentral", "nexus2")
            softly.assertThat(deployers.map { it.name }).containsExactly("sonatype", "sonatype-snapshots")
            softly.assertThat((deployers[0] as MavenCentralDeployer).active).isEqualTo("RELEASE")
            softly.assertThat((deployers[1] as Nexus2SnapshotsDeployer).active).isEqualTo("SNAPSHOT")
        }
    }

    @Test
    fun `nexus config demotes remote deployers and appends nexus3`() {
        val deployers = deployersFor(config().copy(nexusUrl = "http://localhost:8081", nexusUsername = "u", nexusPassword = "p"))

        assertSoftly { softly ->
            softly.assertThat(deployers.map { it.section }).containsExactly("mavenCentral", "nexus2", "nexus3")
            softly.assertThat((deployers[0] as MavenCentralDeployer).active).isEqualTo("NEVER")
            softly.assertThat((deployers[1] as Nexus2SnapshotsDeployer).active).isEqualTo("NEVER")
            softly.assertThat((deployers[2] as Nexus3TestDeployer).url).isEqualTo("http://localhost:8081")
        }
    }

    @Test
    fun `each deployer renders its unique keys plus shared keys`() {
        assertSoftly { softly ->
            softly.assertThat(MavenCentralDeployer("RELEASE", "build/stagingRepo", "u", "p").toMap()).containsKeys(
                "active",
                "url",
                "stagingRepositories",
                "username",
                "password",
            )
            val nexus2 = Nexus2SnapshotsDeployer("SNAPSHOT", "build/stagingRepo", "u", "p").toMap()
            softly.assertThat(nexus2).containsEntry("snapshotSupported", true)
            softly.assertThat(nexus2).containsEntry("url", CENTRAL_SNAPSHOTS_URL)
            softly.assertThat(nexus2).containsEntry("snapshotUrl", CENTRAL_SNAPSHOTS_URL)
            softly
                .assertThat(
                    Nexus3TestDeployer("http://localhost:8081", "build/stagingRepo", "u", "p").toMap(),
                ).containsEntry("authorization", "BASIC")
            softly.assertThat(nexus2).containsEntry("closeRepository", false)
            softly.assertThat(nexus2).containsEntry("releaseRepository", false)
        }
    }

    private fun config(): Config =
        Config(
            projectName = "demo",
            projectVersion = "1.0.0",
            projectGroupId = "com.example",
            stagingDir = "build/stagingRepo",
            gpgPublicKey = "pub",
            gpgPrivateKey = "priv",
            gpgPassphrase = "pass",
            mavenCentralUsername = "central-user",
            mavenCentralPassword = "central-pass",
        )
}
