#!/usr/bin/env bun
/**
 * Writes the shared message catalogs into the Android assets.
 *
 * The catalogs live in the TypeScript package, because that is where every
 * other client reads them. The phone loads the generated JSON at runtime, so
 * a string exists in one place and the two clients cannot drift apart.
 *
 * Run it with Bun, which reads the TypeScript modules directly:
 *
 *   bun packages/android/tools/generate-catalogs.mjs
 */

import { mkdir, writeFile, readdir } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const messagesDir = resolve(here, '../../ui/src/lib/i18n/messages');
const outputDir = resolve(here, '../app/src/main/assets/i18n');

const LOCALE_FILES = [
  'ar',
  'de',
  'en',
  'es',
  'fr',
  'he',
  'ja',
  'ko',
  'nl',
  'pl',
  'pt-BR',
  'tr',
  'uk',
  'zh-CN',
  'zh-TW',
];

const isLocaleFile = (name) =>
  name.endsWith('.ts') &&
  !name.endsWith('.test.ts') &&
  !name.endsWith('.settings.ts') &&
  !name.endsWith('.i18n.ts');

const available = new Set(
  (await readdir(messagesDir)).filter(isLocaleFile).map((name) => name.replace(/\.ts$/, '')),
);

await mkdir(outputDir, { recursive: true });

let written = 0;
for (const locale of LOCALE_FILES) {
  if (!available.has(locale)) {
    console.warn(`[catalogs] ${locale} is missing, skipped`);
    continue;
  }
  const module = await import(join(messagesDir, `${locale}.ts`));
  const dict = module.dict;
  if (!dict || typeof dict !== 'object') {
    throw new Error(`[catalogs] ${locale} has no dict`);
  }
  const target = join(outputDir, `${locale}.json`);
  await writeFile(target, `${JSON.stringify(dict, null, 0)}\n`, 'utf8');
  written += 1;
}

console.log(`[catalogs] wrote ${written} catalogs to ${outputDir}`);
