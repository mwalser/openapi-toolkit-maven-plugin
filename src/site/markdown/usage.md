# Usage and examples

Start with the plugin declaration and `redocly.yaml` from the [quick start](index.html).
All commands below assume the plugin is declared in the project's POM.
The [goal reference](plugin-info.html) lists every parameter, default, and command-line property.

## Select API descriptions

The `apis` parameter accepts aliases from `redocly.yaml`, file paths relative to the Maven module,
and URLs. With no explicit selection, the plugin processes every API in the configuration.
Without a configuration file, provide paths explicitly; linting uses Redocly's built-in `recommended` ruleset.

```sh
mvn openapi-toolkit:lint -Dopenapi.toolkit.apis=petstore,admin
mvn openapi-toolkit:lint -Dopenapi.toolkit.apis=src/main/openapi/openapi.yaml
```

List parameters also accept Maven XML inside an execution's `<configuration>`:

```xml
<apis>
  <api>petstore</api>
  <api>src/main/openapi/admin.yaml</api>
</apis>
```

Set `configFile` to use a configuration other than `redocly.yaml` in the module directory.
Use `extends` on lint and bundle to override the configured rulesets, for example
`-Dopenapi.toolkit.extends=minimal`.

## Lint and write CI reports

[Lint](lint-mojo.html) runs in `validate` by default. If another plugin generates your descriptions,
bind lint to a later phase so the input files exist first.

```xml
<execution>
  <id>lint</id>
  <goals><goal>lint</goal></goals>
  <configuration>
    <failOnWarnings>true</failOnWarnings>
    <reportFile>${project.build.directory}/openapi/lint.xml</reportFile>
    <reportFormat>junit</reportFormat>
  </configuration>
</execution>
```

Lint fails on API errors by default. `failOnWarnings` additionally fails on warnings, while
`failOnErrors=false` allows API errors. Configuration lint errors are handled independently;
`lintConfig=off` disables configuration linting.

The report contains all API problems, even when `maxProblems` limits the build log.
For GitHub Actions annotations, use `-Dopenapi.toolkit.lint.format=github-actions`.

To establish a baseline, run lint with `-Dopenapi.toolkit.lint.generateIgnoreFile=true`.
This writes `.redocly.lint-ignore.yaml` and does not fail on API problems during baseline generation.

## Bundle and package descriptions

[Bundle](bundle-mojo.html) runs in `generate-resources`, resolves references, and applies configured decorators.
By default, it writes `<alias>.<ext>` or `<basename>.<ext>` into
`target/generated-resources/openapi`. JSON inputs remain JSON; other supported formats are YAML and YML.

Add the bundle to the main JAR and attach it as a separate Maven artifact:

```xml
<execution>
  <id>bundle</id>
  <goals><goal>bundle</goal></goals>
  <configuration>
    <addResource>true</addResource>
    <attach>true</attach>
    <ext>yaml</ext>
  </configuration>
</execution>
```

Attached artifacts use the output extension as their type and the API alias as their classifier.
For inputs without an alias, `classifier` defaults to `openapi`. Give multiple inputs distinct
aliases when attaching outputs of the same type.

To choose an exact output path for a single API:

```sh
mvn openapi-toolkit:bundle -Dopenapi.toolkit.apis=petstore -Dopenapi.toolkit.bundle.outputFile=target/petstore.json
```

`outputFile` overrides `outputDirectory` and determines the output format unless `ext` is also set;
when both are set, they must agree. Maven controls output destinations, so `apis.<alias>.output`
in `redocly.yaml` is ignored.

Use `dereferenced=true` to inline references or `removeUnusedComponents=true` to remove unused components.
`force=true` writes bundles even when bundling reports errors.

## Check the configuration

```sh
mvn openapi-toolkit:check-config
```

[Check-config](check-config-mojo.html) fails on configuration problems by default.
Use `-Dopenapi.toolkit.checkConfig.severity=warn` to report problems without failing.
An absent implicit `redocly.yaml` produces a warning; an explicitly configured missing file fails.

## Statistics and scoring

[Stats](stats-mojo.html) supports stylish, JSON, and Markdown output:

```sh
mvn openapi-toolkit:stats -Dopenapi.toolkit.apis=petstore -Dopenapi.toolkit.stats.format=json -Dopenapi.toolkit.stats.outputFile=target/stats.json
```

[Score](score-mojo.html) evaluates OpenAPI 3 descriptions for integration simplicity and agent readiness:

```sh
mvn openapi-toolkit:score -Dopenapi.toolkit.apis=petstore -Dopenapi.toolkit.score.minScore=75
```

The score goal fails when any selected API scores below `minScore` (0–100).
Use `operationDetails=true` for per-operation details or `format=json` for structured output.
For both stats and score, writing to `outputFile` requires a single selected API.

## Join descriptions

[Join](join-mojo.html) combines two or more OpenAPI 3 descriptions. This command is experimental upstream.

```sh
mvn openapi-toolkit:join -Dopenapi.toolkit.apis=petstore,admin -Dopenapi.toolkit.join.prefixTagsWithInfoProp=title
```

The default output is `target/generated-resources/openapi/joined.yaml`. Set `outputFile` to choose another
YAML or JSON destination. `prefixComponentsWithInfoProp` prefixes component names using an `info` property.

`prefixTagsWithInfoProp`, `prefixTagsWithFilename`, and `withoutXTagGroups` are mutually exclusive;
set at most one.

## Split a description

[Split](split-mojo.html) extracts a single-file OpenAPI 3 or AsyncAPI description into a tree of files:

```sh
mvn openapi-toolkit:split -Dopenapi.toolkit.split.api=openapi.yaml -Dopenapi.toolkit.split.outputDirectory=src/main/openapi
```

`api` and `outputDirectory` are required. `separator` defaults to `_` and controls generated path filenames.
This goal does not read `redocly.yaml`.

## Skip executions

Set `-Dopenapi.toolkit.skip=true` to skip the plugin's goals, or use a goal-specific property such as
`-Dopenapi.toolkit.lint.skip=true`. For check-config, the property is `openapi.toolkit.checkConfig.skip`.

## Remote references

Maven offline mode (`-o`) applies to remote `$ref` and `extends` URLs. Online requests use Maven's active
proxy configuration, including encrypted credentials. Header patterns in `resolve.http.headers` match
the full URL; use patterns such as `https://api.example.com/**`.

```yaml
resolve:
  http:
    headers:
      - matches: 'https://api.example.com/**'
        name: Authorization
        envVariable: OPENAPI_AUTHORIZATION
```

The environment variable supplies the complete header value, such as `Bearer <token>`.
