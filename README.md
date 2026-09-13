# OpenAPI Toolkit Maven Plugin

Lint, bundle and transform OpenAPI / AsyncAPI descriptions from Maven, powered by
[Redocly](https://redocly.com/docs/cli).

Requires JDK 21+ and Maven 3.9+.

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

Add a `redocly.yaml` in the project base directory:

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

Common parameters are listed below. For the full Maven reference, run
`mvn help:describe -Dplugin=openapi -Ddetail`.

| Parameter | Property | Default | Description | Goals |
|---|---|---|---|---|
| `skip` | `openapi.skip` | `false` | Skip every goal of the plugin. | all |
| `skipLint`, `skipBundle`, … | `openapi.<goal>.skip` | `false` | Skip one goal, e.g. `-Dopenapi.lint.skip`. For `check-config`, use `openapi.checkConfig.skip`. | each its own |
| `configFile` | `openapi.configFile` | `redocly.yaml` if it exists | Redocly configuration file. | all except `split` |
| `maxProblems` | `openapi.maxProblems` | `100` | Maximum number of problems printed per API and for the configuration file. | all except `split` |
| `apis` | `openapi.apis` | all APIs of the config | Aliases from `apis:` or paths/URLs to process. | `lint`, `bundle`, `stats`, `score`, `join` |
| `lintConfig` | `openapi.lintConfig` | `warn` | Lint the configuration file first: `warn`, `error`, `off`. | `lint`, `bundle`, `stats`, `score`, `join` |
| `extends` | `openapi.extends` | – | Overrides the `extends` list (`recommended`, `minimal`, `recommended-strict`, …). | `lint`, `bundle` |

List parameters accept comma-separated command-line values (`-Dopenapi.apis=petstore,admin`) or Maven XML:

```xml
<apis>
  <api>petstore</api>
  <api>src/main/openapi/admin.yaml</api>
</apis>
```

### `openapi:lint` (default phase: `validate`)

Lints API descriptions and fails the build on errors. If descriptions are generated during `generate-sources`,
bind this execution to a later phase so the files exist before linting.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `format` | `openapi.lint.format` | `stylish` | Build log format: `stylish`, `codeframe`, `summary`, `markdown` or `github-actions` (GitHub Actions annotations). |
| `reportFile` | `openapi.lint.reportFile` | – | Write all API problems to a report, without the `maxProblems` limit. |
| `reportFormat` | `openapi.lint.reportFormat` | `checkstyle` | Report format: `checkstyle`, `junit`, `json`, `codeclimate` or any build log format. |
| `failOnErrors` | `openapi.lint.failOnErrors` | `true` | Fail the build on `error` problems. |
| `failOnWarnings` | `openapi.lint.failOnWarnings` | `false` | Fail the build on `warn` problems. |
| `skipRules` | `openapi.lint.skipRules` | – | Rule ids to skip. |
| `generateIgnoreFile` | `openapi.lint.generateIgnoreFile` | `false` | Write problems to `.redocly.lint-ignore.yaml`; this baseline-generation mode does not fail on API problems. |

Configuration lint errors fail independently of `failOnErrors`; use `lintConfig=off` to disable configuration linting.

### `openapi:bundle` (default phase: `generate-resources`)

Resolves `$ref`s into one file and applies the configured decorators.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `outputDirectory` | `openapi.bundle.outputDirectory` | `${project.build.directory}/generated-resources/openapi` | Output directory; files are named `<alias>.<ext>` (or `<basename>.<ext>`). |
| `outputFile` | `openapi.bundle.outputFile` | – | Explicit output file; overrides `outputDirectory` (single API only). |
| `ext` | `openapi.bundle.ext` | extension of `outputFile`, else of the input, else `yaml` | `yaml`, `yml` or `json`. |
| `dereferenced` | `openapi.bundle.dereferenced` | `false` | Inline everything, leave no `$ref`. |
| `force` | `openapi.bundle.force` | `false` | Write the bundle even if there are errors. |
| `removeUnusedComponents` | `openapi.bundle.removeUnusedComponents` | `false` | Drop unreferenced components. |
| `keepUrlReferences` | `openapi.bundle.keepUrlReferences` | `false` | Keep absolute URL `$ref`s. |
| `componentNamesStrategy` | `openapi.bundle.componentNamesStrategy` | `basename` | Naming of components pulled in from other files: `basename` (file name) or `title` (schema title). |
| `componentRenamingConflicts` | `openapi.bundle.componentRenamingConflicts` | `warn` | Report component renaming conflicts as `warn`, `error` or `off`. |
| `skipDecorators` | `openapi.bundle.skipDecorators` | – | Decorator ids to skip. |
| `addResource` | `openapi.bundle.addResource` | `false` | Include the written bundles as project resources (packaged in the JAR). |
| `attach` | `openapi.bundle.attach` | `false` | Attach each bundle as a build artifact (`type` = `ext`, `classifier` = alias). |
| `classifier` | `openapi.bundle.classifier` | `openapi` | Classifier used when an API has no alias. |

Maven controls bundle destinations; `apis.<alias>.output` in `redocly.yaml` is ignored.

### `openapi:check-config`

Lints the Redocly configuration file.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `severity` | `openapi.checkConfig.severity` | `error` | `warn` reports problems; `error` fails the build. |
| `format` | `openapi.checkConfig.format` | `stylish` | Build log format, as for `lint`. |

If no implicit `redocly.yaml` exists, the goal warns and succeeds. An explicitly configured missing `configFile` fails.

### `openapi:stats`

Prints statistics for each selected API.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `format` | `openapi.stats.format` | `stylish` | `stylish`, `json` or `markdown`. |
| `outputFile` | `openapi.stats.outputFile` | build log | Write the output to a file instead (requires a single selected API). |

### `openapi:score`

Scores each selected OpenAPI 3 description for integration simplicity and agent readiness.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `format` | `openapi.score.format` | `stylish` | `stylish` or `json`. |
| `operationDetails` | `openapi.score.operationDetails` | `false` | Include per-operation details in stylish output. |
| `outputFile` | `openapi.score.outputFile` | build log | Write the output to a file instead (requires a single selected API). |
| `minScore` | `openapi.score.minScore` | – | Fail the build when any selected API scores below this agent-readiness value (0-100). |

For example, `mvn openapi:score -Dopenapi.apis=petstore -Dopenapi.score.minScore=75` works as a quality gate.

### `openapi:join`

Joins two or more OpenAPI 3 descriptions. This command is experimental upstream.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `outputFile` | `openapi.join.outputFile` | `target/generated-resources/openapi/joined.yaml` | Joined YAML or JSON file. |
| `prefixTagsWithInfoProp` | `openapi.join.prefixTagsWithInfoProp` | – | Prefix tags with an `info` property such as `title`. |
| `prefixTagsWithFilename` | `openapi.join.prefixTagsWithFilename` | `false` | Prefix tags with each source filename. |
| `prefixComponentsWithInfoProp` | `openapi.join.prefixComponentsWithInfoProp` | – | Prefix component names with an `info` property. |
| `withoutXTagGroups` | `openapi.join.withoutXTagGroups` | `false` | Do not generate `x-tagGroups`. |

`prefixTagsWithInfoProp`, `prefixTagsWithFilename` and `withoutXTagGroups` are mutually exclusive; set at most one.

### `openapi:split`

Splits a single-file description into a multi-file tree:

```
mvn openapi:split -Dopenapi.split.api=openapi.yaml -Dopenapi.split.outputDirectory=src/main/openapi
```

`api` and `outputDirectory` are required. `separator` (`openapi.split.separator`, default `_`) controls generated
path filenames. This goal does not read `redocly.yaml`.

## Remote references

Maven offline mode (`-o`) applies to remote `$ref` and `extends` URLs. Online requests use Maven's active proxy
configuration, including encrypted credentials. Header patterns in `resolve.http.headers` match the full URL;
use patterns such as `https://api.example.com/**`.

## Performance

For faster processing of large descriptions, add the native GraalJS isolate to the **plugin's dependencies**:

```xml
<plugin>
  <groupId>net.mwalser</groupId>
  <artifactId>openapi-toolkit-maven-plugin</artifactId>
  <version>…</version>
  <dependencies>
    <dependency>
      <groupId>org.graalvm.polyglot</groupId>
      <artifactId>js-isolate-linux-amd64-community</artifactId>
      <version>25.2.4</version>
      <type>pom</type>
    </dependency>
  </dependencies>
</plugin>
```

Replace `linux-amd64` with `linux-aarch64`, `darwin-aarch64` or `windows-amd64` for your platform.
See [performance and runtime details](docs/performance.md) for benchmarks, engine selection and troubleshooting.

## Limitations

- Custom JavaScript plugins are unsupported, including those inherited through `extends` or declared per API.
  Built-in rulesets, rules and decorators are supported.
- Windows path handling is covered by unit tests; the plugin has not yet been tested on a Windows machine.

## Development

See the [development guide](docs/development.md) for build commands, tests and implementation notes.

## License

[Apache License 2.0](LICENSE). The embedded Redocly code is [MIT-licensed](js/vendor/redocly-cli/LICENSE)
(© Redocly Inc.); bundled dependency licenses are listed in [third-party notices](src/main/resources/META-INF/THIRD-PARTY-NOTICES.txt).
