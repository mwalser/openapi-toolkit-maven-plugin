// Mirrors packages/cli/src/commands/bundle.ts of redocly-cli.
import { bundle, getTotals } from '@redocly/openapi-core';
import * as path from 'node:path';
import {
  describeProblem,
  dumpBundle,
  formatToString,
  lintConfigFile,
  loadProjectConfig,
  outputFileFor,
  rejectCollidingOutputs,
  resolveApis,
  saveFile,
  sortTopLevelKeys,
  unusedWarnings,
  CommandError,
} from './common.js';

const OUTPUT_EXTENSIONS = ['json', 'yaml', 'yml'];

/**
 * @param opts {{
 *   cwd: string, configPath?: string, apis?: string[], extends?: string[],
 *   outputDirectory: string, outputFile?: string, ext?: 'json'|'yaml'|'yml',
 *   dereferenced?: boolean, force?: boolean, removeUnusedComponents?: boolean, keepUrlReferences?: boolean,
 *   componentNamesStrategy?: string, componentRenamingConflicts?: string,
 *   skipDecorators?: string[], lintConfig?: 'warn'|'error'|'off',
 *   maxProblems?: number, format?: string
 * }}
 */
export async function runBundle(opts) {
  const { cwd, format = 'codeframe', maxProblems = 100 } = opts;
  if (opts.ext && !OUTPUT_EXTENSIONS.includes(opts.ext)) {
    throw new CommandError(`Invalid output extension '${opts.ext}'. Allowed: ${OUTPUT_EXTENSIONS.join(', ')}.`);
  }
  const config = await loadProjectConfig({ configPath: opts.configPath, customExtends: opts.extends });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, format, maxProblems, cwd });
  if (configLint?.totals.errors > 0) return { configLint, apis: [], totals: { errors: 0, warnings: 0, ignored: 0 } };
  const apis = resolveApis(config, opts.apis, cwd);
  if (opts.outputFile && apis.length > 1) {
    throw new CommandError(`openapi.toolkit.bundle.outputFile can only be used with a single API, but ${apis.length} were selected; use <apis> to select one.`);
  }
  const targets = apis.map((api) => ({ ...api, ...outputTarget(api, opts) }));
  rejectCollidingOutputs(targets, 'bundled');

  const totals = { errors: 0, warnings: 0, ignored: 0 };
  const results = [];

  for (const { path: ref, alias, outputFile, ext } of targets) {
    const aliasConfig = config.forAlias(alias);
    aliasConfig.skipDecorators(opts.skipDecorators);
    const configuredOutput = alias && config.resolvedConfig.apis?.[alias]?.output;
    if (configuredOutput) {
      console.warn(`Ignoring output '${configuredOutput}' of api '${alias}' in redocly.yaml: the bundle goal writes to ${path.relative(cwd, outputFile)}`);
    }

    const started = performance.now();
    const { bundle: result, problems, ...meta } = await bundle({
      ref,
      config: aliasConfig,
      dereference: opts.dereferenced,
      removeUnusedComponents: opts.removeUnusedComponents,
      keepUrlRefs: opts.keepUrlReferences,
      componentRenamingConflicts: opts.componentRenamingConflicts,
      componentNamesStrategy: opts.componentNamesStrategy,
    });
    const fileTotals = getTotals(problems);
    totals.errors += fileTotals.errors;
    totals.warnings += fileTotals.warnings;
    totals.ignored += fileTotals.ignored;

    const written = fileTotals.errors === 0 || !!opts.force;
    if (written) saveFile(outputFile, dumpBundle(sortTopLevelKeys(result.parsed), ext, opts.dereferenced));

    results.push({
      path: ref,
      alias,
      outputFile,
      ext,
      written,
      totals: fileTotals,
      durationMillis: Math.round(performance.now() - started),
      removedComponents: meta.visitorsData?.['remove-unused-components']?.removedCount || 0,
      problems: problems.map(describeProblem),
      output: await formatToString(problems, { format, maxProblems, totals: fileTotals, command: 'bundle', cwd }),
    });
  }

  return { configLint, apis: results, totals, unused: unusedWarnings(config) };
}

/**
 * Where an API's bundle goes and in which format. The format is the requested `ext`, else the extension of
 * `outputFile`, else that of the input (JSON stays JSON, as in the CLI), else yaml.
 */
function outputTarget(api, opts) {
  const extensionOf = (file) => path.extname(file).slice(1).toLowerCase();
  const explicitFile = opts.outputFile && path.resolve(opts.cwd, opts.outputFile);
  const ext = opts.ext || [explicitFile, api.path].filter(Boolean).map(extensionOf).find((e) => OUTPUT_EXTENSIONS.includes(e)) || 'yaml';
  return { outputFile: outputFileFor(api, opts, ext, 'bundle'), ext };
}
