# FrameBucket desktop app (Windows .exe and macOS .dmg)

A small Electron wrapper that opens the live FrameBucket web app in its own window, with the FrameBucket
icon, a start-menu / dock entry and downloads that ask where to save. The app itself is loaded from the
server, so every web release (new features, fixes) reaches desktop users at once — **you only rebuild the
installers when this folder changes** (icon, `config.json`, `main.js`) or to bump Electron for security.

## Change the server address

Edit `config.json` → `appUrl` (e.g. `https://app.framebucket.in` once you move off Railway) and add the
host to `allowedHosts`. Pages outside `allowedHosts` open in the normal browser.

## Build it yourself

You need Node.js 20+ and internet access for `npm install`.

**Windows (.exe installer)** — on a Windows PC:

```bash
cd desktop
npm install
npm run dist:win          # -> desktop/dist/FrameBucket-1.0.0-win-x64.exe
```

**macOS (.dmg)** — must be built on a Mac (Apple's disk-image tools only exist there):

```bash
cd desktop
npm install
npm run dist:mac          # -> desktop/dist/FrameBucket-1.0.0-mac-universal.dmg (Intel + Apple Silicon)
```

**Both, without a Mac** — GitHub Actions: run the workflow **Desktop apps** (Actions tab → Run workflow).
It builds the .exe on Windows and the .dmg on macOS and attaches both to the run as downloads. Pushing a
tag like `desktop-v1.0.1` also builds them and creates a GitHub Release with the files.

Try it without packaging: `npm start`.

## New version

1. Change `version` in `package.json` (e.g. 1.0.1).
2. Build as above (or push the tag `desktop-v1.0.1`).
3. Share the new installer. Installing it over the old one keeps users signed in.

## Signing (recommended before giving it to customers)

Unsigned builds work, but:
- **Windows** shows "Windows protected your PC" — click *More info → Run anyway*. A code-signing
  certificate (OV/EV) removes this; electron-builder signs when `CSC_LINK` / `CSC_KEY_PASSWORD` are set.
- **macOS** blocks apps from unidentified developers — right-click the app → *Open* the first time
  (or System Settings → Privacy & Security → *Open anyway*). If it says the app "is damaged", run
  `xattr -cr /Applications/FrameBucket.app` once in Terminal. An Apple Developer account ($99/year) lets
  you sign and notarize: set `CSC_LINK`, `CSC_KEY_PASSWORD`, `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`
  and `APPLE_TEAM_ID` (as GitHub secrets for the workflow) and add `"notarize": true` under `build.mac`.
