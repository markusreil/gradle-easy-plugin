package com.mreil.utils

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junitpioneer.jupiter.SetEnvironmentVariable
import org.junitpioneer.jupiter.SetSystemProperty
import java.io.File
import java.nio.file.Files
import java.util.Base64

class PropertyResolverTest {
    private fun projectWithGradleProperty(
        key: String,
        value: String,
    ): org.gradle.api.Project {
        val dir = Files.createTempDirectory("gradle-project").toFile()
        File(dir, "gradle.properties").writeText("$key=$value")
        return ProjectBuilder.builder().withProjectDir(dir).build()
    }

    // StringProvider (inner of resolver) core

    @Test
    fun `get returns original value`() {
        val project = ProjectBuilder.builder().build()
        val delegate = project.providers.provider { "hello" }
        val provider = PropertyResolver.StringProvider(delegate)

        assertThat(provider.get()).isEqualTo("hello")
        assertThat(provider.isPresent).isTrue()
    }

    @Test
    fun `isPresent is false when delegate is absent`() {
        val project = ProjectBuilder.builder().build()
        val delegate = project.providers.gradleProperty("missing.key.12345")
        val provider = PropertyResolver.StringProvider(delegate)

        assertThat(provider.isPresent).isFalse()
        assertThat(provider.base64Decode().isPresent).isFalse()
    }

    @Test
    fun `base64Decode decodes value`() {
        val project = ProjectBuilder.builder().build()
        val encoded = Base64.getEncoder().encodeToString("hello world".toByteArray())
        val delegate = project.providers.provider { encoded }
        val provider = PropertyResolver.StringProvider(delegate)

        assertThat(provider.base64Decode().get()).isEqualTo("hello world")
    }

    @Test
    fun `base64Decode is lazy and absent when delegate absent`() {
        val project = ProjectBuilder.builder().build()
        val delegate = project.providers.gradleProperty("absent.base64")
        val provider = PropertyResolver.StringProvider(delegate)

        assertThat(provider.base64Decode().isPresent).isFalse()
    }

    @Test
    @SetEnvironmentVariable(key = "MY_SECRET", value = "aGVsbG8gd29ybGQ=")
    fun `base64Decode via Resolver and env var`() {
        val project = ProjectBuilder.builder().build()
        val resolver = PropertyResolver(project.providers)

        val decoded = resolver.get("MY_SECRET").base64Decode().get()

        assertThat(decoded).isEqualTo("hello world")
    }

    @Test
    @SetSystemProperty(key = "my.secret", value = "aGVsbG8gd29ybGQ=")
    fun `base64Decode via Resolver and system property`() {
        val project = ProjectBuilder.builder().build()
        val resolver = PropertyResolver(project.providers)

        val decoded = resolver.get("my.secret").base64Decode().get()

        assertThat(decoded).isEqualTo("hello world")
    }

    @Test
    fun `base64Decode propagates Provider semantics via map`() {
        val project = ProjectBuilder.builder().build()
        val encoded = Base64.getEncoder().encodeToString("gradle".toByteArray())
        val delegate = project.providers.provider { encoded }
        val provider = PropertyResolver.StringProvider(delegate)

        val upper = provider.base64Decode().map { it.uppercase() }

        assertThat(upper.get()).isEqualTo("GRADLE")
    }

    @Test
    fun `StringProvider alias resolves`() {
        val project = ProjectBuilder.builder().build()
        val delegate = project.providers.provider { "hello" }
        val viaAlias = StringProvider(delegate)
        val viaNested = PropertyResolver.StringProvider(delegate)

        assertThat(viaAlias.get()).isEqualTo(viaNested.get())
    }

    // Resolver

    @Test
    @SetEnvironmentVariable(key = "MY_PROPERTY", value = "fromEnv")
    fun `resolves env var when queried with env name`() {
        val project = ProjectBuilder.builder().build()
        val resolver = PropertyResolver(project.providers)

        val provider = resolver.get("MY_PROPERTY")

        assertThat(provider.isPresent).isTrue()
        assertThat(provider.get()).isEqualTo("fromEnv")
    }

    @Test
    @SetEnvironmentVariable(key = "MY_PROPERTY", value = "fromEnv")
    fun `resolves env var when queried with dot property name`() {
        val project = ProjectBuilder.builder().build()
        val resolver = PropertyResolver(project.providers)

        val provider = resolver.get("my.property")

        assertThat(provider.get()).isEqualTo("fromEnv")
    }

    @Test
    @SetEnvironmentVariable(key = "MY_PROPERTY", value = "fromEnv")
    fun `resolves env var when queried with camelCase`() {
        val project = ProjectBuilder.builder().build()
        val resolver = PropertyResolver(project.providers)

        val provider = resolver.get("myProperty")

        assertThat(provider.get()).isEqualTo("fromEnv")
    }

    @Test
    @SetEnvironmentVariable(key = "MY_PROPERTY", value = "fromEnv")
    fun `resolves env var when queried with kebab-case`() {
        val project = ProjectBuilder.builder().build()
        val resolver = PropertyResolver(project.providers)

        val provider = resolver.get("my-property")

        assertThat(provider.get()).isEqualTo("fromEnv")
    }

    @Test
    @SetSystemProperty(key = "my.property", value = "fromSys")
    fun `resolves system property when queried with dot name`() {
        val project = ProjectBuilder.builder().build()
        val resolver = PropertyResolver(project.providers)

        assertThat(resolver.get("my.property").get()).isEqualTo("fromSys")
    }

    @Test
    @SetSystemProperty(key = "my.property", value = "fromSys")
    fun `resolves system property when queried with env name`() {
        val project = ProjectBuilder.builder().build()
        val resolver = PropertyResolver(project.providers)

        assertThat(resolver.get("MY_PROPERTY").get()).isEqualTo("fromSys")
    }

    @Test
    @SetSystemProperty(key = "my.property", value = "fromSys")
    fun `resolves system property when queried with camelCase`() {
        val project = ProjectBuilder.builder().build()
        val resolver = PropertyResolver(project.providers)

        assertThat(resolver.get("myProperty").get()).isEqualTo("fromSys")
    }

    @Test
    fun `resolves gradle property when queried directly`() {
        val project = projectWithGradleProperty("my.property", "fromGradle")
        val resolver = PropertyResolver(project.providers)

        assertThat(resolver.get("my.property").get()).isEqualTo("fromGradle")
    }

    @Test
    fun `resolves gradle property when queried with env name`() {
        val project = projectWithGradleProperty("my.property", "fromGradle")
        val resolver = PropertyResolver(project.providers)

        assertThat(resolver.get("MY_PROPERTY").get()).isEqualTo("fromGradle")
    }

    @Test
    fun `resolves gradle property when queried with camelCase`() {
        val project = projectWithGradleProperty("my.property", "fromGradle")
        val resolver = PropertyResolver(project.providers)

        assertThat(resolver.get("myProperty").get()).isEqualTo("fromGradle")
    }

    @Test
    @SetEnvironmentVariable(key = "MY_PROPERTY", value = "fromEnv")
    @SetSystemProperty(key = "my.property", value = "fromSys")
    fun `prefers env over system property`() {
        val project = projectWithGradleProperty("my.property", "fromGradle")
        val resolver = PropertyResolver(project.providers)

        assertThat(resolver.get("my.property").get()).isEqualTo("fromEnv")
    }

    @Test
    @SetSystemProperty(key = "my.property", value = "fromSys")
    fun `prefers system property over gradle property`() {
        val project = projectWithGradleProperty("my.property", "fromGradle")
        val resolver = PropertyResolver(project.providers)

        assertThat(resolver.get("my.property").get()).isEqualTo("fromSys")
    }

    @Test
    fun `returns absent when nothing set`() {
        val project = ProjectBuilder.builder().build()
        val resolver = PropertyResolver(project.providers)

        val provider = resolver.get("my.property")

        assertThat(provider.isPresent).isFalse()
    }

    @Test
    fun `throws on blank name`() {
        val project = ProjectBuilder.builder().build()
        val resolver = PropertyResolver(project.providers)

        assertThatThrownBy { resolver.get("  ") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @ParameterizedTest
    @CsvSource(
        "myProperty, MY_PROPERTY",
        "my.property, MY_PROPERTY",
        "my-property, MY_PROPERTY",
        "MY_PROPERTY, MY_PROPERTY",
        "myPropertyName, MY_PROPERTY_NAME",
        "my.property.name, MY_PROPERTY_NAME",
    )
    fun `toEnvKey conversions`(
        input: String,
        expected: String,
    ) {
        assertThat(PropertyResolver.toEnvKey(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "MY_PROPERTY, my.property",
        "myProperty, my.property",
        "my-property, my.property",
        "my.property, my.property",
        "MY_PROPERTY_NAME, my.property.name",
        "myPropertyName, my.property.name",
    )
    fun `toPropertyKey conversions`(
        input: String,
        expected: String,
    ) {
        assertThat(PropertyResolver.toPropertyKey(input)).isEqualTo(expected)
    }
}
