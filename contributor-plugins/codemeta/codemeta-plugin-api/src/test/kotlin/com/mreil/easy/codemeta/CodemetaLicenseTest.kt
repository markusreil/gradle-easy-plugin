package com.mreil.easy.codemeta

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test

class CodemetaLicenseTest {
    @Test
    fun `toUrl accepts SPDX id shorthand`() {
        assertSoftly { softly ->
            softly.assertThat(CodemetaLicense.toUrl("MIT")).isEqualTo("https://spdx.org/licenses/MIT")
        }
    }

    @Test
    fun `toUrl passes through full URL`() {
        assertSoftly { softly ->
            softly
                .assertThat(CodemetaLicense.toUrl("https://spdx.org/licenses/Apache-2.0"))
                .isEqualTo("https://spdx.org/licenses/Apache-2.0")
        }
    }

    @Test
    fun `toSpdxId derives id from URL`() {
        assertSoftly { softly ->
            softly.assertThat(CodemetaLicense.toSpdxId("https://spdx.org/licenses/MIT")).isEqualTo("MIT")
            softly.assertThat(CodemetaLicense.toSpdxId("MIT")).isEqualTo("MIT")
        }
    }

    @Test
    fun `single author object deserializes as list`() {
        val mapper =
            jacksonObjectMapper().apply {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            }
        val json =
            """
            {
              "@context": "https://doi.org/10.5063/schema/codemeta-2.0",
              "@type": "SoftwareSourceCode",
              "name": "test",
              "description": "test",
              "version": "1.0.0",
              "author": { "@type": "Person", "givenName": "Ada", "familyName": "Lovelace", "email": "ada@example.com" }
            }
            """.trimIndent()

        val codemeta: Codemeta = mapper.readValue(json)

        assertSoftly { softly ->
            softly.assertThat(codemeta.author).hasSize(1)
            softly.assertThat(codemeta.author?.single()?.givenName).isEqualTo("Ada")
            softly.assertThat(codemeta.author?.single()?.email).isEqualTo("ada@example.com")
        }
    }
}
