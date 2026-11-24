#!/usr/bin/env node

/**
 * Strips heavy binary resources (PDFs, LaTeX .tex sources, datasets, archives)
 * from the Android assets directory so they are NOT wrapped inside the APK.
 *
 * This keeps the installed app ultra-compact (<15 MB) across all 132 topics.
 * Resources are downloaded on demand from the GitHub repository when online.
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

const androidAssetsResources = path.join(
  rootDir,
  'android',
  'app',
  'src',
  'main',
  'assets',
  'public',
  'resources'
);

const STRIP_EXTENSIONS = new Set([
  '.pdf',
  '.tex',
  '.zip',
  '.tar',
  '.gz',
  '.rar',
  '.7z',
  '.csv',
  '.parquet',
  '.npy',
  '.npz',
  '.bin',
  '.pt',
  '.onnx',
  '.h5',
]);

let strippedCount = 0;
let bytesSaved = 0;

function pruneDir(dirPath) {
  if (!fs.existsSync(dirPath)) return;

  const entries = fs.readdirSync(dirPath, { withFileTypes: true });

  for (const entry of entries) {
    const fullPath = path.join(dirPath, entry.name);

    if (entry.isDirectory()) {
      pruneDir(fullPath);
      // Remove directory if empty
      try {
        if (fs.readdirSync(fullPath).length === 0) {
          fs.rmdirSync(fullPath);
        }
      } catch {
        // Ignore
      }
    } else if (entry.isFile()) {
      const ext = path.extname(entry.name).toLowerCase();
      if (STRIP_EXTENSIONS.has(ext)) {
        try {
          const stat = fs.statSync(fullPath);
          bytesSaved += stat.size;
          fs.unlinkSync(fullPath);
          strippedCount++;
          console.log(`  🗑️ Stripped from Android asset: ${path.relative(rootDir, fullPath)} (${(stat.size / 1024).toFixed(1)} KB)`);
        } catch (err) {
          console.error(`Failed to delete ${fullPath}:`, err);
        }
      }
    }
  }
}

console.log('\n📦 [ML204 Asset Optimizer] Pruning heavy resources from Android APK bundle...');

if (fs.existsSync(androidAssetsResources)) {
  pruneDir(androidAssetsResources);
  const mbSaved = (bytesSaved / (1024 * 1024)).toFixed(2);
  console.log(`✅ Pruned ${strippedCount} heavy resource files. Saved ${mbSaved} MB in APK assets.`);
  console.log('⚡ Resources will be fetched on demand over the internet.\n');
} else {
  console.log('ℹ️ Android assets resources directory not found. Nothing to prune.\n');
}
