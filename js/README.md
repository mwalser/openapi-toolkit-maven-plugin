# Embedded Redocly bundle

This directory builds `src/main/resources/net/mwalser/openapi/toolkit/redocly/redocly-core.mjs`,
the single JavaScript file the plugin evaluates in GraalJS. Node.js is only needed here, never by
plugin users.

```
npm ci --ignore-scripts
npm run build -- ../src/main/resources/net/mwalser/openapi/toolkit/redocly/redocly-core.mjs ../src/main/resources/META-INF/THIRD-PARTY-NOTICES.txt
```
or from the project root: `mvn generate-resources -Pbuild-js`. The second output collects the license of every
bundled package; CI rebuilds both files and fails when they differ from the committed ones.

- `src/host.js` – access to the host object `globalThis.__jvm` installed by `HostBridge.kt`, and the
  conversion of host failures into Node-style errors (`ENOENT: …`, `error.code`).
- `src/polyfills.js` – globals GraalJS lacks (`process`, `console`, timers, `structuredClone`, `URL`, `fetch`).
- `src/shims/` – replacements for Node built-ins (`fs`, `process`, `perf_hooks`) and `colorette`.
- `src/api/` – the command layer (`lint`, `bundle`, `check-config`, `stats`, `join`, `split`, `score`)
  mirroring `packages/cli/src/commands` of redocly-cli; every command takes plain options and returns
  JSON-serializable data.
- `vendor/redocly-cli/` – command sources from the `@redocly/cli` 2.47.0 release (MIT) for
  stats/join/split/score, compiled by esbuild from TypeScript. `types.ts`, `wrapper.ts`,
  `utils/error.ts`, and `utils/miscellaneous.ts` are plugin-authored compatibility replacements;
  the remaining files track the matching upstream release. The only edit to an upstream file is marked with
  an `openapi-toolkit:` comment (`commands/score/index.ts` returns the computed score).

Upgrading Redocly: bump `@redocly/openapi-core` in `package.json`, refresh `vendor/redocly-cli` from the
matching `@redocly/cli` tag, rebuild, run `mvn verify -Prun-its`, and update `redocly.version` in `pom.xml`.
