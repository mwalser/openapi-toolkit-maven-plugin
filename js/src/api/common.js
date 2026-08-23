// Shared helpers mirroring packages/cli/src/utils/miscellaneous.ts of redocly-cli (MIT).
import {
  loadConfig,
  lintConfig,
  formatProblems,
  getTotals,
  getLineColLocation,
  isAbsoluteUrl,
  stringifyYaml,
} from '@redocly/openapi-core';
import * as path from 'node:path';
import * as fs from 'node:fs';
import { captureOutput } from '../polyfills.js';

/** A failure caused by the input (options, configuration, API descriptions) rather than by a bug. */
export class CommandError extends Error {
  constructor(message, options) {
    super(message, options);
    this.name = 'CommandError';
  }
}

/** Loads redocly.yaml (or the built-in defaults when no config file is given). */
export async function loadProjectConfig({ configPath, customExtends }) {
  let config;
  try {
    config = await loadConfig({
      configPath: configPath || undefined,
      customExtends: customExtends?.length ? customExtends : undefined,
    });
  } catch (e) {
    throw new CommandError(`Error while loading the configuration${configPath ? ` from ${configPath}` : ''}: ${e.message}`, { cause: e });
  }
  rejectCustomPlugins(config);
  return config;
}

/**
 * The bundle runs openapi-core in browser mode, which silently ignores `plugins:` — and with them every rule and
 * decorator they provide. Failing is better than linting with a ruleset the user did not configure.
 */
function rejectCustomPlugins(config) {
  const configured = config.document?.parsed || {};
  const plugins = (configured.plugins || []).filter((plugin) => typeof plugin === 'string');
  if (!plugins.length) return;
  const pluginIds = plugins.map((plugin) => path.basename(plugin).replace(/\.[^.]+$/, ''));
  const providedBy = (id) => pluginIds.some((pluginId) => id.startsWith(`${pluginId}/`));
  const uses = ['rules', 'preprocessors', 'decorators'].flatMap((section) => Object.keys(configured[section] || {}).filter(providedBy));
  throw new CommandError(
    `Custom JavaScript plugins are not supported: ${plugins.join(', ')}` +
      (uses.length ? `. Configured plugin rules/decorators: ${uses.join(', ')}` : ''),
  );
}

export function configDirectory(config, cwd) {
  return config.configPath ? path.dirname(config.configPath) : cwd;
}

/**
 * Resolves the APIs to process: either the explicitly requested aliases/paths, or every API from
 * the `apis` section of the config. Returns [{ path, alias }] with absolute paths.
 */
export function resolveApis(config, requested, cwd) {
  const configDir = configDirectory(config, cwd);
  const apis = config.resolvedConfig.apis || {};
  const absolute = (p) => (isAbsoluteUrl(p) ? p : path.resolve(cwd, p));

  let entries;
  if (requested && requested.length) {
    entries = requested.map((aliasOrPath) => {
      const aliasApi = apis[aliasOrPath];
      if (aliasApi) {
        return { path: isAbsoluteUrl(aliasApi.root) ? aliasApi.root : path.resolve(configDir, aliasApi.root), alias: aliasOrPath };
      }
      const abs = absolute(aliasOrPath);
      const alias = Object.entries(apis).find(([, api]) => path.resolve(configDir, api.root) === abs)?.[0];
      return { path: abs, alias };
    });
  } else {
    entries = Object.entries(apis).map(([alias, { root }]) => ({
      path: isAbsoluteUrl(root) ? root : path.resolve(configDir, root),
      alias,
    }));
  }

  const invalid = entries.filter(({ path: p }) => !isAbsoluteUrl(p) && !fs.existsSync(p));
  if (invalid.length) {
    throw new CommandError(
      `The following API description${invalid.length > 1 ? 's do' : ' does'} not exist: ${invalid.map((e) => e.path).join(', ')}`,
    );
  }
  if (entries.length === 0) {
    throw new CommandError(
      'No APIs were provided. Specify an API via the <apis> parameter or define one in the `apis` section of redocly.yaml.',
    );
  }
  return entries;
}

export function checkIfRulesetExist(rules) {
  const ruleset = {
    ...rules.oas2, ...rules.oas3_0, ...rules.oas3_1, ...rules.oas3_2,
    ...rules.async2, ...rules.async3, ...rules.arazzo1, ...rules.overlay1, ...rules.openrpc1, ...rules.graphql,
  };
  if (Object.keys(ruleset).length === 0) {
    throw new CommandError('No rules were configured. Learn how to configure rules: https://redocly.com/docs/cli/rules/');
  }
}

/** Runs formatProblems and returns what it would have printed. */
export async function formatToString(problems, opts) {
  const { output } = await captureOutput(() => formatProblems(problems, { color: false, ...opts }));
  return output;
}

/** Compact, JSON-friendly view of a problem (the raw objects reference whole source documents). */
export function describeProblem(problem) {
  return {
    ruleId: problem.ruleId,
    severity: problem.severity,
    message: problem.message,
    ignored: !!problem.ignored,
    suggest: problem.suggest || [],
    location: (problem.location || []).map((loc) => {
      let line, col;
      try {
        const lc = getLineColLocation(loc);
        line = lc.start?.line;
        col = lc.start?.col;
      } catch {
        /* ignore */
      }
      return { file: loc.source?.absoluteRef, pointer: loc.pointer, line, col };
    }),
  };
}

export function unusedWarnings(config) {
  const { preprocessors, rules, decorators } = config.getUnusedRules();
  return { rules, preprocessors, decorators };
}

/**
 * Lints the config file itself (what the CLI does before every command).
 * Returns null when there is nothing to check.
 */
export async function lintConfigFile(config, { severity = 'warn', format = 'stylish', maxProblems = 100, cwd }) {
  if (severity === 'off' || config.document === undefined) {
    return null;
  }
  const problems = await lintConfig({ config, severity });
  const totals = getTotals(problems);
  return {
    totals,
    problems: problems.map(describeProblem),
    output: await formatToString(problems, { format, maxProblems, totals, command: 'check-config', cwd }),
  };
}

const oas2OrderedKeys = ['swagger', 'info', 'host', 'basePath', 'schemes', 'consumes', 'produces', 'security', 'tags', 'externalDocs', 'paths', 'definitions', 'parameters', 'responses', 'securityDefinitions'];
const oas3OrderedKeys = ['openapi', 'info', 'jsonSchemaDialect', 'servers', 'security', 'tags', 'externalDocs', 'paths', 'webhooks', 'x-webhooks', 'components'];
const asyncApi2OrderedKeys = ['asyncapi', 'id', 'info', 'externalDocs', 'tags', 'defaultContentType', 'servers', 'channels', 'components'];
const asyncApi3OrderedKeys = ['asyncapi', 'id', 'info', 'servers', 'defaultContentType', 'channels', 'operations', 'components'];

export function sortTopLevelKeys(document) {
  const keys =
    'asyncapi' in document
      ? String(document.asyncapi).startsWith('2.') ? asyncApi2OrderedKeys : asyncApi3OrderedKeys
      : 'swagger' in document ? oas2OrderedKeys : oas3OrderedKeys;
  const result = {};
  for (const key of keys) {
    if (Object.prototype.hasOwnProperty.call(document, key)) result[key] = document[key];
  }
  return Object.assign(result, document);
}

export function dumpBundle(obj, ext, dereference) {
  if (ext === 'json') {
    try {
      return JSON.stringify(obj, null, 2);
    } catch (e) {
      if (String(e.message).includes('circular')) {
        throw new CommandError('Circular references are not supported for JSON output; use YAML or disable dereferencing.');
      }
      throw e;
    }
  }
  return stringifyYaml(obj, { noRefs: !dereference, lineWidth: -1 });
}

export function saveFile(filename, content) {
  fs.mkdirSync(path.dirname(filename), { recursive: true });
  fs.writeFileSync(filename, content);
}
