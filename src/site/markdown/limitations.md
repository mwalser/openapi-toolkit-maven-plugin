# Limitations

- **No custom plugins.** `plugins:` in `redocly.yaml` (also when inherited through `extends` or set per API) is
  not supported: Redocly runs in its browser mode, which cannot load JavaScript. All built-in rulesets, rules and
  decorators work. Preprocessors need a plugin, so there is no `skipPreprocessors` parameter.
- **Output paths are Maven parameters.** `apis.<alias>.output` in `redocly.yaml` is ignored; use
  `outputDirectory` or `outputFile`.
- **Not every Redocly CLI command has a goal.** The plugin provides lint, bundle, check-config, stats, score,
  join and split. Documentation builds, previews, `push`, `login` and the other commands are not available.
- **Format support follows upstream.** join and score accept OpenAPI 3 only; split accepts OpenAPI 3 and
  AsyncAPI; join is experimental upstream. Split does not read `redocly.yaml`.
- **Bundle problems are always printed in codeframe format.** The bundle goal has no `format` parameter; use
  lint for other formats and for report files.
- **`dereferenced=true` cannot write circular references as JSON.** The bundle fails with
  `Circular references are not supported for JSON output`; keep YAML output or leave the reference in place.
- **Speed.** The default interpreter is several times slower than Node.js on large descriptions; see
  [performance](performance.html) for the native isolate.
