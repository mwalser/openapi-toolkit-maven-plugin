# Development

Requires JDK 21+ and Maven 3.9+. Run commands from the repository root:

```sh
mvn verify                        # unit tests
mvn verify -Prun-its               # also run integration tests: Maven builds under target/it
mvn test -Pisolate-tests           # unit tests with the native isolate for this platform
mvn generate-resources -Pbuild-js  # rebuild the embedded JS bundle (requires Node.js and npm)
```

CI runs the unit and integration tests on Linux, macOS and Windows with JDK 21 and 25, the native isolate
on Temurin and GraalVM, and rebuilds the embedded bundle to compare it with the committed one.

## Implementation

The plugin embeds `@redocly/openapi-core` and selected `@redocly/cli` commands as a JavaScript bundle executed
by GraalJS. The embedded Redocly version is set by `redocly.version` in [pom.xml](https://github.com/mwalser/openapi-toolkit-maven-plugin/blob/main/pom.xml).

The implementation is Kotlin. The Mojo classes in `src/main/java` declare parameters and delegate to Kotlin
`Goal` classes; they are Java because Maven extracts goal and parameter descriptions from Javadoc.

Redocly runs in browser mode (see [limitations](limitations.html) for the consequences). Windows paths are
mapped for the bundle's POSIX `path` implementation.

See [the JavaScript guide](https://github.com/mwalser/openapi-toolkit-maven-plugin/blob/main/js/README.md) for the bundle layout and Redocly upgrade procedure, and
[performance](performance.html) for engine selection and caching.

## Release

Releases are built on the maintainer's machine with the Maven release plugin. `~/.m2/settings.xml` must hold
the Central Portal token as server `central` and select the signing key via `gpg.keyname`; the namespace
`net.mwalser` must be verified in the [Central Portal](https://central.sonatype.com/publishing/namespaces).

1. Pre-flight: `main` equals `origin/main`, the working tree is clean, and CI is green on that commit.
2. Update `README.md` for the release: the plugin version in the quick start and the sentence that calls the
   project unreleased. Commit, push, and wait for CI again; the tag must carry this README.
3. Rehearse: `mvn -B release:prepare -DdryRun=true`, then `mvn release:clean`.
4. Pre-warm gpg-agent, because `release:perform` asks for the passphrase in the middle of the build and the
   agent forgets it after ten minutes by default: `echo test | gpg --clearsign -u <gpg.keyname> > /dev/null`
   (the agent caches per key), or raise `default-cache-ttl` in `~/.gnupg/gpg-agent.conf`.
5. `mvn release:prepare` sets the release version, tags `v<version>`, moves `main` to the next SNAPSHOT and
   pushes both. The tag push deploys the documentation website.
6. `mvn release:perform` clones the tag over SSH into `target/checkout`, builds it with `-Prelease` (sources,
   javadoc, signatures) and uploads to Central. It stops once Central has validated the deployment.
7. Publish the deployment in the [Central Portal](https://central.sonatype.com/publishing/deployments). The
   artifact appears at <https://repo1.maven.org/maven2/net/mwalser/openapi-toolkit-maven-plugin/0.1.0/>
   after the sync.
8. Write the release notes and publish them: `gh release create v0.1.0 --title 0.1.0 --notes-file notes.md`.
   There is no changelog file; the release notes are the changelog.

To abandon a release after step 5 and before the upload: `mvn release:rollback` reverts the version commits;
delete the tag locally (`git tag -d v0.1.0`) and on origin (`git push origin :refs/tags/v0.1.0`), then
`mvn release:clean`.

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
The quick start in `README.md` and in `index.md.vm` is the same text; change both.

The Documentation workflow builds and checks the site on every push and pull request and uploads a
`documentation` artifact for review. It deploys to GitHub Pages only for release tags (`v*`), so the public
site always documents the latest release. To redeploy, run the workflow manually on that tag; running it
manually on `main` publishes the development version.

