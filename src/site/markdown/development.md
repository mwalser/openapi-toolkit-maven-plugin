# Development

Requires JDK 21+ and Maven 3.9+. Run commands from the repository root:

```sh
mvn verify                        # unit tests
mvn verify -Prun-its               # also run integration tests: Maven builds under target/it
mvn test -Pisolate-tests           # unit tests with the native isolate for this platform
mvn generate-resources -Pbuild-js  # rebuild the embedded JS bundle (requires Node.js and npm)
```

CI runs the unit and integration tests on Linux, macOS and Windows with JDK 21, 25 and 26, the native isolate
on Temurin and GraalVM, and rebuilds the embedded bundle to compare it with the committed one.

## Implementation

The plugin embeds `@redocly/openapi-core` and selected `@redocly/cli` commands as a JavaScript bundle executed
by GraalJS. The embedded Redocly version is set by `redocly.version` in [pom.xml](https://github.com/mwalser/openapi-toolkit-maven-plugin/blob/main/pom.xml),
GraalJS by `graalvm.version`; a GraalJS upgrade also updates the JDK range of the native isolate on the
[performance](performance.html) page.

The implementation is Kotlin. The Mojo classes in `src/main/java` declare parameters and delegate to Kotlin
`Goal` classes; they are Java because Maven extracts goal and parameter descriptions from Javadoc.

Redocly runs in browser mode (see [limitations](limitations.html) for the consequences). Windows paths are
mapped for the bundle's POSIX `path` implementation.

See [the JavaScript guide](https://github.com/mwalser/openapi-toolkit-maven-plugin/blob/main/js/README.md) for the bundle layout and Redocly upgrade procedure, and
[performance](performance.html) for engine selection and caching.

## Release

The Release workflow builds, signs and uploads releases on GitHub; the maintainer's machine only tags. The
workflow runs for `v*` tags in the GitHub environment `release`, which requires the maintainer's approval and
holds the secrets `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` (the signing key, exported with
`gpg --armor --export-secret-keys <fingerprint>`, and its passphrase) and `CENTRAL_USERNAME` and
`CENTRAL_PASSWORD` (a Central Portal user token). The namespace `net.mwalser` must be verified in the
[Central Portal](https://central.sonatype.com/publishing/namespaces).

1. Pre-flight: `main` equals `origin/main`, the working tree is clean, and CI is green on that commit.
2. Set the release version in the quick start of `README.md`. Commit, push, and wait for CI again; the tag must
   carry this README.
3. Rehearse: `mvn -B release:prepare -DdryRun=true`, then `mvn release:clean`. After a change to the workflow or
   the secrets, also run the Release workflow by hand on `main` with "publish" unchecked: it builds and signs
   without uploading anything.
4. `mvn release:prepare` sets the release version, tags `v<version>`, moves `main` to the next SNAPSHOT and
   pushes both. The tag starts the Release workflow and deploys the documentation website.
5. Approve the `release` environment in the workflow run. The workflow runs the CI matrix on the tag, builds
   with `-Prelease` (sources, javadoc, signatures), records the build provenance of the jars, uploads to Central
   and stops once Central has validated the deployment, and drafts the GitHub release with the jars and
   signatures attached.
6. Publish the deployment in the [Central Portal](https://central.sonatype.com/publishing/deployments). The
   artifact appears at <https://repo1.maven.org/maven2/net/mwalser/openapi-toolkit-maven-plugin/> after the
   sync.
7. Write the release notes into the draft GitHub release and publish it. There is no changelog file; the release
   notes are the changelog.

To abandon a release after step 4 and before the upload: `mvn release:rollback` reverts the version commits;
delete the tag locally (`git tag -d v0.1.0`) and on origin (`git push origin :refs/tags/v0.1.0`), then
`mvn release:clean`; drop the deployment in the Portal and delete the draft release if they exist. A run that
failed for a reason outside the repository can be re-run from GitHub; a fix in the repository needs the rollback
and a new `release:prepare`.

`gh attestation verify <jar> --owner mwalser` checks the provenance of a published jar. The build is
reproducible: `mvn -B package -DskipTests` on the tag yields a jar with the same entries and checksums. The jar
itself is byte-identical only with the same zlib as the GitHub runner; a JDK linked against zlib-ng, such as
Homebrew's, compresses differently.

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

