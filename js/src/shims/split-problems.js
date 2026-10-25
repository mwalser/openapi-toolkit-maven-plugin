// The vendored split command reports the file names that would collide through formatProblems, on the same channel
// as its progress lines. Its import of openapi-core resolves here (see build.mjs), so that runSplit can collect the
// problems themselves while the command runs and format them like the other commands do.
import { formatProblems as printProblems } from '@redocly/openapi-core';

export * from '@redocly/openapi-core';

let collected = null;

export function formatProblems(problems, opts) {
  if (collected) collected.push(...problems);
  else printProblems(problems, opts);
}

/** Runs `fn` with the problems it reports added to `problems` instead of printed. */
export async function collectProblems(problems, fn) {
  const outer = collected;
  collected = problems;
  try {
    return await fn();
  } finally {
    collected = outer;
  }
}
