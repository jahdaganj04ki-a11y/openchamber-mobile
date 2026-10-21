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
import ipaddr from '../../../../node_modules/.bun/ipaddr.js@1.9.1/node_modules/ipaddr.js/lib/ipaddr.js';

const part = '(0?\\d+|0x[a-f0-9]+)';
const fourOctet = new RegExp(`^${part}\\.${part}\\.${part}\\.${part}$`, 'i');
const literal = /^(0?\d+|0x[a-f0-9]+)\.(0?\d+|0x[a-f0-9]+)\.(0?\d+|0x[a-f0-9]+)\.(0?\d+|0x[a-f0-9]+)$/i;

console.log(`the runtime: ${Bun.version}`);
console.log(`the pattern: ${fourOctet.source}`);
console.log(`a built pattern matches: ${JSON.stringify('127.0.0.1'.match(fourOctet)?.[0] ?? null)}`);
console.log(`a written pattern matches: ${JSON.stringify('127.0.0.1'.match(literal)?.[0] ?? null)}`);
console.log(`a simple pattern matches: ${JSON.stringify('127.0.0.1'.match(/^\d+\.\d+\.\d+\.\d+$/)?.[0] ?? null)}`);
console.log(`the flags of the built pattern: ${fourOctet.flags}`);

console.log(`the library: ${Object.keys(ipaddr).join(', ')}`);
console.log(`the parser of the library: ${JSON.stringify(ipaddr.IPv4?.parser?.('127.0.0.1') ?? null)}`);
console.log(`the validity of the library: ${ipaddr.IPv4?.isValid?.('127.0.0.1') ?? 'no answer'}`);
console.log(`the validity of the library for six: ${ipaddr.IPv6?.isValid?.('127.0.0.1') ?? 'no answer'}`);
console.log(`the validity of the library for all: ${ipaddr.isValid?.('127.0.0.1') ?? 'no answer'}`);

const app = express();
try {
  app.set('trust proxy', 'loopback, linklocal, uniquelocal');
  console.log('the trust proxy was set');
} catch (error) {
  console.log(`the trust proxy failed: ${(error as Error).message}`);
}
