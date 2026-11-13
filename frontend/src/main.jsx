import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import './styles.css';
import './landing.css';
import { initTheme } from './theme.jsx';
import { installNativeDownloadLinks } from './nativeSave.js';

initTheme();
installNativeDownloadLinks(import.meta.env.VITE_API_BASE_URL || '');

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
