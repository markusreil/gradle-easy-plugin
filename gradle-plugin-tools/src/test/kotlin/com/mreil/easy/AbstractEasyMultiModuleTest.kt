package com.mreil.easy

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Multi-module matrix for `AbstractEasyProjectPlugin` `enabled` handling.
 *
 * Each test builds a `root`+`sub` hierarchy via `ProjectBuilder`, creates `EasyExtension`
 * + `TestEnabledExtension` copies per project, then applies `EnabledProjectPlugin` through
 * `Proxy`-captured `afterEvaluate` to assert `init` is always eager while `afterEnabled`
 * respects each project's own `isEnabled` — covering `enabled`/`disabled` combos,
 * inheritance, and injection.
 */
class AbstractEasyMultiModuleTest {
    @Test
    fun `both enabled calls afterEnabled for root and child`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val sub =
            ProjectBuilder
                .builder()
                .withName("sub")
                .withParent(root)
                .build()
        createEasy(root, TestEnabledExtension::class)
        createEasy(sub, TestEnabledExtension::class)
        val rootEasy = root.extensions.getByName(EasyExtension.name) as ExtensionAware
        val subEasy = sub.extensions.getByName(EasyExtension.name) as ExtensionAware
        rootEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(true)
        subEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(true)

        val capturedRoot = mutableListOf<Action<Project>>()
        val capturedSub = mutableListOf<Action<Project>>()
        val rootProxy = newProjectProxy(root, capturedRoot)
        val subProxy = newProjectProxy(sub, capturedSub)

        val rootPlugin = EnabledProjectPlugin()
        val subPlugin = EnabledProjectPlugin()
        rootPlugin.apply(rootProxy)
        subPlugin.apply(subProxy)

        assertSoftly { softly ->
            softly.assertThat(rootPlugin.initCalled).isTrue()
            softly.assertThat(subPlugin.initCalled).isTrue()
            softly.assertThat(rootPlugin.afterEnabledCalled).isFalse()
            softly.assertThat(subPlugin.afterEnabledCalled).isFalse()
        }

        capturedRoot.single().execute(rootProxy)
        capturedSub.single().execute(subProxy)

        assertSoftly { softly ->
            softly.assertThat(rootPlugin.afterEnabledCalled).isTrue()
            softly.assertThat(subPlugin.afterEnabledCalled).isTrue()
        }
    }

    @Test
    fun `root enabled child disabled only root calls afterEnabled`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val sub =
            ProjectBuilder
                .builder()
                .withName("sub")
                .withParent(root)
                .build()
        createEasy(root, TestEnabledExtension::class)
        createEasy(sub, TestEnabledExtension::class)
        val rootEasy = root.extensions.getByName(EasyExtension.name) as ExtensionAware
        val subEasy = sub.extensions.getByName(EasyExtension.name) as ExtensionAware
        rootEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(true)
        subEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(false)

        val capturedRoot = mutableListOf<Action<Project>>()
        val capturedSub = mutableListOf<Action<Project>>()
        val rootProxy = newProjectProxy(root, capturedRoot)
        val subProxy = newProjectProxy(sub, capturedSub)

        val rootPlugin = EnabledProjectPlugin()
        val subPlugin = EnabledProjectPlugin()
        rootPlugin.apply(rootProxy)
        subPlugin.apply(subProxy)

        capturedRoot.single().execute(rootProxy)
        capturedSub.single().execute(subProxy)

        assertSoftly { softly ->
            softly.assertThat(rootPlugin.afterEnabledCalled).isTrue()
            softly.assertThat(subPlugin.afterEnabledCalled).isFalse()
        }
    }

    @Test
    fun `root disabled child enabled only child calls afterEnabled`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val sub =
            ProjectBuilder
                .builder()
                .withName("sub")
                .withParent(root)
                .build()
        createEasy(root, TestEnabledExtension::class)
        createEasy(sub, TestEnabledExtension::class)
        val rootEasy = root.extensions.getByName(EasyExtension.name) as ExtensionAware
        val subEasy = sub.extensions.getByName(EasyExtension.name) as ExtensionAware
        rootEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(false)
        subEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(true)

        val capturedRoot = mutableListOf<Action<Project>>()
        val capturedSub = mutableListOf<Action<Project>>()
        val rootProxy = newProjectProxy(root, capturedRoot)
        val subProxy = newProjectProxy(sub, capturedSub)

        val rootPlugin = EnabledProjectPlugin()
        val subPlugin = EnabledProjectPlugin()
        rootPlugin.apply(rootProxy)
        subPlugin.apply(subProxy)

        capturedRoot.single().execute(rootProxy)
        capturedSub.single().execute(subProxy)

        assertSoftly { softly ->
            softly.assertThat(rootPlugin.afterEnabledCalled).isFalse()
            softly.assertThat(subPlugin.afterEnabledCalled).isTrue()
        }
    }

    @Test
    fun `both disabled neither calls afterEnabled`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val sub =
            ProjectBuilder
                .builder()
                .withName("sub")
                .withParent(root)
                .build()
        createEasy(root, TestEnabledExtension::class)
        createEasy(sub, TestEnabledExtension::class)
        val rootEasy = root.extensions.getByName(EasyExtension.name) as ExtensionAware
        val subEasy = sub.extensions.getByName(EasyExtension.name) as ExtensionAware
        rootEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(false)
        subEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(false)

        val capturedRoot = mutableListOf<Action<Project>>()
        val capturedSub = mutableListOf<Action<Project>>()
        val rootProxy = newProjectProxy(root, capturedRoot)
        val subProxy = newProjectProxy(sub, capturedSub)

        val rootPlugin = EnabledProjectPlugin()
        val subPlugin = EnabledProjectPlugin()
        rootPlugin.apply(rootProxy)
        subPlugin.apply(subProxy)

        capturedRoot.single().execute(rootProxy)
        capturedSub.single().execute(subProxy)

        assertSoftly { softly ->
            softly.assertThat(rootPlugin.initCalled).isTrue()
            softly.assertThat(subPlugin.initCalled).isTrue()
            softly.assertThat(rootPlugin.afterEnabledCalled).isFalse()
            softly.assertThat(subPlugin.afterEnabledCalled).isFalse()
        }
    }

    @Test
    fun `child inherits disabled from parent via copy`() {
        val parentHolder = ProjectBuilder.builder().build()
        val parentEasy = createEasy(parentHolder, TestEnabledExtension::class)
        parentEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(false)

        val root = ProjectBuilder.builder().withName("root").build()
        val sub =
            ProjectBuilder
                .builder()
                .withName("sub")
                .withParent(root)
                .build()
        createEasy(root, TestEnabledExtension::class)
        createEasy(sub, TestEnabledExtension::class)
        val rootEasy = root.extensions.getByName(EasyExtension.name) as ExtensionAware
        val subEasy = sub.extensions.getByName(EasyExtension.name) as ExtensionAware
        rootEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(false)
        subEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(false)

        assertSoftly { softly ->
            softly.assertThat(rootEasy.extensions.getByType(TestEnabledExtension::class.java).isEnabled()).isFalse()
            softly.assertThat(subEasy.extensions.getByType(TestEnabledExtension::class.java).isEnabled()).isFalse()
        }

        val capturedRoot = mutableListOf<Action<Project>>()
        val capturedSub = mutableListOf<Action<Project>>()
        val rootProxy = newProjectProxy(root, capturedRoot)
        val subProxy = newProjectProxy(sub, capturedSub)

        val rootPlugin = EnabledProjectPlugin()
        val subPlugin = EnabledProjectPlugin()
        rootPlugin.apply(rootProxy)
        subPlugin.apply(subProxy)

        capturedRoot.single().execute(rootProxy)
        capturedSub.single().execute(subProxy)

        assertSoftly { softly ->
            softly.assertThat(rootPlugin.afterEnabledCalled).isFalse()
            softly.assertThat(subPlugin.afterEnabledCalled).isFalse()
        }
    }

    @Test
    fun `extension injection creates EasyExtension in subprojects`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val sub1 =
            ProjectBuilder
                .builder()
                .withName("sub1")
                .withParent(root)
                .build()
        val sub2 =
            ProjectBuilder
                .builder()
                .withName("sub2")
                .withParent(root)
                .build()
        createEasy(root, TestEnabledExtension::class)
        createEasy(sub1, TestEnabledExtension::class)
        createEasy(sub2, TestEnabledExtension::class)

        assertSoftly { softly ->
            softly.assertThat(sub1.extensions.findByName(EasyExtension.name)).isNotNull
            softly.assertThat(sub2.extensions.findByName(EasyExtension.name)).isNotNull
            val sub1Easy = sub1.extensions.getByName(EasyExtension.name) as ExtensionAware
            val sub2Easy = sub2.extensions.getByName(EasyExtension.name) as ExtensionAware
            softly.assertThat(sub1Easy.extensions.findByName(Named.extensionName(TestEnabledExtension::class))).isNotNull
            softly.assertThat(sub2Easy.extensions.findByName(Named.extensionName(TestEnabledExtension::class))).isNotNull
        }
    }

    @Test
    fun `init always called even when disabled`() {
        val root = ProjectBuilder.builder().withName("root").build()
        val sub =
            ProjectBuilder
                .builder()
                .withName("sub")
                .withParent(root)
                .build()
        createEasy(root, TestEnabledExtension::class)
        createEasy(sub, TestEnabledExtension::class)
        val rootEasy = root.extensions.getByName(EasyExtension.name) as ExtensionAware
        val subEasy = sub.extensions.getByName(EasyExtension.name) as ExtensionAware
        rootEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(false)
        subEasy.extensions
            .getByType(TestEnabledExtension::class.java)
            .enabled
            .set(false)

        val capturedRoot = mutableListOf<Action<Project>>()
        val capturedSub = mutableListOf<Action<Project>>()
        val rootProxy = newProjectProxy(root, capturedRoot)
        val subProxy = newProjectProxy(sub, capturedSub)

        val rootPlugin = EnabledProjectPlugin()
        val subPlugin = EnabledProjectPlugin()
        rootPlugin.apply(rootProxy)
        subPlugin.apply(subProxy)

        assertSoftly { softly ->
            softly.assertThat(rootPlugin.initCalled).isTrue()
            softly.assertThat(subPlugin.initCalled).isTrue()
        }

        capturedRoot.single().execute(rootProxy)
        capturedSub.single().execute(subProxy)

        assertSoftly { softly ->
            softly.assertThat(rootPlugin.afterEnabledCalled).isFalse()
            softly.assertThat(subPlugin.afterEnabledCalled).isFalse()
        }
    }
}
