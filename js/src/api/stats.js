import { handleStats } from '../../vendor/redocly-cli/commands/stats/index.js';
import { startCapture, stopCapture } from '../polyfills.js';
import { lintConfigFile, loadProjectConfig, resolveApis } from './common.js';

/** Strips the "Document: <path> stats:" header and the "processed in" footer printed by the CLI command. */
function stripWrapper(output, command) {
  return output
    .replace(new RegExp(`^Document: .*? ${command}:\\n+`), '')
    .replace(new RegExp(`\\n*[^\\n]*: ${command} processed in \\d+ms\\n*$`), '\n');
}

/** @param opts {{ cwd: string, configPath?: string, apis?: string[], format?: 'stylish'|'json'|'markdown', lintConfig?: string, maxProblems?: number }} */
export async function runStats(opts) {
  const config = await loadProjectConfig({ configPath: opts.configPath });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, maxProblems: opts.maxProblems, cwd: opts.cwd });
  const format = opts.format || 'stylish';
  const results = [];
  for (const { path, alias } of resolveApis(config, opts.apis, opts.cwd)) {
    startCapture();
    let output;
    try {
      await handleStats({ argv: { api: path, format }, config: config.forAlias(alias), version: '' });
    } finally {
      output = stopCapture();
    }
    const body = stripWrapper(output, 'stats');
    results.push({ path, alias, output: body, stats: format === 'json' ? JSON.parse(body) : null });
  }
  return { configLint, format, apis: results };
}

export { stripWrapper };
