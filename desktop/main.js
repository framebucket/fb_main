// FrameBucket desktop app: a secure window onto the FrameBucket web app.
// The app itself (features, fixes) is loaded live from the server, so web releases reach desktop users
// immediately; rebuild this wrapper only when main.js, config.json or the icon change.
const { app, BrowserWindow, Menu, shell, session, dialog } = require('electron');
const path = require('path');
const config = require('./config.json');

const APP_URL = process.env.FB_APP_URL || config.appUrl;
const APP_ORIGIN = new URL(APP_URL).origin;
const allowedHosts = new Set([new URL(APP_URL).host, ...(config.allowedHosts || [])]);
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

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1360,
    height: 880,
    minWidth: 960,
    minHeight: 640,
    title: 'FrameBucket',
    icon: path.join(__dirname, 'build', 'icon.png'),
    backgroundColor: '#1c1a17',
    show: false,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: true,
    },
  });

  mainWindow.once('ready-to-show', () => mainWindow.show());

  // Links to other sites (WhatsApp, Google Maps, YouTube…) open in the normal browser.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (isAppUrl(url)) return { action: 'allow' };
    openExternally(url);
    return { action: 'deny' };
  });
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (!isAppUrl(url)) {
      event.preventDefault();
      openExternally(url);
    }
  });

  // No connection: show a friendly page with a retry button instead of a blank window.
  mainWindow.webContents.on('did-fail-load', (_event, code, _description, url, isMainFrame) => {
    if (isMainFrame && code !== -3) {
      mainWindow.loadFile(path.join(__dirname, 'offline.html'), { query: { url: url || APP_URL } });
    }
  });

  mainWindow.loadURL(APP_URL);
}

function buildMenu() {
  const isMac = process.platform === 'darwin';
  const template = [
    ...(isMac ? [{ role: 'appMenu' }] : []),
    { role: 'fileMenu' },
    { role: 'editMenu' },
    {
      label: 'View',
      submenu: [
        { label: 'Home', accelerator: 'CmdOrCtrl+H', click: () => mainWindow && mainWindow.loadURL(APP_URL) },
        { role: 'reload' },
        { role: 'forceReload' },
        { type: 'separator' },
        { role: 'resetZoom' },
        { role: 'zoomIn' },
        { role: 'zoomOut' },
        { type: 'separator' },
        { role: 'togglefullscreen' },
      ],
    },
    { role: 'windowMenu' },
    {
      role: 'help',
      submenu: [
        { label: 'framebucket.in', click: () => shell.openExternal('https://www.framebucket.in') },
        { label: 'Open in browser', click: () => shell.openExternal(mainWindow ? mainWindow.webContents.getURL() : APP_URL) },
        { label: `Version ${app.getVersion()}`, enabled: false },
      ],
    },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

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
        if (state === 'interrupted') {
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
    if (process.platform !== 'darwin') app.quit();
  });
}
