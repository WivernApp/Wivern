#!/usr/bin/env node
/**
 * Transforms Unicode property escapes (\p{...}) in regex literals
 * to equivalent character class ranges using Babel.
 * This is needed because nodejs-mobile is compiled without ICU.
 *
 * Auto-discovers all .js/.mjs files in node_modules containing \p{ patterns.
 */
import { transformSync } from '@babel/core';
import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';

const ST_DIR = path.dirname(new URL(import.meta.url).pathname);
const NM_DIR = path.join(ST_DIR, 'node_modules');

// Auto-discover files with \p{ using grep
console.log('Scanning node_modules for Unicode property escapes...');
let files;
try {
  const output = execSync(
    `grep -rl '\\\\p{' --include='*.js' --include='*.mjs' "${NM_DIR}"`,
    { encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 }
  );
  files = output.trim().split('\n').filter(Boolean);
} catch (e) {
  if (e.status === 1) {
    console.log('No files found with \\p{ patterns. Nothing to do.');
    process.exit(0);
  }
  throw e;
}

console.log(`Found ${files.length} files to check.\n`);

const plugin = '@babel/plugin-transform-unicode-property-regex';

let succeeded = 0;
let failed = 0;
let skipped = 0;

for (const filePath of files) {
  const rel = path.relative(ST_DIR, filePath);
  const code = fs.readFileSync(filePath, 'utf-8');

  try {
    const result = transformSync(code, {
      filename: filePath,
      plugins: [plugin],
      presets: [],
      sourceType: 'unambiguous',
      retainLines: true,
      sourceMaps: false,
      compact: rel.includes('.min.') ? true : 'auto',
    });

    if (result && result.code && result.code !== code) {
      fs.writeFileSync(filePath, result.code, 'utf-8');
      console.log(`FIXED: ${rel}`);
      succeeded++;
    } else {
      skipped++;
    }
  } catch (err) {
    console.error(`FAILED: ${rel} - ${err.message.split('\n')[0]}`);
    failed++;
  }
}

console.log(`\nDone: ${succeeded} fixed, ${skipped} unchanged, ${failed} failed`);
