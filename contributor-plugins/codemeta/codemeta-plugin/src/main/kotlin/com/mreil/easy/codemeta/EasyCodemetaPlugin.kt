package com.mreil.easy.codemeta

import com.mreil.easy.AbstractEasyProjectPlugin
import com.mreil.easy.EasyExtension
import com.mreil.easy.EnabledBy
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

/**
 * Easy plugin that handles CodeMeta generation.
 *
 * Reads `codemeta.json` from the root project directory via [CodemetaService]
 * (hidden, not on public extension) and ensures an initial file exists.
 * The file is mapped to [Codemeta] via Jackson (`@JsonProperty` for `@context`/`@type`).
 */
@EnabledBy(EasyCodemetaExtension::class)
class EasyCodemetaPlugin : AbstractEasyProjectPlugin() {
    /**
     * Registers the shared [CodemetaService] eagerly so other contributors
     * (e.g. publish POM configuration) can resolve Codemeta during
     * `afterEvaluate`, regardless of contributor application order.
     *
     * Only wiring — parameters stay lazy [org.gradle.api.provider.Provider]s and
     * task registration remains in [afterEnabled] behind the enabled flag.
     */
    @Suppress("ReturnCount")
    override fun init(target: Project) {
        if (target != target.rootProject) return
        val easy = target.extensions.findByType(EasyExtension::class.java) as? ExtensionAware ?: return
        val codemetaExt = easy.extensions.findByType(EasyCodemetaExtension::class.java) as? DefaultEasyCodemetaExtension ?: return

        val codemetaFile =
            codemetaExt.filename.map {
                target.rootProject.layout.projectDirectory
                    .file(it)
            }

        target.gradle.sharedServices.registerIfAbsent("codemeta", CodemetaService::class.java) {
            it.parameters.codemetaFile.set(codemetaFile)
        }
    }

    @Suppress("ReturnCount")
    override fun afterEnabled(target: Project) {
        if (target != target.rootProject) return
        val easy = target.extensions.findByType(EasyExtension::class.java) as? ExtensionAware ?: return
        val codemetaExt = easy.extensions.findByType(EasyCodemetaExtension::class.java) as? DefaultEasyCodemetaExtension ?: return

        val codemetaFile =
            codemetaExt.filename.map {
                target.rootProject.layout.projectDirectory
                    .file(it)
            }

        val generateTask =
            target.tasks.register("generateCodemeta", GenerateCodemetaTask::class.java) { task ->
                task.outputFile.set(codemetaFile)
                task.projectName.set(target.provider { target.name })
                task.projectVersion.set(target.provider { target.version.toString() })
                task.projectDescription.set(
                    target.provider { target.description ?: "TODO: Add description - replace with project description" },
                )
                task.onlyIf {
                    !task.outputFile
                        .get()
                        .asFile
                        .exists()
                }
            }

        // Auto-wire regardless of which task is invoked - every task depends on generateCodemeta if file missing.
        // `onlyIf` ensures no work when file already exists; failure after creation is intentional.
        target.tasks.configureEach {
            if (it.name != "generateCodemeta") {
                it.dependsOn(generateTask)
            }
        }
    }
}
