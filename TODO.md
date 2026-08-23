# TODO

Consolidated from six independent reviews (Kotlin, JavaScript, Maven, Redocly, GraalVM embedding, security &
supply chain) on 2026-08-23. Items are grouped into batches in the order they should be worked on; within a batch
they are ordered by value. Each item names where the problem is, what to do, and how it is verified.

## Decisions taken (2026-08-23)

- **Mojos move to Java.** plugin-tools extracts descriptions from Javadoc only (QDox), never from KDoc, so the
  seven mojo classes and their three abstract bases become thin Java classes (annotations, Javadoc, delegation).
  The runtime layer (`net.mwalser.openapi.toolkit.redocly`) stays Kotlin.
- **Network is Maven-aware:** refuse remote `$ref`/`extends` under `mvn -o`, honor the active `settings.xml`
  proxy, add a connect timeout. No additional parameter.
- **Custom `plugins:` in `redocly.yaml` fail the build** with an error naming the plugins and the rules/decorators
  that would silently be lost.
- **`check-config` defaults to `severity=error`**, matching `redocly check-config`.
- **`bundle` with `attach=true` fails validation** when more than one selected API has no alias (classifier
  collision) instead of silently replacing the first attachment.
- Keep: no `SandboxPolicy`/CPU limits (the proxy-only bridge is the trust boundary; a statement limit would only
  break large-spec builds); no user-facing engine parameter; interpreter as default, isolate opt-in.

---

## Batch 1 — Bugs and release blockers

- [x] **Relative paths must resolve against the module basedir, not the JVM `user.dir`.**
  `HostBridge.path()` → `workingDirectory.resolve(...)`; `js/src/shims/fs.js` → `path.resolve(p)` before every
  host call. Today `findConfig()`/`loadIgnoreConfig()` probe `redocly.yaml` and `.redocly.lint-ignore.yaml`
  relative to where `mvn` was started: a reactor build from a root that has a `redocly.yaml` fails modules
  without one, and ignore files are silently skipped.
  *Test:* runtime test with JVM cwd ≠ basedir (parent has `redocly.yaml`, module has none; module has only an
  ignore file); two-module invoker IT (see batch 2).

- [x] **`resolve.http.headers[].envVariable` sends an empty header.** openapi-core's `lib/env.js` sets `env = {}`
  in browser mode; our `process.env` proxy is never consulted. Replace the module at bundle time with an esbuild
  `onLoad` plugin (`export const isBrowser = true; export const env = globalThis.process.env;`) — do not flip
  `process.platform`, that would re-enable plugin loading. Document in README that explicit
  `https://host/**` patterns should be used for `matches` (picomatch matches the URL string, so `*.example.com/**`
  also matches `https://evil.com?x=.example.com/`).
  *Test:* extend the `HttpServer` test with an `envVariable` header and assert the value arrives.

- [x] **Java exceptions must not cross into JS.** Host exceptions arrive as read-only foreign errors; openapi-core
  assigns `error.message` and the user sees `writeMember (message) on java.nio.charset.MalformedInputException
  failed … Unknown identifier`. Wrap all host calls in `fs.js` and the `fetch` polyfill into real `Error`s with
  Node-style codes (`ENOENT`, `EACCES`, `ECONNREFUSED`) and the path/URL in the message. Decode files leniently
  (`String(Files.readAllBytes(), UTF_8)`) like Node instead of failing on Latin-1.
  *Test:* Latin-1 file, unreadable file (mode 000), refused connection, 404 — messages contain code and path/URL.

- [x] **Run the JS context on a dedicated thread with a large stack.** GraalJS recursion depth is bounded by the
  calling thread's stack; Maven's 1 MB main thread gives ~720 frames (Node: ~12 500), so specs with long `$ref`
  chains fail with `RangeError: Maximum call stack size exceeded`. Create engine, context and bundle on a
  single-thread daemon executor with a 256 MB (lazily committed) stack; this replaces the `ReentrantLock`.
  *Test:* synthetic spec with a 200-deep `$ref` chain lints in both engine modes.

- [x] **Drop `engine.Mode=latency`.** Measured 40–50 % slower on large specs in the isolate (12.4 s vs 8.5 s);
  only helps tiny scripts. Keep `WarnInterpreterOnly=false`.

- [x] **`addResource` must only include the written bundles.** Today the parent directory of `outputFile` (or
  `outputDirectory`) is added with no includes — `outputFile=${basedir}/openapi.yaml` ships `src/`, `.git/`,
  `target/` in the jar. Pass `includes = names of written bundles`.
  *Test:* IT with `outputFile` + `addResource`; jar contains only the bundle.

- [x] **Captured output is lost when a vendored command fails; `return` inside `finally` swallows exceptions.**
  `join` prints the bundling problems and then throws — the user sees only the final line. Introduce one
  `captureOutput(fn)` helper in `polyfills.js` that restores the previous buffer, attaches captured text to the
  rejection (`details.output`) on failure, and never returns from `finally`; read `details.output` in
  `RedoclyRuntime.toException` and include it in the Maven error output. Use it in `formatToString`, stats, score,
  join, split.
  *Test:* `join` with a broken input shows the problems before the failure.

- [x] **Third-party license notices in the jar.** The bundle redistributes 18 packages (MIT/BSD-3/Apache-2.0 —
  inventory in the security review: @redocly/openapi-core, @redocly/cli (vendored), @redocly/config, @redocly/ajv,
  ajv-formats, json-schema-traverse, fast-deep-equal, fast-uri, graphql, js-yaml, yaml-ast-parser, picomatch,
  buffer, base64-js, ieee754, path-browserify, pluralize, js-levenshtein) and `legalComments: 'none'` strips the
  few `/*!` banners. Generate `THIRD-PARTY-NOTICES.txt` from the esbuild metafile in `build.mjs` (license text of
  every bundled package; Redocly's from `js/vendor/redocly-cli/LICENSE`), write it next to the bundle, and package
  it together with `LICENSE` under `META-INF/` via a `<resource>` in `pom.xml`.
  *Check:* `unzip -l` of the jar lists both files; CI rebuild + `cmp` keeps bundle and notices in sync.

- [x] **Leak on failed runtime creation.** `RedoclyRuntime.create` only closes context/engine on
  `PolyglotException`; a `RedoclyException` from `loadBundle()` or an `IllegalArgumentException` leaks a live
  engine (a native isolate, in that mode) on every retry. Use `try/catch (Exception)` with cleanup, rethrow
  `RedoclyException` unchanged. Also pass `e.asHostException()` as cause for host exceptions.

- [x] **Attach classifier collision** (decision above): in `BundleMojo.validateParameters()`/`run()`, fail when
  `attach` and more than one selected API lacks an alias.

- [x] **`check-config` default `severity=error`** (decision above); update README and the IT.

- [x] **Abort after configuration-lint errors.** `lint`/`bundle`/`stats`/`score`/`join` currently lint the
  config, then process every API, then fail — minutes wasted on the interpreter and the API results are never
  shown. Return early from the JS command when `configLint.totals.errors > 0`, as the CLI does.

- [x] **Fail on `plugins:` in `redocly.yaml`** (decision above): in `loadProjectConfig`, if
  `config.document.parsed.plugins` contains strings, throw a `CommandError` listing the plugins and the
  plugin-prefixed rules/decorators found in the config. Remove the "verify the added plugin prefix" hint from the
  unused-rules warning (it cannot apply here).
  *Test:* config with `plugins: [./x.js]` and `rules: { x/rule: error }` fails with that message.

- [x] **`JsPaths.toHost` must leave URLs alone** (Windows: `https://host/a` → `https:\\host\a` in every log line);
  mirror the URL guard of `toJs`, add a test.

- [x] **Wrong fix hint for `check-config`:** `reportConfigLint` says "set lintConfig=off", a parameter
  `check-config` does not have. Make the hint a parameter of `reportConfigLint`.

- [x] **Unhandled `IOException`s surface as `PluginExecutionException`** ("Execution lint of goal … failed",
  reads like a plugin bug). Catch `IOException`/`UncheckedIOException` around report/output writes and throw
  `MojoExecutionException("Could not write <what> to <path>: …")`.

## Batch 2 — Maven integration

- [x] **Mojos in Java** (decision above). `src/main/java/net/mwalser/openapi/toolkit/mojo/`: `AbstractRedoclyMojo`,
  `AbstractConfiguredMojo`, `AbstractApiMojo`, the seven goals, `MavenJsLog`; Javadoc on every class and
  parameter (today's KDoc text). Kotlin compiles first (current plugin order), Java subclasses call into the
  Kotlin runtime. Add `<goal>helpmojo</goal>` to maven-plugin-plugin so `openapi:help` exists. Keep
  `MojoParametersTest` (descriptor parameter sets) and add an assertion that every goal and parameter has a
  non-empty `<description>`.

- [x] **Offline mode, proxies, timeouts** (decision above). Inject `@Parameter(defaultValue = "${session}",
  readonly = true) MavenSession` and `@Inject SettingsDecrypter`; per execution hand `offline` and the decrypted
  active proxy (with `nonProxyHosts`) to `RedoclyRuntime.run` like `log`/`workingDirectory`. `HostBridge.fetch`:
  throw "Maven is offline (-o): remote reference <url> was not fetched" when offline; build the `HttpClient` with
  `.proxy(ProxySelector)`, an `Authenticator` for proxy credentials and `.connectTimeout(15 s)`, cached per proxy
  configuration instead of once per JVM.
  *Test:* runtime test with `offline=true` against the local `HttpServer` (no request arrives, clear error).

- [x] **Isolate the integration-test repository.** `maven-invoker-plugin` currently installs the SNAPSHOT into
  the real `~/.m2`. Set `<localRepositoryPath>${project.build.directory}/local-repo</localRepositoryPath>` and a
  `src/it/settings.xml` with `<mergeUserSettings>true</mergeUserSettings>`; use `invoker.goals` (not
  `invoker.mavenOpts`) for `-D` options everywhere; drop the redundant `postBuildHookScript` default.

- [x] **Reactor integration test.** Two modules, each with its own `redocly.yaml`, run with `-T 2 verify` from
  the parent — proves basedir-as-cwd, the shared runtime across modules, and thread safety. Add ITs for `skip`,
  `failOnWarnings`, `addResource` + `outputFile`, and `-o` with a remote ref.

- [x] **m2e lifecycle-mapping metadata** (`META-INF/m2e/lifecycle-mapping-metadata.xml`): `<ignore/>` for
  `lint`/`check-config`/`stats`/`score`, `<execute><runOnIncremental>false</runOnIncremental></execute>` for
  `bundle`, so Eclipse does not flag "plugin execution not covered".

- [x] **JDK 24+ documentation.** Truffle prints `WARNING: A restricted method in java.lang.System has been
  called` (native access from the unnamed module) once per build; the plugin cannot suppress it. Document
  `.mvn/jvm.config` with `--enable-native-access=ALL-UNNAMED` (and `-Xss16m` until the dedicated-thread fix is
  in). Mention the isolate's resource cache (`~/.cache/org.graalvm.polyglot/…`, ~140 MB;
  `-Dpolyglot.engine.userResourceCache=<dir>` for read-only homes).

- [x] **README corrections.** In-process JIT needs a *GraalVM JDK 25* (same major as GraalJS 25; a GraalVM JDK 21
  or a stock JDK 25 with JVMCI still falls back); the ~4 s interpreter bundle load is HotSpot warm-up, not
  parsing; `-Dopenapi.apis=a,b` works (Sisu splits CSV); specs produced during `generate-sources` need the lint
  execution rebound to a later phase; `resolve.http.headers` pattern advice (see batch 1); `.mvn/jvm.config` notes.

- [x] **`isolate-tests` profile on unsupported platforms** resolves to a literal `${isolate.platform}` artifact.
  Add `<isolate.platform>unsupported</isolate.platform>` to the base properties so the error is readable.

- [x] **Central publishing readiness** (does not block builds): `<scm>`, `<developers>`,
  `project.build.outputTimestamp`, sources jar, javadoc jar (Dokka `javadocJar` or empty jar), GPG, Central
  publishing plugin; `maven-enforcer-plugin` `requirePluginVersions`.

## Batch 3 — Polish and fidelity

- [x] **Simplify promise awaiting.** `setTimeout` is a microtask, so every command promise settles inside
  `run.execute()`; the `drainTimers` loop is gone (the "did not settle" guard stays).
- [ ] **Engine knobs.** Done: `HostAccess.NONE` in both modes, `js.print=false`, `js.load=false`. Open: route the
  context's `out`/`err` streams to `JsLog`; expose `engine.MaxIsolateMemory` via a system property (the isolate heap
  lives outside `-Xmx`).
- [x] **Polyfill cleanup.** `performance` comes from `js.performance=true`; the unused `TextEncoder`/`TextDecoder`,
  `AbortController`, `queueMicrotask`, `setImmediate`, `URLSearchParams` polyfills, the `utf8Encode`/`utf8Decode`/
  `nowMillis`/`delete` bridge entries and the `typeof` guards are gone; `structuredClone` no longer exposes `seen`.
- [ ] **Capture the `output` and `info` console channels separately** so stats/score take the payload verbatim
  and the regex `stripWrapper` (depends on upstream wording) goes away; lazy `perf_hooks`/`process` shims.
- [x] **Score computed once.** `runScore` returns `agentReadiness` in every format (one marked line in the vendored
  `commands/score/index.ts`), so `minScore` no longer re-runs bundle + score.
- [ ] **Bundle fidelity:** default `ext` from the input file's extension when neither `ext` nor `outputFile`
  decides (JSON input → JSON bundle, as the CLI); collision check for two alias-less APIs with the same basename;
  pass `version` (2.47.0) to `formatProblems` so reports do not say `2.0`; rewrite the `--max-problems N` hint
  to `openapi.maxProblems`; align `sortTopLevelKeys` for AsyncAPI 3 with upstream (or document the `id`
  difference); `log.debug` when `apis.<alias>.output` is set and ignored.
- [x] **Kotlin idioms** (from the Kotlin review): `CommandOptions.cwd`, `Value.stringMember()`, `resolve`/`buildEngine`,
  `Totals.hasProblems`, one line splitter in `MavenJsLog` with the level carrying its emitter, `listDirectoryEntries()`,
  `use` as expression, no `@JsonIgnoreProperties`, shared test `fixture()`, `ScoreCommandTest` folded into
  `Tier2CommandsTest`. Mojo behavior lives in Kotlin `Goal` classes; the Java mojos only declare parameters.
- [ ] **JS idioms:** done — `{ cause }` when rewrapping config errors, `path.dirname` in `build.mjs`, version check
  against `pom.xml`, `outdent` dropped, readable error for unparsable stats/score JSON. Open — `resolveRoot` helper
  in `resolveApis` (also fixes URL roots in the alias back-lookup); `addTotals` helper; dedupe the double-bundled
  `ajv` via an esbuild alias (~214 KB).
- [x] **Supply-chain hygiene:** `npm ci --ignore-scripts`, upstream release recorded in `js/README.md` with the
  plugin-authored files named, CI job that rebuilds bundle and notices and `cmp`s them, `engines`/`.nvmrc`.
- [ ] **Test cases from the Redocly review:** nested multi-file refs (`../common/b.yaml#/…`), two `pet.yaml`
  files with different content (`componentRenamingConflicts` warn vs error, `componentNamesStrategy=title`),
  `keepUrlReferences` with a remote ref, `removeUnusedComponents` count; OpenAPI 3.1 (webhooks,
  `jsonSchemaDialect`) and 3.2; AsyncAPI 2/3 lint/bundle/stats/split; Arazzo and Overlay lint; `yml` input;
  `dereferenced` + circular schema → `CommandError`; remote root API and remote `extends`; `$ref` to a directory;
  CRLF and UTF-8-BOM files; `extends: [spec]`/`[all]`; two aliases with different `extends`;
  `generateIgnoreFile` with two aliases then re-lint; `remove-x-internal`/`filter-in`/`filter-out`/`info-override`
  decorators with `configFile` outside basedir; `skipDecorators` actually skipping; mojo-level `failOnWarnings`,
  `outputFile` + two APIs rejected, join with conflicting prefix options; a large-spec smoke test comparing problem
  counts with the CLI; a Windows CI job.
- [x] **CI:** GitHub Actions matrix (Linux/macOS/Windows × JDK 21/25) running `verify -Prun-its`, the bundle
  reproducibility check, and `-Pisolate-tests` on Linux (`.github/workflows/ci.yml`, not yet run on GitHub).

## Deferred to later

- Per-goal skip parameters (`openapi.lint.skip`, `openapi.bundle.skip`, …) in addition to `openapi.skip`.
- Glob patterns in `apis` (`src/main/openapi/*.yaml`), expanded like the CLI.
- The two remaining cheap CLI options: bundle `metafile`, score `debugOperationId`.
- A context pool (one JS context per worker thread on the shared engine) so `-T` builds run goals in parallel;
  additional contexts cost ~0.2 s each thanks to the engine-level source cache.
