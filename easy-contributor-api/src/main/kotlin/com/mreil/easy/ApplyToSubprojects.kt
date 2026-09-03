package com.mreil.easy

/** Marks a plugin to be applied to all projects, not just the current one. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplyToSubprojects
