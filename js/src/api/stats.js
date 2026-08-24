import { handleStats } from '../../vendor/redocly-cli/commands/stats/index.js';
import { captureOutput } from '../polyfills.js';
import { lintConfigFile, loadProjectConfig, resolveApis } from './common.js';

/** Parses the JSON a CLI command printed; the command's own output is the only source of the structured result. */
export function parseJsonOutput(body, command, api) {
  try {
    return JSON.parse(body);
  } catch (e) {
    throw new Error(`Could not parse the JSON output of ${command} for ${api}: ${e.message}`, { cause: e });
  }
}

/** @param opts {{ cwd: string, configPath?: string, apis?: string[], format?: 'stylish'|'json'|'markdown', lintConfig?: string, maxProblems?: number }} */
export async function runStats(opts) {
  const config = await loadProjectConfig({ configPath: opts.configPath });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, maxProblems: opts.maxProblems, cwd: opts.cwd });
  const format = opts.format || 'stylish';
  if (configLint?.totals.errors > 0) return { configLint, format, apis: [] };

  const apis = [];
  for (const { path, alias } of resolveApis(config, opts.apis, opts.cwd)) {
    // the statistics are printed on the output channel; header and timing go to info and are dropped
    const { output } = await captureOutput(() =>
      handleStats({ argv: { api: path, format }, config: config.forAlias(alias), version: '' }),
    );
    apis.push({ path, alias, output, stats: format === 'json' ? parseJsonOutput(output, 'stats', path) : null });
  }
  return { configLint, format, apis };
}
