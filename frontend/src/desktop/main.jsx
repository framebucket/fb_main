import React from 'react';
import { createRoot } from 'react-dom/client';
import DesktopApp from './DesktopApp.jsx';
import { initTheme } from '../theme.jsx';
// The website's styles are loaded too: settings screens reuse its panels.
import '../styles.css';
import '../mobile/mobile.css';
import './desktop.css';

initTheme();
document.body.classList.add('d-root');
if (window.fbDesktop) document.body.classList.add('d-in-app', `d-os-${window.fbDesktop.platform}`);

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <DesktopApp />
  </React.StrictMode>,
);
