// Subset of packages/cli/src/types.ts needed by the vendored commands.
import type { RuleSeverity } from '@redocly/openapi-core';

export type Totals = {
  errors: number;
  warnings: number;
  ignored: number;
};

export type Entrypoint = {
  path: string;
  alias?: string;
  output?: string;
};

export const outputExtensions = ['json', 'yaml', 'yml'] as const;
export type OutputExtension = (typeof outputExtensions)[number];

export type VerifyConfigOptions = {
  config?: string;
  'lint-config'?: RuleSeverity;
};
