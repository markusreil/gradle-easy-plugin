package com.mreil.easy

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class EasyLoggingTest {
    @Test
    fun `easyInfo logs with prefix and arguments`() {
        val project = ProjectBuilder.builder().build()

        project.easyInfo("message with {} and {}", "one", "two")
    }

    @Test
    fun `easyLifecycle logs with prefix and arguments`() {
        val project = ProjectBuilder.builder().build()

        project.easyLifecycle("message with {}", "one")
    }
}
