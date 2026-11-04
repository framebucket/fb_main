import { useEffect, useState } from 'react';
import { api } from './api.js';

function currentPath() {
  return window.location.pathname.replace(/\/+$/, '') || '/';
}

export default function App() {
  const [path, setPath] = useState(currentPath);
  const [user, setUser] = useState(null);
  const [loadingSession, setLoadingSession] = useState(path === '/dashboard');
  const [notice, setNotice] = useState('');

  function navigate(destination, replace = false) {
    if (replace) {
      window.history.replaceState({}, '', destination);
    } else {
      window.history.pushState({}, '', destination);
    }
    setPath(currentPath());
  }

  useEffect(() => {
    function handlePopState() {
      setPath(currentPath());
    }

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  useEffect(() => {
    if (path !== '/dashboard' || user) {
      return undefined;
    }

    let cancelled = false;
    setLoadingSession(true);
    api.me()
      .then((profile) => {
        if (!cancelled) setUser(profile);
      })
      .catch(() => {
        if (!cancelled) navigate('/login', true);
      })
      .finally(() => {
        if (!cancelled) setLoadingSession(false);
      });

    return () => {
      cancelled = true;
    };
  }, [path, user]);

  if (path === '/register') {
    return (
      <RegisterPage
        navigate={navigate}
        onRegistered={() => setNotice('Your account is ready. Sign in to open your workspace.')}
      />
    );
  }

  if (path === '/login') {
    return (
      <LoginPage
        navigate={navigate}
        notice={notice}
        clearNotice={() => setNotice('')}
        onLogin={setUser}
      />
    );
  }

  if (path === '/dashboard') {
    if (loadingSession || !user) {
      return <LoadingPage />;
    }
    return (
      <Dashboard
        user={user}
        onLogout={async () => {
          await api.logout();
          setUser(null);
          navigate('/login', true);
        }}
      />
    );
  }

  return <HomePage navigate={navigate} />;
}

function Brand({ light = false }) {
  return (
    <a className={'brand' + (light ? ' brand-light' : '')} href="/" aria-label="FrameBucket home">
      <span className="brand-mark" aria-hidden="true">
        <span />
        <span />
        <span />
        <span />
      </span>
      <span>framebucket</span>
    </a>
  );
}

function HomePage({ navigate }) {
  return (
    <main className="landing-shell">
      <header className="site-header">
        <Brand />
        <nav className="header-actions" aria-label="Main navigation">
          <button className="button button-quiet" onClick={() => navigate('/login')}>Log in</button>
          <button className="button button-dark button-small" onClick={() => navigate('/register')}>
            Create account <span aria-hidden="true">↗</span>
          </button>
        </nav>
      </header>

      <section className="hero">
        <div className="hero-copy">
          <p className="eyebrow"><span className="eyebrow-dot" /> YOUR STUDIO, IN FOCUS</p>
          <h1>More time behind the camera. <em>Less time chasing details.</em></h1>
          <p className="hero-description">
            Keep your client work moving from the first booking to the final album, all in one calm workspace.
          </p>
          <div className="hero-actions">
            <button className="button button-dark" onClick={() => navigate('/register')}>
              Start your workspace <span aria-hidden="true">↗</span>
            </button>
            <button className="button button-quiet" onClick={() => navigate('/login')}>I already have an account</button>
          </div>
          <div className="hero-note">
            <span className="note-line" />
            Built for independent photographers and small studios
          </div>
        </div>

        <div className="hero-art" aria-label="A preview of the photographer workspace">
          <div className="art-grain" />
          <div className="art-topline"><span>STUDIO OVERVIEW</span><span>01 / 03</span></div>
          <div className="art-heading">Good work,<br /><i>in progress.</i></div>
          <div className="art-card art-card-customer">
            <span className="art-card-icon">01</span>
            <span><b>Customer stories</b><small>Bookings, people, and plans</small></span>
            <span className="art-arrow">↗</span>
          </div>
          <div className="art-card art-card-data">
            <span className="art-card-icon">02</span>
            <span><b>Photo collections</b><small>Uploads, selections, and delivery</small></span>
            <span className="art-arrow">↗</span>
          </div>
          <div className="art-stamp">YOUR<br />STUDIO<br />SPACE</div>
          <div className="art-bottomline"><span>FRAMEBUCKET / FIELD NOTES</span><span>MADE FOR THE MOMENTS THAT MATTER</span></div>
        </div>
      </section>

      <section className="feature-strip">
        <div><span className="feature-number">01</span><span><b>One clear home</b><small>Your bookings and work, together.</small></span></div>
        <div><span className="feature-number">02</span><span><b>Clients in the loop</b><small>Share progress as the story unfolds.</small></span></div>
        <div><span className="feature-number">03</span><span><b>Made to grow</b><small>Start simple. Add tools as you need them.</small></span></div>
      </section>

      <footer className="site-footer"><Brand /><span>Thoughtful tools for people who make memories.</span><span>© 2026 FrameBucket</span></footer>
    </main>
  );
}

function RegisterPage({ navigate, onRegistered }) {
  const [form, setForm] = useState({ fullName: '', studioName: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  function update(event) {
    setForm({ ...form, [event.target.name]: event.target.value });
  }

  async function submit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      await api.register(form);
      onRegistered();
      navigate('/login');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout eyebrow="A GOOD PLACE TO BEGIN" title={<>Make room for<br /><em>the work you love.</em></>} subtitle="Create your photographer account and set up your studio workspace.">
      <form className="auth-form" onSubmit={submit}>
        <label>Full name<input name="fullName" value={form.fullName} onChange={update} autoComplete="name" required maxLength="120" placeholder="Your name" /></label>
        <label>Studio name <span className="optional-label">OPTIONAL</span><input name="studioName" value={form.studioName} onChange={update} maxLength="160" placeholder="The name clients know" /></label>
        <label>Email address<input name="email" type="email" value={form.email} onChange={update} autoComplete="email" required maxLength="320" placeholder="you@yourstudio.com" /></label>
        <label>Password<input name="password" type="password" value={form.password} onChange={update} autoComplete="new-password" required minLength="12" maxLength="64" placeholder="At least 12 characters" /></label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button button-dark button-full" type="submit" disabled={busy}>
          {busy ? 'Creating your account…' : 'Create photographer account'} <span aria-hidden="true">↗</span>
        </button>
        <p className="form-footnote">Your password is stored as a one-way hash and is never shown in your profile.</p>
      </form>
      <p className="auth-switch">Already registered? <button onClick={() => navigate('/login')}>Log in</button></p>
    </AuthLayout>
  );
}

function LoginPage({ navigate, notice, clearNotice, onLogin }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const profile = await api.login({ email, password });
      onLogin(profile);
      navigate('/dashboard');
      clearNotice();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout eyebrow="WELCOME BACK" title={<>Pick up right<br /><em>where you left off.</em></>} subtitle="Sign in to see what is happening across your studio.">
      {notice && <p className="form-success" role="status">{notice}</p>}
      <form className="auth-form" onSubmit={submit}>
        <label>Email address<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" required placeholder="you@yourstudio.com" /></label>
        <label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required placeholder="Your password" /></label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button button-dark button-full" type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in to your studio'} <span aria-hidden="true">↗</span>
        </button>
      </form>
      <p className="auth-switch">New to FrameBucket? <button onClick={() => navigate('/register')}>Create an account</button></p>
    </AuthLayout>
  );
}

function AuthLayout({ eyebrow, title, subtitle, children }) {
  return (
    <main className="auth-shell">
      <aside className="auth-side">
        <Brand light />
        <div className="auth-side-note"><span>01 / YOUR WORKSPACE</span><p>Every good story<br />starts with <em>a little space.</em></p><span className="auth-side-rule" /></div>
        <span className="auth-side-caption">FRAMEBUCKET · PHOTOGRAPHER PORTAL</span>
      </aside>
      <section className="auth-main">
        <button className="back-link" onClick={() => window.history.length > 1 ? window.history.back() : window.location.assign('/')}>← Back</button>
        <div className="auth-content">
          <p className="eyebrow">{eyebrow}</p>
          <h1>{title}</h1>
          <p className="auth-subtitle">{subtitle}</p>
          {children}
        </div>
      </section>
    </main>
  );
}

function Dashboard({ user, onLogout }) {
  const [activeTab, setActiveTab] = useState('customers');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const firstName = user.fullName.split(' ')[0];

  async function logout() {
    setError('');
    setBusy(true);
    try {
      await onLogout();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="dashboard-shell">
      <header className="dashboard-header">
        <Brand />
        <div className="account-menu">
          <span className="account-avatar">{firstName.slice(0, 1).toUpperCase()}</span>
          <span className="account-name">{user.fullName}</span>
          <button className="button button-quiet button-small" onClick={logout} disabled={busy}>{busy ? 'Signing out…' : 'Sign out'}</button>
        </div>
      </header>
      <section className="dashboard-welcome">
        <p className="eyebrow"><span className="eyebrow-dot" /> YOUR STUDIO WORKSPACE</p>
        <h1>Good to have you here, <em>{firstName}.</em></h1>
        <p>Choose where you want to pick up today.</p>
      </section>
      {error && <p className="form-error dashboard-error" role="alert">{error}</p>}
      <nav className="module-tabs" aria-label="Workspace modules">
        <button className={activeTab === 'customers' ? 'module-tab active' : 'module-tab'} onClick={() => setActiveTab('customers')} aria-pressed={activeTab === 'customers'}>
          <span className="tab-index">01</span> Customer module <span className="tab-arrow">↗</span>
        </button>
        <button className={activeTab === 'data' ? 'module-tab active' : 'module-tab'} onClick={() => setActiveTab('data')} aria-pressed={activeTab === 'data'}>
          <span className="tab-index">02</span> Data module <span className="tab-arrow">↗</span>
        </button>
      </nav>
      {activeTab === 'customers' ? <CustomerPanel user={user} /> : <DataPanel />}
      <footer className="dashboard-footer"><Brand /><span>Make space for good work.</span><span>© 2026 FrameBucket</span></footer>
    </main>
  );
}

function CustomerPanel({ user }) {
  return (
    <section className="module-panel">
      <div className="panel-copy">
        <p className="eyebrow">YOUR PEOPLE, ALL IN ONE PLACE</p>
        <h2>Customer stories<br /><em>start here.</em></h2>
        <p>Keep client details, booked events, and order progress together. When you are ready, this is where you will onboard a customer and share their private status link.</p>
        <button className="button button-dark" disabled title="Customer management will be added next">Customer tools are coming next <span aria-hidden="true">↗</span></button>
      </div>
      <div className="panel-card">
        <div className="panel-card-top"><span>STUDIO PROFILE</span><span className="status-pill"><i /> ACCOUNT ACTIVE</span></div>
        <div className="profile-monogram">{user.fullName.slice(0, 1).toUpperCase()}</div>
        <h3>{user.fullName}</h3>
        <p>{user.studioName || 'Photographer account'}</p>
        <div className="profile-divider" />
        <div className="profile-detail"><span>EMAIL</span><b>{user.email}</b></div>
        <div className="profile-detail"><span>WORKSPACE</span><b>Photographer portal</b></div>
      </div>
    </section>
  );
}

function DataPanel() {
  return (
    <section className="module-panel data-panel">
      <div className="panel-copy">
        <p className="eyebrow">YOUR IMAGES, WITH A CLEAR NEXT STEP</p>
        <h2>Every frame has<br /><em>a place to go.</em></h2>
        <p>Upload event photos, prepare customer previews, collect album selections, and share finished work. The data tools will be added here.</p>
        <button className="button button-dark" disabled title="Photo uploads will be added next">Photo tools are coming next <span aria-hidden="true">↗</span></button>
      </div>
      <div className="upload-placeholder">
        <div className="upload-mark">＋</div>
        <b>Your collections will live here</b>
        <span>RAW uploads · JPEG previews · Album selections</span>
        <div className="upload-lines"><i /><i /><i /></div>
        <small>DATA MODULE / PREVIEW</small>
      </div>
    </section>
  );
}

function LoadingPage() {
  return <main className="loading-shell"><Brand /><p>Opening your workspace…</p></main>;
}
