package com.mreil.easy.codemeta

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test

class CodemetaDeserializationTest {
    @Test
    fun `single author object deserializes as list`() {
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

        val codemeta: Codemeta = CodemetaJson.decode(json)

        assertSoftly { softly ->
            softly.assertThat(codemeta.author).hasSize(1)
            softly.assertThat(codemeta.author?.single()?.givenName).isEqualTo("Ada")
            softly.assertThat(codemeta.author?.single()?.email).isEqualTo("ada@example.com")
        }
    }
}
