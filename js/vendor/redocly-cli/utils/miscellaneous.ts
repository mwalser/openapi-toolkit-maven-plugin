// Replacement for packages/cli/src/utils/miscellaneous.ts providing only what the vendored
// stats/join/split commands use, implemented on top of the plugin's own helpers.
import { logger, parseYaml, stringifyYaml, type Config } from '@redocly/openapi-core';
import * as fs from 'node:fs';
import { dirname } from 'node:path';

import { resolveApis, sortTopLevelKeys } from '../../../src/api/common.js';
import { outputExtensions, type Entrypoint, type OutputExtension } from '../types.js';

export { sortTopLevelKeys };

export async function getFallbackApisOrExit(argsApis: string[] | undefined, config: Config): Promise<Entrypoint[]> {
  return resolveApis(config, argsApis, process.cwd());
}

export function getExecutionTime(startedAt: number) {
  return `${Math.ceil(performance.now() - startedAt)}ms`;
}

export function printExecutionTime(commandName: string, startedAt: number, api: string) {
  logger.info(`\n${api}: ${commandName} processed in ${getExecutionTime(startedAt)}\n\n`);
}

export function pathToFilename(path: string, pathSeparator: string) {
  if (path === '/') {
    return pathSeparator;
  }
  return path.replaceAll('~1', '/').replaceAll('~0', '~').replace(/^\//, '').replaceAll('/', pathSeparator);
}

export function readYaml(filename: string) {
  return parseYaml(fs.readFileSync(filename, 'utf-8'), { filename });
}

export function writeToFileByExtension(data: unknown, filePath: string, noRefs?: boolean) {
  const ext = getAndValidateFileExtension(filePath);
  const content = ext === 'json' ? JSON.stringify(data, null, 2) : stringifyYaml(data, { noRefs });
  fs.mkdirSync(dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, content);
}

export function getAndValidateFileExtension(fileName: string): NonNullable<OutputExtension> {
  const ext = fileName.split('.').pop();
  if (outputExtensions.includes(ext as OutputExtension)) {
    return ext as OutputExtension;
  }
  logger.warn(`Unsupported file extension: ${ext}. Using yaml.\n`);
  return 'yaml';
}

export function escapeLanguageName(lang: string) {
  return lang.replace(/#/g, '_sharp').replace(/\//, '_').replace(/\s/g, '');
}

export function langToExt(lang: string) {
  const langObj: Record<string, string> = {
    php: '.php',
    'c#': '.cs',
    shell: '.sh',
    curl: '.sh',
    bash: '.sh',
    javascript: '.js',
    js: '.js',
    python: '.py',
    c: '.c',
    'c++': '.cpp',
    coffeescript: '.litcoffee',
    dart: '.dart',
    elixir: '.ex',
    go: '.go',
    groovy: '.groovy',
    java: '.java',
    kotlin: '.kt',
    'objective-c': '.m',
    perl: '.pl',
    powershell: '.ps1',
    ruby: '.rb',
    rust: '.rs',
    scala: '.sc',
    swift: '.swift',
    typescript: '.ts',
    tsx: '.tsx',
    'visual basic': '.vb',
    'c/al': '.al',
  };
  return langObj[lang.toLowerCase()] ?? '';
}
