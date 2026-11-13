// FrameBucket mobile build helper. Works on Windows, macOS and Linux.
//
//   node scripts/build.mjs prepare android|ios   create/refresh the native project (icons, versions, config)
//   node scripts/build.mjs apk                   Android test APK (debug-signed, installs directly on phones)
//   node scripts/build.mjs apk-release           Android release APK, signed with your keystore (FB_ANDROID_* env)
//   node scripts/build.mjs aab                   Android App Bundle for the Play Store (FB_ANDROID_* env)
//   node scripts/build.mjs ios-sim               iOS Simulator build (macOS + Xcode only)
//
// Optional env:
//   FB_APP_URL       point the app at another server (default: server.url in capacitor.config.json)
//   FB_BUILD_NUMBER  versionCode / CFBundleVersion (default: derived from package.json version)
//
// The android/ and ios/ folders are generated here and are not committed: every change to the app
// lives in capacitor.config.json, resources/, www/ or this script, so a fresh checkout always rebuilds the same app.
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const isWindows = process.platform === 'win32';
const pkg = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'));
const config = JSON.parse(readFileSync(path.join(root, 'capacitor.config.json'), 'utf8'));
const appUrl = (process.env.FB_APP_URL || config.server.url).replace(/\/$/, '');
const version = pkg.version;
const buildNumber = Number(process.env.FB_BUILD_NUMBER) || versionToCode(version);
const dist = path.join(root, 'dist');
const ICON_BG = '#f1ece4';
const SPLASH_BG = '#f1ece4';

function versionToCode(v) {
  const [major = 0, minor = 0, patch = 0] = v.split('.').map((part) => parseInt(part, 10) || 0);
  return major * 10000 + minor * 100 + patch;
}

function run(command, args, options = {}) {
  console.log(`\n> ${command} ${args.join(' ')}`);
  // On Windows the command goes through cmd.exe, so arguments with spaces must be quoted.
  const finalArgs = isWindows ? args.map((arg) => (/[\s"&|<>^]/.test(arg) ? `"${arg.replace(/"/g, '\\"')}"` : arg)) : args;
  const result = spawnSync(command, finalArgs, { stdio: 'inherit', shell: isWindows, cwd: root, ...options });
  if (result.status !== 0) {
    console.error(`\nFailed: ${command} ${args.join(' ')}`);
    process.exit(result.status || 1);
  }
}

function edit(file, change) {
  if (!existsSync(file)) return;
  const before = readFileSync(file, 'utf8');
  const after = change(before);
  if (after !== before) writeFileSync(file, after);
}

function fail(message) {
  console.error(`\n${message}`);
  process.exit(1);
}

function writeWebConfig() {
  writeFileSync(path.join(root, 'www', 'config.js'), `window.FB_APP_URL = ${JSON.stringify(appUrl)};\n`);
}

// cap sync copies capacitor.config.json into the native project; FB_APP_URL overrides it there.
function applyUrlOverride(nativeConfigFile) {
  edit(nativeConfigFile, (text) => {
    const json = JSON.parse(text);
    json.server = { ...json.server, url: appUrl };
    const host = new URL(appUrl).host;
    json.server.allowNavigation = Array.from(new Set([host, ...(json.server.allowNavigation || [])]));
    return JSON.stringify(json, null, 2);
  });
}

function patchAndroid() {
  // Shrink the app when the keyboard opens, so forms stay visible above it.
  edit(path.join(root, 'android', 'app', 'src', 'main', 'AndroidManifest.xml'), (text) => {
    if (/windowSoftInputMode=/.test(text)) return text.replace(/android:windowSoftInputMode="[^"]*"/, 'android:windowSoftInputMode="adjustResize"');
    return text.replace(/<activity\b/, '<activity\n            android:windowSoftInputMode="adjustResize"');
  });
  edit(path.join(root, 'android', 'app', 'build.gradle'), (text) => text
    .replace(/versionCode\s+\d+/, `versionCode ${buildNumber}`)
    .replace(/versionName\s+"[^"]*"/, `versionName "${version}"`));
  applyUrlOverride(path.join(root, 'android', 'app', 'src', 'main', 'assets', 'capacitor.config.json'));
}

function patchIos() {
  edit(path.join(root, 'ios', 'App', 'App.xcodeproj', 'project.pbxproj'), (text) => text
    .replace(/MARKETING_VERSION = [^;]+;/g, `MARKETING_VERSION = ${version};`)
    .replace(/CURRENT_PROJECT_VERSION = [^;]+;/g, `CURRENT_PROJECT_VERSION = ${buildNumber};`));
  const usage = {
    NSCameraUsageDescription: 'FrameBucket uses the camera when you choose to take a photo to upload.',
    NSPhotoLibraryUsageDescription: 'FrameBucket lets you pick photos and videos to upload to your events.',
    NSPhotoLibraryAddUsageDescription: 'FrameBucket saves the photos, albums and invitations you download.',
  };
  edit(path.join(root, 'ios', 'App', 'App', 'Info.plist'), (text) => {
    let out = text;
    const lastDict = out.lastIndexOf('</dict>');
    let additions = '';
    for (const [key, value] of Object.entries(usage)) {
      if (!out.includes(`<key>${key}</key>`)) additions += `\t<key>${key}</key>\n\t<string>${value}</string>\n`;
    }
    if (!out.includes('<key>ITSAppUsesNonExemptEncryption</key>')) {
      additions += '\t<key>ITSAppUsesNonExemptEncryption</key>\n\t<false/>\n';
    }
    if (additions) out = out.slice(0, lastDict) + additions + out.slice(lastDict);
    return out;
  });
  applyUrlOverride(path.join(root, 'ios', 'App', 'App', 'capacitor.config.json'));
}

function prepare(platform) {
  if (!['android', 'ios'].includes(platform)) fail('Use: prepare android | prepare ios');
  if (!existsSync(path.join(root, 'node_modules', '@capacitor', 'cli'))) fail('Run "npm install" (or "npm.cmd install" in PowerShell) first.');
  console.log(`FrameBucket ${version} (build ${buildNumber}) -> ${appUrl}`);
  writeWebConfig();
  if (!existsSync(path.join(root, platform))) {
    run('npx', platform === 'ios' ? ['cap', 'add', 'ios', '--packagemanager', 'SPM'] : ['cap', 'add', 'android']);
  }
  run('npx', ['capacitor-assets', 'generate', `--${platform}`, '--assetPath', 'resources',
    '--iconBackgroundColor', ICON_BG, '--iconBackgroundColorDark', ICON_BG,
    '--splashBackgroundColor', SPLASH_BG, '--splashBackgroundColorDark', SPLASH_BG]);
  run('npx', ['cap', 'sync', platform]);
  if (platform === 'android') patchAndroid();
  else patchIos();
}

// Gradle needs the Android SDK path. Use ANDROID_HOME / ANDROID_SDK_ROOT, else Android Studio's default folder,
// and write it to android/local.properties so a fresh PowerShell window is not required.
function ensureAndroidSdk() {
  const candidates = [
    process.env.ANDROID_HOME,
    process.env.ANDROID_SDK_ROOT,
    process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk'),
    process.env.HOME && path.join(process.env.HOME, 'Library', 'Android', 'sdk'),
    process.env.HOME && path.join(process.env.HOME, 'Android', 'Sdk'),
  ].filter(Boolean);
  const sdk = candidates.find((dir) => existsSync(path.join(dir, 'platform-tools')) || existsSync(path.join(dir, 'platforms')));
  if (!sdk) {
    fail('Android SDK not found. Open Android Studio > More Actions > SDK Manager, note the "Android SDK Location", '
      + 'install an Android SDK Platform there, then set ANDROID_HOME to that folder (see README).\nLooked in:\n  '
      + candidates.join('\n  '));
  }
  const escaped = sdk.replace(/\\/g, '\\\\').replace(/:/g, '\\:');
  writeFileSync(path.join(root, 'android', 'local.properties'), `sdk.dir=${escaped}\n`);
  console.log(`Android SDK: ${sdk}`);
}

// Capacitor 7 needs Java 21. Use JAVA_HOME if it is 21+, else the JDK bundled with Android Studio (jbr).
function javaMajor(home) {
  try {
    const release = readFileSync(path.join(home, 'release'), 'utf8');
    const match = /JAVA_VERSION="(\d+)/.exec(release);
    return match ? Number(match[1]) : 0;
  } catch {
    return 0;
  }
}

function listDirs(parent) {
  try {
    return readdirSync(parent, { withFileTypes: true }).filter((d) => d.isDirectory()).map((d) => path.join(parent, d.name));
  } catch {
    return [];
  }
}

// The Android build asks Gradle for exactly Java 21 (a newer JDK such as 23 does not count), so look for a 21
// in the usual places: JAVA_HOME, Android Studio's bundled jbr, and installed JDKs (Oracle, Temurin, Microsoft, Zulu…).
function findJdk() {
  const programFiles = [process.env.ProgramFiles, process.env['ProgramFiles(x86)'], process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Programs')].filter(Boolean);
  const vendorDirs = ['Java', 'Eclipse Adoptium', 'Microsoft', 'Zulu', 'Amazon Corretto', 'BellSoft', 'OpenJDK'];
  const candidates = [
    process.env.FB_JAVA_HOME,
    process.env.JAVA_HOME,
    ...programFiles.map((dir) => path.join(dir, 'Android', 'Android Studio', 'jbr')),
    ...programFiles.map((dir) => path.join(dir, 'Android Studio', 'jbr')),
    ...programFiles.flatMap((dir) => vendorDirs.flatMap((vendor) => listDirs(path.join(dir, vendor)))),
    process.env.USERPROFILE && path.join(process.env.USERPROFILE, '.jdks'),
    ...(process.env.USERPROFILE ? listDirs(path.join(process.env.USERPROFILE, '.jdks')) : []),
    '/Applications/Android Studio.app/Contents/jbr/Contents/Home',
    ...listDirs('/Library/Java/JavaVirtualMachines').map((dir) => path.join(dir, 'Contents', 'Home')),
    process.env.HOME && path.join(process.env.HOME, 'android-studio', 'jbr'),
    '/opt/android-studio/jbr',
  ].filter(Boolean);
  const seen = new Set();
  const found = [];
  for (const home of candidates) {
    const key = path.resolve(home).toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    const major = javaMajor(home);
    if (major) found.push({ home, major });
  }
  const jdk = found.find((entry) => entry.major === 21);
  if (!jdk) {
    fail('Java 21 not found (the Android build needs exactly 21; newer versions such as 23 are not accepted).\n'
      + 'Install "Eclipse Temurin 21 (JDK)" from https://adoptium.net/temurin/releases/?version=21 '
      + '(Windows x64 .msi), then run the build again. Or set FB_JAVA_HOME to a JDK 21 folder.\nJDKs found:\n  '
      + (found.length ? found.map((entry) => `${entry.home} (Java ${entry.major})`).join('\n  ') : '(none)'));
  }
  console.log(`Java ${jdk.major}: ${jdk.home}`);
  return jdk.home;
}

function gradle(task, extraArgs = []) {
  ensureAndroidSdk();
  const androidDir = path.join(root, 'android');
  const wrapper = isWindows ? 'gradlew.bat' : './gradlew';
  const jdk = findJdk();
  const env = { ...process.env, JAVA_HOME: jdk, PATH: `${path.join(jdk, 'bin')}${path.delimiter}${process.env.PATH || ''}` };
  run(wrapper, [task, '--no-daemon', `-Dorg.gradle.java.installations.paths=${jdk}`,
    '-Dorg.gradle.java.installations.auto-download=false', ...extraArgs], { cwd: androidDir, env });
}

function signingArgs() {
  const store = process.env.FB_ANDROID_KEYSTORE;
  const storePassword = process.env.FB_ANDROID_KEYSTORE_PASSWORD;
  const alias = process.env.FB_ANDROID_KEY_ALIAS;
  const keyPassword = process.env.FB_ANDROID_KEY_PASSWORD || storePassword;
  if (!store || !storePassword || !alias) {
    fail('Release builds need FB_ANDROID_KEYSTORE, FB_ANDROID_KEYSTORE_PASSWORD and FB_ANDROID_KEY_ALIAS (see README).');
  }
  if (!existsSync(store)) fail(`Keystore not found: ${store}`);
  return [
    `-Pandroid.injected.signing.store.file=${path.resolve(store)}`,
    `-Pandroid.injected.signing.store.password=${storePassword}`,
    `-Pandroid.injected.signing.key.alias=${alias}`,
    `-Pandroid.injected.signing.key.password=${keyPassword}`,
  ];
}

function deliver(from, name) {
  if (!existsSync(from)) fail(`Build finished but ${from} is missing.`);
  mkdirSync(dist, { recursive: true });
  const to = path.join(dist, name);
  copyFileSync(from, to);
  console.log(`\nDone: ${to}`);
}

const outputs = path.join(root, 'android', 'app', 'build', 'outputs');
const [command, arg] = process.argv.slice(2);
switch (command) {
  case 'prepare':
    prepare(arg);
    break;
  case 'apk':
    prepare('android');
    gradle('assembleDebug');
    deliver(path.join(outputs, 'apk', 'debug', 'app-debug.apk'), `FrameBucket-${version}-test.apk`);
    break;
  case 'apk-release':
    prepare('android');
    gradle('assembleRelease', signingArgs());
    deliver(path.join(outputs, 'apk', 'release', 'app-release.apk'), `FrameBucket-${version}.apk`);
    break;
  case 'aab':
    prepare('android');
    gradle('bundleRelease', signingArgs());
    deliver(path.join(outputs, 'bundle', 'release', 'app-release.aab'), `FrameBucket-${version}.aab`);
    break;
  case 'ios-sim': {
    if (process.platform !== 'darwin') fail('iOS builds need a Mac with Xcode (or the GitHub workflow).');
    prepare('ios');
    const derived = path.join(root, 'ios', 'build');
    run('xcodebuild', ['-project', 'ios/App/App.xcodeproj', '-scheme', 'App', '-configuration', 'Release',
      '-sdk', 'iphonesimulator', '-destination', 'generic/platform=iOS Simulator',
      '-derivedDataPath', derived, 'CODE_SIGNING_ALLOWED=NO', 'build']);
    mkdirSync(dist, { recursive: true });
    const appPath = path.join(derived, 'Build', 'Products', 'Release-iphonesimulator', 'App.app');
    const zip = path.join(dist, `FrameBucket-${version}-ios-simulator.zip`);
    run('ditto', ['-c', '-k', '--keepParent', appPath, zip]);
    console.log(`\nDone: ${zip}`);
    break;
  }
  default:
    fail('Commands: prepare android|ios, apk, apk-release, aab, ios-sim');
}
