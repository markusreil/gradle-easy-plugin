package com.mreil.easy.publish

import com.mreil.easy.codemeta.Codemeta
import com.mreil.easy.codemeta.CodemetaLicense
import com.mreil.easy.codemeta.EasyCodemeta
import com.mreil.easy.codemeta.EasyCodemetaExtension
import com.mreil.easy.codemeta.Person
import com.mreil.easy.isExtensionEnabled
import com.mreil.utils.hasGroup
import com.mreil.utils.hasVersion
import com.mreil.utils.isSpecified
import org.gradle.api.Project
import org.gradle.api.publish.Publication
import org.gradle.api.publish.maven.MavenPublication

/**
 * Normalises `maven-publish` publications to a consistent, publishable shape.
 *
 * For regular [MavenPublication]s the group/artifactId/version are taken from the
 * project (failing if `group`/`version` are unset), while plugin marker publications
 * keep their marker coordinates and only have the version enforced. In both cases a
 * POM is populated with name, description, URL, license, developers and SCM metadata
 * (overlaid from Codemeta when the codemeta extension is enabled, leniently omitted
 * when absent), and version mapping is set up to resolve versions from the runtime
 * classpath. Missing extended properties never fail here; the JReleaser task
 * validates Central requirements later.
 */
internal object MavenPublicationConfigurer {
    fun configure(
        target: Project,
        publication: Publication,
    ) {
        if (publication !is MavenPublication) return
        val isPluginMarker = publication.name.endsWith("PluginMarkerMaven")
        configureCoordinates(target, publication, isPluginMarker)
        configurePom(target, publication, resolveCodemeta(target))
        configureVersionMapping(publication)
    }

    private fun configureCoordinates(
        target: Project,
        publication: MavenPublication,
        isPluginMarker: Boolean,
    ) {
        if (!isPluginMarker) {
            val group = target.group.toString()
            if (!target.hasGroup()) {
                error("Project group must be set for publication ${publication.name} (e.g. group = \"com.example\")")
            }
            if (!publication.groupId.isSpecified()) {
                publication.groupId = group
            }
            if (publication.artifactId.isNullOrEmpty()) {
                publication.artifactId = target.name
            }
        }
        val version = target.version.toString()
        if (!target.hasVersion()) {
            val hint = if (isPluginMarker) "plugin marker publication" else "publication"
            error("Project version must be set for $hint ${publication.name} (e.g. version = \"1.0.0\")")
        }
        if (!publication.version.isSpecified()) {
            publication.version = version
        }
    }

    private fun configurePom(
        target: Project,
        publication: MavenPublication,
        codemeta: Codemeta?,
    ) {
        val scmBase = codemeta?.codeRepository ?: "https://github.com/mreil/gradle-easy-plugin-new"
        publication.pom { pom ->
            pom.name.set(codemeta?.name ?: target.name)
            pom.description.set(codemeta?.description ?: target.description ?: "Published via EasyPublishPlugin")
            pom.url.set(codemeta?.url ?: scmBase)
            codemeta?.license?.let { raw ->
                pom.licenses { licenses ->
                    licenses.license { license ->
                        license.name.set(CodemetaLicense.toSpdxId(raw))
                        license.url.set(CodemetaLicense.toUrl(raw))
                    }
                }
            }
            codemeta?.author.orEmpty().mapNotNull { displayName(it)?.let { name -> it to name } }.forEach { (person, name) ->
                pom.developers { developers ->
                    developers.developer { developer ->
                        developer.name.set(name)
                        person.email?.let { developer.email.set(it) }
                    }
                }
            }
            pom.scm { scm ->
                scm.connection.set("scm:git:$scmBase")
                scm.developerConnection.set("scm:git:$scmBase")
                scm.url.set(scmBase)
            }
        }
    }

    internal fun displayName(person: Person): String? =
        listOfNotNull(person.givenName, person.familyName)
            .joinToString(" ")
            .ifBlank { person.name }
            ?.ifBlank { null }

    private fun resolveCodemeta(target: Project): Codemeta? =
        runCatching {
            if (!target.isExtensionEnabled(EasyCodemetaExtension::class)) {
                null
            } else {
                EasyCodemeta.of(target).orNull
            }
        }.getOrNull()

    private fun configureVersionMapping(publication: MavenPublication) {
        publication.versionMapping { mapping ->
            mapping.usage("java-api") { it.fromResolutionOf("runtimeClasspath") }
            mapping.usage("java-runtime") { it.fromResolutionResult() }
        }
        publication.suppressPomMetadataWarningsFor("java-api")
        publication.suppressPomMetadataWarningsFor("java-runtime")
    }
}
