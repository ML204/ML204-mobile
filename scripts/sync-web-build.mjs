#!/usr/bin/env node

import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');
const outDir = path.join(rootDir, 'out');

const args = process.argv.slice(2);
const isRelease = args.includes('--release');
const localWebOut = fs.existsSync(path.resolve(rootDir, '../mltheta/out')) 
  ? path.resolve(rootDir, '../mltheta/out') 
  : path.resolve(rootDir, '../ml204/out');
const isLocal = args.includes('--local') || (!isRelease && fs.existsSync(localWebOut));

async function main() {
  if (isLocal) {
    if (!fs.existsSync(localWebOut)) {
      console.error(`❌ Local web build not found at ${localWebOut}. Run "npm run build" in the web repository first.`);
      process.exit(1);
    }
    console.log(`📦 Syncing web assets from local: ${localWebOut} -> ${outDir}`);
    fs.rmSync(outDir, { recursive: true, force: true });
    fs.cpSync(localWebOut, outDir, { recursive: true });
    console.log(`✅ Web assets copied successfully.`);
    return;
  }

  // Release mode: fetch web-build.zip from GitHub Release of mltheta/mltheta
  const tag = args.find((a) => !a.startsWith('--')) || 'latest';
  console.log(`🌐 Fetching web build release asset for tag "${tag}" from mltheta/mltheta...`);

  const zipPath = path.join(rootDir, 'web-build.zip');
  try {
    if (tag === 'latest') {
      execSync(`gh release download --repo mltheta/mltheta --pattern "web-build.zip" --dir "${rootDir}" --clobber`, { stdio: 'inherit' });
    } else {
      execSync(`gh release download "${tag}" --repo mltheta/mltheta --pattern "web-build.zip" --dir "${rootDir}" --clobber`, { stdio: 'inherit' });
    }
    console.log(`📦 Unzipping ${zipPath} into ${outDir}...`);
    fs.rmSync(outDir, { recursive: true, force: true });
    fs.mkdirSync(outDir, { recursive: true });
    execSync(`unzip -q -o "${zipPath}" -d "${outDir}"`, { stdio: 'inherit' });
    fs.rmSync(zipPath, { force: true });
    console.log(`✅ Web release build extracted successfully.`);
  } catch (err) {
    console.error(`❌ Failed to download or extract web-build.zip:`, err.message);
    process.exit(1);
  }
}

main();
