package com.mreil.easy.publish.central

import com.mreil.easy.ApplyToSubprojects
import com.mreil.easy.EnabledBy
import com.mreil.easy.publish.EasyPublishContributor
import com.mreil.easy.publish.EasyPublishExtension
import com.mreil.easy.publish.EasyPublishPlugin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EasyJreleaserPluginTest {
    @Test
    fun `contributor provides publish and jreleaser plugins`() {
        val plugins = EasyPublishContributor().projectPlugins()

        assertThat(plugins).containsExactlyInAnyOrder(
            EasyPublishPlugin::class,
            EasyJreleaserPlugin::class,
        )
    }

    @Test
    fun `jreleaser plugin is gated by the publish extension and root-only`() {
        val jreleaser = EasyJreleaserPlugin::class.java
        val publish = EasyPublishPlugin::class.java

        assertThat(jreleaser.getAnnotation(EnabledBy::class.java)?.value)
            .isEqualTo(EasyPublishExtension::class)
        assertThat(jreleaser.isAnnotationPresent(ApplyToSubprojects::class.java))
            .describedAs("EasyJreleaserPlugin must stay root-only (no fan-out)")
            .isFalse()
        assertThat(publish.isAnnotationPresent(ApplyToSubprojects::class.java)).isTrue()
    }
}
