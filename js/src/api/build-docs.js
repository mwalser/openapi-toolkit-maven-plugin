// Mirrors packages/cli/src/commands/build-docs/index.ts of redocly-cli: the configuration, the API selection,
// loading and bundling (with the configured decorators and preprocessors) and the output placement use the
// embedded openapi-core here; the rendering lives in redocly-docs.mjs (src/docs/), which the host evaluates
// before this command runs.
import { BaseResolver, bundle, detectSpec, getMajorSpecVersion, isAbsoluteUrl, isGraphqlRef } from '@redocly/openapi-core';
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
 *   title?: string, template?: string, templateOptions?: Record<string, string>,
 *   redocOptions?: Record<string, string>, inlineBundle?: boolean, removeRedoclyLogo?: boolean, telemetry?: boolean,
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
  if (opts.removeRedoclyLogo && !opts.inlineBundle) {
    throw new CommandError('openapi.toolkit.buildDocs.removeRedoclyLogo requires openapi.toolkit.buildDocs.inlineBundle: the Redoc script from the CDN adds the logo back');
  }
  const overrides = parseRedocOptions(opts.redocOptions || {});
  // unlike the CLI, the pages send Redoc telemetry only when asked to; the CLI's off switches still apply
  const disableTelemetry = !opts.telemetry || !isTelemetryEnabled(config);

  const results = [];
  for (const { path: ref, alias, outputFile } of targets) {
    const aliasConfig = config.forAlias(alias);
    const started = performance.now();
    let rendered;
    let specType;
    try {
      const loaded = await loadDefinition(ref, aliasConfig, docs);
      specType = loaded.specType;
      // the CLI takes Redoc's options from the configuration section of the description's type (`openapi`,
      // `asyncapi`, `graphql`) unless given on the command line; the Maven parameter overrides them option by option
      const redocOptions = { ...(aliasConfig.resolvedConfig?.[specType] ?? {}), ...overrides };
      rendered = await docs.renderPage({
        definition: loaded.definition,
        title: opts.title || undefined,
        templateFile: opts.template ? path.resolve(cwd, opts.template) : undefined,
        templateOptions: opts.templateOptions || {},
        redocOptions,
        configPath: config.configPath,
        disableTelemetry,
        inlineBundle: !!opts.inlineBundle,
        removeRedoclyLogo: !!opts.removeRedoclyLogo,
      });
    } catch (e) {
      const shown = isAbsoluteUrl(ref) ? ref : path.relative(cwd, ref);
      throw new CommandError(`Could not build the documentation for ${shown}: ${e?.message || e}`, { cause: e });
    }
    saveFile(outputFile, rendered.page);
    results.push({ path: ref, alias, outputFile, title: rendered.title, specType, durationMillis: Math.round(performance.now() - started) });
  }
  return { configLint, apis: results, redocVersion: docs.redocVersion };
}

const UNSUPPORTED_SPEC_NAMES = { arazzo1: 'Arazzo', arazzo1_1: 'Arazzo', overlay1: 'Overlay', openrpc1: 'OpenRPC' };

/** The bundled description and the Redoc options section it reads, as `loadDefinition` of the CLI. */
async function loadDefinition(ref, config, docs) {
  if (isGraphqlRef(ref)) {
    const resolver = new BaseResolver(config.resolve);
    const source = await resolver.loadExternalRef(ref);
    return { definition: resolver.parseDocument(source, true).parsed, specType: 'graphql' };
  }
  const { bundle: result } = await bundle({ ref, config });
  const parsed = result.parsed;
  const specVersion = getMajorSpecVersion(detectSpec(parsed));
  switch (specVersion) {
    case 'oas2':
      return { definition: await docs.convertSwagger2OpenAPI(parsed), specType: 'openapi' };
    case 'oas3':
      return { definition: parsed, specType: 'openapi' };
    case 'async2':
    case 'async3':
      return { definition: parsed, specType: 'asyncapi' };
    default:
      throw new CommandError(
        `build-docs renders OpenAPI, AsyncAPI, and GraphQL. This file is ${UNSUPPORTED_SPEC_NAMES[specVersion] ?? specVersion}.`,
      );
  }
}

/** `isTelemetryEnabled` of the CLI: off when the environment or the configuration turns it off. */
function isTelemetryEnabled(config) {
  return process.env.REDOCLY_TELEMETRY !== 'off' && config?.resolvedConfig.telemetry !== 'off';
}

/**
 * Maven hands every option value over as a string. Redoc converts scalars itself (`"true"`, `"200,201"`); nested
 * options are given as JSON.
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
