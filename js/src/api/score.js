import { handleScore } from '../../vendor/redocly-cli/commands/score/index.js';
import { startCapture, stopCapture } from '../polyfills.js';
import { lintConfigFile, loadProjectConfig, resolveApis } from './common.js';
import { stripWrapper } from './stats.js';

/** @param opts {{ cwd: string, configPath?: string, apis?: string[], format?: 'stylish'|'json', operationDetails?: boolean, lintConfig?: string, maxProblems?: number }} */
export async function runScore(opts) {
  const config = await loadProjectConfig({ configPath: opts.configPath });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, maxProblems: opts.maxProblems, cwd: opts.cwd });
  const format = opts.format || 'stylish';
  const results = [];
  for (const { path, alias } of resolveApis(config, opts.apis, opts.cwd)) {
    startCapture();
    let output;
    try {
      await handleScore({
        argv: { api: path, format, 'operation-details': !!opts.operationDetails },
        config: config.forAlias(alias),
        version: '',
      });
    } finally {
      output = stopCapture();
    }
    const body = stripWrapper(output, 'score');
    results.push({ path, alias, output: body, score: format === 'json' ? JSON.parse(body) : null });
  }
  return { configLint, format, apis: results };
}
