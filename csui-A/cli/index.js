#!/usr/bin/env node
'use strict';

const { execSync, spawn } = require('child_process');
const { existsSync, readdirSync, statSync, writeFileSync, readFileSync, mkdirSync } = require('fs');
const { join, resolve } = require('path');
const os   = require('os');
const http = require('http');
const https = require('https');

const VERSION = '1.0.0';
const cmd     = process.argv[2];

switch (cmd) {
    case 'setup':   setup();   break;
    case 'create':  create();  break;
    case 'run':     run();     break;
    case 'build':   build();   break;
    case 'install': install(); break;
    case 'logs':    logs();    break;
    case 'devices': devices(); break;
    case '--version': case '-v': console.log('csua v' + VERSION); break;
    default:        help();
}

// ── HELP ─────────────────────────────────────────────

function help() {
    console.log(`
  csua v${VERSION} — CtrlScript Android Runtime

  Commands:
    csua setup          Install Android SDK (guided, no Android Studio)
    csua create <name>  Create a new project
    csua run            Build + install + watch for changes
    csua build          Build release APK
    csua install        Build debug APK + install on device
    csua logs           Stream app logs from device
    csua devices        List connected devices

  All you need to write: app.js + csua.config.js
`);
}

// ── SETUP ─────────────────────────────────────────────

async function setup() {
    console.log('\n  csua setup\n');

    // Check Java
    try { execSync('java -version', { stdio: 'pipe' }); tick('Java found'); }
    catch (e) { fail('Java 17+ not found. Install from: https://adoptium.net'); }

    // Check ADB (if SDK already installed)
    const existingHome = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
    if (existingHome && existsSync(join(existingHome, 'platform-tools', adb()))) {
        tick('Android SDK already installed at ' + existingHome);
        tick('ADB found');
        console.log('\n  All good! Run: csua create my-app\n');
        return;
    }

    // Show drives + space
    console.log('  Available drives:\n');
    const drives = getDrives();
    drives.forEach((d, i) => {
        const rec = i === drives.length - 1 ? '← recommended' : '';
        console.log(`  ${i + 1}) ${d.name}  —  ${d.free} GB free  ${rec}`);
    });

    const readline = require('readline').createInterface({ input: process.stdin, output: process.stdout });
    const ask = q => new Promise(r => readline.question(q, r));

    const choice = parseInt(await ask('\n  Install on which drive? (number): ')) - 1;
    const drive  = drives[Math.max(0, Math.min(choice, drives.length - 1))];
    const sdkDir = join(drive.name.replace(/\\/g, '/'), 'android-sdk');

    const DOWNLOAD_SIZE = '~1.8 GB';
    const confirm = await ask(`\n  Will download Android SDK (${DOWNLOAD_SIZE}) to ${sdkDir}\n  Proceed? (Y/n): `);
    if (confirm.trim().toLowerCase() === 'n') { readline.close(); return; }

    readline.close();
    console.log('\n  Downloading...');

    mkdirSync(sdkDir, { recursive: true });

    // Download commandlinetools
    const toolsUrl = _sdkUrl();
    const toolsZip = join(sdkDir, 'cmdtools.zip');
    await _download(toolsUrl, toolsZip, p => process.stdout.write(`\r  [${_bar(p)}] ${p}%`));
    console.log('\n  Extracting...');
    execSync(`${_unzip()} "${toolsZip}" -d "${sdkDir}"`, { stdio: 'inherit' });

    // Accept licenses + install components
    const sdkmanager = join(sdkDir, 'cmdline-tools', 'bin', os.platform() === 'win32' ? 'sdkmanager.bat' : 'sdkmanager');
    const env = { ...process.env, ANDROID_HOME: sdkDir };
    console.log('  Installing platform-tools, build-tools, android-34...');
    execSync(`"${sdkmanager}" --sdk_root="${sdkDir}" "platform-tools" "build-tools;34.0.0" "platforms;android-34"`,
        { env, stdio: ['pipe', 'inherit', 'inherit'], input: Buffer.from(Array(20).fill('y\n').join('')) });

    // Write ANDROID_HOME to shell profile
    const profileLine = `\nexport ANDROID_HOME="${sdkDir}"\nexport PATH="$PATH:$ANDROID_HOME/platform-tools"\n`;
    const profile = join(os.homedir(), os.platform() === 'win32' ? '.bash_profile' : '.zshrc');
    try { writeFileSync(profile, readFileSync(profile, 'utf8') + profileLine); } catch (e) {
        writeFileSync(profile, profileLine);
    }

    tick('Android SDK installed at ' + sdkDir);
    console.log('\n  Restart your terminal, then run: csua create my-app\n');
}

// ── CREATE ────────────────────────────────────────────

function create() {
    const name = process.argv[3];
    if (!name) { fail('Usage: csua create <app-name>'); }

    const src = join(__dirname, '..', 'template');
    const dst = resolve(name);
    if (existsSync(dst)) { fail(`Folder "${name}" already exists.`); }

    _copyDir(src, dst);

    // Patch package name in config
    const cfg = join(dst, 'csua.config.js');
    writeFileSync(cfg, readFileSync(cfg, 'utf8')
        .replace('com.yourname.myapp', 'com.' + name.toLowerCase().replace(/[^a-z0-9]/g, '') + '.app')
        .replace('My App', name));

    tick(`Created ${name}/`);
    console.log(`\n  cd ${name}\n  csua run\n`);
}

// ── RUN (build + install + watch) ────────────────────

function run() {
    _requireProject();
    console.log('\n  Building and installing...\n');
    install();
    console.log('\n  Watching app.js for changes...\n');
    console.log('  Edit app.js → save → app reloads on device\n');

    // Push csua.js to device assets folder for hot reload
    const appJs   = resolve('app.js');
    const csuaJs  = join(__dirname, '..', 'csua.js');
    let   lastMod = 0;

    setInterval(() => {
        try {
            const mtime = statSync(appJs).mtimeMs;
            if (mtime > lastMod) {
                lastMod = mtime;
                process.stdout.write('  Change detected → pushing app.js...');
                // Push to /sdcard/csua/ — Bootstrap reads from there first (hot reload path)
                execSync(`${adb()} shell mkdir -p /sdcard/csua`, { stdio: 'pipe' });
                execSync(`${adb()} push "${appJs}" /sdcard/csua/app.js`, { stdio: 'pipe' });
                execSync(`${adb()} push "${csuaJs}" /sdcard/csua/csua.js`, { stdio: 'pipe' });
                // Broadcast reload signal
                execSync(`${adb()} shell am broadcast -a com.csua.RELOAD`, { stdio: 'pipe' });
                console.log(' done');
            }
        } catch (e) {}
    }, 500);
}

// ── BUILD ─────────────────────────────────────────────

function build() {
    _requireProject();
    _checkNativeLibs();
    _copyAssets();
    console.log('\n  Building release APK...\n');
    execSync('./gradlew assembleRelease --stacktrace', { cwd: _templateDir(), stdio: 'inherit' });
    const apk = join(_templateDir(), 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
    if (existsSync(apk)) {
        const mb = (statSync(apk).size / 1048576).toFixed(1);
        console.log(`\n  APK: ${apk} (${mb} MB)\n`);
    }
}

// ── INSTALL ───────────────────────────────────────────

function install() {
    _requireProject();
    _checkNativeLibs();
    _copyAssets();
    execSync(_gradlew() + ' assembleDebug', { cwd: _templateDir(), stdio: 'inherit' });
    const apk = join(_templateDir(), 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
    execSync(`${adb()} install -r "${apk}"`, { stdio: 'inherit' });
    tick('Installed on device');
}

// ── LOGS ──────────────────────────────────────────────

function logs() {
    console.log('\n  Streaming logs (CSUA + errors only)...\n  Ctrl+C to stop\n');
    execSync(`${adb()} shell am broadcast -a com.csua.RELOAD 2>/dev/null || true`);
    const child = spawn(adb(), ['logcat', '-v', 'brief', 'CSUA:V', 'AndroidRuntime:E', '*:S'],
        { stdio: ['ignore', 'pipe', 'pipe'] });
    child.stdout.on('data', d => process.stdout.write(d));
    child.stderr.on('data', d => process.stderr.write(d));
}

// ── DEVICES ───────────────────────────────────────────

function devices() {
    console.log('\n  Connected devices:\n');
    try {
        const out = execSync(`${adb()} devices -l`, { encoding: 'utf8' });
        const lines = out.trim().split('\n').slice(1).filter(l => l.trim() && !l.startsWith('*'));
        if (!lines.length) { console.log('  No devices found.\n  Connect a device via USB and enable USB Debugging.\n'); return; }
        lines.forEach(l => console.log('  ✓ ' + l.trim()));
        console.log();
    } catch (e) { fail('ADB not found. Run: csua setup'); }
}

// ── HELPERS ───────────────────────────────────────────

function adb()     { return os.platform() === 'win32' ? 'adb.exe' : 'adb'; }
function _gradlew(){ return os.platform() === 'win32' ? 'gradlew.bat' : './gradlew'; }
function _unzip()  { return os.platform() === 'win32' ? 'powershell -command Expand-Archive' : 'unzip'; }
function _templateDir() { return resolve(join(__dirname, '..', 'template')); }

function _requireProject() {
    if (!existsSync(resolve('app.js'))) fail('No app.js found. Run from your project folder or: csua create <name>');
    if (!existsSync(resolve('csua.config.js'))) fail('No csua.config.js found.');
}

function _copyAssets() {
    const dst = join(_templateDir(), 'app', 'src', 'main', 'assets');
    mkdirSync(dst, { recursive: true });
    execSync(`cp "${resolve('app.js')}" "${dst}/app.js"`);
    execSync(`cp "${join(__dirname, '..', 'csua.js')}" "${dst}/csua.js"`);
}

function getDrives() {
    if (os.platform() !== 'win32') {
        const total = Math.round(statSync('/').size / 1073741824);
        return [{ name: '/', free: total }];
    }
    try {
        const out = execSync('wmic logicaldisk get name,freespace /format:csv', { encoding: 'utf8' });
        return out.trim().split('\n').slice(1)
            .map(l => l.trim().split(','))
            .filter(p => p.length >= 3 && p[2])
            .map(p => ({ name: p[1] + '\\', free: Math.round(parseInt(p[2]) / 1073741824) }))
            .filter(d => d.free > 0)
            .sort((a, b) => b.free - a.free);
    } catch (e) { return [{ name: 'C:\\', free: '?' }]; }
}

function _sdkUrl() {
    const v = '11076708';
    if (os.platform() === 'win32') return `https://dl.google.com/android/repository/commandlinetools-win-${v}_latest.zip`;
    if (os.platform() === 'darwin') return `https://dl.google.com/android/repository/commandlinetools-mac-${v}_latest.zip`;
    return `https://dl.google.com/android/repository/commandlinetools-linux-${v}_latest.zip`;
}

function _download(url, dest, onProgress) {
    return new Promise((resolve, reject) => {
        const file = require('fs').createWriteStream(dest);
        const get  = url.startsWith('https') ? https : http;
        get.get(url, res => {
            if (res.statusCode === 301 || res.statusCode === 302)
                return _download(res.headers.location, dest, onProgress).then(resolve).catch(reject);
            const total = parseInt(res.headers['content-length'] || '0');
            let received = 0;
            res.on('data', chunk => { received += chunk.length; if (total) onProgress(Math.round(received * 100 / total)); });
            res.pipe(file);
            file.on('finish', () => { file.close(); resolve(); });
        }).on('error', reject);
    });
}

function _copyDir(src, dst) {
    mkdirSync(dst, { recursive: true });
    readdirSync(src).forEach(f => {
        const s = join(src, f), d = join(dst, f);
        statSync(s).isDirectory() ? _copyDir(s, d) : require('fs').copyFileSync(s, d);
    });
}

function _checkNativeLibs() {
    const abis = ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'];
    const jniDir = join(_templateDir(), 'app', 'src', 'main', 'jniLibs');
    const missing = abis.filter(abi => !existsSync(join(jniDir, abi, 'libquickjs.so')));
    if (missing.length) {
        console.error('\n  ✗ Missing libquickjs.so for: ' + missing.join(', '));
        console.error('  The pre-built native library is required to build.');
        console.error('  Go to: https://github.com/rudrachitambare/CtrlScript/actions');
        console.error('  Run "Build Native Libraries" workflow, then git pull.\n');
        process.exit(1);
    }
    tick('Native libs present (' + abis.length + ' ABIs)');
}

function _bar(pct) { const w=20, f=Math.round(pct/100*w); return '█'.repeat(f)+'░'.repeat(w-f); }
function tick(msg) { console.log('  ✓ ' + msg); }
function fail(msg) { console.error('\n  ✗ ' + msg + '\n'); process.exit(1); }
