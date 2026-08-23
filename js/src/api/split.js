import { handleSplit } from '../../vendor/redocly-cli/commands/split/index.js';
import * as path from 'node:path';
import { startCapture, stopCapture } from '../polyfills.js';

/** @param opts {{ cwd: string, api: string, outDir: string, separator?: string }} */
export async function runSplit(opts) {
  const api = path.resolve(opts.cwd, opts.api);
  const outDir = path.resolve(opts.cwd, opts.outDir);
  startCapture();
  let log;
  try {
    await handleSplit({ argv: { api, outDir, separator: opts.separator || '_' }, version: '' });
  } finally {
    log = stopCapture();
  }
  return { api, outDir, output: log };
}
