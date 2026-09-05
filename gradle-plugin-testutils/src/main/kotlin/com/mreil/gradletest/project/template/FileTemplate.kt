package com.mreil.gradletest.project.template

/** A file content staged in memory and materialized as text on flush. */
fun interface FileTemplate {
    /** Builds the file content lazily. */
    fun render(): String
}
