package com.mreil.easy.jvm

import com.mreil.easy.jvm.kotlin.DokkaJavadocSettingsPlugin
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class JvmDefaultsSettingsTest {
    @Test
    fun `contributes the dokka javadoc settings plugin`() {
        assertSoftly { softly ->
            softly
                .assertThat(EasyJvmDefaultsSettingsContributor().settingsPlugins())
                .contains(DokkaJavadocSettingsPlugin::class)
            softly
                .assertThat(EasyJvmDefaultsSettingsContributor().settingsExtensions())
                .contains(DefaultEasyJvmDefaultsSettingsExtension::class)
            softly.assertThat(EasyJvmDefaultsSettingsExtension.DEFAULT_DOKKA_VERSION).isEqualTo("2.2.0")
            softly.assertThat(DokkaJavadoc.PLUGIN_ID).isEqualTo("org.jetbrains.dokka-javadoc")
        }
    }

    @Test
    fun `dokkaJavadoc opts in with the default version and stays absent otherwise`() {
        val notOptedIn = createJvmDefaultsSettingsExtension()
        val defaulted = createJvmDefaultsSettingsExtension()
        defaulted.dokkaJavadoc()

        assertSoftly { softly ->
            softly.assertThat(notOptedIn.dokkaJavadocVersion.orNull).isNull()
            softly
                .assertThat(defaulted.dokkaJavadocVersion.get())
                .isEqualTo(EasyJvmDefaultsSettingsExtension.DEFAULT_DOKKA_VERSION)
        }
    }

    @Test
    fun `dokkaJavadoc rejects blank versions`() {
        val extension = createJvmDefaultsSettingsExtension()

        assertThatThrownBy { extension.dokkaJavadoc("") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must not be blank")
        assertThatThrownBy { extension.dokkaJavadoc("   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must not be blank")
    }

    private fun createJvmDefaultsSettingsExtension(): DefaultEasyJvmDefaultsSettingsExtension =
        ProjectBuilder
            .builder()
            .build()
            .extensions
            .create(
                EasyJvmDefaultsSettingsExtension::class.java,
                "jvmDefaults",
                DefaultEasyJvmDefaultsSettingsExtension::class.java,
            ) as DefaultEasyJvmDefaultsSettingsExtension
}
