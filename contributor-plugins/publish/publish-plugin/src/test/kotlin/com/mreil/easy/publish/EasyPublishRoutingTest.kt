package com.mreil.easy.publish

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class EasyPublishRoutingTest {
    @ParameterizedTest(name = "repo={0} snapshot={1} expected={2}")
    @CsvSource(
        "myRelease, true, false",
        "mySnapshot, true, true",
        "myNeutral, true, true",
        "MY-RELEASE, true, false",
        "my-release, true, false",
        "myRelease, false, true",
        "mySnapshot, false, false",
        "myNeutral, false, true",
        "myRelease, null, true",
        "mySnapshot, null, true",
        "myNeutral, null, true",
        "mySnapshotRelease, true, false",
        "mySnapshotRelease, false, false",
    )
    fun `shouldPublishToRepo follows naming convention`(
        repoName: String,
        isSnapshotParam: String,
        expected: Boolean,
    ) {
        val isSnapshot = isSnapshotParam.takeIf { it != "null" }?.toBooleanStrict()
        val result = RepoRouting.shouldPublishToRepo(repoName, isSnapshot)
        assertSoftly { softly ->
            softly.assertThat(result).isEqualTo(expected)
        }
    }
}
