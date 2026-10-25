// Bundles the plugin's JavaScript into two ES modules that GraalJS can evaluate, plus the license notices of
// everything they contain:
//   redocly-core.mjs     @redocly/openapi-core, the vendored CLI commands and the command layer of every goal
//   redocly-docs.mjs     Redoc with React for build-docs, evaluated into the same context when that goal first runs
//   redoc-standalone.js  Redoc's page script, copied next to the docs bundle for build-docs' inlineBundle
//
//   node build.mjs [core bundle] [docs bundle] [notices file]
//   (defaults: dist/redocly-core.mjs, dist/redocly-docs.mjs, dist/THIRD-PARTY-NOTICES.txt)
import * as esbuild from 'esbuild';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import * as path from 'node:path';
import { removeRedoclyLogo, replaceRedoclyLogo } from './src/docs/redocly-logo.js';

const coreVersion = packageVersion('@redocly/openapi-core');
const pomVersion = readFileSync('../pom.xml', 'utf8').match(/<redocly\.version>([^<]+)<\/redocly\.version>/)?.[1];
if (pomVersion !== coreVersion) {
  throw new Error(`Redocly version mismatch: package.json has ${coreVersion}, pom.xml has ${pomVersion}`);
}
const redocVersion = packageVersion('redoc');
// The page loads redoc.standalone.js from Redocly's CDN with this subresource integrity hash, as the CLI does.
const redocStandalone = readFileSync('node_modules/redoc/bundles/redoc.standalone.js');
const redocStandaloneSri = 'sha384-' + createHash('sha384').update(redocStandalone).digest('base64');
// renderPage removes the Redocly logo link from the inlined script at run time; fail here if it cannot
removeRedoclyLogo(redocStandalone.toString('utf8'), 'redoc.standalone.js');

const [coreFile = 'dist/redocly-core.mjs', docsFile = 'dist/redocly-docs.mjs', noticesFile = 'dist/THIRD-PARTY-NOTICES.txt'] = process.argv.slice(2);
for (const file of [coreFile, docsFile, noticesFile]) mkdirSync(path.dirname(file), { recursive: true });
// the page script that `inlineBundle` embeds, read by the docs bundle through the host only when needed
const standaloneFile = path.join(path.dirname(docsFile), 'redoc-standalone.js');

const core = await build({
  entry: 'src/index.js',
  outfile: coreFile,
  define: { __REDOCLY_VERSION__: JSON.stringify(coreVersion) },
  plugins: [splitProblemsCollected()],
});
const docs = await build({
  entry: 'src/docs.js',
  outfile: docsFile,
  alias: {
    // the server build; the browser build inserts its styles into a document
    'styled-components': path.resolve('node_modules/styled-components/dist/styled-components.esm.js'),
    stream: './src/shims/stream.js', // styled-components' server build requires it
  },
  define: { __REDOC_VERSION__: JSON.stringify(redocVersion), __REDOC_STANDALONE_SRI__: JSON.stringify(redocStandaloneSri) },
  plugins: [redocWithOptionalLogo()],
});
writeFileSync(standaloneFile, redocStandalone);
writeFileSync(noticesFile, thirdPartyNotices([core.metafile, docs.metafile]));

async function build({ entry, outfile, alias = {}, define = {}, plugins = [] }) {
  const result = await esbuild.build({
    entryPoints: [entry],
    bundle: true,
    format: 'esm',
    platform: 'browser',
    mainFields: ['browser', 'module', 'main'],
    target: 'es2022',
    outfile,
    logLevel: 'info',
    metafile: true,
    legalComments: 'none', // every license is reproduced in THIRD-PARTY-NOTICES.txt instead
    plugins: [browserEnvWithHostVariables(), ...plugins],
    alias: {
      'node:fs': './src/shims/fs.js',
      fs: './src/shims/fs.js',
      'node:path': 'path-browserify',
      path: 'path-browserify',
      'node:process': './src/shims/process.js',
      process: './src/shims/process.js',
      'node:perf_hooks': './src/shims/perf_hooks.js',
      perf_hooks: './src/shims/perf_hooks.js',
      colorette: './src/shims/colorette.js',
      ...alias,
    },
    define: {
      'import.meta.url': JSON.stringify(`file:///bundle/${path.basename(outfile)}`),
      'process.env.NODE_ENV': '"production"', // React ships a development build that checks this at load
      ...define,
    },
  });
  printLargestInputs(outfile, result.metafile);
  return result;
}

function packageVersion(name) {
  return JSON.parse(readFileSync(`node_modules/${name}/package.json`, 'utf8')).version;
}

/**
 * openapi-core's browser build hard-codes `env = {}`, which would leave `resolve.http.headers[].envVariable`
 * empty. The replacement keeps browser mode (no plugin loading) but reads variables through the host.
 */
function browserEnvWithHostVariables() {
  return {
    name: 'browser-env-with-host-variables',
    setup(build) {
      build.onLoad({ filter: /@redocly\/openapi-core\/lib\/env\.js$/ }, () => ({
        contents: 'export const isBrowser = true; export const env = globalThis.process.env;',
        loader: 'js',
      }));
    },
  };
}

/** The vendored split command reports file name conflicts through src/shims/split-problems.js, which runSplit collects. */
function splitProblemsCollected() {
  return {
    name: 'split-problems-collected',
    setup(build) {
      build.onResolve({ filter: /^@redocly\/openapi-core$/ }, (args) =>
        /report-file-name-conflicts\.ts$/.test(args.importer) ? { path: path.resolve('src/shims/split-problems.js') } : undefined,
      );
    },
  };
}

/** Redoc's library with the Redocly logo link left out while `globalThis.__openapiToolkitRemoveRedoclyLogo` is set. */
function redocWithOptionalLogo() {
  return {
    name: 'redoc-with-optional-logo',
    setup(build) {
      build.onLoad({ filter: /node_modules[\\/]redoc[\\/]bundles[\\/]redoc\.js$/ }, (args) => ({
        contents: replaceRedoclyLogo(
          readFileSync(args.path, 'utf8'),
          (element) => `(globalThis.__openapiToolkitRemoveRedoclyLogo?null:${element})`,
          'redoc.js',
        ),
        loader: 'js',
      }));
    },
  };
}

/** One section per bundled npm package (from the metafiles) plus the vendored redocly-cli sources. */
function thirdPartyNotices(metafiles) {
  const packageRoots = new Set();
  for (const metafile of metafiles) {
    for (const input of Object.keys(metafile.inputs)) {
      // the innermost package directory: redoc brings its own copy of @redocly/openapi-core
      const root = input.match(/^(?:.*\/)?node_modules\/(?:@[^/]+\/[^/]+|[^/]+)/)?.[0];
      if (root && !input.startsWith('(disabled)')) packageRoots.add(root);
    }
  }
  const listed = new Set(); // an npm alias (node_modules/ajv -> @redocly/ajv) must not list a package twice
  const sections = [...packageRoots].sort().flatMap((root) => {
    const pkg = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'));
    if (listed.has(`${pkg.name}@${pkg.version}`)) return [];
    listed.add(`${pkg.name}@${pkg.version}`);
    const licenseFile = ['LICENSE', 'LICENSE.md', 'LICENSE.txt', 'LICENCE', 'license'].map((name) => path.join(root, name)).find(existsSync);
    const license = licenseFile
      ? readFileSync(licenseFile, 'utf8').replace(/\r\n?/g, '\n').trim() // git normalizes line endings; the comparison in CI must see the same bytes
      : `No license file is included in the package; its metadata declares ${pkg.license || 'an unspecified license'}.`;
    return [section(`${pkg.name} ${pkg.version} (${pkg.license || 'see below'})`, license)];
  });
  sections.push(section('@redocly/cli (vendored command sources)', readFileSync('vendor/redocly-cli/LICENSE', 'utf8').trim()));
  return ['THIRD-PARTY NOTICES', '', 'This product bundles the following third-party software:', ...sections, ''].join('\n');
}

function section(title, body) {
  return ['', '='.repeat(80), title, '-'.repeat(80), body].join('\n');
}

function printLargestInputs(outfile, metafile) {
  const inputs = Object.entries(metafile.inputs).sort((a, b) => b[1].bytes - a[1].bytes).slice(0, 12);
  console.log(`largest inputs of ${path.basename(outfile)}:`);
  for (const [file, { bytes }] of inputs) console.log(`  ${(bytes / 1024).toFixed(0).padStart(6)} KB  ${file}`);
}
