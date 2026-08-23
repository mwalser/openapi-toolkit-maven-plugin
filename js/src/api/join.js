import { handleJoin } from '../../vendor/redocly-cli/commands/join/index.js';
import * as path from 'node:path';
import { startCapture, stopCapture } from '../polyfills.js';
import { lintConfigFile, loadProjectConfig } from './common.js';

/**
 * @param opts {{
 *   cwd: string, configPath?: string, apis: string[], output: string,
 *   prefixTagsWithInfoProp?: string, prefixTagsWithFilename?: boolean,
 *   prefixComponentsWithInfoProp?: string, withoutXTagGroups?: boolean, lintConfig?: string
 * }}
 */
export async function runJoin(opts) {
  const config = await loadProjectConfig({ configPath: opts.configPath });
  const configLint = await lintConfigFile(config, { severity: opts.lintConfig, cwd: opts.cwd });
  const output = path.resolve(opts.cwd, opts.output);
  startCapture();
  let log;
  try {
    await handleJoin({
      argv: {
        apis: opts.apis,
        output,
        'prefix-tags-with-info-prop': opts.prefixTagsWithInfoProp,
        'prefix-tags-with-filename': opts.prefixTagsWithFilename,
        'prefix-components-with-info-prop': opts.prefixComponentsWithInfoProp,
        'without-x-tag-groups': opts.withoutXTagGroups,
      },
      config,
      version: '',
    });
  } finally {
    log = stopCapture();
  }
  return { configLint, outputFile: output, output: log };
}
