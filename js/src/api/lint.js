// Mirrors packages/cli/src/commands/lint.ts of redocly-cli.
import { lint, getTotals } from '@redocly/openapi-core';
import {
  checkIfRulesetExist,
  describeProblem,
  formatToString,
  lintConfigFile,
  loadProjectConfig,
  resolveApis,
  unusedWarnings,
} from './common.js';

/**
 * @param opts {{
 *   cwd: string, configPath?: string, apis?: string[], extends?: string[],
 *   format?: string, reportFormat?: string, maxProblems?: number,
 *   skipRules?: string[], skipPreprocessors?: string[],
 *   generateIgnoreFile?: boolean, lintConfig?: 'warn'|'error'|'off'
 * }}
 */
export async function runLint(opts) {
  const { cwd, format = 'stylish', maxProblems = 100 } = opts;
  const config = await loadProjectConfig({ configPath: opts.configPath, customExtends: opts.extends });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, format, maxProblems, cwd });
  const apis = resolveApis(config, opts.apis, cwd);

  const totals = { errors: 0, warnings: 0, ignored: 0 };
  const results = [];
  const allProblems = [];
  let totalIgnored = 0;

  for (const { path, alias } of apis) {
    const aliasConfig = config.forAlias(alias);
    checkIfRulesetExist(aliasConfig.rules);
    aliasConfig.skipRules(opts.skipRules);
    aliasConfig.skipPreprocessors(opts.skipPreprocessors);

    const started = performance.now();
    const problems = await lint({ ref: path, config: aliasConfig });
    const fileTotals = getTotals(problems);
    totals.errors += fileTotals.errors;
    totals.warnings += fileTotals.warnings;
    totals.ignored += fileTotals.ignored;

    if (opts.generateIgnoreFile) {
      config.clearIgnoreForRef(path);
      for (const p of problems) {
        config.addIgnore(p);
        totalIgnored++;
      }
    }
    allProblems.push(...problems);
    results.push({
      path,
      alias,
      totals: fileTotals,
      durationMillis: Math.round(performance.now() - started),
      problems: problems.map(describeProblem),
      output: formatToString(problems, { format, maxProblems, totals: fileTotals, command: 'lint', cwd }),
    });
  }

  let ignoreFile = null;
  if (opts.generateIgnoreFile) {
    config.saveIgnore();
    ignoreFile = { ignored: totalIgnored };
  }

  const report = opts.reportFormat
    ? formatToString(allProblems, { format: opts.reportFormat, maxProblems, totals, command: 'lint', cwd })
    : null;

  return {
    usedDefaultConfig: typeof config.document?.parsed === 'undefined' && !(opts.extends && opts.extends.length),
    configLint,
    apis: results,
    totals,
    report,
    ignoreFile,
    unused: unusedWarnings(config),
  };
}
