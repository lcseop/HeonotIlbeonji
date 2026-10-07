import { readFileSync, writeFileSync, copyFileSync, mkdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

// Stage a verified APK and its versioned download manifest for the normal site deployment.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const gradle = readFileSync(resolve(root, 'android-admin/app/build.gradle'), 'utf8');
const versionCode = Number(gradle.match(/versionCode\s+(\d+)/)[1]);
const versionName = gradle.match(/versionName\s+'([\d.]+)'/)[1];
const source = resolve(process.argv[2] || resolve(root, `outputs/android/헌옷일번지-관리자-${versionName}.apk`));
const bytes = readFileSync(source);
if (!bytes.length || bytes.length > 25 * 1024 * 1024) throw new Error('APK size is outside the supported range.');
const folder = resolve(root, 'public/admin-app'); mkdirSync(folder, { recursive: true });
const filename = `heonot-admin-${versionName}.apk`, destination = resolve(folder, filename);
if (source !== destination) copyFileSync(source, destination);
const manifest = { packageName: 'com.heonotilbeonji.admin', versionCode, versionName,
  apkPath: `/admin-app/${filename}`, sha256: createHash('sha256').update(bytes).digest('hex'), size: bytes.length,
  releaseNotes: process.argv[3] || '앱 안에서 새 버전 확인, 다운로드, 업데이트 설치를 진행할 수 있습니다.' };
writeFileSync(resolve(folder, 'latest.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(`Prepared admin app ${versionName}: ${bytes.length} bytes. Deploy the committed files to publish.`);
