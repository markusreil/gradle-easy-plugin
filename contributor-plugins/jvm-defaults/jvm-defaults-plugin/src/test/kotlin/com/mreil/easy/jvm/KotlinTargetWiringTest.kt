package com.mreil.easy.jvm

import com.mreil.easy.jvm.java.TargetCompatibilityWiring
import com.mreil.easy.jvm.kotlin.KotlinTargetWiring
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.junit.jupiter.api.Test

/** Exercises the KGP-reflective path of [KotlinTargetWiring] without a KGP dependency. */
class KotlinTargetWiringTest {
    @Test
    fun `pins jvmTarget and jdk release reflectively`() {
        System.setProperty(TargetCompatibilityWiring.PROPERTY_NAME, "11")
        try {
            val project = ProjectBuilder.builder().build()
            val kotlinExtension = FakeKotlinExtension(project.objects)
            project.extensions.add("kotlin", kotlinExtension)

            KotlinTargetWiring.configure(project)

            assertSoftly { softly ->
                softly
                    .assertThat(
                        kotlinExtension.compilerOptions.jvmTarget
                            .get()
                            .target,
                    ).isEqualTo("11")
                softly.assertThat(kotlinExtension.compilerOptions.jvmTarget.get()).isEqualTo(JvmTarget.JVM_11)
                softly
                    .assertThat(kotlinExtension.compilerOptions.freeCompilerArgs.get())
                    .containsExactly("-Xjdk-release=11")
            }
        } finally {
            System.clearProperty(TargetCompatibilityWiring.PROPERTY_NAME)
        }
    }

    @Test
    fun `does nothing without a java target version`() {
        val project = ProjectBuilder.builder().build()
        val kotlinExtension = FakeKotlinExtension(project.objects)
        project.extensions.add("kotlin", kotlinExtension)

        KotlinTargetWiring.configure(project)

        assertSoftly { softly ->
            softly.assertThat(kotlinExtension.compilerOptions.jvmTarget.orNull).isNull()
            softly.assertThat(kotlinExtension.compilerOptions.freeCompilerArgs.get()).isEmpty()
        }
    }

    @Test
    fun `fails fast when the kotlin extension lacks the expected methods`() {
        System.setProperty(TargetCompatibilityWiring.PROPERTY_NAME, "11")
        try {
            val project = ProjectBuilder.builder().build()
            project.extensions.add("kotlin", UnexpectedKotlinExtension())

            assertThatThrownBy { KotlinTargetWiring.configure(project) }
                .isInstanceOf(GradleException::class.java)
                .hasMessageContaining("KGP >= 1.8")
        } finally {
            System.clearProperty(TargetCompatibilityWiring.PROPERTY_NAME)
        }
    }

    private class FakeKotlinExtension(
        objects: ObjectFactory,
    ) : KotlinJvmExtension {
        override val compilerOptions: KotlinJvmCompilerOptions = FakeKotlinCompilerOptions(objects)
    }

    private class FakeKotlinCompilerOptions(
        objects: ObjectFactory,
    ) : KotlinJvmCompilerOptions {
        override val jvmTarget = objects.property(JvmTarget::class.java)
        override val freeCompilerArgs = objects.listProperty(String::class.java)
    }

    private class UnexpectedKotlinExtension
}
