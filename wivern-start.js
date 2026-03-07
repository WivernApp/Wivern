#!/usr/bin/env node
// Wivern wrapper: set CWD to server directory before SillyTavern initializes
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));
process.chdir(dir);

// --- Polyfills for nodejs-mobile (compiled without ICU) ---

// Patch TextDecoder to accept the "fatal" option without throwing.
// nodejs-mobile is built without ICU so new TextDecoder(enc, {fatal:true}) fails.
const OriginalTextDecoder = globalThis.TextDecoder;
globalThis.TextDecoder = class PatchedTextDecoder extends OriginalTextDecoder {
    constructor(encoding, options) {
        // Strip the "fatal" option that requires ICU support
        const safeOptions = options ? { ...options, fatal: false } : options;
        super(encoding || 'utf-8', safeOptions);
    }
};

// Polyfill Intl (not available without ICU).
// Provides basic Collator for string sorting used by SillyTavern server code.
if (typeof globalThis.Intl === 'undefined') {
    globalThis.Intl = {
        Collator: function () {
            return { compare: (a, b) => a < b ? -1 : a > b ? 1 : 0 };
        },
    };
}

// --- End polyfills ---

// Ensure config.yaml exists before server.js tries to read it
const configPath = path.join(dir, 'config.yaml');
const defaultConfigPath = path.join(dir, 'default', 'config.yaml');
if (!fs.existsSync(configPath) && fs.existsSync(defaultConfigPath)) {
    fs.copyFileSync(defaultConfigPath, configPath);
    console.log('Created config.yaml from default');
}

await import('./server.js');
