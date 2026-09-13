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
  if (opts.ext && !OUTPUT_EXTENSIONS.includes(opts.ext)) {
    throw new CommandError(`Invalid output extension '${opts.ext}'. Allowed: ${OUTPUT_EXTENSIONS.join(', ')}.`);
  }
  const config = await loadProjectConfig({ configPath: opts.configPath, customExtends: opts.extends });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, format, maxProblems, cwd });
  if (configLint?.totals.errors > 0) return { configLint, apis: [], totals: { errors: 0, warnings: 0, ignored: 0 } };
  const apis = resolveApis(config, opts.apis, cwd);
  if (opts.outputFile && apis.length > 1) {
    throw new CommandError(`<outputFile> can only be used with a single API, but ${apis.length} were selected.`);
  }
  const targets = apis.map((api) => ({ ...api, ...outputTarget(api, opts) }));
  rejectCollidingOutputs(targets);

  const totals = { errors: 0, warnings: 0, ignored: 0 };
  const results = [];

  for (const { path: ref, alias, outputFile, ext } of targets) {
    const aliasConfig = config.forAlias(alias);
    aliasConfig.skipDecorators(opts.skipDecorators);
    const configuredOutput = alias && config.resolvedConfig.apis?.[alias]?.output;
    if (configuredOutput) {
      console.warn(`Ignoring output '${configuredOutput}' of api '${alias}' in redocly.yaml: the bundle goal writes to ${outputFile}`);
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
 * Where an API's bundle goes and in which format: `outputFile` when given, otherwise `<alias|basename>.<ext>` in
 * `outputDirectory`. The format is the requested `ext`, else the extension of `outputFile`, else that of the
 * input (JSON stays JSON, as in the CLI), else yaml.
 */
function outputTarget({ path: ref, alias }, { cwd, outputDirectory, outputFile, ext: requestedExt }) {
  const extensionOf = (file) => path.extname(file).slice(1).toLowerCase();
  const explicitFile = outputFile && path.resolve(cwd, outputFile);
  const ext = requestedExt || [explicitFile, ref].filter(Boolean).map(extensionOf).find((e) => OUTPUT_EXTENSIONS.includes(e)) || 'yaml';
  if (explicitFile) return { outputFile: explicitFile, ext };
  const directory = path.resolve(cwd, outputDirectory);
  // Treat both separator styles consistently, including when checking Windows aliases on a POSIX host.
  const name = (alias || path.basename(ref, path.extname(ref))).replaceAll('\\', '/');
  const file = path.resolve(directory, `${name}.${ext}`);
  const relative = path.relative(directory, file);
  if (/^[A-Za-z]:/.test(name) || relative === '..' || relative.startsWith('../') || path.isAbsolute(relative)) {
    throw new CommandError(`API '${alias || ref}' would write outside the bundle output directory: ${file}. Choose another alias or set outputFile explicitly.`);
  }
  return { outputFile: file, ext };
}

/** Two alias-less APIs with the same file name would silently overwrite each other. */
function rejectCollidingOutputs(targets) {
  const inputsByOutput = new Map();
  for (const { path: ref, outputFile } of targets) {
    inputsByOutput.set(outputFile, [...(inputsByOutput.get(outputFile) || []), ref]);
  }
  const collisions = [...inputsByOutput].filter(([, inputs]) => inputs.length > 1);
  if (collisions.length) {
    const described = collisions.map(([outputFile, inputs]) => `${outputFile} (from ${inputs.join(', ')})`).join('; ');
    throw new CommandError(`Several APIs would be bundled to the same file: ${described}. Give them aliases in redocly.yaml or select them separately.`);
  }
}
