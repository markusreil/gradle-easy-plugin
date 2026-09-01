package com.mreil.easy

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

/**
 * Shared test fixtures for `AbstractEasy*Plugin` lifecycle tests.
 *
 * Provides minimal `EasyExtension`/`EasyPluginExtension` doubles, controllable `CanBeEnabled`
 * extensions, and `Proxy`-based `Project`/`Settings`/`Gradle` fakes that capture
 * `afterEvaluate`/`settingsEvaluated` actions so `init` vs `afterEnabled` can be asserted
 * without a full Gradle build.
 */
abstract class SimpleEasyExtension : EasyExtension {
    companion object : Named {
        override val name: String = EasyExtension.name
    }
}

abstract class TestEnabledExtension :
    EasyPluginExtension,
    CanBeEnabled {
    companion object : Named {
        override val name: String = "testEnabled"
    }
}

abstract class OtherExtension : EasyPluginExtension {
    companion object : Named {
        override val name: String = "other"
    }
}

@EnabledBy(TestEnabledExtension::class)
class EnabledProjectPlugin : AbstractEasyProjectPlugin() {
    var initCalled = false
    var afterEnabledCalled = false

    override fun init(target: Project) {
        initCalled = true
    }

    override fun afterEnabled(target: Project) {
        afterEnabledCalled = true
    }
}

class NoAnnotationProjectPlugin : AbstractEasyProjectPlugin() {
    var initCalled = false
    var afterEnabledCalled = false

    override fun init(target: Project) {
        initCalled = true
    }

    override fun afterEnabled(target: Project) {
        afterEnabledCalled = true
    }
}

@EnabledBy(OtherExtension::class)
class BadProjectPlugin : AbstractEasyProjectPlugin()

@EnabledBy(TestEnabledExtension::class)
class EnabledSettingsPlugin : AbstractEasySettingsPlugin() {
    var initCalled = false
    var afterEnabledCalled = false

    override fun init(target: Settings) {
        initCalled = true
    }

    override fun afterEnabled(target: Settings) {
        afterEnabledCalled = true
    }
}

class NoAnnotationSettingsPlugin : AbstractEasySettingsPlugin() {
    var initCalled = false
    var afterEnabledCalled = false

    override fun init(target: Settings) {
        initCalled = true
    }

    override fun afterEnabled(target: Settings) {
        afterEnabledCalled = true
    }
}

@EnabledBy(OtherExtension::class)
class BadSettingsPlugin : AbstractEasySettingsPlugin()

internal fun createEasy(
    holder: Project,
    vararg extClasses: KClass<out EasyPluginExtension>,
): ExtensionAware {
    holder.extensions.create(EasyExtension::class.java, EasyExtension.name, SimpleEasyExtension::class.java)
    val easy = holder.extensions.getByName(EasyExtension.name) as ExtensionAware
    for (kClass in extClasses) {
        easy.extensions.create(Named.extensionName(kClass), kClass.java)
    }
    return easy
}

internal fun newProjectProxy(
    holder: Project,
    captured: MutableList<Action<Project>>,
): Project =
    Proxy.newProxyInstance(
        Project::class.java.classLoader,
        arrayOf(Project::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getExtensions" -> holder.extensions
            "afterEvaluate" -> {
                @Suppress("UNCHECKED_CAST")
                captured.add(args[0] as Action<Project>)
                null
            }
            "getName" -> holder.name
            else -> null
        }
    } as Project

internal fun newSettingsProxy(
    holder: Project,
    captured: MutableList<Action<Settings>>,
): Settings =
    Proxy.newProxyInstance(
        Settings::class.java.classLoader,
        arrayOf(Settings::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getExtensions" -> holder.extensions
            "getGradle" -> newGradleProxy(captured)
            else -> null
        }
    } as Settings

internal fun newThrowingSettingsProxy(holder: Project): Settings =
    Proxy.newProxyInstance(
        Settings::class.java.classLoader,
        arrayOf(Settings::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getExtensions" -> holder.extensions
            "getGradle" -> error("gradle unavailable")
            else -> null
        }
    } as Settings

internal fun newGradleProxy(captured: MutableList<Action<Settings>>): Gradle =
    Proxy.newProxyInstance(
        Gradle::class.java.classLoader,
        arrayOf(Gradle::class.java),
    ) { _, method, args ->
        when (method.name) {
            "settingsEvaluated" -> {
                @Suppress("UNCHECKED_CAST")
                captured.add(args[0] as Action<Settings>)
                null
            }
            else -> null
        }
    } as Gradle
