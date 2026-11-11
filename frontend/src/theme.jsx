import { useEffect, useState } from 'react';

const STORAGE_KEY = 'fb-theme';
const THEMES = ['light', 'dark'];
const listeners = new Set();

function storedTheme() {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    return THEMES.includes(value) ? value : null;
  } catch {
    return null;
  }
}

function systemTheme() {
  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}

export function currentTheme() {
  return document.documentElement.dataset.theme || storedTheme() || systemTheme();
}

/** Call once before React renders so the first paint already uses the right palette. */
export function initTheme() {
  applyTheme(storedTheme() || systemTheme(), false);
  window.matchMedia?.('(prefers-color-scheme: light)').addEventListener?.('change', (event) => {
    if (!storedTheme()) applyTheme(event.matches ? 'light' : 'dark', false);
  });
}

export function applyTheme(theme, remember = true) {
  const next = THEMES.includes(theme) ? theme : 'dark';
  document.documentElement.dataset.theme = next;
  document.documentElement.style.colorScheme = next;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', next === 'light' ? '#f2f2ee' : '#15130f');
  if (remember) {
    try { window.localStorage.setItem(STORAGE_KEY, next); } catch { /* private browsing */ }
  }
  listeners.forEach((listener) => listener(next));
}

export function useTheme() {
  const [theme, setTheme] = useState(currentTheme);
  useEffect(() => {
    listeners.add(setTheme);
    setTheme(currentTheme());
    return () => listeners.delete(setTheme);
  }, []);
  return [theme, (value) => applyTheme(value)];
}

export function ThemeToggle({ className = '' }) {
  const [theme, setTheme] = useTheme();
  const next = theme === 'dark' ? 'light' : 'dark';
  return (
    <button
      type="button"
      className={`theme-toggle ${className}`.trim()}
      onClick={() => setTheme(next)}
      aria-label={`Switch to ${next} mode`}
      title={`Switch to ${next} mode`}
    >
      <span className="theme-toggle-track" aria-hidden="true">
        <svg className="theme-toggle-icon theme-toggle-sun" viewBox="0 0 24 24" width="14" height="14">
          <circle cx="12" cy="12" r="4.2" fill="currentColor" />
          {[0, 45, 90, 135, 180, 225, 270, 315].map((angle) => (
            <line key={angle} x1="12" y1="2.6" x2="12" y2="5.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" transform={`rotate(${angle} 12 12)`} />
          ))}
        </svg>
        <svg className="theme-toggle-icon theme-toggle-moon" viewBox="0 0 24 24" width="14" height="14">
          <path d="M20.2 14.6A8.3 8.3 0 0 1 9.4 3.8a8.3 8.3 0 1 0 10.8 10.8Z" fill="currentColor" />
        </svg>
        <span className="theme-toggle-thumb" />
      </span>
    </button>
  );
}
