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
        onRegistered={(email) => setNotice(`Account created for ${email}. Verify your email before signing in.`)}
      />
    );
  }

  if (path === '/verify-email') {
    return <VerifyEmailPage navigate={navigate} />;
  }

  if (path === '/payment') {
    return <PaymentPage navigate={navigate} />;
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

function Brand() {
  return (
    <a className="brand" href="/" aria-label="FrameBucket home">
      <img src="/framebucket-logo.png" alt="" />
    </a>
  );
}

function HomePage({ navigate }) {
  return (
    <main className="landing-shell">
      <header className="site-header">
        <Brand />
        <nav className="header-actions" aria-label="Main navigation">
          <button className="button button-quiet" onClick={() => document.getElementById('pricing')?.scrollIntoView({ behavior: 'smooth' })}>Pricing</button>
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
          <p className="hero-trial-offer">Register now to get a 7-day free trial.</p>
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

      <PricingSection navigate={navigate} />

      <footer className="site-footer"><Brand /><span>Thoughtful tools for people who make memories.</span><span>© 2026 FrameBucket</span></footer>
    </main>
  );
}

function formatInr(amount) {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(Number(amount));
}

function PricingSection({ navigate }) {
  const [plans, setPlans] = useState([]);
  const [billing, setBilling] = useState('MONTHLY');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    api.plans()
      .then((availablePlans) => {
        if (!cancelled) setPlans(availablePlans);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  return (
    <section className="pricing-section" id="pricing">
      <div className="pricing-heading">
        <div>
          <p className="eyebrow">SIMPLE PLANS FOR YOUR STUDIO</p>
          <h2>Choose room to <em>grow.</em></h2>
          <p>Every plan starts with a 7-day free trial. Prices are in INR, before GST.</p>
        </div>
        <div className="billing-picker" role="group" aria-label="Pricing period">
          <button className={billing === 'MONTHLY' ? 'selected' : ''} onClick={() => setBilling('MONTHLY')} aria-pressed={billing === 'MONTHLY'}>Monthly</button>
          <button className={billing === 'YEARLY' ? 'selected' : ''} onClick={() => setBilling('YEARLY')} aria-pressed={billing === 'YEARLY'}>Yearly <span>Save 10%</span></button>
        </div>
      </div>
      {loading && <p className="pricing-message">Loading plans…</p>}
      {error && <p className="form-error pricing-message">Could not load plans. Make sure the backend and PostgreSQL are running, then refresh. {error}</p>}
      {!loading && !error && (
        <>
          <div className="pricing-grid">
            {plans.map((plan) => {
              const selectedPrice = billing === 'YEARLY' ? plan.yearlyPrice : plan.monthlyPrice;
              return (
                <article className="pricing-card" key={plan.id}>
                  <p className="plan-code">{plan.code}</p>
                  <h3>{plan.name}</h3>
                  <p className="plan-description">{plan.description}</p>
                  <div className="plan-price">
                    <strong>{formatInr(selectedPrice)}</strong>
                    <span>/{billing === 'YEARLY' ? 'year' : 'month'}</span>
                  </div>
                  {billing === 'YEARLY' && (
                    <p className="plan-savings">Save {formatInr(plan.yearlySavings)} per year</p>
                  )}
                  <ul>
                    {plan.features.map((feature) => <li key={feature}>{feature}</li>)}
                  </ul>
                  {!plan.dataModuleEnabled && <p className="plan-restriction">Data module is not available on Bronze.</p>}
                  <button
                    className="button button-dark button-full"
                    onClick={() => navigate(`/payment?planCode=${encodeURIComponent(plan.code)}&billing=${billing}`)}
                  >
                    Select {plan.name.replace(' Plan', '')} <span aria-hidden="true">↗</span>
                  </button>
                </article>
              );
            })}
          </div>
          <PlanComparisonTable plans={plans} />
        </>
      )}
    </section>
  );
}

function PlanComparisonTable({ plans }) {
  const features = [
    { label: 'Photoshoot order tracking with customer alerts', key: 'customerOrderTracking' },
    { label: 'AI image scanning and photo delivery', key: 'aiImageDelivery' },
    { label: 'Quotation generator and calendar booking', key: 'quotationCalendar' },
    { label: 'Data module access', key: 'dataModuleEnabled' },
    { label: 'RAW photo selection for album design', key: 'rawPhotoSelectionEnabled' },
  ];

  return (
    <div className="comparison-section">
      <div className="comparison-heading">
        <h3>Compare plans</h3>
        <p>A tick means the feature is included in that subscription.</p>
      </div>
      <div className="comparison-scroll">
        <table className="plan-comparison">
          <thead>
            <tr>
              <th scope="col">Plan details</th>
              {plans.map((plan) => <th scope="col" key={plan.id}>{plan.name.replace(' Plan', '')}</th>)}
            </tr>
          </thead>
          <tbody>
            <tr className="comparison-price-row">
              <th scope="row">Monthly price</th>
              {plans.map((plan) => <td key={plan.id}>{formatInr(plan.monthlyPrice)}</td>)}
            </tr>
            <tr className="comparison-price-row">
              <th scope="row">Yearly price <span>10% discount</span></th>
              {plans.map((plan) => <td key={plan.id}>{formatInr(plan.yearlyPrice)}</td>)}
            </tr>
            {features.map((feature) => (
              <tr key={feature.key}>
                <th scope="row">{feature.label}</th>
                {plans.map((plan) => {
                  const included = plan[feature.key] === true;
                  return (
                    <td key={plan.id}>
                      <span className={included ? 'comparison-mark included' : 'comparison-mark excluded'} aria-label={included ? 'Included' : 'Not included'}>
                        {included ? '✓' : '—'}
                      </span>
                    </td>
                  );
                })}
              </tr>
            ))}
            <tr>
              <th scope="row">RAW photo storage</th>
              {plans.map((plan) => (
                <td key={plan.id}>{plan.maxStorageGb ? `Up to ${plan.maxStorageGb} GB` : '—'}</td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}

function PaymentPage({ navigate }) {
  const query = new URLSearchParams(window.location.search);
  const planCode = query.get('planCode') || '';
  const [billing, setBilling] = useState(query.get('billing') === 'YEARLY' ? 'YEARLY' : 'MONTHLY');
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    api.plans()
      .then((availablePlans) => {
        if (!cancelled) setPlans(availablePlans);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  const plan = plans.find((availablePlan) => availablePlan.code === planCode);
  const subtotal = plan
    ? Number(billing === 'YEARLY' ? plan.yearlyPrice : plan.monthlyPrice)
    : 0;
  const gst = Math.round((subtotal * 0.18 + Number.EPSILON) * 100) / 100;
  const total = Math.round((subtotal + gst + Number.EPSILON) * 100) / 100;

  return (
    <main className="payment-shell">
      <header className="payment-header">
        <Brand />
        <button className="button button-quiet" onClick={() => navigate('/')}>Back to pricing</button>
      </header>
      <section className="payment-content">
        <p className="eyebrow">PAYMENT · PREVIEW</p>
        <h1>Review your <em>plan.</em></h1>
        <p className="payment-intro">Choose a billing period and review the price, including 18% GST.</p>
        {loading && <p className="pricing-message">Loading plan details…</p>}
        {error && <p className="form-error" role="alert">Could not load plan details. {error}</p>}
        {!loading && !error && !plan && (
          <p className="form-error" role="alert">That plan is unavailable. Return to pricing and select an active plan.</p>
        )}
        {plan && (
          <div className="checkout-grid">
            <section className="checkout-card" aria-label="Order summary">
              <p className="plan-code">{plan.code}</p>
              <h2>{plan.name}</h2>
              <label className="billing-select-label" htmlFor="billing-period">Billing period</label>
              <select id="billing-period" value={billing} onChange={(event) => setBilling(event.target.value)}>
                <option value="MONTHLY">Monthly</option>
                <option value="YEARLY">Yearly · save 10%</option>
              </select>
              <dl className="price-breakdown">
                <div><dt>Plan price{billing === 'YEARLY' ? ' (10% annual discount applied)' : ''}</dt><dd>{formatInr(subtotal)}</dd></div>
                <div><dt>GST (18%)</dt><dd>{formatInr(gst)}</dd></div>
                <div className="price-total"><dt>Total</dt><dd>{formatInr(total)}</dd></div>
              </dl>
            </section>
            <section className="gateway-placeholder">
              <div className="gateway-icon">₹</div>
              <h2>Payment gateway coming soon</h2>
              <p>This is a checkout preview. No payment will be collected and your account will remain on its free trial until payment integration is added.</p>
              <button className="button button-dark button-full" disabled>Payment is not available yet</button>
            </section>
          </div>
        )}
      </section>
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
      onRegistered(form.email.trim());
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
  const [resending, setResending] = useState(false);
  const [resendNotice, setResendNotice] = useState('');

  async function resendVerification() {
    setError('');
    setResendNotice('');
    if (!email.trim()) {
      setError('Enter your email address above first.');
      return;
    }

    setResending(true);
    try {
      const response = await api.resendVerification(email.trim());
      setResendNotice(response.message);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setResending(false);
    }
  }

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
      <div className="verification-help">
        <span>Didn’t receive a verification email?</span>
        <button type="button" onClick={resendVerification} disabled={resending}>
          {resending ? 'Sending…' : 'Send a new link'}
        </button>
      </div>
      {resendNotice && <p className="form-success" role="status">{resendNotice}</p>}
      <p className="auth-switch">New to FrameBucket? <button onClick={() => navigate('/register')}>Create an account</button></p>
    </AuthLayout>
  );
}

function VerifyEmailPage({ navigate }) {
  const token = new URLSearchParams(window.location.search).get('token') || '';
  const [busy, setBusy] = useState(false);
  const [verified, setVerified] = useState(false);
  const [error, setError] = useState('');

  async function verify() {
    setError('');
    setBusy(true);
    try {
      await api.verifyEmail(token);
      setVerified(true);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      eyebrow="EMAIL VERIFICATION"
      title={verified ? <>Your email is<br /><em>confirmed.</em></> : <>One final step<br /><em>to open your studio.</em></>}
      subtitle={verified
        ? 'Your FrameBucket account is ready. Sign in to continue.'
        : 'Confirm your email address to activate your photographer account.'}
    >
      {error && <p className="form-error" role="alert">{error}</p>}
      {!token && <p className="form-error" role="alert">This verification link is missing its token. Request a new link from the sign-in page.</p>}
      {verified ? (
        <button className="button button-dark button-full" onClick={() => navigate('/login')}>
          Go to sign in <span aria-hidden="true">↗</span>
        </button>
      ) : token ? (
        <button className="button button-dark button-full" onClick={verify} disabled={busy}>
          {busy ? 'Confirming email…' : 'Confirm my email'} <span aria-hidden="true">↗</span>
        </button>
      ) : null}
      <p className="auth-switch">Already confirmed? <button onClick={() => navigate('/login')}>Go to sign in</button></p>
    </AuthLayout>
  );
}

function AuthLayout({ eyebrow, title, subtitle, children }) {
  return (
    <main className="auth-shell">
      <aside className="auth-side">
        <Brand />
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
  const dataModuleEnabled = user.dataModuleEnabled === true;

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
        <button
          className={activeTab === 'data' ? 'module-tab active' : 'module-tab'}
          onClick={() => setActiveTab('data')}
          aria-pressed={activeTab === 'data'}
          disabled={!dataModuleEnabled}
          title={dataModuleEnabled ? 'Open the Data module' : 'The Data module is not included with Bronze.'}
        >
          <span className="tab-index">02</span> Data module {!dataModuleEnabled && <span className="tab-lock">BRONZE</span>} <span className="tab-arrow">↗</span>
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
        <div className="profile-detail"><span>PLAN</span><b>{user.subscriptionName || 'No plan assigned'}</b></div>
        {user.trialEndsAt && <div className="profile-detail"><span>TRIAL ENDS</span><b>{new Date(user.trialEndsAt).toLocaleDateString()}</b></div>}
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
