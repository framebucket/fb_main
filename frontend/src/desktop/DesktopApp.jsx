import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api.js';
import { useTheme } from '../theme.jsx';
import { Avatar, Empty, Icon, ProgressBar, ToastProvider, useBackToClose, useEscape } from '../mobile/ui.jsx';
import { navigate, useRoute } from '../mobile/router.js';
import { UserContext } from '../mobile/session.js';
import { LayoutContext } from '../mobile/layout.js';
import { dismissUploads, retryFailed, useUploads } from '../mobile/uploads.js';
import LoginScreen from '../mobile/screens/Login.jsx';
import HomeScreen from '../mobile/screens/Home.jsx';
import { CalendarScreen, EventsScreen } from '../mobile/screens/Events.jsx';
import EventDetailScreen, { EventManageScreen } from '../mobile/screens/EventDetail.jsx';
import GalleryScreen from '../mobile/screens/Gallery.jsx';
import { EnquiriesScreen, EnquiryDetailScreen } from '../mobile/screens/Enquiries.jsx';
import { CustomerDetailScreen, CustomersScreen } from '../mobile/screens/Customers.jsx';
import { EventMoneyScreen, MoneyScreen } from '../mobile/screens/Money.jsx';
import { MoreScreen, MoreSectionScreen, TeamScreen } from '../mobile/screens/More.jsx';
import { dayDate } from '../mobile/format.js';

const bridge = typeof window !== 'undefined' ? window.fbDesktop : null;
const platform = bridge?.platform || (/Mac/.test(navigator.platform) ? 'darwin' : 'win32');
const MOD = platform === 'darwin' ? '⌘' : 'Ctrl+';

// Sidebar: [route, label, icon, shortcut digit]
const NAV = [
  ['/home', 'Home', 'home', '1'],
  ['/events', 'Events', 'calendar', '2'],
  ['/enquiries', 'Enquiries', 'inbox', '3'],
  ['/customers', 'Customers', 'users', '4'],
  ['/calendar', 'Calendar', 'clock', '5'],
  ['/money', 'Payments', 'wallet', '6'],
  ['/team', 'Team', 'team', '7'],
];
const NAV_STUDIO = [
  ['/more/crew', 'Crew & gear', 'box'],
  ['/more/insights', 'Insights', 'chart'],
  ['/more/portfolio', 'Portfolio & e-albums', 'book'],
];
const NAV_SETTINGS = [
  ['/more/packages', 'Packages & pricing', 'tag'],
  ['/more/leads', 'Enquiry links', 'link'],
  ['/more/branding', 'Branding & watermark', 'brush'],
  ['/more/style', 'Editing style & logo', 'sparkle'],
  ['/more/studio', 'Studio settings', 'settings'],
  ['/more/plan', 'Plan & usage', 'crown'],
];

function sectionOf(path) {
  const first = path.split('/').filter(Boolean)[0] || 'home';
  if (first === 'more') return path.split('?')[0];
  return `/${first}`;
}

// ------------------------------------------------------------------------------------- title bar

function TitleBar({ user, onSearch }) {
  const [theme, setTheme] = useTheme();
  useEffect(() => { bridge?.setTheme?.(theme); }, [theme]);
  return (
    <header className={`d-titlebar d-platform-${platform}${bridge ? ' is-app' : ''}`}>
      <div className="d-titlebar-brand">
        <img src="/framebucket-mark.png" alt="" />
        <b>FrameBucket</b>
        {user?.studioName && <span>{user.studioName}</span>}
      </div>
      {user && (
        <button type="button" className="d-search-trigger" onClick={onSearch}>
          <Icon name="search" size={15} />
          <span>Search events, customers, enquiries…</span>
          <kbd>{MOD}K</kbd>
        </button>
      )}
      <div className="d-titlebar-actions">
        {user && <button type="button" className="d-title-button" title={`Refresh (F5)`} onClick={() => window.dispatchEvent(new Event('fb-refresh'))}><Icon name="refresh" size={16} /></button>}
        <button type="button" className="d-title-button" title={theme === 'dark' ? 'Light mode' : 'Dark mode'} onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}><Icon name="moon" size={16} /></button>
      </div>
    </header>
  );
}

// ------------------------------------------------------------------------------------- sidebar

function Sidebar({ user, path, collapsed, onToggle }) {
  const active = sectionOf(path);
  const item = ([route, label, icon, key]) => (
    <button key={route} type="button" className={active === route ? 'is-active' : ''}
      onClick={() => navigate(route)} title={collapsed ? label : undefined} aria-current={active === route ? 'page' : undefined}>
      <Icon name={icon} size={18} />
      <span>{label}</span>
      {key && <kbd>{MOD}{key}</kbd>}
    </button>
  );
  async function signOut() {
    try { await api.logout(); } finally { window.location.replace('/desktop'); }
  }
  return (
    <nav className={`d-sidebar${collapsed ? ' is-collapsed' : ''}`} aria-label="Main">
      <div className="d-nav-group">{NAV.map(item)}</div>
      <p className="d-nav-label">Studio</p>
      <div className="d-nav-group">{NAV_STUDIO.map(item)}</div>
      <p className="d-nav-label">Settings</p>
      <div className="d-nav-group">{NAV_SETTINGS.map(item)}</div>
      <div className="d-sidebar-foot">
        <button type="button" className="d-account" onClick={() => navigate('/more')} title="Account">
          <Avatar name={user.fullName} size={30} />
          <span><b>{user.fullName}</b><small>{user.entitlements?.planName || user.email}</small></span>
        </button>
        <div className="d-sidebar-tools">
          <button type="button" onClick={onToggle} title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}><Icon name={collapsed ? 'chevron' : 'back'} size={16} /></button>
          {user.admin && <a href="/admin" title="Admin portal"><Icon name="crown" size={16} /></a>}
          <a href="/dashboard?desktop=1" title="Classic web layout"><Icon name="globe" size={16} /></a>
          <button type="button" onClick={signOut} title="Sign out"><Icon name="logout" size={16} /></button>
        </div>
      </div>
    </nav>
  );
}

// ------------------------------------------------------------------------------------- content

function Placeholder({ icon, title, children }) {
  return <div className="d-placeholder"><Empty icon={icon} title={title}>{children}</Empty></div>;
}

/** List on the left, the selected item on the right — like Mail or Outlook. */
function Split({ list, detail, depth }) {
  return (
    <div className="d-split">
      <div className="d-list">{list}</div>
      <div className="d-detail" data-depth={depth} key={detail.key || 'empty'}>{detail.node}</div>
    </div>
  );
}

function Content({ route }) {
  const [first, id, sub] = route.parts;
  const query = route.query;
  switch (first) {
    case 'events': {
      let node = <Placeholder icon="calendar" title="Select an event">Pick an event on the left to see its details, gallery and payments.</Placeholder>;
      if (id && sub === 'gallery') node = <GalleryScreen eventId={id} folder={query.folder} upload={query.upload === '1'} />;
      else if (id && sub === 'money') node = <EventMoneyScreen eventId={id} record={query.record === '1'} />;
      else if (id && sub === 'manage') node = <EventManageScreen eventId={id} tab={query.tab} />;
      else if (id) node = <EventDetailScreen eventId={id} />;
      return <Split list={<EventsScreen view={query.view} />} detail={{ key: route.path, node }} depth={route.parts.length} />;
    }
    case 'enquiries':
      return <Split list={<EnquiriesScreen filter={query.filter} />} depth={route.parts.length}
        detail={{ key: route.path, node: id ? <EnquiryDetailScreen enquiryId={id} /> : <Placeholder icon="inbox" title="Select an enquiry">Quotes, contracts and follow-ups appear here.</Placeholder> }} />;
    case 'customers':
      return <Split list={<CustomersScreen />} depth={route.parts.length}
        detail={{ key: route.path, node: id ? <CustomerDetailScreen customerId={id} /> : <Placeholder icon="users" title="Select a customer">Contact details, events and total business.</Placeholder> }} />;
    case 'calendar':
      return <div className="d-full d-wide"><CalendarScreen /></div>;
    case 'money':
      return <div className="d-full"><MoneyScreen view={query.view} /></div>;
    case 'team':
      return <div className="d-full"><TeamScreen /></div>;
    case 'more':
      return <div className={id ? 'd-full d-wide' : 'd-full'}>{id ? <MoreSectionScreen section={id} /> : <MoreScreen />}</div>;
    case 'home':
    default:
      return <div className="d-full d-wide"><HomeScreen /></div>;
  }
}

// ------------------------------------------------------------------------------------- search

function CommandPalette({ open, onClose }) {
  useBackToClose(open, onClose);
  useEscape(open, onClose);
  const [query, setQuery] = useState('');
  const [data, setData] = useState(null);
  const [cursor, setCursor] = useState(0);
  const input = useRef(null);
  useEffect(() => {
    if (!open) return;
    setQuery('');
    setCursor(0);
    window.setTimeout(() => input.current?.focus(), 20);
    Promise.all([api.events().catch(() => []), api.customers().catch(() => []), api.enquiries().catch(() => [])])
      .then(([events, customers, enquiries]) => setData({ events, customers, enquiries }));
  }, [open]);

  const results = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const pages = [...NAV, ...NAV_STUDIO, ...NAV_SETTINGS].map(([route, label, icon]) => ({ key: route, icon, title: label, subtitle: 'Go to', route }));
    if (!needle) return pages.slice(0, 8);
    const match = (...values) => values.some((value) => String(value || '').toLowerCase().includes(needle));
    const list = [];
    for (const page of pages) if (match(page.title)) list.push(page);
    for (const event of data?.events || []) if (match(event.customerName, event.eventTypeName, event.eventCode, event.locationName)) list.push({ key: `e${event.id}`, icon: 'calendar', title: `${event.eventTypeName} · ${event.customerName}`, subtitle: `Event · ${dayDate(event.eventDate)}`, route: `/events/${event.id}` });
    for (const customer of data?.customers || []) if (match(customer.name, customer.mobileNumber, customer.email, customer.customerCode)) list.push({ key: `c${customer.id}`, icon: 'user', title: customer.name, subtitle: `Customer · ${customer.mobileNumber}`, route: `/customers/${customer.id}` });
    for (const enquiry of data?.enquiries || []) if (match(enquiry.name, enquiry.mobileNumber, enquiry.email, enquiry.eventName)) list.push({ key: `q${enquiry.id}`, icon: 'inbox', title: enquiry.name, subtitle: `Enquiry · ${enquiry.eventName}`, route: `/enquiries/${enquiry.id}` });
    return list.slice(0, 40);
  }, [query, data]);

  if (!open) return null;
  const choose = (result) => { onClose(); navigate(result.route); };
  return (
    <div className="d-palette-backdrop" role="presentation" onClick={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div className="d-palette" role="dialog" aria-modal="true" aria-label="Search">
        <label className="d-palette-input">
          <Icon name="search" size={18} />
          <input ref={input} value={query} placeholder="Search events, customers, enquiries or pages…"
            onChange={(event) => { setQuery(event.target.value); setCursor(0); }}
            onKeyDown={(event) => {
              if (event.key === 'ArrowDown') { event.preventDefault(); setCursor((value) => Math.min(results.length - 1, value + 1)); }
              if (event.key === 'ArrowUp') { event.preventDefault(); setCursor((value) => Math.max(0, value - 1)); }
              if (event.key === 'Enter' && results[cursor]) choose(results[cursor]);
            }} />
          <kbd>Esc</kbd>
        </label>
        <div className="d-palette-results">
          {results.map((result, index) => (
            <button key={result.key} type="button" className={index === cursor ? 'is-active' : ''} onMouseEnter={() => setCursor(index)} onClick={() => choose(result)}>
              <Icon name={result.icon} size={17} />
              <span><b>{result.title}</b><small>{result.subtitle}</small></span>
            </button>
          ))}
          {query && data && !results.length && <p className="m-muted d-palette-empty">Nothing matches “{query}”.</p>}
          {query && !data && <p className="m-muted d-palette-empty">Searching…</p>}
        </div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------------------------- uploads

function UploadToast() {
  const uploads = useUploads();
  if (!uploads.total) return null;
  const active = Boolean(uploads.current);
  const position = Math.min(uploads.done + uploads.failed.length + (active ? 1 : 0), uploads.total);
  const overall = (uploads.done + uploads.failed.length + (active ? uploads.progress : 0)) / uploads.total;
  return (
    <div className={`d-upload${uploads.failed.length && !active ? ' has-failed' : ''}`} role="status">
      <Icon name={active ? 'upload' : uploads.failed.length ? 'alert' : 'check'} size={18} />
      <div>
        <b>{active ? `Uploading ${position} of ${uploads.total}` : uploads.failed.length ? `${uploads.failed.length} upload${uploads.failed.length === 1 ? '' : 's'} failed` : `${uploads.done} uploaded`}</b>
        {active ? <><span>{uploads.current.file.name}</span><ProgressBar value={overall} /></> : <span>{uploads.failed.length ? uploads.failed[0].message : 'Previews appear once processed.'}</span>}
      </div>
      {!active && uploads.failed.length > 0 && <button type="button" onClick={retryFailed}>Retry</button>}
      {!active && <button type="button" onClick={dismissUploads} aria-label="Dismiss"><Icon name="close" size={15} /></button>}
    </div>
  );
}

// ------------------------------------------------------------------------------------- app

function isTyping(target) {
  return target instanceof HTMLElement && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName));
}

function Shell({ user, setUser }) {
  const route = useRoute();
  const [palette, setPalette] = useState(false);
  const [collapsed, setCollapsed] = useState(() => { try { return localStorage.getItem('fb-sidebar') === 'collapsed'; } catch { return false; } });

  useEffect(() => {
    try { localStorage.setItem('fb-sidebar', collapsed ? 'collapsed' : 'open'); } catch { /* storage unavailable */ }
  }, [collapsed]);

  // Keyboard shortcuts (also wired to the app menu in Electron).
  useEffect(() => {
    const onKey = (event) => {
      const mod = event.ctrlKey || event.metaKey;
      if (mod && event.key.toLowerCase() === 'k') { event.preventDefault(); setPalette(true); return; }
      if (event.key === 'F5' || (mod && event.key.toLowerCase() === 'r' && !event.shiftKey)) { event.preventDefault(); window.dispatchEvent(new Event('fb-refresh')); return; }
      if (mod && /^[1-7]$/.test(event.key)) { event.preventDefault(); navigate(NAV[Number(event.key) - 1][0]); return; }
      if (mod && event.key.toLowerCase() === 'n') { event.preventDefault(); window.dispatchEvent(new Event('fb-new')); return; }
      if (mod && event.key === '\\') { event.preventDefault(); setCollapsed((value) => !value); return; }
      if (event.key === '/' && !isTyping(event.target)) { event.preventDefault(); setPalette(true); }
    };
    // "New" clicks the add button of the screen in front (the detail pane first, then the list).
    const onNew = () => {
      const button = document.querySelector('.d-detail .m-fab:not(.is-extended), .d-list .m-fab, .d-full .m-fab');
      button?.click();
    };
    const onSearch = () => setPalette(true);
    window.addEventListener('keydown', onKey);
    window.addEventListener('fb-new', onNew);
    window.addEventListener('fb-search', onSearch);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('fb-new', onNew);
      window.removeEventListener('fb-search', onSearch);
    };
  }, []);

  useEffect(() => {
    const titles = { home: 'Home', events: 'Events', enquiries: 'Enquiries', customers: 'Customers', calendar: 'Calendar', money: 'Payments', team: 'Team', more: 'Settings' };
    document.title = `${titles[route.parts[0]] || 'FrameBucket'} · FrameBucket`;
  }, [route.parts]);

  return (
    <UserContext.Provider value={{ user, setUser, reloadUser: () => api.me().then(setUser).catch(() => {}) }}>
      <div className="d-app">
        <TitleBar user={user} onSearch={() => setPalette(true)} />
        <div className="d-body">
          <Sidebar user={user} path={route.path} collapsed={collapsed} onToggle={() => setCollapsed((value) => !value)} />
          <main className="d-main" key={sectionOf(route.path)}>
            <Content route={route} />
          </main>
        </div>
        <UploadToast />
        <CommandPalette open={palette} onClose={() => setPalette(false)} />
      </div>
    </UserContext.Provider>
  );
}

export default function DesktopApp() {
  const [user, setUser] = useState(undefined);
  useEffect(() => {
    let cancelled = false;
    api.me()
      .then((profile) => { if (!cancelled) setUser(profile); })
      .catch(() => {
        api.customerMe()
          .then(() => { window.location.replace('/customer-portal'); })
          .catch(() => { if (!cancelled) setUser(null); });
      });
    return () => { cancelled = true; };
  }, []);

  return (
    <LayoutContext.Provider value="desktop">
      <ToastProvider>
        {user === undefined ? (
          <div className="d-app"><TitleBar user={null} /><div className="m-splash d-splash"><img src="/framebucket-mark.png" alt="" /><span className="m-spinner" /></div></div>
        ) : user === null ? (
          <div className="d-app"><TitleBar user={null} /><div className="d-login"><LoginScreen onLogin={(profile) => { navigate('/home', { replace: true }); setUser(profile); }} /></div></div>
        ) : <Shell user={user} setUser={setUser} />}
      </ToastProvider>
    </LayoutContext.Provider>
  );
}
