package com.mreil.easy.fixtures

import com.mreil.easy.EasyPluginContributor
import com.mreil.easy.EasyPluginExtension
import kotlin.reflect.KClass

/** Contributor providing [DummyExtension] for functional tests. */
class DummyContributor : EasyPluginContributor {
    override fun pluginExtensions(): Set<KClass<out EasyPluginExtension>> = setOf(DummyExtension::class)
}
