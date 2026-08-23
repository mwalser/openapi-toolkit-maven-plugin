import { handleStats } from '../../vendor/redocly-cli/commands/stats/index.js';
import { startCapture, stopCapture } from '../polyfills.js';
import { lintConfigFile, loadProjectConfig, resolveApis } from './common.js';

/** @param opts {{ cwd: string, configPath?: string, api?: string, format?: 'stylish'|'json'|'markdown', lintConfig?: string }} */
export async function runStats(opts) {
  const config = await loadProjectConfig({ configPath: opts.configPath });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, cwd: opts.cwd });
  const [{ path, alias }] = resolveApis(config, opts.api ? [opts.api] : [], opts.cwd);
  const format = opts.format || 'stylish';
  startCapture();
  let output;
  try {
    await handleStats({ argv: { api: path, format }, config: config.forAlias(alias), version: '' });
  } finally {
    output = stopCapture();
  }
  // handleStats wraps the formatted stats in a "Document: <path> stats:" header and a "processed in" footer
  const body = output.replace(/^Document: .*? stats:\n+/, '').replace(/\n*[^\n]*: stats processed in \d+ms\n*$/, '\n');
  const stats = format === 'json' ? JSON.parse(body) : null;
  return { configLint, path, alias, format, output: body, stats };
}
