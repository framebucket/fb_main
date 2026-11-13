import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { Icon, ProgressBar, ToastProvider } from './ui.jsx';
import { navDirection, navigate, tabOf, useRoute } from './router.js';
import { dismissUploads, retryFailed, useUploads } from './uploads.js';
import LoginScreen from './screens/Login.jsx';
import HomeScreen from './screens/Home.jsx';
import { EventsScreen, CalendarScreen } from './screens/Events.jsx';
import EventDetailScreen, { EventManageScreen } from './screens/EventDetail.jsx';
import GalleryScreen from './screens/Gallery.jsx';
import { EnquiriesScreen, EnquiryDetailScreen } from './screens/Enquiries.jsx';
import { CustomersScreen, CustomerDetailScreen } from './screens/Customers.jsx';
import { MoneyScreen, EventMoneyScreen } from './screens/Money.jsx';
import { UserContext } from './session.js';
import { MoreScreen, MoreSectionScreen, TeamScreen } from './screens/More.jsx';


const TABS = [
  ['/home', 'Home', 'home'],
  ['/events', 'Events', 'calendar'],
  ['/enquiries', 'Enquiries', 'inbox'],
  ['/money', 'Payments', 'wallet'],
  ['/more', 'More', 'grid'],
];

function BottomNav({ path }) {
  const active = tabOf(path);
  return (
    <nav className="m-tabbar" aria-label="Main">
      {TABS.map(([root, label, icon]) => (
        <button key={root} type="button" className={active === root ? 'is-active' : ''} aria-current={active === root ? 'page' : undefined}
          onClick={() => navigate(root, { replace: path === root || TABS.some(([tab]) => tab === path) })}>
          <span className="m-tab-pill"><Icon name={icon} size={22} stroke={active === root ? 2.1 : 1.8} /></span>
          <span className="m-tab-label">{label}</span>
        </button>
      ))}
    </nav>
  );
}

function UploadBar() {
  const uploads = useUploads();
  if (!uploads.total) return null;
  const active = Boolean(uploads.current);
  const position = Math.min(uploads.done + uploads.failed.length + (active ? 1 : 0), uploads.total);
  const overall = uploads.total ? (uploads.done + uploads.failed.length + (active ? uploads.progress : 0)) / uploads.total : 0;
  return (
    <div className={`m-uploadbar${uploads.failed.length && !active ? ' has-failed' : ''}`} role="status">
      <Icon name={active ? 'upload' : uploads.failed.length ? 'alert' : 'check'} size={18} />
      <div className="m-uploadbar-text">
        <b>{active ? `Uploading ${position} of ${uploads.total}` : uploads.failed.length ? `${uploads.failed.length} upload${uploads.failed.length === 1 ? '' : 's'} failed` : `${uploads.done} uploaded`}</b>
        {active ? <ProgressBar value={overall} /> : <span>{uploads.failed.length ? uploads.failed[0].message : 'Previews appear once processed.'}</span>}
      </div>
      {!active && uploads.failed.length > 0 && <button type="button" onClick={retryFailed}>Retry</button>}
      {!active && <button type="button" onClick={dismissUploads} aria-label="Dismiss"><Icon name="close" size={16} /></button>}
    </div>
  );
}

function Routes({ route }) {
  const [first, id, sub] = route.parts;
  switch (first) {
    case 'events':
      if (id && sub === 'gallery') return <GalleryScreen eventId={id} folder={route.query.folder} upload={route.query.upload === '1'} />;
      if (id && sub === 'money') return <EventMoneyScreen eventId={id} record={route.query.record === '1'} />;
      if (id && sub === 'manage') return <EventManageScreen eventId={id} tab={route.query.tab} />;
      if (id) return <EventDetailScreen eventId={id} />;
      return <EventsScreen view={route.query.view} />;
    case 'calendar':
      return <CalendarScreen />;
    case 'enquiries':
      return id ? <EnquiryDetailScreen enquiryId={id} /> : <EnquiriesScreen filter={route.query.filter} />;
    case 'customers':
      return id ? <CustomerDetailScreen customerId={id} /> : <CustomersScreen />;
    case 'money':
      return <MoneyScreen view={route.query.view} />;
    case 'team':
      return <TeamScreen />;
    case 'more':
      return id ? <MoreSectionScreen section={id} /> : <MoreScreen />;
    case 'home':
    default:
      return <HomeScreen />;
  }
}

function Shell({ user, setUser }) {
  const route = useRoute();
  const direction = navDirection();
  return (
    <UserContext.Provider value={{ user, setUser, reloadUser: () => api.me().then(setUser).catch(() => {}) }}>
      <div className="m-app">
        <main className={`m-stage m-enter-${direction}`} key={route.path}>
          <Routes route={route} />
        </main>
        <UploadBar />
        <BottomNav path={route.path} />
      </div>
    </UserContext.Provider>
  );
}

function Splash() {
  return (
    <div className="m-splash">
      <img src="/framebucket-mark.png" alt="" />
      <span className="m-spinner" aria-label="Loading" />
    </div>
  );
}

export default function MobileApp() {
  const [user, setUser] = useState(undefined);

  useEffect(() => {
    let cancelled = false;
    api.me()
      .then((profile) => { if (!cancelled) setUser(profile); })
      .catch(() => {
        // A signed-in customer belongs in the customer portal.
        api.customerMe()
          .then(() => { window.location.replace('/customer-portal'); })
          .catch(() => { if (!cancelled) setUser(null); });
      });
    return () => { cancelled = true; };
  }, []);

  return (
    <ToastProvider>
      {user === undefined ? <Splash /> : user === null ? <LoginScreen onLogin={(profile) => { navigate('/home', { replace: true }); setUser(profile); }} /> : <Shell user={user} setUser={setUser} />}
    </ToastProvider>
  );
}
