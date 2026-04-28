#!/usr/bin/env node
'use strict';

const { execSync, spawn, spawnSync } = require('child_process');
const { existsSync, statSync, writeFileSync, readFileSync, mkdirSync, copyFileSync, readdirSync } = require('fs');
const { join, resolve } = require('path');
const os    = require('os');
const https = require('https');
const http  = require('http');

const VERSION = '1.0.0';
const cmd     = process.argv[2];

// ── Colours ──────────────────────────────────────────
const C = {
    green:  s => `\x1b[32m${s}\x1b[0m`,
    red:    s => `\x1b[31m${s}\x1b[0m`,
    yellow: s => `\x1b[33m${s}\x1b[0m`,
    blue:   s => `\x1b[34m${s}\x1b[0m`,
    cyan:   s => `\x1b[36m${s}\x1b[0m`,
    gray:   s => `\x1b[90m${s}\x1b[0m`,
    bold:   s => `\x1b[1m${s}\x1b[0m`,
};

switch (cmd) {
    case 'setup':   setup();   break;
    case 'create':  create();  break;
    case 'run':     run();     break;
    case 'build':   process.argv[3] === '--help-sign' ? helpSign() : build(); break;
    case 'install': install(); break;
    case 'logs':    logs();    break;
    case 'devices': devices(); break;
    case 'avd':     avd();     break;
    case 'doctor':  doctor();  break;
    case '--version': case '-v': console.log(`csua v${VERSION}`); break;
    default: help();
}

// ─────────────────────────────────────────────────────
// HELP
// ─────────────────────────────────────────────────────

function help() {
    console.log(`
  ${C.bold(`csua v${VERSION}`)} — CtrlScript Android Runtime

  ${C.bold('Commands')}
    ${C.cyan('csua setup')}               Install Android SDK (no Android Studio needed)
    ${C.cyan('csua create <name>')}       Scaffold a new project (2 files, no folders)
    ${C.cyan('csua run')}                 Build → install → hot reload + inline logs
    ${C.cyan('csua build')}               Build release APK
    ${C.cyan('csua build --help-sign')}   How to sign for Play Store
    ${C.cyan('csua install')}             Build debug APK + install (no watcher)
    ${C.cyan('csua avd [name]')}          List or launch Android emulators
    ${C.cyan('csua doctor')}              Check your environment
    ${C.cyan('csua logs')}                Stream filtered logcat
    ${C.cyan('csua devices')}             List connected devices

  ${C.bold('You write one file:')} app.js — runs on Android ${C.gray('(csua run)')} and browser ${C.gray('(<script src="csui.js">)')}
`);
}

function helpSign() {
    console.log(`
  ${C.bold('── Signing for Play Store ──────────────────────────────')}

  ${C.bold('1.')} Generate a keystore (do this once):

       keytool -genkey -v -keystore my-release.keystore \\
         -alias mykey -keyalg RSA -keysize 2048 -validity 10000

  ${C.bold('2.')} Create ${C.cyan('keystore.properties')} next to csua.config.js:

       storeFile=../my-release.keystore
       storePassword=YOUR_STORE_PASSWORD
       keyAlias=mykey
       keyPassword=YOUR_KEY_PASSWORD

  ${C.bold('3.')} Add keystore.properties to .gitignore  ${C.red('← NEVER commit this')}

  ${C.bold('4.')} Build:  ${C.cyan('csua build')}

  ─────────────────────────────────────────────────────
`);
}

// ─────────────────────────────────────────────────────
// DOCTOR — environment health check
// ─────────────────────────────────────────────────────

function doctor() {
    console.log(`\n  ${C.bold('csua doctor')}\n`);
    let allOk = true;

    // Java
    try {
        const v = execSync('java -version 2>&1', { encoding: 'utf8' });
        const ver = (v.match(/version "([^"]+)"/) || ['', '?'])[1];
        tick(`Java ${ver}`);
    } catch (e) {
        warn('Java not found — install Java 17+ from https://adoptium.net');
        allOk = false;
    }

    // Android SDK
    const sdk = _findSdkHome();
    if (sdk) {
        tick(`Android SDK  ${C.gray(sdk)}`);
    } else {
        warn('Android SDK not found — run: csua setup');
        allOk = false;
    }

    // ADB
    try {
        execSync(`${_adb()} version`, { stdio: 'pipe' });
        tick('ADB');
    } catch (e) {
        warn('ADB not found');
        allOk = false;
    }

    // Emulator
    const emu = _findEmulator();
    if (emu) {
        tick(`Emulator binary  ${C.gray(emu)}`);
        const avds = _listAvds(emu);
        if (avds.length) {
            tick(`AVDs (${avds.length}):  ${C.gray(avds.join(', '))}`);
        } else {
            warn('No AVDs found — create one in Android Studio → Device Manager');
        }
    } else {
        _note('Emulator not found (install Android Studio for AVD support)');
    }

    // Connected devices
    try {
        const out = execSync(`${_adb()} devices`, { encoding: 'utf8', stdio: 'pipe' });
        const devs = out.trim().split('\n').slice(1).filter(l => l.includes('\tdevice'));
        if (devs.length) {
            tick(`${devs.length} device${devs.length > 1 ? 's' : ''} connected`);
        } else {
            _note('No devices/emulators connected right now');
        }
    } catch (e) {}

    // Native libs
    const abis = ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'];
    const jniDir = join(_templateDir(), 'app', 'src', 'main', 'jniLibs');
    const missing = abis.filter(a => !existsSync(join(jniDir, a, 'libquickjs.so')));
    if (!missing.length) {
        tick(`Native libs (${abis.length} ABIs)`);
    } else {
        warn(`Missing libquickjs.so for: ${missing.join(', ')}`);
        warn('Run the GitHub Actions "Build Native Libraries" workflow, then git pull');
        allOk = false;
    }

    console.log(allOk
        ? `\n  ${C.green('✓ All good — run:')} csua run\n`
        : `\n  ${C.yellow('Fix the issues above, then re-run:')} csua doctor\n`);
}

// ─────────────────────────────────────────────────────
// AVD — list / launch emulators
// ─────────────────────────────────────────────────────

function avd() {
    const emu = _findEmulator();
    if (!emu) {
        fail('Emulator binary not found.\n  Install Android Studio or make sure $ANDROID_HOME/emulator is in PATH.');
    }

    const avds = _listAvds(emu);
    const target = process.argv[3];

    if (!avds.length) {
        console.log(`\n  ${C.yellow('No AVDs found.')}`);
        console.log('  Create one: Android Studio → Tools → Device Manager → Create Device\n');
        return;
    }

    if (target) {
        if (!avds.includes(target)) fail(`AVD "${target}" not found.\n  Available: ${avds.join(', ')}`);
        _launchAvd(emu, target);
    } else if (avds.length === 1) {
        _launchAvd(emu, avds[0]);
    } else {
        console.log(`\n  ${C.bold('Available AVDs:')}\n`);
        avds.forEach((a, i) => console.log(`  ${C.cyan(i + 1 + ')')} ${a}`));
        console.log(`\n  Launch one: ${C.cyan('csua avd <name>')}\n`);
    }
}

// ─────────────────────────────────────────────────────
// SETUP — install Android SDK
// ─────────────────────────────────────────────────────

async function setup() {
    console.log(`\n  ${C.bold('csua setup')}\n`);

    // Check Java
    try { execSync('java -version', { stdio: 'pipe' }); tick('Java found'); }
    catch (e) { fail('Java 17+ not found. Install from: https://adoptium.net'); }

    // Already have SDK?
    const existing = _findSdkHome();
    if (existing) {
        tick(`Android SDK already at ${existing}`);
        console.log(`\n  ${C.green('All set!')} Run: csua create my-app\n`);
        return;
    }

    // Show drives + space
    console.log('  Available drives:\n');
    const drives = _getDrives();
    drives.forEach((d, i) => {
        const rec = i === 0 ? C.gray('← recommended') : '';
        console.log(`  ${i + 1}) ${d.name}  —  ${d.free} GB free  ${rec}`);
    });

    const rl  = require('readline').createInterface({ input: process.stdin, output: process.stdout });
    const ask = q => new Promise(r => rl.question(q, r));

    const choice = parseInt(await ask('\n  Install on which drive? (number): ')) - 1;
    const drive  = drives[Math.max(0, Math.min(choice, drives.length - 1))];
    const sdkDir = join(drive.name, 'android-sdk');

    const ok = await ask(`\n  Download Android SDK (~1.8 GB) to ${sdkDir}? (Y/n): `);
    if (ok.trim().toLowerCase() === 'n') { rl.close(); return; }
    rl.close();

    mkdirSync(sdkDir, { recursive: true });

    console.log('\n  Downloading command-line tools...');
    const zip = join(sdkDir, 'cmdtools.zip');
    await _download(_sdkUrl(), zip, p => process.stdout.write(`\r  ${_bar(p)} ${p}%`));
    console.log();

    console.log('  Extracting...');
    if (os.platform() === 'win32') {
        execSync(`powershell -command "Expand-Archive -Path '${zip}' -DestinationPath '${sdkDir}' -Force"`, { stdio: 'inherit' });
    } else {
        execSync(`unzip -o "${zip}" -d "${sdkDir}"`, { stdio: 'inherit' });
    }

    const sdkmanager = join(sdkDir, 'cmdline-tools', 'bin', os.platform() === 'win32' ? 'sdkmanager.bat' : 'sdkmanager');
    const env = { ...process.env, ANDROID_HOME: sdkDir, JAVA_OPTS: '-Dfile.encoding=UTF-8' };

    console.log('  Installing platform-tools, build-tools, platform-34...');
    execSync(`"${sdkmanager}" --sdk_root="${sdkDir}" "platform-tools" "build-tools;34.0.0" "platforms;android-34"`, {
        env,
        stdio: ['pipe', 'inherit', 'inherit'],
        input: Buffer.from(Array(20).fill('y\n').join('')),
    });

    // Persist ANDROID_HOME
    const profileLine = `\nexport ANDROID_HOME="${sdkDir}"\nexport PATH="$PATH:$ANDROID_HOME/platform-tools"\n`;
    const profile = join(os.homedir(), os.platform() === 'win32' ? '.bash_profile' : '.zshrc');
    try {
        writeFileSync(profile, (existsSync(profile) ? readFileSync(profile, 'utf8') : '') + profileLine);
    } catch (e) {}

    tick(`SDK installed at ${sdkDir}`);
    console.log(`\n  ${C.yellow('Restart your terminal')}, then: csua create my-app\n`);
}

// ─────────────────────────────────────────────────────
// CREATE — scaffold a new project
// ─────────────────────────────────────────────────────

function create() {
    const name = process.argv[3];
    if (!name) { fail('Usage: csua create <app-name>'); }

    const dst = resolve(name);
    if (existsSync(dst)) { fail(`"${name}" already exists.`); }
    mkdirSync(dst, { recursive: true });

    const pkg      = `com.${name.toLowerCase().replace(/[^a-z0-9]/g, '')}.app`;
    const safeName = name.charAt(0).toUpperCase() + name.slice(1);

    copyFileSync(join(__dirname, '..', 'template', 'app.js'), join(dst, 'app.js'));

    writeFileSync(join(dst, 'csua.config.js'), [
        `module.exports = {`,
        `    name:    '${safeName}',`,
        `    package: '${pkg}',`,
        `    version: '1.0.0',`,
        `};`,
        '',
    ].join('\n'));

    console.log(`
  ${C.green('✓')} Created ${C.bold(name + '/')}

  ${C.gray('app.js')}          ← your entire app (same file runs on Android + browser)
  ${C.gray('csua.config.js')}  ← package name + version

  ${C.cyan(`cd ${name} && csua run`)}
`);
}

// ─────────────────────────────────────────────────────
// RUN — build + install + hot reload + inline logs
// ─────────────────────────────────────────────────────

function run() {
    _requireProject();
    _ensureDevice();

    console.log(`\n  ${C.bold('Building...')}\n`);
    _build('debug');

    const apk    = join(_templateDir(), 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
    const pkg    = _readConfig().package || 'com.csua.app';
    const appJs  = resolve('app.js');
    const csuaJs = join(__dirname, '..', 'csua.js');

    execSync(`${_adb()} install -r "${apk}"`, { stdio: 'inherit' });
    tick('Installed');

    // Launch the app
    try {
        execSync(`${_adb()} shell monkey -p ${pkg} -c android.intent.category.LAUNCHER 1`, { stdio: 'pipe' });
        tick('App launched');
    } catch (e) {}

    // Seed hot reload dir
    try {
        execSync(`${_adb()} shell mkdir -p /sdcard/csua`, { stdio: 'pipe' });
        execSync(`${_adb()} push "${appJs}"  /sdcard/csua/app.js`,  { stdio: 'pipe' });
        execSync(`${_adb()} push "${csuaJs}" /sdcard/csua/csua.js`, { stdio: 'pipe' });
    } catch (e) {}

    console.log(`\n  ${C.green('Hot reload active')} — save app.js to reload instantly`);
    console.log(`  ${C.gray('Ctrl+C to stop')}\n`);

    // Inline logcat (filtered, coloured)
    const logProc = spawn(_adb(), ['logcat', '-v', 'tag', 'CSUA:V', 'AndroidRuntime:E', '*:S'], {
        stdio: ['ignore', 'pipe', 'pipe'],
    });
    logProc.stdout.on('data', d => {
        const line = d.toString().trim();
        if (!line) return;
        if (line.includes('E/')) process.stdout.write(C.red('  [err] ') + C.gray(line) + '\n');
        else                     process.stdout.write(C.gray('  [log] ') + line + '\n');
    });

    // Hot reload watcher
    let lastApp  = statSync(appJs).mtimeMs;
    let lastCsua = statSync(csuaJs).mtimeMs;

    setInterval(() => {
        try {
            const at = statSync(appJs).mtimeMs, ct = statSync(csuaJs).mtimeMs;
            if (at <= lastApp && ct <= lastCsua) return;
            lastApp = at; lastCsua = ct;

            process.stdout.write(`  ${C.cyan(ts())} pushing...`);
            execSync(`${_adb()} push "${appJs}"  /sdcard/csua/app.js`,  { stdio: 'pipe' });
            execSync(`${_adb()} push "${csuaJs}" /sdcard/csua/csua.js`, { stdio: 'pipe' });
            execSync(`${_adb()} shell am broadcast -a com.csua.RELOAD`, { stdio: 'pipe' });
            process.stdout.write(` ${C.green('reloaded')}\n`);
        } catch (e) {
            process.stdout.write(` ${C.red('failed — is device still connected?')}\n`);
        }
    }, 300);

    process.on('SIGINT', () => { logProc.kill(); process.exit(0); });
}

// ─────────────────────────────────────────────────────
// BUILD — release APK
// ─────────────────────────────────────────────────────

function build() {
    _requireProject();
    _checkNativeLibs();

    const signed = existsSync(resolve('keystore.properties'));
    console.log(`\n  ${C.bold('Building release APK')} ${signed ? C.green('(signed)') : C.yellow('(debug-signed — not for Play Store)')}\n`);
    if (!signed) console.log(`  ${C.gray('Tip: csua build --help-sign to set up Play Store signing')}\n`);

    _build('release');

    const apk = join(_templateDir(), 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
    if (existsSync(apk)) {
        const mb = (statSync(apk).size / 1048576).toFixed(1);
        tick(`APK ready (${mb} MB)`);
        console.log(`\n  ${C.gray('Path:')}    ${apk}`);
        console.log(`  ${C.gray('Install:')} ${_adb()} install -r "${apk}"\n`);
    }
}

// ─────────────────────────────────────────────────────
// INSTALL — debug build + install only
// ─────────────────────────────────────────────────────

function install() {
    _requireProject();
    _ensureDevice();
    _checkNativeLibs();
    _copyAssets();

    execSync(`${_gradlew()} assembleDebug`, { cwd: _templateDir(), stdio: 'inherit' });
    const apk = join(_templateDir(), 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
    execSync(`${_adb()} install -r "${apk}"`, { stdio: 'inherit' });
    tick('Installed on device');
}

// ─────────────────────────────────────────────────────
// LOGS — filtered logcat
// ─────────────────────────────────────────────────────

function logs() {
    console.log(`\n  ${C.bold('Logcat')} ${C.gray('(CSUA tag + errors — Ctrl+C to stop)')}\n`);
    const child = spawn(_adb(), ['logcat', '-v', 'tag', 'CSUA:V', 'AndroidRuntime:E', '*:S'], {
        stdio: ['ignore', 'pipe', 'pipe'],
    });
    child.stdout.on('data', d => {
        d.toString().split('\n').forEach(line => {
            if (!line.trim()) return;
            if (line.includes('E/')) console.log(C.red('  [err] ') + line);
            else                     console.log(C.gray('  [log] ') + line);
        });
    });
    child.stderr.on('data', d => process.stderr.write(d));
    process.on('SIGINT', () => { child.kill(); process.exit(0); });
}

// ─────────────────────────────────────────────────────
// DEVICES — list connected ADB devices
// ─────────────────────────────────────────────────────

function devices() {
    console.log(`\n  ${C.bold('Connected devices:')}\n`);
    try {
        const out   = execSync(`${_adb()} devices -l`, { encoding: 'utf8' });
        const lines = out.trim().split('\n').slice(1).filter(l => l.trim() && !l.startsWith('*'));
        if (!lines.length) {
            console.log(`  ${C.yellow('None found.')}\n  Connect a device via USB with USB Debugging on, or run: ${C.cyan('csua avd')}\n`);
            return;
        }
        lines.forEach(l => console.log(`  ${C.green('✓')} ${l.trim()}`));
        console.log();
    } catch (e) { fail('ADB not found. Run: csua setup'); }
}

// ─────────────────────────────────────────────────────
// SDK / EMULATOR DETECTION
// ─────────────────────────────────────────────────────

function _findSdkHome() {
    // 1. Env vars
    for (const v of ['ANDROID_HOME', 'ANDROID_SDK_ROOT']) {
        const p = process.env[v];
        if (p && existsSync(join(p, 'platform-tools'))) return p;
    }
    // 2. Android Studio default locations
    for (const p of _sdkCandidates()) {
        if (existsSync(join(p, 'platform-tools'))) return p;
    }
    return null;
}

function _sdkCandidates() {
    const home = os.homedir();
    if (os.platform() === 'win32') return [
        join(process.env.LOCALAPPDATA || '', 'Android', 'Sdk'),
        join(home, 'AppData', 'Local', 'Android', 'Sdk'),
        'C:\\Android\\Sdk',
    ];
    if (os.platform() === 'darwin') return [
        join(home, 'Library', 'Android', 'sdk'),
    ];
    return [
        join(home, 'Android', 'Sdk'),
        join(home, 'android-sdk'),
    ];
}

function _findEmulator() {
    const bin = os.platform() === 'win32' ? 'emulator.exe' : 'emulator';
    // From SDK home
    const sdk = _findSdkHome();
    if (sdk) {
        const p = join(sdk, 'emulator', bin);
        if (existsSync(p)) return p;
    }
    // Android Studio bundled
    for (const base of _studioLocations()) {
        const p = join(base, 'emulator', bin);
        if (existsSync(p)) return p;
    }
    // PATH
    try { execSync(`${bin} -list-avds`, { stdio: 'pipe' }); return bin; } catch (e) {}
    return null;
}

function _studioLocations() {
    const home = os.homedir();
    if (os.platform() === 'win32') return [
        'C:\\Program Files\\Android\\Android Studio',
        join(home, 'AppData', 'Local', 'Android', 'Sdk'),
    ];
    if (os.platform() === 'darwin') return [
        '/Applications/Android Studio.app/Contents',
        join(home, 'Library', 'Android', 'sdk'),
    ];
    return [
        join(home, 'android-studio'),
        '/opt/android-studio',
    ];
}

function _listAvds(emuBin) {
    try {
        return execSync(`"${emuBin}" -list-avds`, { encoding: 'utf8', stdio: 'pipe' })
            .trim().split('\n').filter(Boolean);
    } catch (e) { return []; }
}

function _launchAvd(emuBin, name) {
    console.log(`\n  Launching ${C.cyan(name)}...`);
    spawn(emuBin, ['-avd', name], { detached: true, stdio: 'ignore' }).unref();

    process.stdout.write('  Waiting for boot');
    for (let i = 0; i < 90; i++) {
        _sleep(2000);
        process.stdout.write('.');
        try {
            const ready = execSync(`${_adb()} shell getprop sys.boot_completed`, {
                encoding: 'utf8', stdio: 'pipe',
            }).trim();
            if (ready === '1') { console.log(` ${C.green('ready!')}\n`); return true; }
        } catch (e) {}
    }
    console.log(` ${C.yellow('timed out — emulator may still be booting')}\n`);
    return false;
}

// ─────────────────────────────────────────────────────
// INTERNAL HELPERS
// ─────────────────────────────────────────────────────

function _adb() {
    const bin = os.platform() === 'win32' ? 'adb.exe' : 'adb';
    const sdk  = _findSdkHome();
    if (sdk) {
        const full = join(sdk, 'platform-tools', bin);
        if (existsSync(full)) return `"${full}"`;
    }
    return bin;
}

function _gradlew() {
    return os.platform() === 'win32'
        ? join(_templateDir(), 'gradlew.bat')
        : join(_templateDir(), 'gradlew');
}

function _templateDir() { return join(__dirname, '..', 'template'); }

function _requireProject() {
    if (!existsSync(resolve('app.js')))
        fail('No app.js found. Run from your project folder or: csua create <name>');
}

function _readConfig() {
    const f = resolve('csua.config.js');
    if (!existsSync(f)) return {};
    try { return require(f); } catch (e) { return {}; }
}

function _ensureDevice() {
    const devs = _connectedDevices();
    if (devs.length) { tick(`Device: ${devs[0].split('\t')[0]}`); return; }

    console.log(`  ${C.yellow('No device connected.')} Looking for emulators...\n`);
    const emu  = _findEmulator();
    if (!emu) {
        fail('No device and no emulator found.\n  Options:\n  1. Connect a device via USB with USB Debugging on\n  2. Install Android Studio and create an AVD\n  3. Run: csua avd');
    }
    const avds = _listAvds(emu);
    if (!avds.length) {
        fail('No AVDs found.\n  Create one in Android Studio → Tools → Device Manager → Create Device');
    }
    _launchAvd(emu, avds[0]);

    const after = _connectedDevices();
    if (!after.length) fail('Emulator launched but ADB cannot see it yet. Try csua run again in a moment.');
    tick(`Device: ${after[0].split('\t')[0]}`);
}

function _connectedDevices() {
    try {
        return execSync(`${_adb()} devices`, { encoding: 'utf8', stdio: 'pipe' })
            .trim().split('\n').slice(1).filter(l => l.includes('\tdevice'));
    } catch (e) { return []; }
}

function _build(variant) {
    _checkNativeLibs();
    _copyAssets();
    const task = variant === 'release' ? 'assembleRelease' : 'assembleDebug';
    const gradlew = _gradlew();
    const env = { ...process.env };
    const sdk = _findSdkHome();
    if (sdk) env.ANDROID_HOME = sdk;
    execSync(`"${gradlew}" ${task}`, { cwd: _templateDir(), stdio: 'inherit', env });
}

function _copyAssets() {
    const dst = join(_templateDir(), 'app', 'src', 'main', 'assets');
    mkdirSync(dst, { recursive: true });
    copyFileSync(resolve('app.js'), join(dst, 'app.js'));
    copyFileSync(join(__dirname, '..', 'csua.js'), join(dst, 'csua.js'));
}

function _checkNativeLibs() {
    const abis    = ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'];
    const jniDir  = join(_templateDir(), 'app', 'src', 'main', 'jniLibs');
    const missing = abis.filter(a => !existsSync(join(jniDir, a, 'libquickjs.so')));
    if (missing.length) {
        console.error(C.red(`\n  ✗ Missing libquickjs.so for: ${missing.join(', ')}`));
        console.error('  Go to: https://github.com/rudrachitambare/CtrlScript/actions');
        console.error('  Run the "Build Native Libraries" workflow, then git pull.\n');
        process.exit(1);
    }
    tick(`Native libs (${abis.length} ABIs)`);
}

// ─────────────────────────────────────────────────────
// SDK DOWNLOAD
// ─────────────────────────────────────────────────────

function _sdkUrl() {
    const v = '11076708';
    const p = os.platform();
    if (p === 'win32')  return `https://dl.google.com/android/repository/commandlinetools-win-${v}_latest.zip`;
    if (p === 'darwin') return `https://dl.google.com/android/repository/commandlinetools-mac-${v}_latest.zip`;
    return `https://dl.google.com/android/repository/commandlinetools-linux-${v}_latest.zip`;
}

function _download(url, dest, onProgress) {
    return new Promise((res, rej) => {
        const file = require('fs').createWriteStream(dest);
        const get  = url.startsWith('https') ? https : http;
        get.get(url, resp => {
            if (resp.statusCode === 301 || resp.statusCode === 302)
                return _download(resp.headers.location, dest, onProgress).then(res).catch(rej);
            const total = parseInt(resp.headers['content-length'] || '0');
            let got = 0;
            resp.on('data', c => { got += c.length; if (total) onProgress(Math.round(got * 100 / total)); });
            resp.pipe(file);
            file.on('finish', () => { file.close(); res(); });
        }).on('error', rej);
    });
}

function _getDrives() {
    if (os.platform() !== 'win32') return [{ name: '/', free: '?' }];
    try {
        return execSync('wmic logicaldisk get name,freespace /format:csv', { encoding: 'utf8' })
            .trim().split('\n').slice(1)
            .map(l => l.trim().split(','))
            .filter(p => p.length >= 3 && p[2])
            .map(p => ({ name: p[1] + '\\', free: Math.round(parseInt(p[2]) / 1073741824) }))
            .filter(d => d.free > 0)
            .sort((a, b) => b.free - a.free);
    } catch (e) { return [{ name: 'C:\\', free: '?' }]; }
}

// ─────────────────────────────────────────────────────
// UTILITIES
// ─────────────────────────────────────────────────────

function _sleep(ms) {
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

function ts() { return new Date().toLocaleTimeString(); }
function _bar(pct) { const w=20,f=Math.round(pct/100*w); return '█'.repeat(f)+'░'.repeat(w-f); }

function tick(msg)  { console.log(`  ${C.green('✓')} ${msg}`); }
function warn(msg)  { console.log(`  ${C.yellow('!')} ${msg}`); }
function _note(msg) { console.log(`  ${C.gray('·')} ${msg}`); }
function fail(msg)  { console.error(`\n  ${C.red('✗')} ${msg}\n`); process.exit(1); }
