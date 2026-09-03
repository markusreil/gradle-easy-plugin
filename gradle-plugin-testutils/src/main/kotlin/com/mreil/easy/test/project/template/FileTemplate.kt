package com.mreil.easy.test.project.template

/** A file content staged in memory and materialized as text on flush. */
fun interface FileTemplate {
    /** Builds the file content lazily. */
    fun render(): String
}
