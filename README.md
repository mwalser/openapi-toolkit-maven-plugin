# OpenAPI Toolkit Maven Plugin

Lint, bundle and transform OpenAPI / AsyncAPI descriptions with [Redocly](https://redocly.com/docs/cli) from
Maven — **without Node.js**. The plugin embeds `@redocly/openapi-core` (plus the relevant commands of
`@redocly/cli`) as a JavaScript bundle and runs it inside the JVM with [GraalJS](https://www.graalvm.org/javascript/).
Nothing is downloaded at build time except ordinary Maven dependencies.

- Requirements: JDK 21+, Maven 3.9+. Works on any JDK (GraalVM not required).
- Embedded Redocly version: see `redocly.version` in `pom.xml` (currently 2.47.0).
- Configuration is the usual `redocly.yaml`: `extends`, `rules`, `apis`, `decorators`, `preprocessors`,
  `resolve.http.headers`, `.redocly.lint-ignore.yaml` — all handled by Redocly's own code.

## Quick start

```xml
<plugin>
  <groupId>net.mwalser</groupId>
  <artifactId>openapi-toolkit-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <executions>
    <execution>
      <id>lint</id>
      <goals><goal>lint</goal></goals>        <!-- bound to validate -->
    </execution>
    <execution>
      <id>bundle</id>
      <goals><goal>bundle</goal></goals>      <!-- bound to generate-resources -->
      <configuration>
        <addResource>true</addResource>      <!-- ship the bundled spec inside the jar -->
      </configuration>
    </execution>
  </executions>
</plugin>
```

With a `redocly.yaml` in the project base directory:

```yaml
extends:
  - recommended
apis:
  petstore:
    root: src/main/openapi/openapi.yaml
rules:
  operation-4xx-response: warn
```

`mvn verify` then lints `src/main/openapi/openapi.yaml` (failing the build on errors) and writes the bundled
description to `target/generated-resources/openapi/petstore.yaml`.

Without a `redocly.yaml`, point the plugin at files directly and Redocly's built-in `recommended` ruleset is used:

```
mvn net.mwalser:openapi-toolkit-maven-plugin:lint -Dopenapi.apis=src/main/openapi/openapi.yaml
```

## Goals

All goals share these parameters:

| Parameter | Property | Default | Description |
|---|---|---|---|
| `skip` | `openapi.skip` | `false` | Skip execution. |
| `configFile` | `openapi.configFile` | `redocly.yaml` if it exists | Redocly configuration file. |
| `apis` | `openapi.apis` | all APIs of the config | Aliases from `apis:` or paths/URLs. |
| `extends` | `openapi.extends` | – | Overrides the `extends` list (`recommended`, `minimal`, `recommended-strict`, …). |
| `lintConfig` | `openapi.lintConfig` | `warn` | Lint the configuration file first: `warn`, `error`, `off`. |
| `maxProblems` | `openapi.maxProblems` | `100` | Maximum number of problems printed per API. |
| `engine` | `openapi.engine` | `AUTO` | JavaScript engine mode, see [Performance](#performance). |

### `openapi:lint` (default phase: `validate`)

Runs `redocly lint`. Fails the build when errors are found.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `format` | `openapi.lint.format` | `stylish` | Console format: `stylish`, `codeframe`, `summary`, `markdown`, `github-actions`, `json`, `checkstyle`, `codeclimate`, `junit`. |
| `reportFile` | `openapi.lint.reportFile` | – | Additionally write all problems to this file … |
| `reportFormat` | `openapi.lint.reportFormat` | `checkstyle` | … in this format (e.g. `checkstyle` or `junit` for CI integration). |
| `failOnErrors` | `openapi.lint.failOnErrors` | `true` | Fail the build on `error` problems. |
| `failOnWarnings` | `openapi.lint.failOnWarnings` | `false` | Fail the build on `warn` problems. |
| `skipRules` | `openapi.lint.skipRules` | – | Rule ids to skip. |
| `skipPreprocessors` | `openapi.lint.skipPreprocessors` | – | Preprocessor ids to skip. |
| `generateIgnoreFile` | `openapi.lint.generateIgnoreFile` | `false` | Write all problems to `.redocly.lint-ignore.yaml` instead of reporting them. |

### `openapi:bundle` (default phase: `generate-resources`)

Runs `redocly bundle`: resolves all `$ref`s into one file and applies the configured decorators.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `outputDirectory` | `openapi.bundle.outputDirectory` | `${project.build.directory}/generated-resources/openapi` | Output directory; files are named `<alias>.<ext>` (or `<basename>.<ext>`). |
| `outputFile` | `openapi.bundle.outputFile` | – | Explicit output file (single API only). |
| `ext` | `openapi.bundle.ext` | `yaml` | `yaml`, `yml` or `json`. |
| `dereferenced` | `openapi.bundle.dereferenced` | `false` | Inline everything, leave no `$ref`. |
| `force` | `openapi.bundle.force` | `false` | Write the bundle even if there are errors. |
| `removeUnusedComponents` | `openapi.bundle.removeUnusedComponents` | `false` | Drop unreferenced components. |
| `keepUrlReferences` | `openapi.bundle.keepUrlReferences` | `false` | Keep absolute URL `$ref`s. |
| `componentNamesStrategy` | `openapi.bundle.componentNamesStrategy` | – | Naming strategy for imported components. |
| `skipDecorators` / `skipPreprocessors` | `openapi.bundle.skip…` | – | Ids to skip. |
| `addResource` | `openapi.bundle.addResource` | `false` | Add the output directory as a project resource (bundle ends up in the jar). |
| `attach` | `openapi.bundle.attach` | `false` | Attach each bundle as a build artifact (`type` = `ext`, `classifier` = alias). |
| `classifier` | `openapi.bundle.classifier` | `openapi` | Classifier used when an API has no alias. |

Note: the `output` field of `apis.<alias>` in `redocly.yaml` is ignored; Maven controls where bundles go.

### `openapi:check-config`

Lints `redocly.yaml` (`redocly check-config`). `severity` (`openapi.checkConfig.severity`, default `warn`) decides
whether problems fail the build.

### `openapi:stats`

Prints `redocly stats` for one API (`api` / `openapi.stats.api`, defaults to the first configured API) in
`format` `stylish`, `json` or `markdown`; `outputFile` writes it to a file instead of the log.

### `openapi:score`

Runs `redocly score` (integration simplicity / agent readiness, OpenAPI 3 only). Parameters `api`, `format`
(`stylish`/`json`), `operationDetails`, `outputFile`, and `minScore` (`openapi.score.minScore`) to fail the build
when the agent-readiness score is below a threshold.

### `openapi:join`

Joins two or more OpenAPI 3 descriptions (`redocly join`, experimental upstream). Select them with `apis`;
`outputFile` (default `target/generated-resources/openapi/joined.yaml`), `prefixTagsWithInfoProp`,
`prefixTagsWithFilename`, `prefixComponentsWithInfoProp`, `withoutXTagGroups` mirror the CLI options.

### `openapi:split`

Splits a single-file description into a multi-file tree (`redocly split`). Meant to be run by hand once:

```
mvn openapi:split -Dopenapi.split.api=openapi.yaml -Dopenapi.split.outputDirectory=src/main/openapi
```

## Performance

GraalJS is not V8. The default **interpreter** mode (plain `js-community` dependency, ~30 MB, works on every
JDK 21+) is fine for typical API descriptions but noticeably slower than Node on very large ones:

| Description | Size | Node | interpreter | isolate |
|---|---|---|---|---|
| Petstore | 17 KB | 0.06 s | 1.1 s | 0.4 s |
| Twilio API | 1.9 MB | 1.1 s | 18 s | 7 s |
| GitHub REST API | 12.9 MB | 6 s | 90 s | 44 s |

(lint, cold, 4 cores; plus a one-time ~4 s / ~1.4 s to load the bundle per Maven build. Problem counts are identical.)

`engine` = `AUTO` picks the best available option:

1. **JIT in-process** — when Maven itself runs on a GraalVM JDK (`Engine.supportsCompilation()`).
2. **Isolate** — GraalJS as a pre-compiled native image inside the JVM (Community licence since GraalVM 25.1).
   Opt in by adding the artifact for your platform to the *plugin's* dependencies (~60 MB download):
   ```xml
   <plugin>
     <groupId>net.mwalser</groupId>
     <artifactId>openapi-toolkit-maven-plugin</artifactId>
     <version>…</version>
     <dependencies>
       <dependency>
         <groupId>org.graalvm.polyglot</groupId>
         <artifactId>js-isolate-linux-amd64-community</artifactId>   <!-- linux-aarch64, darwin-aarch64, windows-amd64 -->
         <version>25.2.4</version>
         <type>pom</type>
       </dependency>
     </dependencies>
   </plugin>
   ```
3. **Interpreter** — otherwise.

`engine` = `INTERPRETER` or `ISOLATE` forces a mode (`ISOLATE` fails when the artifact is missing). The JavaScript
runtime is created once per JVM and reused by every goal and module of the build.

## Limitations

- **Custom JavaScript plugins** (`plugins:` in `redocly.yaml`) are not supported: the bundle runs in Redocly's
  "browser" mode, which cannot load plugin files. Built-in rulesets, rule configuration and built-in decorators /
  preprocessors all work.
- Not included (they need a Node process, the Redocly cloud or live HTTP): `build-docs`, `preview`, `push`,
  `login`, `respect`, `translate`, `eject`, `generate-*`, `drift`, `proxy`, `scorecard-classic`.
- Windows: paths are mapped for the POSIX `path` implementation used by the bundle; this is covered by unit tests
  but has not been exercised on a real Windows machine yet.

## Development

```
mvn verify                      # unit tests
mvn verify -Prun-its            # + integration tests (real Maven builds under target/it)
mvn test -Pisolate-tests        # unit tests with the native isolate for this platform
mvn generate-resources -Pbuild-js   # rebuild the embedded JS bundle (needs Node.js + npm)
```

The project is written in Kotlin. `js/README.md` explains the JavaScript side and how to upgrade Redocly.

## Licence

Apache License 2.0. The embedded bundle contains `@redocly/openapi-core` and parts of `@redocly/cli`
(MIT, © Redocly Inc.) and their dependencies; see `LICENSE` and `js/vendor/redocly-cli/LICENSE`.
