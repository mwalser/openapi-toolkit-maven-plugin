import { handleSplit } from '../../vendor/redocly-cli/commands/split/index.js';
import * as path from 'node:path';
import { captureOutput } from '../polyfills.js';

/** @param opts {{ cwd: string, api: string, outDir: string, separator?: string }} */
export async function runSplit(opts) {
  const api = path.resolve(opts.cwd, opts.api);
  const outDir = path.resolve(opts.cwd, opts.outDir);
  const { output } = await captureOutput(() =>
    handleSplit({ argv: { api, outDir, separator: opts.separator || '_' }, version: '' }),
  );
  return { api, outDir, output };
}
