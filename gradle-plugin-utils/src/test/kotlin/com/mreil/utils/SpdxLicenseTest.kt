package com.mreil.utils

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test

class SpdxLicenseTest {
    @Test
    fun `toUrl accepts SPDX id shorthand`() {
        assertSoftly { softly ->
            softly.assertThat(SpdxLicense.toUrl("MIT")).isEqualTo("https://spdx.org/licenses/MIT")
        }
    }

    @Test
    fun `toUrl passes through full URL`() {
        assertSoftly { softly ->
            softly
                .assertThat(SpdxLicense.toUrl("https://spdx.org/licenses/Apache-2.0"))
                .isEqualTo("https://spdx.org/licenses/Apache-2.0")
        }
    }

    @Test
    fun `toSpdxId derives id from URL`() {
        assertSoftly { softly ->
            softly.assertThat(SpdxLicense.toSpdxId("https://spdx.org/licenses/MIT")).isEqualTo("MIT")
            softly.assertThat(SpdxLicense.toSpdxId("MIT")).isEqualTo("MIT")
        }
    }
}
