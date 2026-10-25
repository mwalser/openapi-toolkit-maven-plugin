// Port of the rendering half of packages/cli/src/commands/build-docs/utils.ts of redocly-cli (MIT): prepare the
// bundled description with Redoc 3, render the reference with React on the server, and fill the page template.
// Configuration, loading and bundling (decorators and preprocessors included), API selection and output
// placement happen in the core bundle (src/api/build-docs.js).
import handlebars from 'handlebars';
import { createElement } from 'react';
import { renderToString } from 'react-dom/server';
import { convertSwagger2OpenAPI, logoFromSpec, prepareApiDocs, RedoclyApiDocsStandalone, ServerStyleSheet } from 'redoc';
import { host } from '../host.js';
import { removeRedoclyLogo as withoutRedoclyLogo } from './redocly-logo.js';
import * as fs from 'node:fs';
import * as path from 'node:path';

export { convertSwagger2OpenAPI };

const DEFAULT_TEMPLATE_SOURCE = `<!DOCTYPE html>
<html lang="en">

<head>
  <meta charset="utf8" />
  <title>{{title}}</title>
  <!-- needed for adaptive design -->
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    body {
      padding: 0;
      margin: 0;
    }
  </style>
  {{{redocHead}}}
</head>

<body>
  {{{redocHTML}}}
</body>

</html>
`;

/**
 * Renders one bundled API description to an HTML page.
 *
 * @param options {{
 *   definition: object|string, title?: string, templateFile?: string, templateOptions: object,
 *   redocOptions: object, configPath?: string, disableTelemetry: boolean, inlineBundle: boolean,
 *   removeRedoclyLogo: boolean
 * }} `definition` is the bundled OpenAPI 3 or AsyncAPI document, or the GraphQL schema source;
 * `templateFile` an absolute path. `removeRedoclyLogo` requires `inlineBundle`: the script from the CDN would
 * add the link back when the page hydrates.
 * @returns {Promise<{ page: string, title: string }>}
 */
export async function renderPage({ definition, title, templateFile, templateOptions, redocOptions, configPath, disableTelemetry, inlineBundle, removeRedoclyLogo }) {
  const pageOptions = { ...redocOptions, skipBundle: true };
  const prepared = await prepareApiDocs({ spec: definition, options: pageOptions });
  const app = createElement(RedoclyApiDocsStandalone, {
    items: prepared.items,
    store: prepared.store,
    basePath: '/',
    options: prepared.options,
    logo: logoFromSpec(prepared.document),
    telemetryConfig: { disabled: disableTelemetry },
    spec: prepared.document,
  });
  const sheet = new ServerStyleSheet();
  let html;
  globalThis.__openapiToolkitRemoveRedoclyLogo = !!removeRedoclyLogo; // read by the patched library (see build.mjs)
  try {
    html = renderToString(sheet.collectStyles(app));
  } finally {
    globalThis.__openapiToolkitRemoveRedoclyLogo = false;
  }
  const css = sheet.getStyleTags();

  const customTemplate =
    templateFile ||
    (typeof redocOptions.htmlTemplate === 'string'
      ? path.resolve(configPath ? path.dirname(configPath) : '', redocOptions.htmlTemplate)
      : undefined);
  const templateSource = customTemplate ? fs.readFileSync(customTemplate, 'utf-8') : DEFAULT_TEMPLATE_SOURCE;
  const template = handlebars.compile(templateSource);

  const redocBundleUrl = `https://cdn.redocly.com/redoc/v${__REDOC_VERSION__}/bundles/redoc.standalone.js`;
  const redocScript = inlineBundle ? inlinedScript(removeRedoclyLogo) : `import { hydrate } from "${redocBundleUrl}";`;
  const redocHead = inlineBundle
    ? css
    : `<link rel="modulepreload" href="${redocBundleUrl}" integrity="${__REDOC_STANDALONE_SRI__}" crossorigin="anonymous" />` + css;

  const pageTitle = title || (typeof definition === 'string' ? undefined : definition.info?.title) || 'ReDoc documentation';
  const page = template({
    redocHTML: `
      <div id="redoc">${html}</div>
      <script type="module">
      ${redocScript}

      const __redoc_definition = ${sanitizeJSONString(JSON.stringify(definition))};
      const __redoc_options = ${sanitizeJSONString(JSON.stringify({ ...pageOptions, disableTelemetry }))};

      hydrate(__redoc_definition, __redoc_options, document.getElementById('redoc'));
      </script>`,
    redocHead,
    title: pageTitle,
    templateOptions,
  });
  return { page, title: pageTitle };
}

// Redoc's page script for `inlineBundle`, a resource next to this bundle (see build.mjs) that is read on first use
// and kept, with and without the Redocly logo link, ready to be embedded.
const inlinedScripts = new Map();

function inlinedScript(removeRedoclyLogo) {
  let script = inlinedScripts.get(removeRedoclyLogo);
  if (script === undefined) {
    script = escapeClosingScriptTag(host.resource('redoc-standalone.js'));
    if (removeRedoclyLogo) script = withoutRedoclyLogo(script, 'redoc-standalone.js');
    inlinedScripts.set(removeRedoclyLogo, script);
  }
  return script;
}

function sanitizeJSONString(str) {
  return escapeUnicode(str).replace(/</g, '\\u003c');
}

function escapeClosingScriptTag(str) {
  return str.replace(/<\/(script)/gi, '<\\/$1');
}

// see http://www.thespanner.co.uk/2011/07/25/the-json-specification-is-now-wrong/
function escapeUnicode(str) {
  return str.replace(new RegExp('\\u2028|\\u2029', 'g'), (m) => '\\u202' + (m.charCodeAt(0) === 0x2028 ? '8' : '9'));
}
