# Embedded Redocly bundle

This directory builds `src/main/resources/net/mwalser/openapi/toolkit/redocly/redocly-core.mjs`,
the single JavaScript file the plugin evaluates in GraalJS. Node.js is only needed here, never by
plugin users.

```
npm ci
npm run build -- ../src/main/resources/net/mwalser/openapi/toolkit/redocly/redocly-core.mjs
```
or from the project root: `mvn generate-resources -Pbuild-js`.

- `src/polyfills.js` – globals GraalJS lacks (`process`, `console`, timers, `URL`, `fetch`, ...), all
  delegating to the host object `globalThis.__jvm` installed by `HostBridge.kt`.
- `src/shims/` – replacements for Node built-ins (`fs`, `process`, `perf_hooks`) and `colorette`.
- `src/api/` – the command layer (`lint`, `bundle`, `check-config`, `stats`, `join`, `split`, `score`)
  mirroring `packages/cli/src/commands` of redocly-cli; every command takes plain options and returns
  JSON-serialisable data.
- `vendor/redocly-cli/` – unmodified command sources of `@redocly/cli` (MIT) for stats/join/split/score,
  compiled by esbuild from TypeScript. Update them together with the `@redocly/openapi-core` version.

Upgrading Redocly: bump `@redocly/openapi-core` in `package.json`, refresh `vendor/redocly-cli` from the
matching `@redocly/cli` tag, rebuild, run `mvn verify -Prun-its`, and update `redocly.version` in `pom.xml`.
