# Limitations

- **No custom plugins.** `plugins:` in `redocly.yaml` (also when inherited through `extends` or set per API) is
  not supported: Redocly runs in its browser mode, which cannot load JavaScript. All built-in rulesets, rules and
  decorators work. Preprocessors need a plugin, so there is no `skipPreprocessors` parameter.
- **Output paths are Maven parameters.** `apis.<alias>.output` in `redocly.yaml` is ignored; use
  `outputDirectory` or `outputFile`.
- **Not every Redocly CLI command has a goal.** The plugin provides lint, bundle, check-config, stats, score,
  join, split and build-docs. Previews, `push`, `login` and the other commands are not available.
- **Documentation pages need the internet when viewed.** build-docs pre-renders the reference, but the page loads
  the Redoc script from `cdn.redocly.com` (with a subresource integrity hash) and fonts from Google Fonts
  (`disableGoogleFont=true` leaves the fonts out). The pages render with Redoc 2, as the Redocly CLI's build-docs
  does today; Redocly is moving the command to Redoc 3, and the goal will follow.
- **Format support follows upstream.** join and score accept OpenAPI 3 only; split accepts OpenAPI 3 and
  AsyncAPI; build-docs accepts OpenAPI 2.0, 3.0 and 3.1; join is experimental upstream. Split does not read
  `redocly.yaml`.
- **Bundle problems are always printed in codeframe format.** The bundle goal has no `format` parameter; use
  lint for other formats and for report files.
- **`dereferenced=true` cannot write circular references as JSON.** The bundle fails with
  `Circular references are not supported for JSON output`; keep YAML output or leave the reference in place.
- **Speed.** The default interpreter is several times slower than Node.js on large descriptions, and build-docs
  is the slowest goal; see [performance](performance.html) for the native isolate.
