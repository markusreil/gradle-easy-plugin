package com.mreil.easy.codemeta

/**
 * Normalizes the Codemeta `license` value to an SPDX URL.
 *
 * Accepts both the SPDX id shorthand (`MIT`) and the full URL
 * (`https://spdx.org/licenses/MIT`); anything starting with `http` is used as-is.
 */
object CodemetaLicense {
    private const val SPDX_PREFIX = "https://spdx.org/licenses/"

    fun toUrl(raw: String): String =
        if (raw.startsWith("http")) {
            raw
        } else {
            SPDX_PREFIX + raw
        }

    fun toSpdxId(raw: String): String = raw.removePrefix(SPDX_PREFIX).substringBefore("/")
}
