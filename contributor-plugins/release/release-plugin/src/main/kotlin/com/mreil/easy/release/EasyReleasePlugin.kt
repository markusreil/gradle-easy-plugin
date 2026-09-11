package com.mreil.easy.release

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.ApplyToSubprojects
import com.mreil.easy.EnabledBy
import org.gradle.api.Project

@EnabledBy(EasyReleaseExtension::class)
@ApplyToSubprojects
class EasyReleasePlugin : AbstractEasyProjectPlugin() {
    override fun init(target: Project) {
    }

    override fun afterEnabled(target: Project) {
    }
}
