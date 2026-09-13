# Development

Requires JDK 21+ and Maven 3.9+. Run commands from the repository root:

```sh
mvn verify                        # unit tests
mvn verify -Prun-its               # also run integration tests: Maven builds under target/it
mvn test -Pisolate-tests           # unit tests with the native isolate for this platform
mvn generate-resources -Pbuild-js  # rebuild the embedded JS bundle (requires Node.js and npm)
```

## Implementation

The plugin embeds `@redocly/openapi-core` and selected `@redocly/cli` commands as a JavaScript bundle executed
by GraalJS. The embedded Redocly version is set by `redocly.version` in [pom.xml](../pom.xml).

The implementation is Kotlin. The Mojo classes in `src/main/java` declare parameters and delegate to Kotlin
`Goal` classes; they are Java because Maven extracts goal and parameter descriptions from Javadoc.

Redocly runs in browser mode, which cannot load custom JavaScript plugins. Preprocessors require custom plugins,
so there is no `skipPreprocessors` parameter. Windows paths are mapped for the bundle's POSIX `path` implementation.

See [the JavaScript guide](../js/README.md) for the bundle layout and Redocly upgrade procedure, and
[performance and runtime](performance.md) for engine selection and caching.
