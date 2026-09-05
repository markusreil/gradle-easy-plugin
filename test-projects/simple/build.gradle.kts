plugins {
    `java`
    id("com.mreil.easy.project") version "latest.integration"
}

easy {
    publish {}
    semver {}
    codemeta {}
}
