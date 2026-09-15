package com.mreil.utils

/**
 * Normalizes SPDX license ids to `https://spdx.org/licenses/<id>` URLs and back.
 *
 * Accepts either the SPDX id shorthand (`MIT`) or a full URL; anything starting
 * with `http` is treated as an already-complete URL.
 */
object SpdxLicense {
    private const val SPDX_PREFIX = "https://spdx.org/licenses/"

    fun toUrl(raw: String): String = if (raw.startsWith("http")) raw else SPDX_PREFIX + raw

    fun toSpdxId(raw: String): String = raw.removePrefix(SPDX_PREFIX).substringBefore("/")
}
