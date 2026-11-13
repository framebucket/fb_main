# FrameBucket mobile apps (Android APK + iOS)

The Android and iOS apps are a native shell (Capacitor 7) around the live FrameBucket web app, the same idea
as `desktop/`. They currently open the Railway deployment:

    https://fbmain-production.up.railway.app

Because the screens are loaded from the server, **web releases reach the phone apps immediately**. Rebuild
the apps only when something in this folder changes (URL, icon, version, plugins).

What the shell adds on top of the website:

- App icon, splash screen and dark status bar in FrameBucket colours.
- Downloads (gallery ZIPs, album PSD/JPEG, invitation video, UPI QR) open the phone's share sheet, so they can
  be saved to Files/Downloads/Photos or sent on WhatsApp (`frontend/src/nativeSave.js`).
- Photo/video upload uses the phone's picker (gallery or camera).
- Links to other sites (WhatsApp, Maps, Instagram…) open in their own apps; the Android back button goes back.
- A "Can't reach FrameBucket" page with a retry button when offline.

Desktop-only features stay desktop-only: folder sync (`showDirectoryPicker`) is not available on phones.

## Folder layout

| Path | What it is |
| --- | --- |
| `capacitor.config.json` | App id `in.framebucket.app`, name, **server URL**, allowed hosts, splash/status bar |
| `package.json` | Version (`1.0.0`), Capacitor packages, build commands |
| `resources/` | Icon and splash sources (icons for every size are generated from these) |
| `www/` | Offline page; `config.js` is rewritten on every build |
| `scripts/build.mjs` | One script that creates the native projects and builds the APK/AAB/iOS app |
| `android/`, `ios/`, `dist/` | Generated, **not committed** |

## Android APK on Windows (your PC)

One-time setup:

1. Node 20 or newer (you already have it from the desktop build).
2. Install **Android Studio** (https://developer.android.com/studio). Open it once and let it install the SDK.
3. Use the JDK that comes with Android Studio. In PowerShell:

   ```powershell
   [Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")
   [Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
   ```

   Close and reopen PowerShell after this.

Build the test APK:

```powershell
cd C:\Framebucket\fb_main\fb_main\mobile
npm.cmd install
npm.cmd run apk
```

Result: `mobile\dist\FrameBucket-1.0.0-test.apk`. Copy it to a phone (WhatsApp/Drive/USB), open it and allow
"Install unknown apps" when Android asks. The test APK is signed with a debug key, which is fine for testing
but not for the Play Store.

### Signed release APK / Play Store bundle

Create a keystore once and **keep it and its passwords safe and out of git**. Losing it means you can never
update the app on the Play Store.

```powershell
& "$env:JAVA_HOME\bin\keytool" -genkeypair -v -keystore C:\Framebucket\keys\framebucket-release.jks `
  -alias framebucket -keyalg RSA -keysize 2048 -validity 10000
```

Then:

```powershell
$env:FB_ANDROID_KEYSTORE = "C:\Framebucket\keys\framebucket-release.jks"
$env:FB_ANDROID_KEYSTORE_PASSWORD = "<store password>"
$env:FB_ANDROID_KEY_ALIAS = "framebucket"
$env:FB_ANDROID_KEY_PASSWORD = "<key password>"
npm.cmd run apk:release   # dist\FrameBucket-1.0.0.apk
npm.cmd run aab           # dist\FrameBucket-1.0.0.aab  (upload this one to Play Console)
```

## iOS

Apple only allows iOS apps to be built on a Mac with Xcode, so on Windows use the GitHub workflow
(`.github/workflows/mobile.yml`), which runs on a GitHub-hosted Mac.

- **Without an Apple Developer account**: the workflow builds a Simulator app
  (`FrameBucket-1.0.0-ios-simulator.zip`). It runs in the iOS Simulator on any Mac, not on a real iPhone.
- **To install on real iPhones** you need the Apple Developer Program (USD 99/year). With it, add the signing
  secrets listed in the workflow and it also produces `FrameBucket-1.0.0.ipa` (Ad Hoc for registered test
  iPhones, or App Store for TestFlight).

On a Mac you can also do it by hand: `npm install`, `npm run ios:open`, then in Xcode pick your team under
Signing & Capabilities and press Run with your iPhone connected.

## Building in GitHub Actions

Actions → **Mobile apps** → Run workflow. Choose Android, iOS or both; optionally override the server URL.
The APK/IPA/zip appear under the run's **Artifacts**. Pushing a tag like `mobile-v1.0.1` also builds both and
attaches them to a GitHub release (private, like the repo).

Secrets (repo Settings → Secrets and variables → Actions), all optional:

| Secret | Used for |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Release APK/AAB. Create with `certutil -encode framebucket-release.jks ks.txt` (remove the BEGIN/END lines) or `base64 -i framebucket-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | Release APK/AAB |
| `IOS_CERTIFICATE_P12_BASE64`, `IOS_CERTIFICATE_PASSWORD` | Apple Distribution certificate exported from Keychain as .p12 |
| `IOS_PROVISIONING_PROFILE_BASE64` | Ad Hoc or App Store provisioning profile for `in.framebucket.app` |
| `IOS_TEAM_ID` | Your 10-character Apple team id |
| `IOS_EXPORT_METHOD` (variable, not secret) | `release-testing` (Ad Hoc, default) or `app-store-connect` |

Without the Android secrets the workflow still builds the test APK; without the iOS secrets it builds the
Simulator app.

## Making a new version

1. Bump `"version"` in `mobile/package.json` (for example `1.0.1`). The Android versionCode and iOS build
   number are derived from it (`10001`), or set `FB_BUILD_NUMBER`. The Play Store and App Store need a
   higher number every upload.
2. Run `npm.cmd run apk` (or the workflow).

## Pointing at another server

Change `server.url`, `allowNavigation` in `capacitor.config.json` and `www/config.js` (for example to
`https://app.framebucket.in` when production moves), or for a one-off build:

```powershell
$env:FB_APP_URL = "https://staging.example.com"; npm.cmd run apk
```

The backend must allow the app's origin for cookies/CSRF just like the website (it is the same origin, since
the app loads the site itself, so nothing changes on the server).

## Troubleshooting

- `npm : File ... npm.ps1 cannot be loaded`: use `npm.cmd` as above.
- `JAVA_HOME is not set` / `SDK location not found`: redo step 3 and open a new PowerShell window.
- Want a clean rebuild: delete `mobile\android` (or `mobile\ios`); the next build regenerates it.
- Icon changed: replace the files in `resources/` and rebuild.
