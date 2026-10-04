# Embedded Redocly bundles

This directory builds the JavaScript files the plugin evaluates in GraalJS, under
`src/main/resources/net/mwalser/openapi/toolkit/redocly/`. Node.js 22 (`.nvmrc`) and npm are only needed here,
never by plugin users.

- `redocly-core.mjs` – `@redocly/openapi-core`, the vendored CLI commands and the command layer of every goal.
- `redocly-docs.mjs` – Redoc with React for `build-docs`; `RedoclyRuntime` evaluates it into the same context when
  that goal first runs, so the other goals do not pay for it.

```
npm ci --ignore-scripts
npm run build -- ../src/main/resources/net/mwalser/openapi/toolkit/redocly/redocly-core.mjs ../src/main/resources/net/mwalser/openapi/toolkit/redocly/redocly-docs.mjs ../src/main/resources/META-INF/THIRD-PARTY-NOTICES.txt
```
or from the project root: `mvn generate-resources -Pbuild-js`. The third output collects the license of every
bundled package; CI rebuilds all three files and fails when they differ from the committed ones.

- `src/host.js` – access to the host object `globalThis.__jvm` installed by `HostBridge.kt`, and the
  conversion of host failures into Node-style errors (`ENOENT: …`, `error.code`).
- `src/polyfills.js` – globals GraalJS lacks (`process`, `console`, timers, `structuredClone`, `URL`, `fetch`, and
  the `global`, `MessageChannel` and `TextEncoder` that Redoc's server-side rendering reaches).
- `src/shims/` – replacements for Node built-ins (`fs`, `process`, `perf_hooks`, `stream`) and `colorette`.
- `src/api/` – the command layer (`lint`, `bundle`, `check-config`, `stats`, `join`, `split`, `score`,
  `build-docs`) mirroring `packages/cli/src/commands` of redocly-cli; every command takes plain options and returns
  JSON-serializable data.
- `src/docs.js`, `src/docs/render.js` – the entry of `redocly-docs.mjs` and the port of build-docs' rendering:
  Redoc's loader, React's `renderToString` with styled-components, and the Handlebars page template. It is built
  from Redoc's Node library (`redoc.lib.js`) and styled-components' server build; the Redoc version and the
  integrity hash of the CDN script are computed at build time.
- `vendor/redocly-cli/` – command sources from the `@redocly/cli` 2.57.0 release (MIT) for
  stats/join/split/score, compiled by esbuild from TypeScript. `types.ts`, `wrapper.ts` and
  `utils/miscellaneous.ts` are plugin-authored compatibility replacements;
  the remaining files track the matching upstream release. The only edit to an upstream file is marked with
  an `openapi-toolkit:` comment (`commands/score/index.ts` returns the computed score).

Upgrading Redocly: bump `@redocly/openapi-core` in `package.json`, refresh `vendor/redocly-cli` from the
matching `@redocly/cli` tag (and the release named above), rebuild, run `mvn verify -Prun-its`, and update
`redocly.version` in `pom.xml`.
Upgrading Redoc: bump `redoc` in `package.json` (an exact version: the page loads `redoc.standalone.js` of the same
version from the CDN) and rebuild; the Redocly CLI pins the Redoc it ships in `packages/cli/package.json`.
