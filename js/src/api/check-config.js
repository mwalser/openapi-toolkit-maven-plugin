import { lintConfigFile, loadProjectConfig } from './common.js';

/** @param opts {{ cwd: string, configPath: string, severity?: 'warn'|'error', format?: string, maxProblems?: number }} */
export async function runCheckConfig(opts) {
  const config = await loadProjectConfig({ configPath: opts.configPath });
  const configLint = await lintConfigFile(config, {
    severity: opts.severity || 'warn',
    format: opts.format || 'stylish',
    maxProblems: opts.maxProblems,
    cwd: opts.cwd,
  });
  return { configLint };
}
