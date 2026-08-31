package com.mreil.easy

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import javax.inject.Inject

class ExtensionCopierTest {
    private fun newExtension(): EasyExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create(
            EasyExtension::class.java,
            EasyExtension.name,
            DefaultEasyExtension::class.java,
        )
    }

    @Test
    fun `copies property convention lazily`() {
        val from = newTestExtension()
        val to = newTestExtension()
        from.deep.set("initial")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.deep.get()).isEqualTo("initial")
            from.deep.set("updated")
            softly.assertThat(to.deep.get()).isEqualTo("updated")
        }
    }

    @Test
    fun `copies property convention`() {
        val from = newTestExtension()
        val to = newTestExtension()
        from.deep.set("initial")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.deep.get()).isEqualTo("initial")
        }
    }

    @Test
    fun `to can override convention`() {
        val from = newTestExtension()
        val to = newTestExtension()
        from.deep.set("initial")
        ExtensionCopier.copy(from, to)
        to.deep.set("overridden")

        assertSoftly { softly ->
            softly.assertThat(to.deep.get()).isEqualTo("overridden")
            softly.assertThat(from.deep.get()).isEqualTo("initial")
        }
    }

    @Test
    fun `copies EasyExtension without error`() {
        val from = newExtension()
        val to = newExtension()

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to).isNotNull
        }
    }

    @Test
    fun `READ_ONLY property copies convention`() {
        val from = newTestExtension()
        val to = newTestExtension()
        from.readOnly.set("original")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.readOnly.get()).isEqualTo("original")
            from.readOnly.set("updated")
            softly.assertThat(to.readOnly.get()).isEqualTo("updated")
        }
    }

    @Test
    fun `READ_ONLY property fails on set and convention`() {
        val from = newTestExtension()
        val to = newTestExtension()
        from.readOnly.set("original")
        ExtensionCopier.copy(from, to)

        assertThatThrownBy { to.readOnly.set("new") }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { to.readOnly.convention("new") }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { to.readOnly.value("new") }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `DEEP is default when annotation absent`() {
        val from = newTestExtension()
        val to = newTestExtension()
        from.deep.set("initial")
        ExtensionCopier.copy(from, to)
        // DEEP allows override
        to.deep.set("overridden")
        assertSoftly { softly ->
            softly.assertThat(to.deep.get()).isEqualTo("overridden")
        }
    }

    private fun newTestExtension(): TestCopyExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create(
            TestCopyExtension::class.java,
            "testCopy",
            DefaultTestCopyExtension::class.java,
        )
    }

    @Test
    fun `copies all Property members via reflection`() {
        val from = newTestExtension()
        val to = newTestExtension()
        from.deep.set("deepVal")
        from.readOnly.set("readOnlyVal")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.deep.get()).isEqualTo("deepVal")
            softly.assertThat(to.readOnly.get()).isEqualTo("readOnlyVal")
        }
    }

    @Test
    fun `copies DomainObjectCollection items via DEEP copy by default`() {
        val from = newCollectionTestExtension()
        val to = newCollectionTestExtension()
        from.items.addAll(listOf("alpha", "beta"))

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.items).containsExactlyInAnyOrder("alpha", "beta")
            to.items.add("gamma")
            softly.assertThat(to.items).containsExactlyInAnyOrder("alpha", "beta", "gamma")
            softly.assertThat(from.items).containsExactlyInAnyOrder("alpha", "beta")
        }
    }

    @Test
    fun `copies DomainObjectCollection items with explicit DEEP annotation`() {
        val from = newCollectionTestExtension()
        val to = newCollectionTestExtension()
        from.deepItems.add("deepItem")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.deepItems).containsExactly("deepItem")
        }
    }

    @Test
    fun `copies DomainObjectCollection items preserving existing items in target`() {
        val from = newCollectionTestExtension()
        val to = newCollectionTestExtension()
        to.items.add("existing")
        from.items.add("incoming")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.items).containsExactlyInAnyOrder("existing", "incoming")
            softly.assertThat(from.items).containsExactly("incoming")
        }
    }

    @Test
    fun `DomainObjectCollection with READ_ONLY annotation fails`() {
        val from = newReadOnlyCollectionTestExtension()
        val to = newReadOnlyCollectionTestExtension()
        from.readOnlyItems.add("item")

        assertThatThrownBy { ExtensionCopier.copy(from, to) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("DomainObjectCollection 'readOnlyItems' only supports DEEP, was READ_ONLY")
    }

    @Test
    fun `copies NamedDomainObjectContainer items via DEEP copy`() {
        val from = newCollectionTestExtension()
        val to = newCollectionTestExtension()
        from.container.create("itemA")
        from.container.create("itemB")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.container.names).containsExactlyInAnyOrder("itemA", "itemB")
        }
    }

    @Test
    fun `copies nested CanBeCopied extension without CopyMode annotation by default`() {
        val from = newNestedExtension()
        val to = newNestedExtension()
        from.inner.innerProp.set("innerVal")
        from.inner.deep.set("deepVal")
        from.inner.readOnly.set("readOnlyVal")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.inner.innerProp.get()).isEqualTo("innerVal")
            softly.assertThat(to.inner.deep.get()).isEqualTo("deepVal")
            softly.assertThat(to.inner.readOnly.get()).isEqualTo("readOnlyVal")

            from.inner.innerProp.set("updatedInnerVal")
            softly.assertThat(to.inner.innerProp.get()).isEqualTo("updatedInnerVal")

            to.inner.deep.set("overriddenDeepVal")
            softly.assertThat(to.inner.deep.get()).isEqualTo("overriddenDeepVal")
            softly.assertThat(from.inner.deep.get()).isEqualTo("deepVal")
        }

        assertThatThrownBy { to.inner.readOnly.set("fail") }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `nested CanBeCopied with READ_ONLY annotation fails`() {
        val from = newReadOnlyNestedExtension()
        val to = newReadOnlyNestedExtension()
        from.inner.innerProp.set("item")

        assertThatThrownBy { ExtensionCopier.copy(from, to) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("CanBeCopied 'inner' only supports DEEP, was READ_ONLY")
    }

    @Test
    fun `ignores and logs unsupported immutable property type without failing`() {
        val from = newUnsupportedTypeExtension()
        val to = newUnsupportedTypeExtension()
        from.supported.set("valid")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.supported.get()).isEqualTo("valid")
            softly.assertThat(to.unsupported).isEqualTo("default")
        }
    }

    @Test
    fun `copies MutableKProperty that is not CanBeCopied`() {
        val from = newMutablePropertyExtension()
        val to = newMutablePropertyExtension()
        from.text = "customText"
        from.number = 42

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.text).isEqualTo("customText")
            softly.assertThat(to.number).isEqualTo(42)
        }
    }

    @Test
    fun `copies nullable MutableKProperty when source or target is null`() {
        val from = newMutablePropertyExtension()
        val to = newMutablePropertyExtension()
        from.nullableText = "fromValue"

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.nullableText).isEqualTo("fromValue")
        }

        from.nullableText = null
        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.nullableText).isNull()
        }
    }

    @Test
    fun `NONE MutableKProperty does not copy value`() {
        val from = newMutablePropertyExtension()
        val to = newMutablePropertyExtension()
        from.disabled = "custom"

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.disabled).isEqualTo("default")
        }
    }

    @Test
    fun `READ_ONLY MutableKProperty fails`() {
        val from = newReadOnlyMutablePropertyExtension()
        val to = newReadOnlyMutablePropertyExtension()
        from.readOnly = "custom"

        assertThatThrownBy { ExtensionCopier.copy(from, to) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Mutable property 'readOnly' only supports DEEP, was READ_ONLY")
    }

    @Test
    fun `copies MutableKProperty holding CanBeCopied via deep copy`() {
        val from = newMutableNestedExtension()
        val to = newMutableNestedExtension()
        val initialToInner = to.inner
        from.inner.innerProp.set("nestedVal")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.inner).isSameAs(initialToInner)
            softly.assertThat(to.inner.innerProp.get()).isEqualTo("nestedVal")
        }
    }

    @Test
    fun `NONE property does not copy convention`() {
        val from = newTestExtension()
        val to = newTestExtension()
        from.disabled.set("fromValue")
        to.disabled.set("toValue")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.disabled.get()).isEqualTo("toValue")
            from.disabled.set("updatedFromValue")
            softly.assertThat(to.disabled.get()).isEqualTo("toValue")
        }
    }

    @Test
    fun `NONE DomainObjectCollection does not copy items`() {
        val from = newCollectionTestExtension()
        val to = newCollectionTestExtension()
        from.noneItems.add("item")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.noneItems).isEmpty()
        }
    }

    @Test
    fun `NONE nested CanBeCopied does not copy nested extension`() {
        val from = newNoneNestedExtension()
        val to = newNoneNestedExtension()
        from.inner.innerProp.set("fromVal")
        to.inner.innerProp.set("toVal")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(to.inner.innerProp.get()).isEqualTo("toVal")
            from.inner.innerProp.set("updatedFromVal")
            softly.assertThat(to.inner.innerProp.get()).isEqualTo("toVal")
        }
    }

    private fun newCollectionTestExtension(): TestCollectionCopyExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create(
            TestCollectionCopyExtension::class.java,
            "testCollectionCopy",
            DefaultTestCollectionCopyExtension::class.java,
        )
    }

    private fun newReadOnlyCollectionTestExtension(): TestReadOnlyCollectionCopyExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create(
            TestReadOnlyCollectionCopyExtension::class.java,
            "testReadOnlyCollectionCopy",
            DefaultTestReadOnlyCollectionCopyExtension::class.java,
        )
    }

    private fun newNestedExtension(): NestedExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create(
            NestedExtension::class.java,
            "nestedExtension",
            NestedExtension::class.java,
        )
    }

    private fun newReadOnlyNestedExtension(): ReadOnlyNestedExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create(
            ReadOnlyNestedExtension::class.java,
            "readOnlyNestedExtension",
            ReadOnlyNestedExtension::class.java,
        )
    }

    private fun newNoneNestedExtension(): NoneNestedExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create(
            NoneNestedExtension::class.java,
            "noneNestedExtension",
            NoneNestedExtension::class.java,
        )
    }

    private fun newUnsupportedTypeExtension(): UnsupportedTypeExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create(
            UnsupportedTypeExtension::class.java,
            "unsupportedTypeExtension",
            DefaultUnsupportedTypeExtension::class.java,
        )
    }

    private fun newMutablePropertyExtension(): MutablePropertyExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create(
            MutablePropertyExtension::class.java,
            "mutablePropertyExtension",
            DefaultMutablePropertyExtension::class.java,
        )
    }

    private fun newReadOnlyMutablePropertyExtension(): ReadOnlyMutablePropertyExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create(
            ReadOnlyMutablePropertyExtension::class.java,
            "readOnlyMutablePropertyExtension",
            DefaultReadOnlyMutablePropertyExtension::class.java,
        )
    }

    @Test
    fun `copies child extensions attached to ExtensionAware`() {
        val from = newExtension()
        val to = newExtension()

        val fromChild = from.extensions.create(TestCopyExtension::class.java, "testChild", DefaultTestCopyExtension::class.java)
        val toChild = to.extensions.create(TestCopyExtension::class.java, "testChild", DefaultTestCopyExtension::class.java)

        fromChild.deep.set("childValue")

        ExtensionCopier.copy(from, to)

        assertSoftly { softly ->
            softly.assertThat(toChild.deep.get()).isEqualTo("childValue")
        }
    }

    private fun newMutableNestedExtension(): MutableNestedExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create(
            MutableNestedExtension::class.java,
            "mutableNestedExtension",
            MutableNestedExtension::class.java,
        )
    }
}

interface UnsupportedTypeExtension : CanBeCopied {
    val supported: Property<String>
    val unsupported: String
}

abstract class DefaultUnsupportedTypeExtension : UnsupportedTypeExtension {
    override val unsupported: String = "default"
}

interface MutablePropertyExtension : CanBeCopied {
    var text: String
    var number: Int
    var nullableText: String?

    @get:CopyMode(CopyMode.Mode.NONE)
    var disabled: String
}

abstract class DefaultMutablePropertyExtension : MutablePropertyExtension {
    override var text: String = "default"
    override var number: Int = 0
    override var nullableText: String? = null
    override var disabled: String = "default"
}

interface ReadOnlyMutablePropertyExtension : CanBeCopied {
    @get:CopyMode(CopyMode.Mode.READ_ONLY)
    var readOnly: String
}

abstract class DefaultReadOnlyMutablePropertyExtension : ReadOnlyMutablePropertyExtension {
    override var readOnly: String = "default"
}

abstract class MutableNestedExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) : CanBeCopied {
        var inner: NestedExtension.Inner = objects.newInstance(NestedExtension.Inner::class.java)
    }

interface NamedItem {
    val name: String
}

interface TestCollectionCopyExtension : CanBeCopied {
    val items: DomainObjectSet<String>

    @get:CopyMode(CopyMode.Mode.DEEP)
    val deepItems: DomainObjectSet<String>

    @get:CopyMode(CopyMode.Mode.NONE)
    val noneItems: DomainObjectSet<String>

    val container: NamedDomainObjectContainer<NamedItem>
}

abstract class DefaultTestCollectionCopyExtension : TestCollectionCopyExtension

interface TestReadOnlyCollectionCopyExtension : CanBeCopied {
    @get:CopyMode(CopyMode.Mode.READ_ONLY)
    val readOnlyItems: DomainObjectSet<String>
}

abstract class DefaultTestReadOnlyCollectionCopyExtension : TestReadOnlyCollectionCopyExtension

interface TestCopyExtension : CanBeCopied {
    @get:CopyMode(CopyMode.Mode.READ_ONLY)
    val readOnly: Property<String>

    val deep: Property<String>

    @get:CopyMode(CopyMode.Mode.NONE)
    val disabled: Property<String>
}

abstract class DefaultTestCopyExtension : TestCopyExtension

abstract class NestedExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) : CanBeCopied {
        val inner: Inner = objects.newInstance(Inner::class.java)

        abstract class Inner : TestCopyExtension {
            abstract val innerProp: Property<String>
        }
    }

abstract class ReadOnlyNestedExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) : CanBeCopied {
        @get:CopyMode(CopyMode.Mode.READ_ONLY)
        val inner: NestedExtension.Inner = objects.newInstance(NestedExtension.Inner::class.java)
    }

abstract class NoneNestedExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) : CanBeCopied {
        @get:CopyMode(CopyMode.Mode.NONE)
        val inner: NestedExtension.Inner = objects.newInstance(NestedExtension.Inner::class.java)
    }
