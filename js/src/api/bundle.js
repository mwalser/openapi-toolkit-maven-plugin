// Mirrors packages/cli/src/commands/bundle.ts of redocly-cli.
import { bundle, getTotals } from '@redocly/openapi-core';
import * as path from 'node:path';
import {
  describeProblem,
  dumpBundle,
  formatToString,
  lintConfigFile,
  loadProjectConfig,
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
  const ext = opts.ext || 'yaml';
  if (!OUTPUT_EXTENSIONS.includes(ext)) {
    throw new CommandError(`Invalid output extension '${ext}'. Allowed: ${OUTPUT_EXTENSIONS.join(', ')}.`);
  }
  const config = await loadProjectConfig({ configPath: opts.configPath, customExtends: opts.extends });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, format, maxProblems, cwd });
  if (configLint?.totals.errors > 0) return { configLint, apis: [], totals: { errors: 0, warnings: 0, ignored: 0 } };
  const apis = resolveApis(config, opts.apis, cwd);
  if (opts.outputFile && apis.length > 1) {
    throw new CommandError(`<outputFile> can only be used with a single API, but ${apis.length} were selected.`);
  }

  const totals = { errors: 0, warnings: 0, ignored: 0 };
  const results = [];

  for (const { path: ref, alias } of apis) {
    const aliasConfig = config.forAlias(alias);
    aliasConfig.skipDecorators(opts.skipDecorators);

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

    const baseName = alias || path.basename(ref, path.extname(ref));
    const outputFile = opts.outputFile
      ? path.resolve(cwd, opts.outputFile)
      : path.join(path.resolve(cwd, opts.outputDirectory), `${baseName}.${ext}`);

    let written = false;
    if (fileTotals.errors === 0 || opts.force) {
      const content = dumpBundle(sortTopLevelKeys(result.parsed), ext, opts.dereferenced);
      saveFile(outputFile, content);
      written = true;
    }

    results.push({
      path: ref,
      alias,
      outputFile,
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
