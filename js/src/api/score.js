import { handleScore } from '../../vendor/redocly-cli/commands/score/index.js';
import { captureOutput } from '../polyfills.js';
import { lintConfigFile, loadProjectConfig, resolveApis } from './common.js';
import { parseJsonOutput, stripWrapper } from './stats.js';

/** @param opts {{ cwd: string, configPath?: string, apis?: string[], format?: 'stylish'|'json', operationDetails?: boolean, lintConfig?: string, maxProblems?: number }} */
export async function runScore(opts) {
  const config = await loadProjectConfig({ configPath: opts.configPath });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, maxProblems: opts.maxProblems, cwd: opts.cwd });
  const format = opts.format || 'stylish';
  if (configLint?.totals.errors > 0) return { configLint, format, apis: [] };

  const apis = [];
  for (const { path, alias } of resolveApis(config, opts.apis, opts.cwd)) {
    const { output, value: result } = await captureOutput(() =>
      handleScore({
        argv: { api: path, format, 'operation-details': !!opts.operationDetails },
        config: config.forAlias(alias),
        version: '',
      }),
    );
    const body = stripWrapper(output, 'score');
    apis.push({
      path,
      alias,
      output: body,
      agentReadiness: result.agentReadiness,
      score: format === 'json' ? parseJsonOutput(body, 'score', path) : null,
    });
  }
  return { configLint, format, apis };
}
