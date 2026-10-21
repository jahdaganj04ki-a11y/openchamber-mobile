#!/usr/bin/env bun
/**
 * Checks the pieces of the address parsing of the server on a phone.
 *
 * The server trusts the addresses of the loopback and of the private
 * networks, and it asks the `proxy-addr` library to compile that list. The
 * library asks `ipaddr.js` whether a string is an address, and that library
 * builds its patterns with `new RegExp` at load time. On a phone the server
 * did not start because the library said that `127.0.0.1` is not an
 * address, so this script runs the same code on the device and prints what
 * it finds.
 */

import express from 'express';

const part = '(0?\\d+|0x[a-f0-9]+)';
const fourOctet = new RegExp(`^${part}\\.${part}\\.${part}\\.${part}$`, 'i');
const literal = /^(0?\d+|0x[a-f0-9]+)\.(0?\d+|0x[a-f0-9]+)\.(0?\d+|0x[a-f0-9]+)\.(0?\d+|0x[a-f0-9]+)$/i;

console.log(`the runtime: ${Bun.version}`);
console.log(`the pattern: ${fourOctet.source}`);
console.log(`a built pattern matches: ${JSON.stringify('127.0.0.1'.match(fourOctet)?.[0] ?? null)}`);
console.log(`a written pattern matches: ${JSON.stringify('127.0.0.1'.match(literal)?.[0] ?? null)}`);
console.log(`a simple pattern matches: ${JSON.stringify('127.0.0.1'.match(/^\d+\.\d+\.\d+\.\d+$/)?.[0] ?? null)}`);
console.log(`the flags of the built pattern: ${fourOctet.flags}`);

const app = express();
try {
  app.set('trust proxy', 'loopback, linklocal, uniquelocal');
  console.log('the trust proxy was set');
} catch (error) {
  console.log(`the trust proxy failed: ${(error as Error).message}`);
}
