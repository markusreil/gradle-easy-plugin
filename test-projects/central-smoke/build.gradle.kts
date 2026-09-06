plugins {
    `java-library`
    id("com.mreil.easy.project") version "latest.integration"
}

easy {
    publish {
        enabled.set(true)
        toMavenCentral()
        toMavenStaging()
    }
    codemeta { enabled.set(true) }
}
