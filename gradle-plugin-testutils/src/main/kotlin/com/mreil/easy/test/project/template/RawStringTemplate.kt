package com.mreil.easy.test.project.template

/** A [FileTemplate] with fixed content. Default for raw staged strings. */
class RawStringTemplate(
    private val content: String,
) : FileTemplate {
    override fun render(): String = content
}
