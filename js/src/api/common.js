// Shared helpers mirroring packages/cli/src/utils/miscellaneous.ts of redocly-cli (MIT).
import {
  BaseResolver,
  DEFAULT_CONFIG,
  createConfig,
  findConfig,
  loadConfig,
  loadIgnoreConfig,
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
    const file = configPath || findConfig();
    if (file) {
      config = await loadConfig({
        configPath: file,
        customExtends: customExtends?.length ? customExtends : undefined,
      });
    } else {
      // loadConfig applies overrides to shared defaults. Own the input so successive goals remain independent.
      const defaults = structuredClone(DEFAULT_CONFIG);
      if (customExtends?.length) defaults.extends = customExtends;
      config = await createConfig(defaults, {
        ignore: await loadIgnoreConfig(undefined, new BaseResolver()),
      });
      // createConfig supplies a synthetic document; there is no configuration file to lint or report here.
      config.document = undefined;
    }
  } catch (e) {
    throw new CommandError(`Error while loading the configuration${configPath ? ` from ${configPath}` : ''}: ${e.message}`, { cause: e });
  }
  rejectCustomPlugins(config);
  return config;
}

/**
 * The bundle runs openapi-core in browser mode, which silently ignores `plugins:` — and with them every rule and
 * decorator they provide. Failing is better than linting with a ruleset the user did not configure. Browser mode
 * drops the declarations while merging, so they are collected from the raw nodes of every resolved configuration
 * document: the root file, everything reachable through `extends`, and per-API overrides.
 */
function rejectCustomPlugins(config) {
  const nodes = configNodes(config);
  const declared = (node) => (Array.isArray(node.plugins) ? node.plugins.filter((plugin) => typeof plugin === 'string') : []);
  const plugins = distinct(nodes.flatMap(declared));
  if (!plugins.length) return;

  const pluginIds = plugins.map((plugin) => path.basename(plugin).replace(/\.[^.]+$/, ''));
  const providedBy = (id) => pluginIds.some((pluginId) => id.startsWith(`${pluginId}/`));
  const configuredIds = (node) => ['rules', 'preprocessors', 'decorators'].flatMap((section) => Object.keys(node[section] || {}));
  const uses = distinct(nodes.flatMap(configuredIds).filter(providedBy));
  throw new CommandError(
    `Custom JavaScript plugins are not supported: ${plugins.join(', ')}` +
      (uses.length ? `. Configured plugin rules/decorators: ${uses.join(', ')}` : ''),
  );
}

/** Every object of a resolved configuration that can declare plugins or rules (same shapes as openapi-core's own plugin collector). */
function configNodes(config) {
  const nodes = new Set();
  const collect = (node) => {
    if (!node || typeof node !== 'object' || nodes.has(node)) return;
    nodes.add(node);
    collect(node.governance);
    for (const api of Object.values(node.apis || {})) collect(api);
    for (const level of node.scorecardClassic?.levels || []) collect(level);
    for (const level of node.scorecard?.levels || []) collect(level);
  };
  collect(config.document?.parsed);
  for (const ref of config.resolvedRefMap?.values() || []) collect(ref.node);
  return [...nodes];
}

function distinct(values) {
  return [...new Set(values)];
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
  const resolveRoot = (root, from) => (isAbsoluteUrl(root) ? root : path.resolve(from, root));

  // `requested` keeps the user's spelling for error messages; it is stripped from the result
  let entries;
  if (requested?.length) {
    entries = requested.map((aliasOrPath) => {
      if (apis[aliasOrPath]) return { path: resolveRoot(apis[aliasOrPath].root, configDir), alias: aliasOrPath, requested: aliasOrPath };
      const root = resolveRoot(aliasOrPath, cwd);
      const alias = Object.keys(apis).find((candidate) => resolveRoot(apis[candidate].root, configDir) === root);
      return { path: root, alias, requested: aliasOrPath };
    });
  } else {
    entries = Object.entries(apis).map(([alias, api]) => ({ path: resolveRoot(api.root, configDir), alias, requested: null }));
  }

  const missing = entries.filter(({ path: p }) => !isAbsoluteUrl(p) && !fs.existsSync(p));
  if (missing.length) {
    const aliases = Object.keys(apis);
    const known = aliases.length ? ` (known: ${aliases.join(', ')})` : '';
    const describe = ({ path: p, alias, requested }) =>
      requested == null
        ? `${path.relative(cwd, p)} (root of api '${alias}' in the configuration file)`
        : `${requested} (not an alias in the configuration file${known} and not an existing file)`;
    throw new CommandError(`API description${missing.length > 1 ? 's' : ''} not found: ${missing.map(describe).join('; ')}`);
  }
  if (entries.length === 0) {
    throw new CommandError(
      "No APIs were provided. Set <apis> (property openapi.toolkit.apis) or define APIs in the 'apis' section of redocly.yaml.",
    );
  }
  return entries.map(({ requested, ...entry }) => entry);
}

/**
 * Where an API's output goes: `outputFile` when given, otherwise `<alias|basename>.<ext>` in `outputDirectory`.
 * `what` names the output in the error message (`bundle`, `documentation`).
 */
export function outputFileFor({ path: ref, alias }, { cwd, outputDirectory, outputFile }, ext, what) {
  if (outputFile) return path.resolve(cwd, outputFile);
  const directory = path.resolve(cwd, outputDirectory);
  // Treat both separator styles consistently, including when checking Windows aliases on a POSIX host.
  const name = (alias || path.basename(ref, path.extname(ref))).replaceAll('\\', '/');
  const file = path.resolve(directory, `${name}.${ext}`);
  const relative = path.relative(directory, file);
  if (/^[A-Za-z]:/.test(name) || relative === '..' || relative.startsWith('../') || path.isAbsolute(relative)) {
    throw new CommandError(`API '${alias || ref}' would write outside the ${what} output directory: ${file}. Choose another alias or set outputFile explicitly.`);
  }
  return file;
}

/** Two alias-less APIs with the same file name would silently overwrite each other. `verb`: `bundled`, `documented`. */
export function rejectCollidingOutputs(targets, verb) {
  const inputsByOutput = new Map();
  for (const { path: ref, outputFile } of targets) {
    inputsByOutput.set(outputFile, [...(inputsByOutput.get(outputFile) || []), ref]);
  }
  const collisions = [...inputsByOutput].filter(([, inputs]) => inputs.length > 1);
  if (collisions.length) {
    const described = collisions.map(([outputFile, inputs]) => `${outputFile} (from ${inputs.join(', ')})`).join('; ');
    throw new CommandError(`Several APIs would be ${verb} to the same file: ${described}. Give them aliases in redocly.yaml or select them separately.`);
  }
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

/** Runs formatProblems and returns what it would have printed, with the CLI's hints rephrased for Maven. */
export async function formatToString(problems, opts) {
  const { transcript } = await captureOutput(() => formatProblems(problems, { color: false, version: __REDOCLY_VERSION__, ...opts }));
  return transcript.replace('increase with `--max-problems N`', 'increase with openapi.toolkit.maxProblems');
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
