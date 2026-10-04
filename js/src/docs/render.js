// Port of the rendering half of packages/cli/src/commands/build-docs/{index,utils}.ts of redocly-cli (MIT):
// bundle the description with Redoc's loader, render the reference with React on the server, and fill the
// page template. Configuration, API selection and output placement happen in the core bundle (src/api/build-docs.js).
import handlebars from 'handlebars';
import { createElement } from 'react';
import { renderToString } from 'react-dom/server';
import * as redocModule from 'redoc';
import { ServerStyleSheet } from 'styled-components';
import * as fs from 'node:fs';
import * as path from 'node:path';

// redoc.lib.js is a webpack UMD build whose namespace carries `__esModule` and no default export, so esbuild's
// default import of it is undefined (Node's interop would make it the namespace).
const redoc = redocModule.loadAndBundleSpec ? redocModule : redocModule.default;

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
  {{#unless disableGoogleFont}}<link href="https://fonts.googleapis.com/css?family=Montserrat:300,400,700|Roboto:300,400,700" rel="stylesheet">{{/unless}}
</head>

<body>
  {{{redocHTML}}}
</body>

</html>
`;

/**
 * Renders one API description to an HTML page.
 *
 * @param options {{
 *   api: string, title?: string, disableGoogleFont: boolean, templateFile?: string,
 *   templateOptions: object, redocOptions: object, configPath?: string
 * }} `api` is an absolute path or URL; `templateFile` an absolute path.
 * @returns {Promise<{ page: string, title: string }>}
 */
export async function renderPage({ api: pathToApi, title, disableGoogleFont, templateFile, templateOptions, redocOptions, configPath }) {
  const api = await redoc.loadAndBundleSpec(isAbsoluteUrl(pathToApi) ? pathToApi : path.resolve(pathToApi));

  const apiUrl = redocOptions.specUrl || (isAbsoluteUrl(pathToApi) ? pathToApi : undefined);
  let store;
  let html;
  let state;
  let css;
  try {
    store = await redoc.createStore(api, apiUrl, redocOptions);
    const sheet = new ServerStyleSheet();
    html = renderToString(sheet.collectStyles(createElement(redoc.Redoc, { store })));
    state = await store.toJS();
    css = sheet.getStyleTags();
  } finally {
    release(store);
  }

  const customTemplate =
    templateFile ||
    (redocOptions.htmlTemplate ? path.resolve(configPath ? path.dirname(configPath) : '', redocOptions.htmlTemplate) : undefined);
  const templateSource = customTemplate ? fs.readFileSync(customTemplate, 'utf-8') : DEFAULT_TEMPLATE_SOURCE;
  const template = handlebars.compile(templateSource);
  const pageTitle = title || api.info?.title || 'ReDoc documentation';
  const page = template({
    redocHTML: `
      <div id="redoc">${html || ''}</div>
      <script>
      const __redoc_state = ${sanitizeJSONString(JSON.stringify(state))};

      var container = document.getElementById('redoc');
      Redoc.hydrate(__redoc_state, container);

      </script>`,
    redocHead:
      `<script src="https://cdn.redocly.com/redoc/v${__REDOC_VERSION__}/bundles/redoc.standalone.js" integrity="${__REDOC_STANDALONE_SRI__}" crossorigin="anonymous"></script>` +
      css,
    title: pageTitle,
    disableGoogleFont,
    templateOptions,
  });
  return { page, title: pageTitle };
}

/**
 * Redoc's stores are built for a page that lives until the tab closes, and the CLI renders one page per process;
 * this context renders many. `store.dispose()` unsubscribes the menu from Redoc's history singleton (which
 * would otherwise retain every rendered model) and resets the search index, which is module-level state of the
 * search worker module; without a real Worker it has nothing to terminate. The index is reset even when
 * `createStore` itself failed half-way, so a broken description cannot leak into the next page's search.
 */
function release(store) {
  if (store) {
    const worker = store.search?.searchWorker;
    if (worker && typeof worker.terminate !== 'function') worker.terminate = () => {};
    store.dispose();
  }
  new redoc.SearchStore().searchWorker.dispose();
}

function isAbsoluteUrl(ref) {
  return ref.startsWith('http://') || ref.startsWith('https://');
}

// see http://www.thespanner.co.uk/2011/07/25/the-json-specification-is-now-wrong/
function sanitizeJSONString(str) {
  return str
    .replace(/<\/script>/g, '<\\/script>')
    .replace(new RegExp('\\u2028|\\u2029', 'g'), (m) => '\\u202' + (m.charCodeAt(0) === 0x2028 ? '8' : '9'));
}
