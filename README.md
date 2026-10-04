# OpenAPI Toolkit Maven Plugin

Lint, bundle, transform and document OpenAPI and AsyncAPI descriptions from Maven with
[Redocly](https://redocly.com/docs/cli). Redocly runs inside the JVM; no Node.js is required.

Requires JDK 21+ and Maven 3.9+.

## Quick start

```xml
<plugin>
  <groupId>net.mwalser</groupId>
  <artifactId>openapi-toolkit-maven-plugin</artifactId>
  <version>0.1.0</version>
  <executions>
    <execution>
      <id>lint</id>
      <goals>
        <goal>lint</goal>                 <!-- runs in the validate phase -->
      </goals>
    </execution>
    <execution>
      <id>bundle</id>
      <goals>
        <goal>bundle</goal>               <!-- runs in the generate-resources phase -->
      </goals>
      <configuration>
        <addResource>true</addResource>   <!-- package the bundle in the JAR -->
      </configuration>
    </execution>
  </executions>
</plugin>
```

Put the API description under `src/main/openapi/` and add a `redocly.yaml` next to the POM:

```yaml
extends:
  - recommended
apis:
  petstore:
    root: src/main/openapi/openapi.yaml
rules:
  security-defined: warn   # downgrade a rule; `off` disables it
```

`mvn package` lints `src/main/openapi/openapi.yaml`, writes the bundle to
`target/generated-resources/openapi/petstore.yaml` and packages it as `petstore.yaml` in the JAR.
An execution of `build-docs` also renders an HTML reference page,
`target/generated-resources/redoc/petstore.html`.

Problems with severity `error` fail the build (`Lint failed with 2 errors.`); warnings do not.
Change severities under `rules:` or pick a smaller ruleset such as `minimal`
(see [Redocly's rules](https://redocly.com/docs/cli/rules)).

Once the plugin is declared in the POM, every goal also runs from the command line.
Without a `redocly.yaml`, pass the files and the built-in `recommended` ruleset applies:

```sh
mvn openapi-toolkit:lint -Dopenapi.toolkit.apis=src/main/openapi/openapi.yaml
```

## Documentation

The [documentation website](https://mwalser.github.io/openapi-toolkit-maven-plugin/) contains:

- [Usage and examples](https://mwalser.github.io/openapi-toolkit-maven-plugin/usage.html)
- [Generated goal and parameter reference](https://mwalser.github.io/openapi-toolkit-maven-plugin/plugin-info.html)
- [Limitations](https://mwalser.github.io/openapi-toolkit-maven-plugin/limitations.html)
- [Performance](https://mwalser.github.io/openapi-toolkit-maven-plugin/performance.html)

For command-line help, run `mvn openapi-toolkit:help -Ddetail=true` after declaring the plugin in your POM.

## Development

See the [development guide](src/site/markdown/development.md) for building, testing, the documentation site
and releasing.

## License

[Apache License 2.0](LICENSE). The embedded Redocly code is [MIT-licensed](js/vendor/redocly-cli/LICENSE)
(© Redocly Inc.); bundled dependency licenses are listed in [third-party notices](src/main/resources/META-INF/THIRD-PARTY-NOTICES.txt).
