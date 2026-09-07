#!/usr/bin/env bash
#
# Manual smoke test: deploys staged artifacts to a local docker Nexus via JReleaser.
#
# Prerequisites: docker (compose v2), java, gpg, curl and a published easy-plugin
# snapshot (see ../README.md) or mavenLocal() fallback in settings.gradle.kts.
#
# What it proves: staging layout, real JReleaser CLI resolution, nexus3 upload
# mechanics and applyMavenCentralRules (PomChecker + sources/javadoc + signing +
# checksums) end to end. Only the Central Portal state machine stays unrehearsed.
set -euo pipefail
cd "$(dirname "$0")"

NEXUS_URL="${NEXUS_URL:-http://localhost:8081}"
NEXUS_REPO="${NEXUS_REPO:-maven-releases}"
NEXUS_USER="${NEXUS_USER:-admin}"
NEXUS_PASSWORD="${NEXUS_PASSWORD:-admin123}"
GPG_PASSPHRASE="${GPG_PASSPHRASE:-smoke-test}"

die() {
    echo "ERROR: $*" >&2
    exit 1
}

for cmd in docker java gpg curl python3 base64; do
    command -v "$cmd" >/dev/null 2>&1 || die "missing prerequisite: $cmd"
done
docker compose version >/dev/null 2>&1 || die "missing prerequisite: docker compose v2"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT
export GNUPGHOME="$WORKDIR/gnupg"
mkdir -p "$GNUPGHOME"
chmod 700 "$GNUPGHOME"

echo "==> generating throwaway GPG key (local-only, never published)"
gpg --batch --pinentry-mode loopback --passphrase "$GPG_PASSPHRASE" \
    --quick-generate-key "Central Smoke <smoke@example.com>" rsa3072 sign never
KEYID="$(gpg --list-secret-keys --with-colons | awk -F: '/^sec:/ {print $5; exit}')"
[ -n "$KEYID" ] || die "failed to generate GPG key"
gpg --armor --export "$KEYID" >"$WORKDIR/public.asc"
# loopback + passphrase required: exporting a protected secret key would otherwise prompt via pinentry
gpg --batch --pinentry-mode loopback --passphrase "$GPG_PASSPHRASE" \
    --armor --export-secret-keys "$KEYID" >"$WORKDIR/private.asc"

echo "==> starting Nexus (fresh volume for deterministic runs)"
docker compose down -v >/dev/null 2>&1 || true
docker compose up -d
trap 'docker compose down >/dev/null 2>&1; rm -rf "$WORKDIR"' EXIT

echo "==> waiting for Nexus at $NEXUS_URL"
ready=0
for _ in $(seq 1 30); do
    if curl -sf -o /dev/null "$NEXUS_URL/service/rest/v1/status"; then
        ready=1
        break
    fi
    sleep 10
done
[ "$ready" = "1" ] || die "Nexus did not become ready in time"

echo "==> accepting Nexus EULA (recent Nexus 3 refuses deploys with 403 until accepted)"
EULA_DISCLAIMER="$(curl -sf -u "$NEXUS_USER:$NEXUS_PASSWORD" "$NEXUS_URL/service/rest/v1/system/eula" | python3 -c "import json,sys; print(json.load(sys.stdin)['disclaimer'])")"
python3 -c "import json; print(json.dumps({'accepted': True, 'disclaimer': '''$EULA_DISCLAIMER'''}))" >"$WORKDIR/eula.json"
curl -sf -u "$NEXUS_USER:$NEXUS_PASSWORD" -X POST "$NEXUS_URL/service/rest/v1/system/eula" \
    -H "Content-Type: application/json" -d @"$WORKDIR/eula.json" -o /dev/null || die "failed to accept Nexus EULA"

echo "==> publishing to staging and deploying via JReleaser"
# base64-encoded: the wiring decodes them (PropertyResolver.base64Decode) so the
# multiline armor survives env transport as a single line.
export JRELEASER_GPG_PUBLIC_KEY
JRELEASER_GPG_PUBLIC_KEY="$(base64 -w0 "$WORKDIR/public.asc")"
export JRELEASER_GPG_PRIVATE_KEY
JRELEASER_GPG_PRIVATE_KEY="$(base64 -w0 "$WORKDIR/private.asc")"
export JRELEASER_GPG_PASSPHRASE="$GPG_PASSPHRASE"
# `publish` includes the JReleaser deploy via `publishToMavenCentral`.
./gradlew --refresh-dependencies publish \
    "-Pjreleaser.testNexusUrl=$NEXUS_URL/service/rest/v1/components?repository=$NEXUS_REPO" \
    "-Pjreleaser.nexus.username=$NEXUS_USER" \
    "-Pjreleaser.nexus.password=$NEXUS_PASSWORD"

echo "==> asserting artifacts landed in $NEXUS_REPO"
found="$(curl -sf -u "$NEXUS_USER:$NEXUS_PASSWORD" \
    "$NEXUS_URL/service/rest/v1/search?repository=$NEXUS_REPO&group=com.example&name=central-smoke&version=0.1.0-smoke" \
    | grep -o 'central-smoke-0\.1\.0-smoke[^"]*' | sort -u)"
echo "$found"
for expected in "central-smoke-0.1.0-smoke.pom" "central-smoke-0.1.0-smoke.jar" \
    "central-smoke-0.1.0-smoke-sources.jar" "central-smoke-0.1.0-smoke-javadoc.jar" ".asc"; do
    echo "$found" | grep -q "$expected" || die "missing expected artifact: $expected"
done

echo "SMOKE TEST PASSED"
