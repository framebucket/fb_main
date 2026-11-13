// FrameBucket desktop app: a native window for FrameBucket's desktop interface (/desktop on the server).
// The interface itself is loaded live from the server, so web releases reach desktop users immediately;
// rebuild this wrapper only when main.js, preload.js, config.json or the icon change.
const { app, BrowserWindow, Menu, shell, session, dialog, ipcMain, screen, nativeTheme } = require('electron');
const fs = require('fs');
const path = require('path');
const config = require('./config.json');

const APP_URL = (process.env.FB_APP_URL || config.appUrl).replace(/\/$/, '');
const START_URL = `${APP_URL}${config.startPath || '/desktop'}`;
const APP_ORIGIN = new URL(APP_URL).origin;
const allowedHosts = new Set([new URL(APP_URL).host, ...(config.allowedHosts || [])]);
const isMac = process.platform === 'darwin';
const STATE_FILE = path.join(app.getPath('userData'), 'window-state.json');

// Title bar colours, matching the app's light and dark themes.
const TITLE_BAR = {
  dark: { color: '#120f0c', symbolColor: '#f1ece4', background: '#15130f' },
  light: { color: '#ebe5da', symbolColor: '#2a241c', background: '#f6f2ea' },
};
let theme = nativeTheme.shouldUseDarkColors ? 'dark' : 'light';
let mainWindow;

function isAppUrl(url) {
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' && allowedHosts.has(parsed.host);
  } catch {
    return false;
  }
}

function openExternally(url) {
  if (/^(https?|mailto|tel|whatsapp):/i.test(url)) shell.openExternal(url);
}

// ------------------------------------------------------------------------------------- window size & position

function loadWindowState() {
  try {
    const state = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'));
    // Only reuse the position if it is still on a connected screen.
    const visible = screen.getAllDisplays().some(({ workArea }) => state.x >= workArea.x - 50 && state.y >= workArea.y - 50
      && state.x + 200 <= workArea.x + workArea.width && state.y + 100 <= workArea.y + workArea.height);
    return visible ? state : { width: state.width, height: state.height, maximized: state.maximized };
  } catch {
    return { width: 1360, height: 880 };
  }
}

function saveWindowState() {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  const bounds = mainWindow.getNormalBounds();
  const state = { ...bounds, maximized: mainWindow.isMaximized() };
  try { fs.writeFileSync(STATE_FILE, JSON.stringify(state)); } catch { /* not critical */ }
}

// ------------------------------------------------------------------------------------- window

/** Run an action in the page (navigation, refresh, search) — the same as its keyboard shortcut. */
function send(script) {
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.executeJavaScript(script).catch(() => {});
}
const go = (route) => () => {
  if (mainWindow && !mainWindow.webContents.getURL().startsWith(START_URL)) mainWindow.loadURL(`${START_URL}#${route}`);
  else send(`window.location.hash = ${JSON.stringify(route)}`);
};
const pageEvent = (name) => () => send(`window.dispatchEvent(new Event(${JSON.stringify(name)}))`);

function createWindow() {
  const state = loadWindowState();
  mainWindow = new BrowserWindow({
    x: state.x,
    y: state.y,
    width: state.width || 1360,
    height: state.height || 880,
    minWidth: 1024,
    minHeight: 640,
    title: 'FrameBucket',
    icon: path.join(__dirname, 'build', 'icon.png'),
    backgroundColor: TITLE_BAR[theme].background,
    show: false,
    // Our own title bar (drawn by the page) with the real Windows / macOS window buttons on top of it.
    titleBarStyle: 'hidden',
    ...(isMac
      ? { trafficLightPosition: { x: 14, y: 13 } }
      : { titleBarOverlay: { color: TITLE_BAR[theme].color, symbolColor: TITLE_BAR[theme].symbolColor, height: 40 } }),
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: true,
    },
  });

  if (state.maximized) mainWindow.maximize();
  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.on('close', saveWindowState);

  // Links to other sites (WhatsApp, Google Maps, YouTube…) open in the normal browser.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (isAppUrl(url)) {
      // PDFs and photos open in a plain child window with the normal frame.
      return { action: 'allow', overrideBrowserWindowOptions: { parent: mainWindow, width: 1000, height: 760, autoHideMenuBar: true, titleBarStyle: 'default', titleBarOverlay: false } };
    }
    openExternally(url);
    return { action: 'deny' };
  });
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (!isAppUrl(url) && !url.startsWith('file:')) {
      event.preventDefault();
      openExternally(url);
    }
  });

  // No connection: show a friendly page with a retry button instead of a blank window.
  mainWindow.webContents.on('did-fail-load', (_event, code, _description, url, isMainFrame) => {
    if (isMainFrame && code !== -3) {
      mainWindow.loadFile(path.join(__dirname, 'offline.html'), { query: { url: url || START_URL } });
    }
  });

  mainWindow.loadURL(START_URL);
}

// The page tells us when the user switches light/dark, so the window buttons match.
ipcMain.on('fb:theme', (event, value) => {
  if (!mainWindow || event.sender !== mainWindow.webContents || !TITLE_BAR[value]) return;
  theme = value;
  mainWindow.setBackgroundColor(TITLE_BAR[value].background);
  if (!isMac && typeof mainWindow.setTitleBarOverlay === 'function') {
    mainWindow.setTitleBarOverlay({ color: TITLE_BAR[value].color, symbolColor: TITLE_BAR[value].symbolColor, height: 40 });
  }
});

// ------------------------------------------------------------------------------------- menu & shortcuts

function buildMenu() {
  const template = [
    ...(isMac ? [{ role: 'appMenu' }] : []),
    {
      label: 'File',
      submenu: [
        { label: 'New…', accelerator: 'CmdOrCtrl+N', click: pageEvent('fb-new') },
        { label: 'Search…', accelerator: 'CmdOrCtrl+K', click: pageEvent('fb-search') },
        { type: 'separator' },
        { label: 'Refresh', accelerator: 'F5', click: pageEvent('fb-refresh') },
        { label: 'Refresh', accelerator: 'CmdOrCtrl+R', click: pageEvent('fb-refresh'), visible: false },
        { type: 'separator' },
        isMac ? { role: 'close' } : { role: 'quit', label: 'Exit' },
      ],
    },
    { role: 'editMenu' },
    {
      label: 'Go',
      submenu: [
        { label: 'Home', accelerator: 'CmdOrCtrl+1', click: go('/home') },
        { label: 'Events', accelerator: 'CmdOrCtrl+2', click: go('/events') },
        { label: 'Enquiries', accelerator: 'CmdOrCtrl+3', click: go('/enquiries') },
        { label: 'Customers', accelerator: 'CmdOrCtrl+4', click: go('/customers') },
        { label: 'Calendar', accelerator: 'CmdOrCtrl+5', click: go('/calendar') },
        { label: 'Payments', accelerator: 'CmdOrCtrl+6', click: go('/money') },
        { label: 'Team', accelerator: 'CmdOrCtrl+7', click: go('/team') },
        { type: 'separator' },
        { label: 'Studio settings', accelerator: 'CmdOrCtrl+,', click: go('/more/studio') },
        { type: 'separator' },
        { label: 'Back', accelerator: isMac ? 'Cmd+[' : 'Alt+Left', click: () => mainWindow?.webContents.navigationHistory?.goBack?.() },
        { label: 'Forward', accelerator: isMac ? 'Cmd+]' : 'Alt+Right', click: () => mainWindow?.webContents.navigationHistory?.goForward?.() },
      ],
    },
    {
      label: 'View',
      submenu: [
        { label: 'Toggle sidebar', accelerator: 'CmdOrCtrl+\\', click: () => send("window.dispatchEvent(new KeyboardEvent('keydown', { key: '\\\\', ctrlKey: true }))") },
        { type: 'separator' },
        { role: 'resetZoom' },
        { role: 'zoomIn' },
        { role: 'zoomOut' },
        { type: 'separator' },
        { role: 'togglefullscreen' },
        { type: 'separator' },
        { label: 'Reload app', accelerator: 'CmdOrCtrl+Shift+R', click: () => mainWindow?.webContents.reloadIgnoringCache() },
        ...(app.isPackaged ? [] : [{ role: 'toggleDevTools' }]),
      ],
    },
    { role: 'windowMenu' },
    {
      role: 'help',
      submenu: [
        { label: 'framebucket.in', click: () => shell.openExternal('https://www.framebucket.in') },
        { label: 'Open in browser', click: () => shell.openExternal(mainWindow ? mainWindow.webContents.getURL() : START_URL) },
        { label: `Version ${app.getVersion()}`, enabled: false },
      ],
    },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

// ------------------------------------------------------------------------------------- start

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  app.whenReady().then(() => {
    // Only FrameBucket's own pages may use the camera, notifications, clipboard or folder access
    // (live folder sync needs folder access).
    session.defaultSession.setPermissionRequestHandler((webContents, permission, callback, details) => {
      const origin = details.requestingUrl ? new URL(details.requestingUrl).origin : '';
      callback(origin === APP_ORIGIN);
    });
    session.defaultSession.setPermissionCheckHandler((_webContents, _permission, requestingOrigin) => requestingOrigin === APP_ORIGIN);

    // Ask where to save downloads (galleries, album ZIPs, PSDs, invitation videos).
    session.defaultSession.on('will-download', (_event, item) => {
      item.once('done', (_e, state) => {
        if (state === 'completed') {
          if (mainWindow && !mainWindow.isFocused()) mainWindow.flashFrame(true);
        } else if (state === 'interrupted') {
          dialog.showMessageBox({ type: 'warning', message: `The download of ${item.getFilename()} was interrupted.` });
        }
      });
    });

    buildMenu();
    createWindow();
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on('window-all-closed', () => {
    if (!isMac) app.quit();
  });
}
