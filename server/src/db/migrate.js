import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { pool } from './pool.js';

const directory = path.join(path.dirname(fileURLToPath(import.meta.url)), 'migrations');
const files = (await fs.readdir(directory)).filter((file) => file.endsWith('.sql')).sort();

try {
  for (const file of files) {
    const sql = await fs.readFile(path.join(directory, file), 'utf8');
    await pool.query(sql);
    console.log(`Applied ${file}`);
  }
} finally {
  await pool.end();
}
