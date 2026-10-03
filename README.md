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
      <goals>
        <goal>lint</goal>                 <!-- bound to validate -->
      </goals>
    </execution>
    <execution>
      <id>bundle</id>
      <goals>
        <goal>bundle</goal>               <!-- bound to generate-resources -->
      </goals>
      <configuration>
        <addResource>true</addResource>   <!-- ship the bundled spec inside the jar -->
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
The short `openapi-toolkit:<goal>` form works after the plugin is declared in the POM:

```
mvn openapi-toolkit:lint -Dopenapi.toolkit.apis=src/main/openapi/openapi.yaml
```

## Documentation

The [documentation website](https://mwalser.github.io/openapi-toolkit-maven-plugin/) contains:

- [Usage and examples](https://mwalser.github.io/openapi-toolkit-maven-plugin/usage.html)
- [Generated goal and parameter reference](https://mwalser.github.io/openapi-toolkit-maven-plugin/plugin-info.html)
- [Performance and runtime](https://mwalser.github.io/openapi-toolkit-maven-plugin/performance.html)
- [Limitations](https://mwalser.github.io/openapi-toolkit-maven-plugin/limitations.html)

For command-line help, run `mvn openapi-toolkit:help -Ddetail=true` after declaring the plugin in your POM.

## Development

Run `mvn verify` for unit tests or `mvn verify -Prun-its` to include Maven integration tests.
Build the documentation with `mvn site` and open `target/site/index.html`.
See the [development guide](src/site/markdown/development.md) for implementation notes and publishing the site.

## License

[Apache License 2.0](LICENSE). The embedded Redocly code is [MIT-licensed](js/vendor/redocly-cli/LICENSE)
(© Redocly Inc.); bundled dependency licenses are listed in [third-party notices](src/main/resources/META-INF/THIRD-PARTY-NOTICES.txt).
