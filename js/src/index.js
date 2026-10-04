import { setCwd } from './polyfills.js';
import { runLint } from './api/lint.js';
import { runBundle } from './api/bundle.js';
import { runCheckConfig } from './api/check-config.js';
import { runStats } from './api/stats.js';
import { runJoin } from './api/join.js';
import { runSplit } from './api/split.js';
import { runScore } from './api/score.js';
import { runBuildDocs } from './api/build-docs.js';

const COMMANDS = {
  lint: runLint,
  bundle: runBundle,
  'check-config': runCheckConfig,
  stats: runStats,
  join: runJoin,
  split: runSplit,
  score: runScore,
  'build-docs': runBuildDocs,
};

export function version() {
  return __REDOCLY_VERSION__;
}

/**
 * Entry point used by the JVM: runs a command with JSON-encoded options and resolves to a JSON-encoded result.
 * Rejections carry a plain `{ name, message, stack, details }` object.
 */
export async function run(command, optionsJson) {
  const handler = COMMANDS[command];
  if (!handler) {
    throw { name: 'CommandError', message: `Unknown command '${command}'` };
  }
  const opts = JSON.parse(optionsJson);
  if (opts.cwd) setCwd(opts.cwd);
  try {
    const result = await handler(opts);
    return JSON.stringify(result);
  } catch (e) {
    throw {
      name: e?.name || 'Error',
      message: e?.message || String(e),
      stack: e?.stack,
      details: e?.details,
    };
  }
}
