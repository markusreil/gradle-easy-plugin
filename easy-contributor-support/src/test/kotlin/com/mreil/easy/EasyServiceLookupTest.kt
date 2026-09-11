package com.mreil.easy

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * A minimal [BuildService] registered under a shared-service name for [easyService] tests.
 */
abstract class DummyService : BuildService<BuildServiceParameters.None> {
    var value: Int = 0
}

/**
 * Tests for [easyService] / [gatedBy] shared-lookup semantics.
 *
 * Verifies the five lookup cases: absent `easy`, absent child extension, disabled, enabled with a
 * registered service, and enabled-but-unregistered (lazy failure at realization).
 */
class EasyServiceLookupTest {
    @Test
    fun `easy absent yields absent without throwing`() {
        val project = ProjectBuilder.builder().build()
        val provider = project.easyService<DummyService, TestEnabledExtension>("dummy", TestEnabledExtension::class)

        assertThat(provider.orNull).isNull()
    }

    @Test
    fun `easy present but child extension absent yields absent`() {
        val project = ProjectBuilder.builder().build()
        createEasy(project)

        val provider = project.easyService<DummyService, TestEnabledExtension>("dummy", TestEnabledExtension::class)

        assertThat(provider.orNull).isNull()
    }

    @Test
    fun `disabled extension yields absent without touching service`() {
        val project = ProjectBuilder.builder().build()
        createEasy(project, TestEnabledExtension::class)
        project
            .getEasyExtension()
            .extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(false)
        // No shared service is registered; if the disabled path wrongly looked it up, realization would fail.
        val provider = project.easyService<DummyService, TestEnabledExtension>("dummy", TestEnabledExtension::class)

        assertThat(provider.orNull).isNull()
    }

    @Test
    fun `enabled and registered service yields value`() {
        val project = ProjectBuilder.builder().build()
        createEasy(project, TestEnabledExtension::class)
        project
            .getEasyExtension()
            .extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(true)
        project.gradle.sharedServices.registerIfAbsent("dummy", DummyService::class.java) {}

        val service = project.easyService<DummyService, TestEnabledExtension>("dummy", TestEnabledExtension::class).get()

        assertThat(service).isNotNull()
        assertThat(service.value).isEqualTo(0)
    }

    @Test
    fun `enabled but unregistered fails lazily at realization`() {
        val project = ProjectBuilder.builder().build()
        createEasy(project, TestEnabledExtension::class)
        project
            .getEasyExtension()
            .extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(true)
        val provider = project.easyService<DummyService, TestEnabledExtension>("dummy", TestEnabledExtension::class)

        assertThatThrownBy { provider.orNull }
            .isInstanceOf(UnknownDomainObjectException::class.java)
            .hasMessageContaining("dummy")
    }
}
