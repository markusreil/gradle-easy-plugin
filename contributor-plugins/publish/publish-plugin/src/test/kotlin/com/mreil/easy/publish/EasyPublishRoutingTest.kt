package com.mreil.easy.publish

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.semver4j.Semver

class EasyPublishRoutingTest {
    @ParameterizedTest(name = "role={0} snapshot={1} expected={2}")
    @CsvSource(
        "SNAPSHOT, true, true",
        "RELEASE, true, false",
        "NEUTRAL, true, true",
        "SNAPSHOT, false, false",
        "RELEASE, false, true",
        "NEUTRAL, false, true",
        "SNAPSHOT, null, true",
        "RELEASE, null, true",
        "NEUTRAL, null, true",
    )
    fun `shouldPublishToRepo follows explicit role`(
        role: RepoRole,
        isSnapshotParam: String,
        expected: Boolean,
    ) {
        val project = ProjectBuilder.builder().build()
        val spec =
            project.objects.newInstance(
                MavenRepoSpec::class.java,
                "anyNameGoesHere",
            )
        spec.role.set(role)
        val isSnapshot = isSnapshotParam.takeIf { it != "null" }?.toBooleanStrict()
        val result = RepoRouting.shouldPublishToRepo(spec, isSnapshot)
        assertSoftly { softly ->
            softly.assertThat(result).isEqualTo(expected)
        }
    }

    @ParameterizedTest(name = "role={0}")
    @EnumSource(RepoRole::class)
    fun `repo name never influences routing`(role: RepoRole) {
        val project = ProjectBuilder.builder().build()
        listOf("myRelease", "mySnapshot", "myNeutral", "mySnapshotRelease", "plain").forEach { name ->
            val spec = project.objects.newInstance(MavenRepoSpec::class.java, name)
            spec.role.set(role)
            assertThat(RepoRouting.shouldPublishToRepo(spec, true))
                .isEqualTo(role != RepoRole.RELEASE)
            assertThat(RepoRouting.shouldPublishToRepo(spec, false))
                .isEqualTo(role != RepoRole.SNAPSHOT)
        }
    }

    @Test
    fun `unset role fails fast`() {
        val project = ProjectBuilder.builder().build()
        val spec = project.objects.newInstance(MavenRepoSpec::class.java, "roless")
        assertThatThrownBy { RepoRouting.shouldPublishToRepo(spec, false) }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("roless")
    }

    @ParameterizedTest(name = "version={0} expected={1}")
    @CsvSource(
        "0.0.105, false",
        "0.0.105-SNAPSHOT, true",
        "1.0.0, false",
        "1.0.0-RC1, false",
        "1.0.0-SNAPSHOT, true",
        "1.0.0-alpha.1, false",
    )
    fun `isSnapshot is true only for SNAPSHOT pre-release`(
        version: String,
        expected: Boolean,
    ) {
        assertThat(RepoRouting.isSnapshot(Semver.parse(version))).isEqualTo(expected)
    }

    @Test
    fun `isSnapshot returns null for null semver`() {
        assertThat(RepoRouting.isSnapshot(null)).isNull()
    }
}
