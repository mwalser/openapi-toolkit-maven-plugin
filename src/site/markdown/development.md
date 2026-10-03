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
by GraalJS. The embedded Redocly version is set by `redocly.version` in [pom.xml](https://github.com/mwalser/openapi-toolkit-maven-plugin/blob/main/pom.xml).

The implementation is Kotlin. The Mojo classes in `src/main/java` declare parameters and delegate to Kotlin
`Goal` classes; they are Java because Maven extracts goal and parameter descriptions from Javadoc.

Redocly runs in browser mode, which cannot load custom JavaScript plugins. Preprocessors require custom plugins,
so there is no `skipPreprocessors` parameter. Windows paths are mapped for the bundle's POSIX `path` implementation.

See [the JavaScript guide](https://github.com/mwalser/openapi-toolkit-maven-plugin/blob/main/js/README.md) for the bundle layout and Redocly upgrade procedure, and
[performance and runtime](performance.html) for engine selection and caching.

## Release

Releases are built from this machine with the Maven release plugin. `~/.m2/settings.xml` must hold the
Central Portal token as server `central` and select the signing key via `gpg.keyname`.

```sh
mvn release:prepare   # sets the release version, tags v<version>, moves main to the next SNAPSHOT
mvn release:perform   # builds the tag with -Prelease and uploads sources, javadoc and signatures to Central
```

CI must be green on the commit being released; `release:prepare` runs the unit tests only.
The upload stops after Central has validated the deployment; publishing is confirmed in the
[Central Portal](https://central.sonatype.com/publishing/deployments). Afterwards, write the GitHub release
notes for the new tag and update the version in the README.

## Documentation website

Build the complete site, including the generated goal reference:

```sh
mvn clean site
python3 scripts/check-site.py
```

Open `target/site/index.html` in a browser. `mvn site` compiles the plugin and generates the
Mojo descriptor before rendering the reference pages; it does not run the tests or require Node.js.
The local link check uses Python 3.9+ and runs automatically in CI.

Edit guides under `src/site/markdown/` and navigation in `src/site/site.xml`.
Files ending in `.md.vm` use Maven's Velocity filtering for the project and GraalJS versions.
Use underlined Markdown headings in those templates: Velocity treats lines starting with `##` as comments.
Parameter descriptions, defaults, and user properties come from the Java Mojo classes;
update those descriptions to update the generated reference.

The Documentation workflow builds and checks the site on pull requests and uploads a
`documentation` artifact for review. Pushes to `main` also deploy it to GitHub Pages.
You can rebuild and deploy manually by running that workflow on `main`.

### Enable GitHub Pages

In the repository's **Settings → Pages → Build and deployment**, select **GitHub Actions**
as the source. Then push the site configuration to `main` or run the Documentation workflow.
The published address is <https://mwalser.github.io/openapi-toolkit-maven-plugin/>.

The website follows `main`; the version displayed on each page identifies the documented build.
Publishing the Maven artifact and publishing this website are separate workflows.
