package com.mreil.easy

import org.gradle.api.DomainObjectCollection
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.findAnnotation

/** Copies any [CanBeCopied] extension property conventions via reflection. */
object ExtensionCopier {
    private val log: Logger = Logging.getLogger(ExtensionCopier::class.java)

    fun copy(
        from: CanBeCopied,
        to: CanBeCopied,
    ) {
        require(from::class == to::class) { "from and to must be same type" }
        from::class
            .members
            .filterIsInstance<KProperty1<Any, *>>()
            .forEach { prop -> copyMember(prop, from, to) }

        if (from is ExtensionAware && to is ExtensionAware) {
            copyExtensionAware(from, to)
        }
    }

    private fun copyExtensionAware(
        from: ExtensionAware,
        to: ExtensionAware,
    ) {
        from.extensions.extensionsSchema.elements.forEach { schema ->
            val name = schema.name
            val fromChild = from.extensions.findByName(name)
            val toChild = to.extensions.findByName(name)
            if (fromChild is CanBeCopied && toChild is CanBeCopied) {
                copy(fromChild, toChild)
            }
        }
    }

    private fun copyMember(
        prop: KProperty1<Any, *>,
        from: CanBeCopied,
        to: CanBeCopied,
    ) {
        if (prop.name == "extensions") return
        val fromRaw = prop.get(from)
        val toRaw = prop.get(to)
        val explicitMode = modeOf(prop, from)
        val mode = explicitMode ?: CopyMode.Mode.DEEP
        if (mode == CopyMode.Mode.NONE) return

        if (fromRaw != null && toRaw != null) {
            when (val pair = FromTo.of(fromRaw, toRaw)) {
                is FromTo.PropertyPair -> copyProperty(pair.from, pair.to, mode)
                is FromTo.CollectionPair -> copyCollection(prop.name, pair.from, pair.to, mode)
                is FromTo.CanBeCopiedPair -> copyNested(prop.name, pair.from, pair.to, mode)
                FromTo.None -> copyOther(prop, to, fromRaw, mode)
            }
        } else if (prop is KMutableProperty1<Any, *> && fromRaw !is CanBeCopied) {
            copyMutableProperty(prop, to, fromRaw, mode)
        }
    }

    private fun copyOther(
        prop: KProperty1<Any, *>,
        to: CanBeCopied,
        fromRaw: Any,
        mode: CopyMode.Mode,
    ) {
        if (prop is KMutableProperty1<Any, *>) {
            copyMutableProperty(prop, to, fromRaw, mode)
        } else {
            log.warn("Unsupported type '${fromRaw::class.qualifiedName}' for property '${prop.name}'")
        }
    }

    private sealed interface FromTo {
        class PropertyPair(
            val from: Property<*>,
            val to: Property<*>,
        ) : FromTo

        class CollectionPair(
            val from: DomainObjectCollection<*>,
            val to: DomainObjectCollection<*>,
        ) : FromTo

        class CanBeCopiedPair(
            val from: CanBeCopied,
            val to: CanBeCopied,
        ) : FromTo

        object None : FromTo

        companion object {
            fun of(
                from: Any,
                to: Any,
            ): FromTo {
                require(from::class == to::class) {
                    "Type mismatch between 'from' (${from::class.qualifiedName}) and 'to' (${to::class.qualifiedName})"
                }
                return when {
                    from is Property<*> && to is Property<*> -> PropertyPair(from, to)
                    from is DomainObjectCollection<*> && to is DomainObjectCollection<*> -> CollectionPair(from, to)
                    from is CanBeCopied && to is CanBeCopied -> CanBeCopiedPair(from, to)
                    else -> None
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun copyProperty(
        from: Property<*>,
        to: Property<*>,
        mode: CopyMode.Mode,
    ) {
        val fromValue = from as Property<Any>
        val toValue = to as Property<Any>
        when (mode) {
            CopyMode.Mode.DEEP -> toValue.convention(fromValue)
            CopyMode.Mode.READ_ONLY -> {
                toValue.convention(fromValue)
                toValue.disallowChanges()
            }
            CopyMode.Mode.NONE -> Unit
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun copyCollection(
        propName: String,
        from: DomainObjectCollection<*>,
        to: DomainObjectCollection<*>,
        mode: CopyMode.Mode,
    ) {
        require(mode == CopyMode.Mode.DEEP) {
            "DomainObjectCollection '$propName' only supports DEEP, was $mode"
        }
        (to as DomainObjectCollection<Any>).addAll(from as Collection<Any>)
    }

    private fun copyNested(
        propName: String,
        from: CanBeCopied,
        to: CanBeCopied,
        mode: CopyMode.Mode,
    ) {
        require(mode == CopyMode.Mode.DEEP) {
            "CanBeCopied '$propName' only supports DEEP, was $mode"
        }
        copy(from, to)
    }

    @Suppress("UNCHECKED_CAST")
    private fun copyMutableProperty(
        prop: KProperty1<Any, *>,
        to: CanBeCopied,
        fromRaw: Any?,
        mode: CopyMode.Mode,
    ) {
        require(mode == CopyMode.Mode.DEEP) {
            "Mutable property '${prop.name}' only supports DEEP, was $mode"
        }
        (prop as KMutableProperty1<Any, Any?>).set(to, fromRaw)
    }

    private fun modeOf(
        prop: KProperty1<Any, *>,
        from: CanBeCopied,
    ): CopyMode.Mode? {
        val getterName = "get" + prop.name.replaceFirstChar { it.uppercase() }
        val isGetterName = "is" + prop.name.replaceFirstChar { it.uppercase() }

        val javaMode =
            generateSequence<Class<*>>(from.javaClass) { it.superclass }
                .flatMap { sequenceOf(it) + it.interfaces.asSequence() }
                .mapNotNull { clazz ->
                    clazz.declaredMethods
                        .firstOrNull {
                            it.name == getterName || it.name == isGetterName || it.name == prop.name
                        }?.getAnnotation(CopyMode::class.java)
                        ?.value
                        ?: clazz.declaredFields
                            .firstOrNull { it.name == prop.name }
                            ?.getAnnotation(CopyMode::class.java)
                            ?.value
                }.firstOrNull()

        return javaMode
            ?: prop.getter.findAnnotation<CopyMode>()?.value
            ?: prop.findAnnotation<CopyMode>()?.value
            ?: (prop.returnType.classifier as? KClass<*>)?.findAnnotation<CopyMode>()?.value
            ?: runCatching {
                from::class
                    .allSupertypes
                    .mapNotNull { it.classifier as? KClass<*> }
                    .flatMap { it.members }
                    .filterIsInstance<KProperty1<Any, *>>()
                    .firstOrNull { it.name == prop.name }
                    ?.let { it.getter.findAnnotation<CopyMode>()?.value ?: it.findAnnotation<CopyMode>()?.value }
            }.getOrNull()
    }
}
