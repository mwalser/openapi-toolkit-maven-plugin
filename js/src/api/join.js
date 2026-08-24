import { handleJoin } from '../../vendor/redocly-cli/commands/join/index.js';
import * as path from 'node:path';
import { captureOutput } from '../polyfills.js';
import { lintConfigFile, loadProjectConfig, resolveApis } from './common.js';

/**
 * @param opts {{
 *   cwd: string, configPath?: string, apis: string[], output: string,
 *   prefixTagsWithInfoProp?: string, prefixTagsWithFilename?: boolean,
 *   prefixComponentsWithInfoProp?: string, withoutXTagGroups?: boolean, lintConfig?: string, maxProblems?: number
 * }}
 */
export async function runJoin(opts) {
  const config = await loadProjectConfig({ configPath: opts.configPath });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, maxProblems: opts.maxProblems, cwd: opts.cwd });
  const outputFile = path.resolve(opts.cwd, opts.output);
  if (configLint?.totals.errors > 0) return { configLint, apis: [], outputFile, output: '' };

  const apis = resolveApis(config, opts.apis, opts.cwd).map(({ path: p, alias }) => ({ path: p, alias }));
  const { transcript: output } = await captureOutput(() =>
    handleJoin({
      argv: {
        apis: opts.apis,
        output: outputFile,
        'prefix-tags-with-info-prop': opts.prefixTagsWithInfoProp,
        'prefix-tags-with-filename': opts.prefixTagsWithFilename,
        'prefix-components-with-info-prop': opts.prefixComponentsWithInfoProp,
        'without-x-tag-groups': opts.withoutXTagGroups,
      },
      config,
      version: '',
    }),
  );
  return { configLint, apis, outputFile, output };
}
