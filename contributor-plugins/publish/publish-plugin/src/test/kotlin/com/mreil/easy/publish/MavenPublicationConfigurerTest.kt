package com.mreil.easy.publish

import com.mreil.easy.codemeta.Person
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test

class MavenPublicationConfigurerTest {
    @Test
    fun `displayName combines given and family name`() {
        assertSoftly { softly ->
            softly
                .assertThat(
                    MavenPublicationConfigurer.displayName(Person(givenName = "Ada", familyName = "Lovelace")),
                ).isEqualTo("Ada Lovelace")
        }
    }

    @Test
    fun `displayName falls back to name`() {
        assertSoftly { softly ->
            softly
                .assertThat(
                    MavenPublicationConfigurer.displayName(Person(name = "Markus Reil")),
                ).isEqualTo("Markus Reil")
        }
    }

    @Test
    fun `displayName returns null when all names missing`() {
        assertSoftly { softly ->
            softly
                .assertThat(
                    MavenPublicationConfigurer.displayName(Person(email = "ada@example.com")),
                ).isNull()
        }
    }
}
