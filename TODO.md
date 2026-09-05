# TODO

This document keeps track of tasks that need to be completed.

## Contributor plugins

* publish: create publishToMavenCentral task. jreleaser cli exec wrapper
* publish: make config yml flexible so that it can point to different nexus3 servers in tests, e.g.
  ```yaml
    deploy:
    maven:
      nexus3:
        local-test:
          active: ALWAYS
          url: http://localhost:8081/service/rest/v1/components?repository=maven-releases
          # Force JReleaser to check for POM elements, sources, and javadocs locally
          applyMavenCentralRules: true 
          stagingRepositories:
            - target/staging-deploy
    signing:
    active: ALWAYS
    armored: true
  ```


## Architecture changes / fixes

## Other tasks
