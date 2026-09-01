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
import kotlin.reflect.full.memberProperties

/** Copies any [CanBeCopied] extension property conventions via reflection. */
@Suppress("TooManyFunctions")
object ExtensionCopier {
    private val log: Logger = Logging.getLogger(ExtensionCopier::class.java)

    fun copy(
        from: CanBeCopied,
        to: CanBeCopied,
    ) {
        require(from::class == to::class) { "from and to must be same type" }
        @Suppress("UNCHECKED_CAST")
        (from::class as KClass<Any>)
            .memberProperties
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

    @Suppress("ReturnCount")
    private fun copyMember(
        prop: KProperty1<Any, *>,
        from: CanBeCopied,
        to: CanBeCopied,
    ) {
        if (prop.isExtensionsProperty()) return
        val mode = modeOf(prop, from) ?: CopyMode.Mode.DEEP
        if (mode == CopyMode.Mode.NONE) return

        val fromRaw = prop.get(from)
        val toRaw = prop.get(to)

        if (fromRaw != null && toRaw != null) {
            dispatchCopy(prop, fromRaw, toRaw, mode, to)
            return
        }

        if (prop is KMutableProperty1<Any, *> && fromRaw !is CanBeCopied) {
            copyMutableProperty(prop, to, fromRaw, mode)
        }
    }

    private fun dispatchCopy(
        prop: KProperty1<Any, *>,
        fromRaw: Any,
        toRaw: Any,
        mode: CopyMode.Mode,
        to: CanBeCopied,
    ) {
        when (val pair = FromTo.of(fromRaw, toRaw)) {
            is FromTo.PropertyPair -> copyProperty(pair, mode)
            is FromTo.CollectionPair -> copyCollection(pair, prop.name, mode)
            is FromTo.CanBeCopiedPair -> copyNested(pair, prop.name, mode)
            FromTo.None -> copyOther(prop, to, fromRaw, mode)
        }
    }

    private fun KProperty1<*, *>.isExtensionsProperty(): Boolean = name == "extensions"

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

    @Suppress("UNCHECKED_CAST")
    private fun copyProperty(
        pair: FromTo.PropertyPair,
        mode: CopyMode.Mode,
    ) {
        val fromValue = pair.from as Property<Any>
        val toValue = pair.to as Property<Any>
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
        pair: FromTo.CollectionPair,
        propName: String,
        mode: CopyMode.Mode,
    ) {
        requireDeep(mode, propName, "DomainObjectCollection")
        (pair.to as DomainObjectCollection<Any>).addAll(pair.from as Collection<Any>)
    }

    private fun copyNested(
        pair: FromTo.CanBeCopiedPair,
        propName: String,
        mode: CopyMode.Mode,
    ) {
        requireDeep(mode, propName, "CanBeCopied")
        copy(pair.from, pair.to)
    }

    @Suppress("UNCHECKED_CAST")
    private fun copyMutableProperty(
        prop: KProperty1<Any, *>,
        to: CanBeCopied,
        fromRaw: Any?,
        mode: CopyMode.Mode,
    ) {
        requireDeep(mode, prop.name, "Mutable property")
        (prop as KMutableProperty1<Any, Any?>).set(to, fromRaw)
    }

    private fun requireDeep(
        mode: CopyMode.Mode,
        propName: String,
        kind: String,
    ) {
        require(mode == CopyMode.Mode.DEEP) {
            "$kind '$propName' only supports DEEP, was $mode"
        }
    }

    /**
     * Resolves [CopyMode] for [prop] by checking Java, Kotlin direct, then supertype sources in order.
     *
     * Split into three helpers because Gradle decorates extensions as Java proxies (getters/fields),
     * Kotlin may annotate the property/getter/return-type directly, and annotations may live on
     * inherited members. Each layer needs different reflection (Java `declaredMethods` vs Kotlin
     * `findAnnotation` vs `allSupertypes` traversal).
     */
    private fun modeOf(
        prop: KProperty1<Any, *>,
        from: CanBeCopied,
    ): CopyMode.Mode? =
        findJavaMode(prop, from)
            ?: findKotlinDirectMode(prop)
            ?: findSupertypeMode(prop, from)
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
            return when (from) {
                is Property<*> if to is Property<*> -> PropertyPair(from, to)
                is DomainObjectCollection<*> if to is DomainObjectCollection<*> -> CollectionPair(from, to)
                is CanBeCopied if to is CanBeCopied -> CanBeCopiedPair(from, to)
                else -> None
            }
        }
    }
}

/**
 * Finds [CopyMode] via Java reflection on [from.javaClass] hierarchy.
 *
 * Needed because Gradle decorates extensions (via `extensions.create`) with generated Java
 * proxies: `@get:CopyMode` on a Kotlin `var` may appear as a Java getter (`getProp`/`isProp`)
 * or field annotation, not on the Kotlin `KProperty` itself.
 */
private fun findJavaMode(
    prop: KProperty1<Any, *>,
    from: CanBeCopied,
): CopyMode.Mode? {
    val getterName = "get" + prop.name.replaceFirstChar { it.uppercase() }
    val isGetterName = "is" + prop.name.replaceFirstChar { it.uppercase() }
    return generateSequence<Class<*>>(from.javaClass) { it.superclass }
        .flatMap { sequenceOf(it) + it.interfaces.asSequence() }
        .firstNotNullOfOrNull { clazz ->
            clazz.declaredMethods
                .firstOrNull { it.name == getterName || it.name == isGetterName || it.name == prop.name }
                ?.getAnnotation(CopyMode::class.java)
                ?.value
                ?: clazz.declaredFields
                    .firstOrNull { it.name == prop.name }
                    ?.getAnnotation(CopyMode::class.java)
                    ?.value
        }
}

/**
 * Finds [CopyMode] directly on the Kotlin property.
 *
 * Covers the common case where the user annotates the property, its getter, or the return type:
 * `@get:CopyMode`, `@CopyMode` on `var`, or `@CopyMode` on the type classifier.
 */
private fun findKotlinDirectMode(prop: KProperty1<Any, *>): CopyMode.Mode? =
    prop.getter.findAnnotation<CopyMode>()?.value
        ?: prop.findAnnotation<CopyMode>()?.value
        ?: (prop.returnType.classifier as? KClass<*>)?.findAnnotation<CopyMode>()?.value

/**
 * Finds [CopyMode] on a supertype declaration of [prop].
 *
 * Handles the case where the annotation lives on an interface or abstract parent that
 * declares the property (e.g., shared base extension), not on the concrete `from` instance.
 * Wrapped in `runCatching` because `allSupertypes` traversal may hit synthetic members.
 */
private fun findSupertypeMode(
    prop: KProperty1<Any, *>,
    from: CanBeCopied,
): CopyMode.Mode? =
    runCatching {
        from::class
            .allSupertypes
            .mapNotNull { it.classifier as? KClass<*> }
            .flatMap { it.members }
            .filterIsInstance<KProperty1<Any, *>>()
            .firstOrNull { it.name == prop.name }
            ?.let { it.getter.findAnnotation<CopyMode>()?.value ?: it.findAnnotation<CopyMode>()?.value }
    }.getOrNull()
