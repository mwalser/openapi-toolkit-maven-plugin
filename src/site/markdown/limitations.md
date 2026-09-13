# Limitations

- Custom JavaScript plugins are unsupported, including those inherited through `extends` or declared per API.
  Built-in rulesets, rules and decorators are supported.
- Windows path handling is covered by unit tests; the plugin has not yet been tested on a Windows machine.

The [join goal](join-mojo.html) is experimental upstream and supports OpenAPI 3 descriptions.
The [score goal](score-mojo.html) also requires OpenAPI 3. The [split goal](split-mojo.html)
supports OpenAPI 3 and AsyncAPI descriptions and does not read `redocly.yaml`.
