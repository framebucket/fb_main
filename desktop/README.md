# FrameBucket desktop app (Windows .exe and macOS .dmg)

An Electron app for FrameBucket's **desktop interface** (`/desktop` on the server; source in
`frontend/desktop.html` + `frontend/src/desktop/`, sharing its screens with the phone app in `frontend/src/mobile/`).

What makes it feel like a native app:
- Its own title bar with the real Windows minimise / maximise / close buttons (macOS: traffic lights), coloured
  to match light or dark mode. The window remembers its size and position.
- Sidebar navigation and Mail-style split views: lists on the left, the selected event, enquiry or customer on the right.
- Centred dialogs, hover states, Esc to close, drag-and-drop photo upload onto a gallery.
- Shortcuts: Ctrl+K search everything, Ctrl+N new (event, enquiry, customer… for the screen you're on),
  Ctrl+1…7 sections, Ctrl+\ sidebar, F5 refresh, Ctrl+, studio settings, Alt+← back.
- Downloads ask where to save and flash the taskbar when done; other sites open in the normal browser.

The interface is loaded from the server, so every web release reaches desktop users at once — **you only
rebuild the installers when this folder changes** (icon, `config.json`, `main.js`, `preload.js`) or to bump
Electron for security. "Classic web layout" (globe icon at the bottom of the sidebar) opens the old website layout.

## Change the server address

Edit `config.json` → `appUrl` (e.g. `https://app.framebucket.in` once you move off Railway) and add the
host to `allowedHosts`. `startPath` is the page the window opens (`/desktop`). Pages outside `allowedHosts` open in the normal browser.

## Build it yourself

You need Node.js 20+ and internet access for `npm install`.

**Windows (.exe installer)** — on a Windows PC:

```bash
cd desktop
npm install
npm run dist:win          # -> desktop/dist/FrameBucket-1.1.0-win-x64.exe
```

**macOS (.dmg)** — must be built on a Mac (Apple's disk-image tools only exist there):

```bash
cd desktop
npm install
npm run dist:mac          # -> desktop/dist/FrameBucket-1.1.0-mac-universal.dmg (Intel + Apple Silicon)
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
