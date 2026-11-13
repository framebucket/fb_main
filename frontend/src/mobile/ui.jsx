import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { closeOverlay, goBack, openOverlay } from './router.js';

// ------------------------------------------------------------------------------------- icons

const ICONS = {
  home: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1Z',
  calendar: 'M4 6.5A1.5 1.5 0 0 1 5.5 5h13A1.5 1.5 0 0 1 20 6.5v12a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5ZM4 10h16M8 3v4M16 3v4',
  inbox: 'M4 13.5 6.2 5.6A1.5 1.5 0 0 1 7.6 4.5h8.8a1.5 1.5 0 0 1 1.4 1.1L20 13.5V18.5A1.5 1.5 0 0 1 18.5 20h-13A1.5 1.5 0 0 1 4 18.5ZM4 13.5h4.5l1.2 2h4.6l1.2-2H20',
  wallet: 'M4 7.5A2.5 2.5 0 0 1 6.5 5H18v3M4 7.5V17a2 2 0 0 0 2 2h13a1 1 0 0 0 1-1v-9a1 1 0 0 0-1-1H6.5A2.5 2.5 0 0 1 4 7.5ZM16 13.5h.01',
  grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  back: 'M15 5l-7 7 7 7',
  chevron: 'M9 6l6 6-6 6',
  down: 'M6 9l6 6 6-6',
  plus: 'M12 5v14M5 12h14',
  close: 'M6 6l12 12M18 6 6 18',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  phone: 'M6.6 3.5h2.6l1.5 4-2 1.3a11 11 0 0 0 6.5 6.5l1.3-2 4 1.5v2.6A2 2 0 0 1 18.5 20 15 15 0 0 1 4 5.5a2 2 0 0 1 2.6-2Z',
  chat: 'M4 18.5 5.3 15A7.5 7.5 0 1 1 8.6 18Z',
  pin: 'M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0C18.5 15.4 12 21 12 21Zm0-8.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z',
  qr: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2v2h-2zM18 18h2v2h-2zM18 14h2M14 18v2',
  upload: 'M12 16V4M7 9l5-5 5 5M4 16v2.5A1.5 1.5 0 0 0 5.5 20h13a1.5 1.5 0 0 0 1.5-1.5V16',
  download: 'M12 4v12M7 11l5 5 5-5M4 20h16',
  folder: 'M3.5 7A1.5 1.5 0 0 1 5 5.5h4.2l2 2.2H19A1.5 1.5 0 0 1 20.5 9.2v8.3A1.5 1.5 0 0 1 19 19H5a1.5 1.5 0 0 1-1.5-1.5Z',
  image: 'M4 6.5A1.5 1.5 0 0 1 5.5 5h13A1.5 1.5 0 0 1 20 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5ZM4 15.5l4.5-4.5 4 4 2.5-2.5 5 5M15.5 9.5h.01',
  camera: 'M4 8.5A1.5 1.5 0 0 1 5.5 7h2.3l1.6-2h5.2l1.6 2h2.3A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5ZM12 16.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z',
  video: 'M4 7.5A1.5 1.5 0 0 1 5.5 6h9A1.5 1.5 0 0 1 16 7.5v9a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 4 16.5ZM16 10.5l4-2.5v8l-4-2.5',
  users: 'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 6.5M18.5 14a6 6 0 0 1 3 6',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM4.5 20.5a7.5 7.5 0 0 1 15 0',
  team: 'M12 10a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM6 20a6 6 0 0 1 12 0M5 11.5a2.5 2.5 0 1 0 0-5M19 11.5a2.5 2.5 0 1 0 0-5M2 18a4 4 0 0 1 3-3.8M22 18a4 4 0 0 0-3-3.8',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  settings: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM19.4 13.5l1.6 1.2-2 3.5-1.9-.7a7.5 7.5 0 0 1-2 1.2L14.8 21h-4l-.3-2.3a7.5 7.5 0 0 1-2-1.2l-1.9.7-2-3.5 1.6-1.2a7.4 7.4 0 0 1 0-2.4L4.6 10l2-3.5 1.9.7a7.5 7.5 0 0 1 2-1.2l.3-2.3h4l.3 2.3a7.5 7.5 0 0 1 2 1.2l1.9-.7 2 3.5-1.6 1.2a7.4 7.4 0 0 1 0 2.4Z',
  brush: 'M14.5 4.5l5 5-8 8a3 3 0 0 1-2 .9H6v-3.5a3 3 0 0 1 .9-2ZM12.5 6.5l5 5M4 20.5h4',
  book: 'M5 4.5A1.5 1.5 0 0 1 6.5 3H19v15H6.5A1.5 1.5 0 0 0 5 19.5ZM5 19.5A1.5 1.5 0 0 0 6.5 21H19',
  tag: 'M3.5 12.2V4.5a1 1 0 0 1 1-1h7.7l8.3 8.3a1 1 0 0 1 0 1.4l-7.3 7.3a1 1 0 0 1-1.4 0ZM8 8h.01',
  link: 'M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1',
  crown: 'M4 18h16M4.5 15 3 7l5 3.5L12 5l4 5.5L21 7l-1.5 8Z',
  moon: 'M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z',
  logout: 'M15 4h3.5A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5H15M10 16l4-4-4-4M14 12H4',
  globe: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM3 12h18M12 3c2.5 2.4 3.7 5.4 3.7 9s-1.2 6.6-3.7 9c-2.5-2.4-3.7-5.4-3.7-9S9.5 5.4 12 3Z',
  search: 'M10.5 17.5a7 7 0 1 0 0-14 7 7 0 0 0 0 14ZM20 20l-4.5-4.5',
  mail: 'M4 6.5A1.5 1.5 0 0 1 5.5 5h13A1.5 1.5 0 0 1 20 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5ZM4.5 6.5 12 12.5l7.5-6',
  share: 'M12 15V3.5M7.5 8 12 3.5 16.5 8M5 12.5V19a1.5 1.5 0 0 0 1.5 1.5h11A1.5 1.5 0 0 0 19 19v-6.5',
  file: 'M6.5 3h7.5l4.5 4.5V19.5A1.5 1.5 0 0 1 17 21H6.5A1.5 1.5 0 0 1 5 19.5v-15A1.5 1.5 0 0 1 6.5 3ZM13.5 3v5h5',
  receipt: 'M6 3h12v18l-3-2-3 2-3-2-3 2ZM9 8h6M9 12h6M9 16h3',
  rupee: 'M7 4h10M7 8.5h10M10 4c3.5 0 5 1.7 5 4.3S13.5 13 10 13H7l8 7',
  sparkle: 'M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8ZM18.5 16l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8Z',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 7.5V12l3 2',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
  box: 'M4 8l8-4.5L20 8v8.5L12 21l-8-4.5ZM4 8l8 4.5L20 8M12 12.5V21',
  film: 'M4 5.5A1.5 1.5 0 0 1 5.5 4h13A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5ZM8 4v16M16 4v16M4 9h4M4 15h4M16 9h4M16 15h4',
  album: 'M3.5 6.5 12 4l8.5 2.5v12L12 21l-8.5-2.5ZM12 4v17',
  refresh: 'M20 11a8 8 0 1 0-2.3 5.7M20 4.5V11h-6.5',
  trash: 'M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1-13',
  edit: 'M4 20h4l10.5-10.5-4-4L4 16ZM13.5 6.5l4 4',
  alert: 'M12 9v4.5M12 17h.01M10.3 4.3 2.7 17.5A2 2 0 0 0 4.4 20.5h15.2a2 2 0 0 0 1.7-3l-7.6-13.2a2 2 0 0 0-3.4 0Z',
};

export function Icon({ name, size = 22, stroke = 1.8, className = '' }) {
  const filled = name === 'more';
  return (
    <svg className={`m-icon ${className}`} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={filled ? 3.2 : stroke} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={ICONS[name] || ICONS.more} />
    </svg>
  );
}

// ------------------------------------------------------------------------------------- data

/** Load data for a screen; keeps the old data while reloading so pull-to-refresh doesn't flash. */
export function useResource(loader, deps = []) {
  const [state, setState] = useState({ data: undefined, loading: true, error: '' });
  const [version, setVersion] = useState(0);
  const pending = useRef(null);
  useEffect(() => {
    let cancelled = false;
    setState((current) => ({ ...current, loading: true, error: '' }));
    const promise = Promise.resolve().then(loader);
    pending.current = promise;
    promise
      .then((data) => { if (!cancelled) setState({ data, loading: false, error: '' }); })
      .catch((error) => { if (!cancelled) setState((current) => ({ ...current, loading: false, error: error.message || 'Something went wrong.' })); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);
  const reload = useCallback(() => {
    setVersion((value) => value + 1);
    return new Promise((resolve) => window.setTimeout(resolve, 450));
  }, []);
  const setData = useCallback((update) => setState((current) => ({ ...current, data: typeof update === 'function' ? update(current.data) : update })), []);
  return { ...state, reload, setData };
}

/** Run an action with a busy flag; errors go to a toast. */
export function useAction() {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const run = useCallback(async (task, success) => {
    setBusy(true);
    try {
      const result = await task();
      if (success) toast(typeof success === 'function' ? success(result) : success);
      return result ?? true;
    } catch (error) {
      toast(error.message || 'Something went wrong.', 'error');
      return null;
    } finally {
      setBusy(false);
    }
  }, [toast]);
  return [busy, run];
}

// ------------------------------------------------------------------------------------- toasts

const ToastContext = createContext(() => {});

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const show = useCallback((message, tone = 'info') => {
    const id = Math.random().toString(36).slice(2);
    setToasts((current) => [...current.slice(-2), { id, message, tone }]);
    window.setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), tone === 'error' ? 5200 : 3200);
  }, []);
  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="m-toasts" aria-live="polite">
        {toasts.map((toast) => <div key={toast.id} className={`m-toast m-toast-${toast.tone}`} role={toast.tone === 'error' ? 'alert' : 'status'}>{toast.message}</div>)}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}

// ------------------------------------------------------------------------------------- back button

/**
 * While an overlay (sheet, photo viewer) is open, Android's back button closes it instead of leaving
 * the screen. Each open overlay adds one history entry; closing it from the UI removes that entry.
 */
export function useBackToClose(open, onClose) {
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    if (!open) return undefined;
    const entry = { close: () => closeRef.current(), closedByBack: false };
    openOverlay(entry);
    return () => closeOverlay(entry);
  }, [open]);
}

/** Esc closes the topmost dialog (desktop). */
const escapeStack = [];
window.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && escapeStack.length) {
    event.preventDefault();
    escapeStack[escapeStack.length - 1].current();
  }
});

export function useEscape(open, onClose) {
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    if (!open) return undefined;
    escapeStack.push(closeRef);
    return () => {
      const index = escapeStack.indexOf(closeRef);
      if (index >= 0) escapeStack.splice(index, 1);
    };
  }, [open]);
}

// ------------------------------------------------------------------------------------- layout

function PullToRefresh({ onRefresh, scrollRef }) {
  const [pull, setPull] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  useEffect(() => {
    const element = scrollRef.current;
    if (!element || !onRefresh) return undefined;
    let startY = null;
    let distance = 0;
    const start = (event) => { startY = element.scrollTop <= 0 ? event.touches[0].clientY : null; distance = 0; };
    const move = (event) => {
      if (startY === null) return;
      distance = Math.max(0, event.touches[0].clientY - startY);
      if (distance > 0 && element.scrollTop <= 0) setPull(Math.min(96, distance * 0.5));
    };
    const end = async () => {
      if (startY === null) return;
      startY = null;
      if (distance * 0.5 >= 64) {
        setRefreshing(true);
        setPull(56);
        try { await onRefresh(); } finally { setRefreshing(false); setPull(0); }
      } else {
        setPull(0);
      }
    };
    element.addEventListener('touchstart', start, { passive: true });
    element.addEventListener('touchmove', move, { passive: true });
    element.addEventListener('touchend', end);
    return () => {
      element.removeEventListener('touchstart', start);
      element.removeEventListener('touchmove', move);
      element.removeEventListener('touchend', end);
    };
  }, [onRefresh, scrollRef]);
  if (!pull && !refreshing) return null;
  return (
    <div className="m-ptr" style={{ height: pull }}>
      <span className={refreshing ? 'm-ptr-icon is-spinning' : 'm-ptr-icon'} style={{ transform: `rotate(${pull * 4}deg)`, opacity: Math.min(1, pull / 56) }}><Icon name="refresh" size={20} /></span>
    </div>
  );
}

/**
 * A full screen: app bar (large title on tab roots, back arrow on sub-screens), scrolling body with
 * pull-to-refresh, and an optional floating action button.
 */
export function Screen({ title, subtitle, back, backTo, actions, onRefresh, fab, large, children, className = '', flush }) {
  const scrollRef = useRef(null);
  const [scrolled, setScrolled] = useState(false);
  // The desktop app's Refresh (F5 / menu) reloads whatever screens are on show.
  const refreshRef = useRef(onRefresh);
  refreshRef.current = onRefresh;
  useEffect(() => {
    const refresh = () => refreshRef.current?.();
    window.addEventListener('fb-refresh', refresh);
    return () => window.removeEventListener('fb-refresh', refresh);
  }, []);
  return (
    <section className={`m-screen ${className}`}>
      <header className={`m-appbar${scrolled || !large ? ' is-solid' : ''}`}>
        {back && <button type="button" className="m-icon-button" onClick={() => goBack(backTo)} aria-label="Back"><Icon name="back" /></button>}
        <div className={`m-appbar-title${large && !scrolled ? ' is-hidden' : ''}`}>
          <h1>{title}</h1>
          {subtitle && !large && <p>{subtitle}</p>}
        </div>
        <div className="m-appbar-actions">{actions}</div>
      </header>
      <div className="m-scroll" ref={scrollRef} onScroll={(event) => setScrolled(event.currentTarget.scrollTop > 24)}>
        <PullToRefresh onRefresh={onRefresh} scrollRef={scrollRef} />
        {large && <div className="m-large-title"><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>}
        <div className={flush ? 'm-body m-body-flush' : 'm-body'}>{children}</div>
      </div>
      {fab}
    </section>
  );
}

export function Fab({ icon = 'plus', label, onClick, extended }) {
  return (
    <button type="button" className={`m-fab${extended ? ' is-extended' : ''}`} onClick={onClick} aria-label={label}>
      <Icon name={icon} size={24} stroke={2.2} />
      {extended && <span>{label}</span>}
    </button>
  );
}

export function Section({ title, action, children, className = '' }) {
  return (
    <section className={`m-section ${className}`}>
      {(title || action) && <div className="m-section-head"><h2>{title}</h2>{action}</div>}
      {children}
    </section>
  );
}

export function Card({ children, className = '', onClick }) {
  if (onClick) return <button type="button" className={`m-card m-card-button ${className}`} onClick={onClick}>{children}</button>;
  return <div className={`m-card ${className}`}>{children}</div>;
}

export function List({ children, inset = true }) {
  return <div className={inset ? 'm-list' : 'm-list m-list-flat'}>{children}</div>;
}

export function Row({ icon, leading, title, subtitle, meta, trailing, onClick, href, chevron = Boolean(onClick || href), tone, multiline, selected }) {
  const content = (
    <>
      {leading || (icon && <span className={`m-row-icon${tone ? ` m-tone-${tone}` : ''}`}><Icon name={icon} size={20} /></span>)}
      <span className="m-row-text">
        <span className={multiline ? 'm-row-title is-multiline' : 'm-row-title'}>{title}</span>
        {subtitle && <span className={multiline ? 'm-row-subtitle is-multiline' : 'm-row-subtitle'}>{subtitle}</span>}
      </span>
      {meta && <span className="m-row-meta">{meta}</span>}
      {trailing}
      {chevron && <Icon name="chevron" size={18} className="m-row-chevron" />}
    </>
  );
  const className = selected ? 'm-row is-selected' : 'm-row';
  if (href) return <a className={className} href={href}>{content}</a>;
  if (onClick) return <button type="button" className={className} onClick={onClick} aria-current={selected ? 'true' : undefined}>{content}</button>;
  return <div className={className}>{content}</div>;
}

export function Chip({ children, tone = 'neutral', icon }) {
  return <span className={`m-chip m-chip-${tone}`}>{icon && <Icon name={icon} size={13} stroke={2.2} />}{children}</span>;
}

export function Avatar({ name, size = 40, tone }) {
  const hue = [...String(name || '')].reduce((sum, char) => sum + char.charCodeAt(0), 0) % 4;
  return <span className={`m-avatar m-avatar-${tone || hue}`} style={{ width: size, height: size, fontSize: size * 0.38 }}>{String(name || '?').trim().split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase()).join('')}</span>;
}

export function Stat({ label, value, tone, icon, onClick }) {
  const Tag = onClick ? 'button' : 'div';
  return (
    <Tag type={onClick ? 'button' : undefined} className={`m-stat${tone ? ` m-stat-${tone}` : ''}`} onClick={onClick}>
      {icon && <Icon name={icon} size={18} />}
      <b>{value}</b>
      <span>{label}</span>
    </Tag>
  );
}

export function Segmented({ value, options, onChange }) {
  return (
    <div className="m-segmented" role="tablist">
      {options.map(([key, label, count]) => (
        <button key={key} type="button" role="tab" aria-selected={value === key} className={value === key ? 'is-active' : ''} onClick={() => onChange(key)}>
          {label}{count !== undefined && <span className="m-seg-count">{count}</span>}
        </button>
      ))}
    </div>
  );
}

export function QuickAction({ icon, label, onClick, href }) {
  const inner = <><span className="m-quick-icon"><Icon name={icon} size={22} /></span><span>{label}</span></>;
  if (href) return <a className="m-quick" href={href}>{inner}</a>;
  return <button type="button" className="m-quick" onClick={onClick}>{inner}</button>;
}

export function Empty({ icon = 'sparkle', title, children, action }) {
  return (
    <div className="m-empty">
      <span className="m-empty-icon"><Icon name={icon} size={28} /></span>
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

export function Skeleton({ rows = 4 }) {
  return <div className="m-skeleton" aria-label="Loading">{Array.from({ length: rows }, (_, index) => <div key={index} className="m-skeleton-row"><span /><div><i /><i /></div></div>)}</div>;
}

export function ErrorNote({ message, onRetry }) {
  if (!message) return null;
  return <div className="m-error"><Icon name="alert" size={18} /><span>{message}</span>{onRetry && <button type="button" onClick={onRetry}>Retry</button>}</div>;
}

export function Search({ value, onChange, placeholder }) {
  return (
    <label className="m-search">
      <Icon name="search" size={18} />
      <input type="search" value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} />
      {value && <button type="button" onClick={() => onChange('')} aria-label="Clear search"><Icon name="close" size={16} /></button>}
    </label>
  );
}

// ------------------------------------------------------------------------------------- sheets & forms

export function Sheet({ open, title, onClose, children, footer, tall }) {
  useBackToClose(open, onClose);
  useEscape(open, onClose);
  useEffect(() => {
    if (!open) return undefined;
    document.body.classList.add('m-no-scroll');
    return () => document.body.classList.remove('m-no-scroll');
  }, [open]);
  if (!open) return null;
  // Rendered at the end of <body> so it covers the tab bar (screens are their own stacking context).
  return createPortal(
    <div className="m-sheet-backdrop" role="presentation" onClick={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section className={`m-sheet${tall ? ' is-tall' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <span className="m-sheet-grip" aria-hidden="true" />
        <header className="m-sheet-head">
          <h2>{title}</h2>
          <button type="button" className="m-icon-button" onClick={onClose} aria-label="Close"><Icon name="close" /></button>
        </header>
        <div className="m-sheet-body">{children}</div>
        {footer && <footer className="m-sheet-foot">{footer}</footer>}
      </section>
    </div>,
    document.body,
  );
}

/** Full-screen layer above everything (photo viewer etc.). */
export function Layer({ children }) {
  return createPortal(children, document.body);
}

export function Field({ label, hint, children, error }) {
  return (
    <label className={`m-field${error ? ' has-error' : ''}`}>
      <span className="m-field-label">{label}</span>
      {children}
      {(error || hint) && <span className="m-field-hint">{error || hint}</span>}
    </label>
  );
}

export function Button({ children, variant = 'primary', icon, busy, block, ...props }) {
  return (
    <button type="button" className={`m-button m-button-${variant}${block ? ' is-block' : ''}`} disabled={busy || props.disabled} {...props}>
      {busy ? <span className="m-spinner" aria-hidden="true" /> : icon && <Icon name={icon} size={18} stroke={2} />}
      <span>{children}</span>
    </button>
  );
}

export function ActionPill({ icon, label, href, onClick, tone }) {
  const inner = <><Icon name={icon} size={20} /><span>{label}</span></>;
  const className = `m-action-pill${tone ? ` m-tone-${tone}` : ''}`;
  if (href) return <a className={className} href={href}>{inner}</a>;
  return <button type="button" className={className} onClick={onClick} disabled={!onClick}>{inner}</button>;
}

export function KeyValue({ items }) {
  return (
    <dl className="m-kv">
      {items.filter(Boolean).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value || '—'}</dd></div>)}
    </dl>
  );
}

export function ProgressBar({ value }) {
  return <div className="m-progress"><span style={{ width: `${Math.max(0, Math.min(1, value)) * 100}%` }} /></div>;
}
