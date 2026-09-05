package com.mreil.gradletest.project

import com.mreil.gradletest.project.assertj.assertSoftly
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ProbeTaskTest {
    @Test
    fun `script renders register block relative to column zero`() {
        val probe =
            probeTask("verifyPublish") {
                expect("HAS_PUBLISH", "tasks.findByName(\"publish\") != null", "true")
            }

        assertThat(probe.script()).isEqualTo(
            """
            tasks.register("verifyPublish") {
                notCompatibleWithConfigurationCache("Probe task uses Task.project at execution time")
                doLast {
                    println("HAS_PUBLISH=" + (tasks.findByName("publish") != null))
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `script keeps probe order`() {
        val probe =
            probeTask("verify") {
                expect("B_KEY", "b()", "1")
                expect("A_KEY", "a()", "2")
            }

        assertThat(probe.script()).contains("B_KEY=").contains("A_KEY=")
        assertThat(probe.script().indexOf("B_KEY=")).isLessThan(probe.script().indexOf("A_KEY="))
    }

    @Test
    fun `assertOutput passes when all lines present`() {
        val probe =
            probeTask("verify") {
                expect("HAS_PUBLISH", "tasks.findByName(\"publish\") != null", "true")
            }

        assertSoftly { softly ->
            probe.assertOutput(softly, "some output\nHAS_PUBLISH=true\nmore output")
        }
    }

    @Test
    fun `assertOutput collects every missing line`() {
        val probe =
            probeTask("verify") {
                expect("FIRST", "a()", "1")
                expect("SECOND", "b()", "2")
            }

        assertThatThrownBy {
            assertSoftly { softly ->
                probe.assertOutput(softly, "FIRST=1")
            }
        }.hasMessageContaining("SECOND=2")
    }

    @Test
    fun `taskExists renders findByName probe defaulting to true`() {
        val probe =
            probeTask("verify") {
                taskExists("HAS_PUBLISH", "publish")
            }

        assertThat(probe.script()).contains("println(\"HAS_PUBLISH=\" + (tasks.findByName(\"publish\") != null))")
        assertSoftly { softly ->
            probe.assertOutput(softly, "HAS_PUBLISH=true")
        }
    }

    @Test
    fun `taskExists supports child scope and negative expectation`() {
        val probe =
            probeTask("verify") {
                taskExists("CHILD_HAS_TASK", "generateJreleaserConfig", expected = false, inProject = ":child")
            }

        assertThat(probe.script()).contains(
            "println(\"CHILD_HAS_TASK=\" + " +
                "(project.findProject(\":child\")!!.tasks.findByName(\"generateJreleaserConfig\") != null))",
        )
        assertSoftly { softly ->
            probe.assertOutput(softly, "CHILD_HAS_TASK=false")
        }
    }

    @Test
    fun `extensionExists renders findByName probe`() {
        val probe =
            probeTask("verify") {
                extensionExists("HAS_EASY", "easy")
            }

        assertThat(probe.script()).contains("println(\"HAS_EASY=\" + (project.extensions.findByName(\"easy\") != null))")
        assertSoftly { softly ->
            probe.assertOutput(softly, "HAS_EASY=true")
        }
    }

    @Test
    fun `expectAbsent passes when line missing and collects when present`() {
        val probe =
            probeTask("verify") {
                expect("CHILD_HAS_STAGING", "childRepos.contains(\"mavenStaging\")", "false")
                expectAbsent("CHILD_REPOS", "childRepos.joinToString()", "mavenStaging")
            }

        assertSoftly { softly ->
            probe.assertOutput(softly, "CHILD_HAS_STAGING=false\nCHILD_REPOS=other")
        }

        assertThatThrownBy {
            assertSoftly { softly ->
                probe.assertOutput(softly, "CHILD_HAS_STAGING=false\nCHILD_REPOS=mavenStaging")
            }
        }.hasMessageContaining("CHILD_REPOS=mavenStaging")
    }

    @Test
    fun `prelude lines render before probes`() {
        val probe =
            probeTask("verify") {
                prelude("val task = tasks.findByName(\"publish\")")
                expect("HAS_PUBLISH", "task != null", "true")
            }

        assertThat(probe.script()).isEqualTo(
            """
            tasks.register("verify") {
                notCompatibleWithConfigurationCache("Probe task uses Task.project at execution time")
                doLast {
                    val task = tasks.findByName("publish")
                    println("HAS_PUBLISH=" + (task != null))
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `extensionExistsByType renders findByType probe`() {
        val probe =
            probeTask("verify") {
                extensionExistsByType("HAS_EASY", "com.mreil.easy.EasyExtension")
            }

        assertThat(probe.script()).contains(
            "println(\"HAS_EASY=\" + (project.extensions.findByType(com.mreil.easy.EasyExtension::class.java) != null))",
        )
        assertSoftly { softly ->
            probe.assertThatMissingKey()
        }
    }

    private fun ProbeTask.assertThatMissingKey() {
        assertThatThrownBy {
            assertSoftly { softly ->
                assertOutput(softly, "HAS_EASY=false")
            }
        }.hasMessageContaining("HAS_EASY=true")
    }
}
