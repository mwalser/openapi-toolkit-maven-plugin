import { getTotals } from '@redocly/openapi-core';
import { handleSplit } from '../../vendor/redocly-cli/commands/split/index.js';
import * as path from 'node:path';
import { captureOutput } from '../polyfills.js';
import { collectProblems } from '../shims/split-problems.js';
import { formatToString } from './common.js';

/**
 * @param opts {{ cwd: string, api: string, outDir: string, separator?: string, fileNameConflictsSeverity?: 'warn'|'error'|'off' }}
 * @returns the command's progress lines (`output`) and, apart from them, the codeframes of the file name
 *   conflicts it reported (`problems`)
 */
export async function runSplit(opts) {
  const api = path.resolve(opts.cwd, opts.api);
  const outDir = path.resolve(opts.cwd, opts.outDir);
  const argv = { api, outDir, separator: opts.separator || '_', 'file-name-conflicts-severity': opts.fileNameConflictsSeverity || 'warn' };
  const problems = [];
  const report = async () => (problems.length ? formatToString(problems, { totals: getTotals(problems), command: 'split' }) : '');
  try {
    const { transcript: output } = await captureOutput(() => collectProblems(problems, () => handleSplit({ argv, version: '' })));
    return { api, outDir, output, problems: await report() };
  } catch (error) {
    // with fileNameConflictsSeverity=error the command fails after reporting; the host shows `details.output`
    if (problems.length && error && typeof error === 'object') error.details = { ...error.details, output: (await report()) + (error.details?.output ?? '') };
    throw error;
  }
}
