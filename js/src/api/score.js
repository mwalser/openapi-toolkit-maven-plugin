import { handleScore } from '../../vendor/redocly-cli/commands/score/index.js';
import { startCapture, stopCapture } from '../polyfills.js';
import { lintConfigFile, loadProjectConfig, resolveApis } from './common.js';

/** @param opts {{ cwd: string, configPath?: string, api?: string, format?: 'stylish'|'json', operationDetails?: boolean, lintConfig?: string }} */
export async function runScore(opts) {
  const config = await loadProjectConfig({ configPath: opts.configPath });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, cwd: opts.cwd });
  const [{ path, alias }] = resolveApis(config, opts.api ? [opts.api] : [], opts.cwd);
  const format = opts.format || 'stylish';
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
  const body = output.replace(/^Document: .*? score:\n+/, '').replace(/\n*[^\n]*: score processed in \d+ms\n*$/, '\n');
  const score = format === 'json' ? JSON.parse(body) : null;
  return { configLint, path, alias, format, output: body, score };
}
