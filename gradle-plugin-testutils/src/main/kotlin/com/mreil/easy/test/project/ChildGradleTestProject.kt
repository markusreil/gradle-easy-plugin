package com.mreil.easy.test.project

import java.io.File

/** A child project of a [GradleTestProject], living in `<parent>/<name>`. Create via `createChild`. */
class ChildGradleTestProject(
    private val parent: TestProject,
    internal val name: String,
    override val projectDir: File = File(parent.projectDir, name).apply { mkdirs() },
) : TestProject()
