// Subset of packages/cli/src/wrapper.ts (types only).
import type { Config } from '@redocly/openapi-core';

export type CommandArgs<T> = {
  argv: T;
  config: Config;
  version: string;
  collectSpecData?: (data: unknown) => void;
  collectResults?: (results: unknown) => void;
};
