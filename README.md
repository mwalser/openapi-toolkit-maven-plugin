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

This project is currently unreleased. Run `mvn install` in this repository before using the snapshot below.

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

Without a `redocly.yaml`, point the plugin at files directly and Redocly's built-in `recommended` ruleset is used.
The short `openapi:<goal>` form works after the plugin is declared in the POM:

```
mvn openapi:lint -Dopenapi.apis=src/main/openapi/openapi.yaml
```

## Goals

Common parameters are only offered by the goals they actually affect (`mvn help:describe -Dplugin=openapi -Ddetail`
shows exactly what a goal accepts):

| Parameter | Property | Default | Description | Goals |
|---|---|---|---|---|
| `skip` | `openapi.skip` | `false` | Skip execution. | all |
| `configFile` | `openapi.configFile` | `redocly.yaml` if it exists | Redocly configuration file. | all except `split` |
| `maxProblems` | `openapi.maxProblems` | `100` | Maximum number of problems printed per API and for the configuration file. | all except `split` |
| `apis` | `openapi.apis` | all APIs of the config | Aliases from `apis:` or paths/URLs to process. | `lint`, `bundle`, `stats`, `score`, `join` |
| `lintConfig` | `openapi.lintConfig` | `warn` | Lint the configuration file first: `warn`, `error`, `off`. | `lint`, `bundle`, `stats`, `score`, `join` |
| `extends` | `openapi.extends` | – | Overrides the `extends` list (`recommended`, `minimal`, `recommended-strict`, …). | `lint`, `bundle` |

Enumerated values (formats, severities, …) are validated before anything runs; an invalid value fails the build
with a message naming the property.

List parameters use normal Maven collection syntax:

```xml
<apis>
  <api>petstore</api>
  <api>src/main/openapi/admin.yaml</api>
</apis>
```

For a single value on the command line, use `-Dopenapi.apis=petstore`.

### `openapi:lint` (default phase: `validate`)

Runs `redocly lint`. Fails the build when errors are found.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `format` | `openapi.lint.format` | `stylish` | Build log format: `stylish`, `codeframe`, `summary`, `markdown` or `github-actions` (annotations, printed unprefixed so GitHub picks them up). |
| `reportFile` | `openapi.lint.reportFile` | – | Additionally write *all* problems (not limited by `maxProblems`) to this file … |
| `reportFormat` | `openapi.lint.reportFormat` | `checkstyle` | … in this format: `checkstyle`, `junit`, `json`, `codeclimate` or any build log format. |
| `failOnErrors` | `openapi.lint.failOnErrors` | `true` | Fail the build on `error` problems. |
| `failOnWarnings` | `openapi.lint.failOnWarnings` | `false` | Fail the build on `warn` problems. |
| `skipRules` | `openapi.lint.skipRules` | – | Rule ids to skip. |
| `generateIgnoreFile` | `openapi.lint.generateIgnoreFile` | `false` | Write problems to `.redocly.lint-ignore.yaml`; this baseline-generation mode does not fail on API problems. |

`reportFormat` has an effect only when `reportFile` is set. Configuration errors are checked independently and
still fail when `failOnErrors` is `false`; use `lintConfig=off` to disable that check explicitly.

### `openapi:bundle` (default phase: `generate-resources`)

Runs `redocly bundle`: resolves all `$ref`s into one file and applies the configured decorators.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `outputDirectory` | `openapi.bundle.outputDirectory` | `${project.build.directory}/generated-resources/openapi` | Output directory; files are named `<alias>.<ext>` (or `<basename>.<ext>`). |
| `outputFile` | `openapi.bundle.outputFile` | – | Explicit output file (single API only). |
| `ext` | `openapi.bundle.ext` | extension of `outputFile`, else `yaml` | `yaml`, `yml` or `json`. |
| `dereferenced` | `openapi.bundle.dereferenced` | `false` | Inline everything, leave no `$ref`. |
| `force` | `openapi.bundle.force` | `false` | Write the bundle even if there are errors. |
| `removeUnusedComponents` | `openapi.bundle.removeUnusedComponents` | `false` | Drop unreferenced components. |
| `keepUrlReferences` | `openapi.bundle.keepUrlReferences` | `false` | Keep absolute URL `$ref`s. |
| `componentNamesStrategy` | `openapi.bundle.componentNamesStrategy` | `basename` | Naming of components pulled in from other files: `basename` (file name) or `title` (schema title). |
| `componentRenamingConflicts` | `openapi.bundle.componentRenamingConflicts` | `warn` | Report component renaming conflicts as `warn`, `error` or `off`. |
| `skipDecorators` | `openapi.bundle.skipDecorators` | – | Decorator ids to skip. |
| `addResource` | `openapi.bundle.addResource` | `false` | Add the output directory as a project resource (bundle ends up in the jar). |
| `attach` | `openapi.bundle.attach` | `false` | Attach each bundle as a build artifact (`type` = `ext`, `classifier` = alias). |
| `classifier` | `openapi.bundle.classifier` | `openapi` | Classifier used when an API has no alias. |

`outputFile` overrides `outputDirectory` and is valid only for one API; its extension determines the format unless
`ext` is set (a conflicting `ext` is an error). `ext` also becomes the type of attached artifacts. The `output` field of `apis.<alias>` in `redocly.yaml` is ignored because
Maven controls where bundles go. With `addResource=true`, bundles are added at the artifact's resource root.

### `openapi:check-config`

Lints `redocly.yaml` (`redocly check-config`).

| Parameter | Property | Default | Description |
|---|---|---|---|
| `severity` | `openapi.checkConfig.severity` | `warn` | `warn` reports problems; `error` fails the build. |
| `format` | `openapi.checkConfig.format` | `stylish` | Build log format, as for `lint`. |

If no implicit `redocly.yaml` exists, the goal warns and succeeds. An explicitly configured missing `configFile` fails.

### `openapi:stats`

Prints `redocly stats` for every selected API (`apis`).

| Parameter | Property | Default | Description |
|---|---|---|---|
| `format` | `openapi.stats.format` | `stylish` | `stylish`, `json` or `markdown`. |
| `outputFile` | `openapi.stats.outputFile` | build log | Write the output to a file instead (requires a single selected API). |

### `openapi:score`

Runs `redocly score` (integration simplicity / agent readiness, OpenAPI 3 only) for every selected API (`apis`).

| Parameter | Property | Default | Description |
|---|---|---|---|
| `format` | `openapi.score.format` | `stylish` | `stylish` or `json`. |
| `operationDetails` | `openapi.score.operationDetails` | `false` | Include per-operation details in stylish output. |
| `outputFile` | `openapi.score.outputFile` | build log | Write the output to a file instead (requires a single selected API). |
| `minScore` | `openapi.score.minScore` | – | Fail the build when any selected API scores below this agent-readiness value (0-100). |

For example, `mvn openapi:score -Dopenapi.apis=petstore -Dopenapi.score.minScore=75` works as a quality gate.

### `openapi:join`

Joins two or more OpenAPI 3 descriptions (`redocly join`, experimental upstream). Select them with `apis`, or omit
`apis` to use all APIs from `redocly.yaml`.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `outputFile` | `openapi.join.outputFile` | `target/generated-resources/openapi/joined.yaml` | Joined YAML or JSON file. |
| `prefixTagsWithInfoProp` | `openapi.join.prefixTagsWithInfoProp` | – | Prefix tags with an `info` property such as `title`. |
| `prefixTagsWithFilename` | `openapi.join.prefixTagsWithFilename` | `false` | Prefix tags with each source filename. |
| `prefixComponentsWithInfoProp` | `openapi.join.prefixComponentsWithInfoProp` | – | Prefix component names with an `info` property. |
| `withoutXTagGroups` | `openapi.join.withoutXTagGroups` | `false` | Do not generate `x-tagGroups`. |

### `openapi:split`

Splits a single-file description into a multi-file tree (`redocly split`). Meant to be run by hand once:

```
mvn openapi:split -Dopenapi.split.api=openapi.yaml -Dopenapi.split.outputDirectory=src/main/openapi
```

`api` and `outputDirectory` are required. `separator` (`openapi.split.separator`, default `_`) controls generated
path filenames. This goal does not read `redocly.yaml`.

## Performance

GraalJS is not V8. By default the plugin runs the GraalJS **interpreter** (plain `js-community` dependency, ~30 MB,
works on every JDK 21+), which is fine for typical API descriptions but noticeably slower than Node on very large ones:

| Description | Size | Node | interpreter | isolate |
|---|---|---|---|---|
| Petstore | 17 KB | 0.06 s | 1.1 s | 0.4 s |
| Twilio API | 1.9 MB | 1.1 s | 18 s | 7 s |
| GitHub REST API | 12.9 MB | 6 s | 90 s | 44 s |

(lint, cold, 4 cores; plus a one-time ~4 s / ~1.4 s to load the bundle per Maven build. Problem counts are identical.)

The engine is chosen from what is available — there is nothing to configure, and the build log states which one is
used (`Redocly 2.47.0 - JavaScript engine: …`):

1. **Runtime compilation in-process** when Maven itself runs on a GraalVM JDK.
2. **Native isolate** — GraalJS as a pre-compiled native image inside the JVM (Community licence since GraalVM 25.1).
   Opt in by adding the artifact for your platform to the *plugin's* dependencies (~60 MB download); if it is present
   but cannot start (e.g. wrong platform), the build fails with a clear message instead of silently running slower:
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
3. **Interpreter** otherwise.

The JavaScript runtime is created once per JVM and reused by every goal and module of the build.

## Limitations

- **Custom JavaScript plugins** (`plugins:` in `redocly.yaml`) are not supported: the bundle runs in Redocly's
  "browser" mode, which cannot load plugin files. Built-in rulesets, rule configuration and built-in decorators all
  work; preprocessors only exist in custom plugins, so there is no `skipPreprocessors` parameter.
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
