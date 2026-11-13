import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import './styles.css';
import './landing.css';
import { initTheme } from './theme.jsx';
import { installNativeDownloadLinks, isNativeApp } from './nativeSave.js';

// Inside the Android/iOS app the photographer workspace is the phone interface at /app, unless the
// user chose "Open the full website" there.
if (isNativeApp()) {
  const params = new URLSearchParams(window.location.search);
  try { if (params.get('desktop') === '1') sessionStorage.setItem('fb-desktop', '1'); } catch { /* storage unavailable */ }
  let wantsDesktop = false;
  try { wantsDesktop = sessionStorage.getItem('fb-desktop') === '1'; } catch { /* storage unavailable */ }
  if (!wantsDesktop && ['/', '/login', '/dashboard'].includes(window.location.pathname.replace(/\/+$/, '') || '/')) {
    window.location.replace('/app');
  }
}

initTheme();
installNativeDownloadLinks(import.meta.env.VITE_API_BASE_URL || '');

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
