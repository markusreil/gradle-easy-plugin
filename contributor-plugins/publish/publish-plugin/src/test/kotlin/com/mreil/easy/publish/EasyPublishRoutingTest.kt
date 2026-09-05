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
        val result = invokeShouldPublishToRepo(repoName, isSnapshot)
        assertSoftly { softly ->
            softly.assertThat(result).isEqualTo(expected)
        }
    }

    private fun invokeShouldPublishToRepo(
        repoName: String,
        isSnapshot: Boolean?,
    ): Boolean {
        val method =
            EasyPublishPlugin::class.java.getDeclaredMethod(
                "shouldPublishToRepo",
                String::class.java,
                Boolean::class.javaObjectType,
            )
        method.isAccessible = true
        val plugin = EasyPublishPlugin::class.java.getDeclaredConstructor().newInstance()
        return method.invoke(plugin, repoName, isSnapshot) as Boolean
    }
}
