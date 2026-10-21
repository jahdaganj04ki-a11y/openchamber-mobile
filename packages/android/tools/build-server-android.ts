#!/usr/bin/env bun
/**
 * Compiles the OpenChamber server for Android.
 *
 * The server is the product's logic, so the phone runs the same code the
 * desktop runs. Three things need attention when the server becomes a single
 * binary:
 *
 * - The native modules with no Android build are replaced by stubs. The file
 *   search needs a ripgrep the phone does not have yet, and the dictation runs
 *   through Android's own speech recognizer.
 * - The server reads a few files next to its own modules. A compiled binary
 *   has no directory next to it, so those reads are replaced by the content
 *   of the files. The server code stays as it is.
 * - The pseudoterminal of `bun-pty` looks for its library next to its own
 *   module. The phone has its own build of that library, so the library comes
 *   from `BUN_PTY_LIB` and nothing else.
 *
 * Usage:
 *
 *   SERVER_OUTFILE=out/server-android \
 *   SERVER_TARGET=bun-linux-aarch64-android \
 *   bun packages/android/tools/build-server-android.ts
 */

import { readFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import type { BunPlugin } from 'bun';

const entry = resolve(process.env.SERVER_ENTRY ?? 'packages/android/tools/server-entry.js');
const outfile = resolve(process.env.SERVER_OUTFILE ?? 'out/server-android');
const target = process.env.SERVER_TARGET ?? 'bun-linux-aarch64-android';

const stubs: Record<string, string> = {
  'node-pty': `export const spawn = () => {
  throw new Error('The pseudoterminal is not available on this device yet.');
};`,
  '@vscode/ripgrep': `export const rgPath = null;`,
  'sherpa-onnx-node': `export default {};`,
};

/**
 * The patches that make the server survive as a single binary.
 *
 * - The server reads two files next to its own modules. A compiled binary has
 *   no directory next to it, so those reads are replaced by the content of
 *   the files.
 * - The server starts itself when its module is the main module. In a
 *   compiled binary that is always the case, and the phone would get a second
 *   server with the command line options. The phone starts the server
 *   itself, so the command line entry is turned off.
 * - The pseudoterminal of `bun-pty` looks for its library next to its own
 *   module, which is a library for a desktop. The phone ships its own build
 *   of it, and the path to it comes from the environment.
 */
const patches: {
  module: RegExp;
  pattern: RegExp;
  replacement: string;
  loader?: 'js' | 'ts';
}[] = [
  {
    module: /lib\/opencode\/settings-(files|helpers)\.js$/,
    pattern: /createRequire\(import\.meta\.url\)\('\.\/settings-registry\.json'\)/g,
    replacement: JSON.stringify(
      JSON.parse(readFileSync(resolve('packages/web/server/lib/opencode/settings-registry.json'), 'utf8')),
    ),
  },
  {
    module: /lib\/spaces\/gatekeeper-channel\.js$/,
    pattern: /readFileSync\(new URL\('\.\/gatekeeper-program\.cjs', import\.meta\.url\), 'utf8'\)/g,
    replacement: JSON.stringify(
      readFileSync(resolve('packages/web/server/lib/spaces/gatekeeper-program.cjs'), 'utf8'),
    ),
  },
  {
    module: /lib\/opencode\/cli-entry-runtime\.js$/,
    pattern: /const isCliExecution = process\.argv\[1\] === currentFilename;/g,
    replacement: 'const isCliExecution = false;',
  },
  {
    module: /bun-pty[/\\]src[/\\]terminal\.ts$/,
    pattern: /function resolveLibPath\(\): string \{[\s\S]*?\n\}/g,
    replacement: `function resolveLibPath(): string {
	const fromEnv = process.env.BUN_PTY_LIB;
	if (!fromEnv) throw new Error("The library of the pseudoterminal is missing");
	return fromEnv;
}`,
    loader: 'ts',
  },
  {
    module: /packages\/web\/server\/index\.js$/,
    pattern: /const OPENCHAMBER_VERSION = \(\(\) => \{[\s\S]*?\n\}\)\(\);/g,
    replacement: `const OPENCHAMBER_VERSION = ${JSON.stringify(readAppVersion())};`,
  },
];

/** The version the user sees on the phone. The server reports the same one. */
function readAppVersion(): string {
  const gradle = readFileSync(resolve('packages/android/app/build.gradle.kts'), 'utf8');
  const match = gradle.match(/versionName\s*=\s*"([^"]+)"/);
  if (!match) {
    throw new Error('The version name of the app was not found in app/build.gradle.kts');
  }
  return match[1];
}

const nativeStubPlugin: BunPlugin = {
  name: 'openchamber-android-native-stubs',
  setup(build) {
    const names = Object.keys(stubs);
    build.onResolve({ filter: new RegExp(`^(${names.map((name) => name.replace('/', '\\/')).join('|')})$`) }, (args) => ({
      path: args.path,
      namespace: 'openchamber-android-native',
    }));
    build.onLoad({ filter: /.*/, namespace: 'openchamber-android-native' }, (args) => ({
      loader: 'js',
      contents: stubs[args.path] ?? 'export default {};',
    }));
  },
};

const patchFilesPlugin: BunPlugin = {
  name: 'openchamber-android-patches',
  setup(build) {
    for (const patch of patches) {
      build.onLoad({ filter: patch.module }, async (args) => {
        const source = await readFile(args.path, 'utf8');
        let replaced = false;
        const patched = source.replace(patch.pattern, () => {
          replaced = true;
          return patch.replacement;
        });
        if (!replaced) {
          throw new Error(`The patch for ${args.path} did not match. The server changed under the build.`);
        }
        return { contents: patched, loader: patch.loader ?? 'js' };
      });
    }
  },
};

const result = await Bun.build({
  entrypoints: [entry],
  plugins: [nativeStubPlugin, patchFilesPlugin],
  external: ['node-gyp'],
  format: 'esm',
  minify: false,
  sourcemap: 'none',
  compile: {
    autoloadBunfig: false,
    autoloadDotenv: false,
    autoloadTsconfig: false,
    autoloadPackageJson: true,
    target: target as Bun.Build.CompileTarget,
    outfile,
  },
});

if (!result.success) {
  for (const log of result.logs) console.error(log);
  process.exit(1);
}

console.log(`built ${outfile} for ${target}`);
