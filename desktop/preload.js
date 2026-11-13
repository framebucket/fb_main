// Tells the FrameBucket page it is running inside the desktop app, and lets it match the window
// buttons to its light/dark theme. Nothing else from the computer is exposed to the page.
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('fbDesktop', {
  platform: process.platform,
  setTheme: (theme) => {
    if (theme === 'light' || theme === 'dark') ipcRenderer.send('fb:theme', theme);
  },
});
