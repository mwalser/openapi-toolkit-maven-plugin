# Usage and examples

Start with the plugin declaration and `redocly.yaml` from the [quick start](index.html).
Everything below assumes the plugin is declared in the POM. The [goal reference](plugin-info.html) lists every
parameter with its default and user property.

## Select API descriptions

`apis` (property `openapi.toolkit.apis`) selects what a goal processes: aliases from the `apis` section of
`redocly.yaml`, paths relative to the module directory, or URLs. Leave it empty to process every API in
`redocly.yaml`. Without a `redocly.yaml`, `apis` is required and lint uses the built-in `recommended` ruleset.

```yaml
# redocly.yaml
extends:
  - recommended
apis:
  petstore:
    root: src/main/openapi/openapi.yaml
  admin:
    root: src/main/openapi/admin.yaml
    rules:
      operation-4xx-response: off   # per-API override
```

```sh
mvn openapi-toolkit:lint -Dopenapi.toolkit.apis=petstore,admin
mvn openapi-toolkit:lint -Dopenapi.toolkit.apis=src/main/openapi/openapi.yaml
```

In the POM, list parameters take one child element per value:

```xml
<apis>
  <api>petstore</api>
  <api>src/main/openapi/admin.yaml</api>
</apis>
```

`configFile` points at a configuration file other than `redocly.yaml` in the module directory.
`extends` (XML element `<extends>` with one `<extend>` per ruleset, property `openapi.toolkit.extends`) replaces
the rulesets of the configuration on lint and bundle, for example `-Dopenapi.toolkit.extends=minimal`.

## Run goals from the command line

Once the plugin is declared in the POM, every goal runs from the command line. Every parameter has a user
property: `openapi.toolkit.<parameter>` for the parameters shared by all goals, `openapi.toolkit.<goal>.<parameter>`
for a goal's own.

```sh
mvn openapi-toolkit:lint -Dopenapi.toolkit.apis=petstore -Dopenapi.toolkit.lint.failOnWarnings=true
```

`lint` and `bundle` have default phases (`validate` and `generate-resources`). `check-config`, `stats`, `score`,
`join` and `split` do not.
Set the phase explicitly:

```xml
<execution>
  <id>check-config</id>
  <phase>validate</phase>
  <goals>
    <goal>check-config</goal>
  </goals>
</execution>
```

## Configure rules and decorators

`lint` evaluates `rules`; `bundle` applies `decorators` and reports only what prevents bundling, such as
unresolved references. Rule and decorator ids are Redocly's:
[built-in rules](https://redocly.com/docs/cli/rules/built-in-rules), [decorators](https://redocly.com/docs/cli/decorators).

```yaml
# redocly.yaml
extends:
  - recommended
apis:
  petstore:
    root: src/main/openapi/openapi.yaml
rules:
  security-defined: warn            # downgrade
  info-license: off                 # disable
  operation-operationId: error      # upgrade
  no-unused-components: error
decorators:
  info-description-override:        # applied by bundle, not by lint
    filePath: src/main/openapi/description.md
  remove-x-internal: on
```

A misspelled rule id is reported with its line in `redocly.yaml`. A rule with an invalid severity is ignored.
The build continues unless `lintConfig` is `error`, see [Lint in CI](#Lint_in_CI).

## Lint in CI

[lint](lint-mojo.html) runs in `validate`. If another plugin generates the description, bind lint to a later
phase so the file exists first.

```xml
<execution>
  <id>lint</id>
  <goals>
    <goal>lint</goal>
  </goals>
  <configuration>
    <failOnWarnings>true</failOnWarnings>
    <reportFile>${project.build.directory}/openapi/lint.xml</reportFile>
    <reportFormat>junit</reportFormat>
  </configuration>
</execution>
```

`reportFile` always contains every problem; `maxProblems` only caps the build log. A JUnit report is picked up by
most CI test reporters, `checkstyle` (the default) by code-quality plugins. On GitHub Actions, problems become
annotations on the pull request:

```yaml
# .github/workflows/ci.yml (excerpt)
- run: mvn -B verify -Dopenapi.toolkit.lint.format=github-actions
```

Lint fails the build on problems with severity `error`. `failOnWarnings=true` also fails on warnings;
`failOnErrors=false` never fails on API problems. Problems in `redocly.yaml` itself are reported first with the
severity given by `lintConfig` (default `warn`, which never fails the build). `lintConfig=error` fails the build
on configuration problems regardless of `failOnErrors`; `lintConfig=off` skips the check.
The [check-config](#Check_the_configuration) goal validates the configuration on its own.

To accept the current problems as a baseline, run lint once with
`-Dopenapi.toolkit.lint.generateIgnoreFile=true`. It writes `.redocly.lint-ignore.yaml` next to `redocly.yaml`
(or into the module directory when there is no configuration file) and does not fail the build; commit the
file and later runs ignore the listed problems.

## Bundle and package descriptions

[bundle](bundle-mojo.html) runs in `generate-resources`. It resolves references to other files, applies the
decorators from `redocly.yaml` and writes `target/generated-resources/openapi/<alias>.yaml` (or
`<basename>.yaml` for paths). JSON input stays JSON; `ext` forces `yaml`, `yml` or `json`.

To package the bundle in the module's JAR and attach it as an artifact of its own:

```xml
<execution>
  <id>bundle</id>
  <goals>
    <goal>bundle</goal>
  </goals>
  <configuration>
    <addResource>true</addResource>   <!-- petstore.yaml at the root of petstore-api.jar -->
    <attach>true</attach>             <!-- petstore-api-1.0.0-petstore.yaml in the repository -->
  </configuration>
</execution>
```

`addResource` adds the output directory as a resource directory, so each bundle lands at the root of the JAR
under its file name. Resources are copied in `process-resources`; keep bundle in `generate-resources` (its
default) or earlier, otherwise the bundle is silently left out of the JAR.

An attached bundle has type `yaml` (or `json`) and the alias as classifier; inputs given as paths use
`classifier` (default `openapi`). Two bundles of the same type need distinct aliases; the build fails otherwise.

To choose an exact output path for a single API:

```sh
mvn openapi-toolkit:bundle -Dopenapi.toolkit.apis=petstore -Dopenapi.toolkit.bundle.outputFile=target/openapi/petstore.json
```

`outputFile` replaces `outputDirectory`; its extension sets the format (`ext`, if also set, must match).
`apis.<alias>.output` in `redocly.yaml` is ignored: output locations are Maven parameters.

`dereferenced=true` inlines every reference; `removeUnusedComponents=true` drops components nothing references;
`force=true` writes the bundle even when bundling reports errors (the build then succeeds with a warning).

A schema that the root file lists under `components` as a `$ref` to a file, while the paths reference that
file directly, ends up twice in the bundle: under its component name as a reference, and under a name derived
from the file name with the schema itself (`Pet` and `pet`). Reference the component
(`#/components/schemas/Pet`) from the paths, or give the schemas a `title` and set
`componentNamesStrategy=title`. Splitting such a bundle writes `Pet.yaml` next to `pet.yaml`, which collide on
case-insensitive file systems.

### Attach bundles for other modules

With `attach=true` in the module `petstore-api`, the bundle is the artifact
`com.example:petstore-api:1.0.0:yaml:petstore` (type and classifier are the bundle's extension and alias).
Another module copies it for a code generator:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-dependency-plugin</artifactId>
  <executions>
    <execution>
      <id>fetch-petstore-bundle</id>
      <phase>generate-sources</phase>
      <goals>
        <goal>copy</goal>
      </goals>
      <configuration>
        <artifactItems>
          <artifactItem>
            <groupId>com.example</groupId>
            <artifactId>petstore-api</artifactId>
            <version>${project.version}</version>
            <type>yaml</type>
            <classifier>petstore</classifier>
            <destFileName>petstore.yaml</destFileName>
          </artifactItem>
        </artifactItems>
        <outputDirectory>${project.build.directory}/openapi</outputDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

A generator in that module then reads `target/openapi/petstore.yaml`. The same coordinates work as a plain
`<dependency>` with `<type>yaml</type>` and `<classifier>petstore</classifier>`, which puts the file on the
classpath. Either way the producer must be built first: in the same reactor, include both modules in the build,
otherwise `mvn install` the producer.

## Multi-module projects

Declare the plugin once in the parent's `pluginManagement` and list it in every module that has an API
description. Each module has its own `redocly.yaml` and `src/main/openapi/`; paths in `redocly.yaml` are
relative to the module.

```xml
<!-- parent pom.xml -->
<build>
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>net.mwalser</groupId>
        <artifactId>openapi-toolkit-maven-plugin</artifactId>
        <version>0.1.0</version>
        <configuration>
          <failOnWarnings>true</failOnWarnings>
        </configuration>
        <executions>
          <execution>
            <id>lint</id>
            <goals>
              <goal>lint</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </pluginManagement>
</build>
```

```xml
<!-- petstore-api/pom.xml, and every other module with an API description -->
<build>
  <plugins>
    <plugin>
      <groupId>net.mwalser</groupId>
      <artifactId>openapi-toolkit-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```

Modules without an API description simply do not list the plugin. The JavaScript engine is initialized once
per build and shared by all modules, also with `mvn -T 4`. To skip the plugin in one module, set
`<skip>true</skip>` in that module's plugin configuration.

## Check the configuration

```sh
mvn openapi-toolkit:check-config
```

[check-config](check-config-mojo.html) fails the build on problems in `redocly.yaml`;
`-Dopenapi.toolkit.checkConfig.severity=warn` reports them without failing. With no `redocly.yaml` the goal
warns `No Redocly configuration file found` and does nothing; a `configFile` that does not exist fails.

## Statistics and scoring

[stats](stats-mojo.html) prints to the build log in `stylish`, `json` or `markdown` format, or to `outputFile`:

```sh
mvn openapi-toolkit:stats -Dopenapi.toolkit.apis=petstore -Dopenapi.toolkit.stats.format=json -Dopenapi.toolkit.stats.outputFile=target/openapi/stats.json
```

[score](score-mojo.html) rates an OpenAPI 3 description (0–100) and can enforce a minimum:

```sh
mvn openapi-toolkit:score -Dopenapi.toolkit.apis=petstore -Dopenapi.toolkit.score.minScore=75
```

`operationDetails=true` adds a per-operation breakdown; `format=json` gives machine-readable output.
`outputFile` on stats and score requires a single selected API.

## Join descriptions

[join](join-mojo.html) merges two or more OpenAPI 3 descriptions into one file (experimental upstream):

```sh
mvn openapi-toolkit:join -Dopenapi.toolkit.apis=petstore,admin -Dopenapi.toolkit.join.prefixComponentsWithInfoProp=title
```

The result is `target/generated-resources/openapi/joined.yaml` unless `outputFile` says otherwise (a `.json`
extension gives JSON). Join stops on conflicts, and two descriptions that both define a component such as
`Error` conflict: `prefixComponentsWithInfoProp` prefixes component names with an `info` property of their
description, `prefixTagsWithInfoProp` or `prefixTagsWithFilename` do the same for tags. Set at most one of
`prefixTagsWithInfoProp`, `prefixTagsWithFilename` and `withoutXTagGroups`.

## Split a description

[split](split-mojo.html) turns a single-file OpenAPI 3 or AsyncAPI description into a multi-file tree, once,
from the command line:

```sh
mvn openapi-toolkit:split -Dopenapi.toolkit.split.api=src/main/openapi/petstore-single.yaml -Dopenapi.toolkit.split.outputDirectory=src/main/openapi/petstore
```

`api` and `outputDirectory` are required. `separator` (default `_`) joins path segments in generated file
names: `/users/{id}` becomes `users_{id}.yaml`. Split does not read `redocly.yaml`.

## Skip goals

`-Dopenapi.toolkit.skip=true` skips every goal of the plugin. Each goal also has its own property:
`openapi.toolkit.lint.skip`, `openapi.toolkit.bundle.skip`, `openapi.toolkit.checkConfig.skip`,
`openapi.toolkit.stats.skip`, `openapi.toolkit.score.skip`, `openapi.toolkit.join.skip`,
`openapi.toolkit.split.skip`. A skipped goal logs `Skipping (openapi.toolkit.skip=true)`.

## JVM warnings on JDK 24 and later

On JDK 24 and later every build starts with four lines such as
`WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`. They come from Truffle, the engine
under GraalJS, and are harmless. Add `--sun-misc-unsafe-memory-access=allow` to `.mvn/jvm.config` to silence
them.

## Offline builds and proxies

Remote references, `extends` URLs and `apis` given as URLs are fetched over HTTP. `mvn -o` (offline) refuses
them and the goal fails with `ENETDOWN: Maven is offline (-o), remote references are not fetched, fetch '<url>'`.
Requests go through the active `<proxy>` of `settings.xml`, including encrypted passwords and `nonProxyHosts`.
To send credentials, add `resolve.http.headers` to `redocly.yaml`; `matches` is compared against the full URL:

```yaml
resolve:
  http:
    headers:
      - matches: 'https://api.example.com/**'
        name: Authorization
        envVariable: OPENAPI_AUTHORIZATION
```

The environment variable supplies the complete header value, such as `Bearer <token>`.
