#!/usr/bin/env bash
# Renders the APIs of src/test/resources/fixtures/build-docs with the Redocly CLI (the version of redocly.version in
# pom.xml, telemetry off as the goal's default) and records the SHA-256 of each page in expected-pages.sha256, which
# BuildDocsCommandTest compares the plugin's pages with. Run it after upgrading Redocly; requires Node.js and npx.
#
#   scripts/build-docs-expected.sh [directory to keep the pages in]
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
fixture="$root/src/test/resources/fixtures/build-docs"
version="$(sed -n 's|.*<redocly.version>\(.*\)</redocly.version>.*|\1|p' "$root/pom.xml")"
pages="${1:-$(mktemp -d)}"
mkdir -p "$pages"
pages="$(cd "$pages" && pwd)" # the renders run in the fixture directory

cd "$fixture"
render() { # <page name> <alias> [CLI options]
  local name="$1" alias="$2"
  shift 2
  REDOCLY_TELEMETRY=off npx --yes "@redocly/cli@$version" build-docs "$alias" -o "$pages/$name.html" "$@" >/dev/null
}
for alias in pets legacy events schema; do render "$alias" "$alias"; done
render pets-inline pets --inlineBundle

(cd "$pages" && sha256sum pets.html legacy.html events.html schema.html pets-inline.html) > expected-pages.sha256
echo "Recorded the pages of @redocly/cli $version in $fixture/expected-pages.sha256 (pages in $pages)"
