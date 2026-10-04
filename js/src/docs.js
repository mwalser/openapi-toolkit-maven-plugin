// Entry of redocly-docs.mjs: Redoc's server-side rendering for the build-docs goal. RedoclyRuntime evaluates it
// into the context of redocly-core.mjs when build-docs first runs, so the polyfills and the host bridge are in
// place; the core bundle's command layer finds the renderer through the global it installs.
import { renderPage } from './docs/render.js';

export const redocVersion = __REDOC_VERSION__;

globalThis.__openapiToolkitDocs = { redocVersion, renderPage };
