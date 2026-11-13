import React from 'react';
import { createRoot } from 'react-dom/client';
import MobileApp from './MobileApp.jsx';
import { initTheme } from '../theme.jsx';
import { installNativeDownloadLinks } from '../nativeSave.js';
import { installKeyboardInsets } from './keyboard.js';
// The website's styles are loaded too: settings screens reuse its panels inside the app frame.
import '../styles.css';
import './mobile.css';

initTheme();
installKeyboardInsets();
installNativeDownloadLinks(import.meta.env.VITE_API_BASE_URL || '');

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <MobileApp />
  </React.StrictMode>,
);
