package com.mreil.easy

/** Marks a contributor to apply its plugins to all projects, not just the current one. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplyToSubprojects
