// Mirrors packages/cli/src/commands/build-docs/index.ts of redocly-cli: the configuration, the API selection
// and the output placement use the embedded openapi-core here; the rendering lives in redocly-docs.mjs
// (src/docs/), which the host evaluates before this command runs.
import { isAbsoluteUrl } from '@redocly/openapi-core';
import * as path from 'node:path';
import {
  CommandError,
  lintConfigFile,
  loadProjectConfig,
  outputFileFor,
  rejectCollidingOutputs,
  resolveApis,
  saveFile,
} from './common.js';

/**
 * @param opts {{
 *   cwd: string, configPath?: string, apis?: string[], outputDirectory: string, outputFile?: string,
 *   title?: string, disableGoogleFont?: boolean, template?: string,
 *   templateOptions?: Record<string, string>, redocOptions?: Record<string, string>,
 *   lintConfig?: 'warn'|'error'|'off', maxProblems?: number
 * }}
 */
export async function runBuildDocs(opts) {
  const docs = globalThis.__openapiToolkitDocs;
  if (!docs) throw new Error('Internal error: the documentation bundle redocly-docs.mjs is not loaded');
  const { cwd, maxProblems = 100 } = opts;
  const config = await loadProjectConfig({ configPath: opts.configPath });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, maxProblems, cwd });
  if (configLint?.totals.errors > 0) return { configLint, apis: [], redocVersion: docs.redocVersion };
  const apis = resolveApis(config, opts.apis, cwd);
  if (opts.outputFile && apis.length > 1) {
    throw new CommandError(`openapi.toolkit.buildDocs.outputFile can only be used with a single API, but ${apis.length} were selected; use <apis> to select one.`);
  }
  const targets = apis.map((api) => ({ ...api, outputFile: outputFileFor(api, opts, 'html', 'documentation') }));
  rejectCollidingOutputs(targets, 'documented');
  const overrides = parseRedocOptions(opts.redocOptions || {});

  const results = [];
  for (const { path: ref, alias, outputFile } of targets) {
    // the CLI takes Redoc's options from the `openapi` section of the configuration unless given on the command line;
    // the Maven parameter overrides them option by option instead
    const redocOptions = { ...(config.forAlias(alias).resolvedConfig?.openapi ?? {}), ...overrides };
    const started = performance.now();
    let rendered;
    try {
      rendered = await docs.renderPage({
        api: ref,
        title: opts.title || undefined,
        disableGoogleFont: !!opts.disableGoogleFont,
        templateFile: opts.template ? path.resolve(cwd, opts.template) : undefined,
        templateOptions: opts.templateOptions || {},
        redocOptions,
        configPath: config.configPath,
      });
    } catch (e) {
      const shown = isAbsoluteUrl(ref) ? ref : path.relative(cwd, ref);
      throw new CommandError(`Could not build the documentation for ${shown}: ${e?.message || e}`, { cause: e });
    }
    saveFile(outputFile, rendered.page);
    results.push({ path: ref, alias, outputFile, title: rendered.title, durationMillis: Math.round(performance.now() - started) });
  }
  return { configLint, apis: results, redocVersion: docs.redocVersion };
}

/**
 * Maven hands every option value over as a string. Redoc converts scalars itself (`"true"`, `"200,201"`); nested
 * options such as `theme` are given as JSON.
 */
function parseRedocOptions(values) {
  const options = {};
  for (const [name, value] of Object.entries(values)) {
    const text = String(value).trim();
    if (!text.startsWith('{') && !text.startsWith('[')) {
      options[name] = value;
      continue;
    }
    try {
      options[name] = JSON.parse(text);
    } catch (e) {
      throw new CommandError(`Invalid JSON in the build-docs parameter redocOptions, option '${name}': ${e.message}`, { cause: e });
    }
  }
  return options;
}
