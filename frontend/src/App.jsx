import { useEffect, useRef, useState } from 'react';
import { api } from './api.js';

function currentPath() {
  return window.location.pathname.replace(/\/+$/, '') || '/';
}

export default function App() {
  const [path, setPath] = useState(currentPath);
  const [user, setUser] = useState(null);
  const [customerProfile, setCustomerProfile] = useState(null);
  const [loadingSession, setLoadingSession] = useState(path === '/dashboard');
  const [loadingCustomerPortal, setLoadingCustomerPortal] = useState(path === '/customer-portal');
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

  useEffect(() => {
    if (path !== '/customer-portal' || customerProfile) {
      return undefined;
    }

    let cancelled = false;
    setLoadingCustomerPortal(true);
    api.customerMe()
      .then((profile) => {
        if (!cancelled) setCustomerProfile(profile);
      })
      .catch(() => {
        if (!cancelled) navigate('/login', true);
      })
      .finally(() => {
        if (!cancelled) setLoadingCustomerPortal(false);
      });

    return () => {
      cancelled = true;
    };
  }, [path, customerProfile]);

  if (path.startsWith('/quote/')) {
    const token = path.split('/')[2] || '';
    return <QuotationBuilderPage token={token} />;
  }

  if (path.startsWith('/team-invite/')) {
    const token = path.split('/')[2] || '';
    return <TeamInvitePage token={token} />;
  }

  if (path === '/register') {
    return (
      <RegisterPage
        navigate={navigate}
        onRegistered={(email) => setNotice(`Account created for ${email}. Verify your email and mobile number before signing in. In local mode, the mobile code appears in the backend console.`)}
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
        onLogin={(profile) => { setUser(profile); setCustomerProfile(null); }}
        onCustomerLogin={(profile) => { setUser(null); setCustomerProfile(profile); }}
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
          setCustomerProfile(null);
          navigate('/login', true);
        }}
      />
    );
  }

  if (path === '/customer-portal') {
    if (loadingCustomerPortal || !customerProfile) {
      return <LoadingPage />;
    }
    return (
      <CustomerPortalPage
        customer={customerProfile}
        onLogout={async () => {
          await api.logout();
          setCustomerProfile(null);
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
      <img className="brand-mark" src="/framebucket-mark.png" alt="" aria-hidden="true" />
      <span className="brand-name">FrameBucket</span>
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

function displayEventPrice(price) {
  if (price == null || String(price).trim() === '') return '—';
  const numericPrice = Number(price);
  return Number.isFinite(numericPrice) ? formatInr(numericPrice) : price;
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
  const [form, setForm] = useState({ fullName: '', studioName: '', email: '', mobileNumber: '', password: '', confirmPassword: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  function update(event) {
    setForm({ ...form, [event.target.name]: event.target.value });
  }

  async function submit(event) {
    event.preventDefault();
    setError('');
    if (form.password !== form.confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
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
        <label>Mobile number<input name="mobileNumber" type="tel" value={form.mobileNumber} onChange={update} autoComplete="tel" required maxLength="13" pattern="([0-9]{10}|\+91[0-9]{10})" placeholder="9876543210" aria-describedby="mobile-format-hint" /><span id="mobile-format-hint" className="field-hint">Enter 10 digits. You may also include +91.</span></label>
        <label>Password<input name="password" type="password" value={form.password} onChange={update} autoComplete="new-password" required minLength="12" maxLength="64" placeholder="At least 12 characters" /></label>
        <label>Confirm password<input name="confirmPassword" type="password" value={form.confirmPassword} onChange={update} autoComplete="new-password" required minLength="12" maxLength="64" placeholder="Enter the same password again" /></label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button button-dark button-full" type="submit" disabled={busy}>
          {busy ? 'Creating your account…' : 'Create photographer account'} <span aria-hidden="true">↗</span>
        </button>
        <p className="form-footnote">We’ll send a verification link to your email and a one-time code to your mobile. Your password is stored as a one-way hash.</p>
      </form>
      <p className="auth-switch">Already registered? <button onClick={() => navigate('/login')}>Log in</button></p>
    </AuthLayout>
  );
}

function LoginPage({ navigate, notice, clearNotice, onLogin, onCustomerLogin }) {
  const [accountType, setAccountType] = useState('photographer');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [resending, setResending] = useState(false);
  const [resendNotice, setResendNotice] = useState('');
  const [needsMobileVerification, setNeedsMobileVerification] = useState(false);
  const [mobileCode, setMobileCode] = useState('');
  const [mobileBusy, setMobileBusy] = useState(false);
  const [mobileNotice, setMobileNotice] = useState('');
  const [customerMobile, setCustomerMobile] = useState('');
  const [customerCodeSent, setCustomerCodeSent] = useState(false);
  const [customerCode, setCustomerCode] = useState('');
  const [customerBusy, setCustomerBusy] = useState(false);
  const [customerNotice, setCustomerNotice] = useState('');

  function chooseAccountType(type) {
    setAccountType(type);
    setError('');
    setNeedsMobileVerification(false);
    setCustomerNotice('');
  }

  async function resendVerification() {
    setError('');
    setResendNotice('');
    if (!username.trim()) {
      setError('Enter your email address above first.');
      return;
    }
    if (!username.includes('@')) {
      setError('Enter your email address above to resend the verification link.');
      return;
    }

    setResending(true);
    try {
      const response = await api.resendVerification(username.trim());
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
      const profile = await api.login({ username, password });
      onLogin(profile);
      navigate('/dashboard');
      clearNotice();
    } catch (requestError) {
      setError(requestError.message);
      if (/verify your mobile/i.test(requestError.message)) {
        setNeedsMobileVerification(true);
      }
    } finally {
      setBusy(false);
    }
  }

  async function verifyMobile(event) {
    event.preventDefault();
    setError('');
    setMobileNotice('');
    setMobileBusy(true);
    try {
      const response = await api.verifyMobile(username.trim(), mobileCode);
      setNeedsMobileVerification(false);
      setMobileCode('');
      setMobileNotice(response.message);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setMobileBusy(false);
    }
  }

  async function resendMobileCode() {
    setError('');
    setMobileNotice('');
    const identifier = username.trim();
    const isMobileNumber = /^(?:[0-9]{10}|\+91[0-9]{10})$/.test(identifier);
    if (!identifier || (!identifier.includes('@') && !isMobileNumber)) {
      setError('Enter your email address or mobile number above to resend the verification code.');
      return;
    }
    setMobileBusy(true);
    try {
      const response = await api.resendMobileCode(identifier);
      setMobileNotice(response.message);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setMobileBusy(false);
    }
  }

  async function requestCustomerCode(event) {
    event?.preventDefault();
    setError('');
    setCustomerNotice('');
    if (!customerMobile.trim()) {
      setError('Enter your registered mobile number first.');
      return;
    }
    setCustomerBusy(true);
    try {
      const response = await api.requestCustomerOtp(customerMobile.trim());
      setCustomerCodeSent(true);
      setCustomerCode('');
      setCustomerNotice(response.message);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setCustomerBusy(false);
    }
  }

  async function verifyCustomerCode(event) {
    event.preventDefault();
    setError('');
    setCustomerBusy(true);
    try {
      const profile = await api.verifyCustomerOtp({ mobileNumber: customerMobile.trim(), code: customerCode });
      onCustomerLogin(profile);
      clearNotice();
      navigate('/customer-portal');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setCustomerBusy(false);
    }
  }

  return (
    <AuthLayout eyebrow="WELCOME BACK" title={accountType === 'photographer' ? <>Pick up right<br /><em>where you left off.</em></> : <>Your memories<br /><em>are close by.</em></>} subtitle={accountType === 'photographer' ? 'Sign in to see what is happening across your studio.' : 'Sign in to view the events you have booked with your photographer.'}>
      <div className="login-account-types" role="tablist" aria-label="Choose account type">
        <button type="button" role="tab" aria-selected={accountType === 'photographer'} className={accountType === 'photographer' ? 'login-account-type active' : 'login-account-type'} onClick={() => chooseAccountType('photographer')}>Photographer</button>
        <button type="button" role="tab" aria-selected={accountType === 'customer'} className={accountType === 'customer' ? 'login-account-type active' : 'login-account-type'} onClick={() => chooseAccountType('customer')}>Customer</button>
      </div>
      {accountType === 'photographer' ? (
        <>
          {notice && <p className="form-success" role="status">{notice}</p>}
          <form className="auth-form" onSubmit={submit}>
            <label>Email or mobile number<input type="text" value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required maxLength="320" placeholder="you@yourstudio.com or 9876543210" /></label>
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
          {needsMobileVerification && (
            <form className="auth-form mobile-verification-form" onSubmit={verifyMobile}>
              <p className="verification-prompt">Enter the 6-digit code sent to your mobile number. In local mode, find it in the backend console.</p>
              <label>Mobile verification code<input type="text" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength="6" value={mobileCode} onChange={(event) => setMobileCode(event.target.value.replace(/\D/g, '').slice(0, 6))} required placeholder="000000" /></label>
              <button className="button button-dark button-full" type="submit" disabled={mobileBusy || mobileCode.length !== 6}>
                {mobileBusy ? 'Verifying…' : 'Verify mobile number'} <span aria-hidden="true">↗</span>
              </button>
              <div className="verification-help"><span>Code expired or missing?</span><button type="button" onClick={resendMobileCode} disabled={mobileBusy}>{mobileBusy ? 'Sending…' : 'Send a new code'}</button></div>
            </form>
          )}
          {mobileNotice && <p className="form-success" role="status">{mobileNotice}</p>}
        </>
      ) : (
        <form className="auth-form customer-login-form" onSubmit={customerCodeSent ? verifyCustomerCode : requestCustomerCode}>
          {customerCodeSent ? (
            <>
              <p className="form-success" role="status">{customerNotice || 'Check your registered email and mobile for the same one-time code.'}</p>
              <label>Registered mobile number<input type="tel" value={customerMobile} readOnly /></label>
              <label>One-time code<input type="text" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength="6" value={customerCode} onChange={(event) => setCustomerCode(event.target.value.replace(/\D/g, '').slice(0, 6))} required placeholder="000000" /></label>
              {error && <p className="form-error" role="alert">{error}</p>}
              <button className="button button-dark button-full" type="submit" disabled={customerBusy || customerCode.length !== 6}>
                {customerBusy ? 'Verifying…' : 'Verify code and continue'} <span aria-hidden="true">↗</span>
              </button>
              <div className="verification-help customer-otp-actions">
                <button type="button" onClick={requestCustomerCode} disabled={customerBusy}>{customerBusy ? 'Sending…' : 'Resend code'}</button>
                <button type="button" onClick={() => { setCustomerCodeSent(false); setCustomerCode(''); setCustomerNotice(''); setError(''); }}>Use another number</button>
              </div>
            </>
          ) : (
            <>
              <p className="verification-prompt">Enter your registered mobile number. We’ll send the same sign-in code to the email and mobile number on file.</p>
              <label>Mobile number<input type="tel" value={customerMobile} onChange={(event) => setCustomerMobile(event.target.value)} autoComplete="tel" required maxLength="13" pattern="([0-9]{10}|\+91[0-9]{10})" placeholder="9876543210" /></label>
              {error && <p className="form-error" role="alert">{error}</p>}
              <button className="button button-dark button-full" type="submit" disabled={customerBusy}>
                {customerBusy ? 'Sending code…' : 'Send sign-in code'} <span aria-hidden="true">↗</span>
              </button>
            </>
          )}
        </form>
      )}
      {accountType === 'photographer' ? (
        <p className="auth-switch">New to FrameBucket? <button onClick={() => navigate('/register')}>Create an account</button></p>
      ) : (
        <p className="auth-switch">Need access? Ask your photographer to add your customer details.</p>
      )}
    </AuthLayout>
  );
}

function VerifyEmailPage({ navigate }) {
  const token = new URLSearchParams(window.location.search).get('token') || '';
  const [busy, setBusy] = useState(false);
  const [verified, setVerified] = useState(false);
  const [verifiedEmail, setVerifiedEmail] = useState('');
  const [mobileCode, setMobileCode] = useState('');
  const [mobileVerified, setMobileVerified] = useState(false);
  const [mobileBusy, setMobileBusy] = useState(false);
  const [mobileNotice, setMobileNotice] = useState('');
  const [error, setError] = useState('');

  async function verify() {
    setError('');
    setBusy(true);
    try {
      const response = await api.verifyEmail(token);
      setVerifiedEmail(response.email);
      setVerified(true);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  async function verifyMobile(event) {
    event.preventDefault();
    setError('');
    setMobileNotice('');
    setMobileBusy(true);
    try {
      const response = await api.verifyMobile(verifiedEmail, mobileCode);
      setMobileVerified(true);
      setMobileNotice(response.message);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setMobileBusy(false);
    }
  }

  async function resendMobileCode() {
    setError('');
    setMobileNotice('');
    setMobileBusy(true);
    try {
      const response = await api.resendMobileCode(verifiedEmail);
      setMobileNotice(response.message);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setMobileBusy(false);
    }
  }

  return (
    <AuthLayout
      eyebrow="EMAIL VERIFICATION"
      title={mobileVerified ? <>Your account is<br /><em>confirmed.</em></> : verified ? <>Verify your<br /><em>mobile number.</em></> : <>One final step<br /><em>to open your studio.</em></>}
      subtitle={verified
        ? (mobileVerified ? 'Your FrameBucket account is ready. Sign in to continue.' : 'Your email is confirmed. Enter the one-time code sent to your mobile number to finish activating the account.')
        : 'Confirm your email address to activate your photographer account.'}
    >
      {error && <p className="form-error" role="alert">{error}</p>}
      {!token && <p className="form-error" role="alert">This verification link is missing its token. Request a new link from the sign-in page.</p>}
      {verified ? (
        mobileVerified ? (
          <button className="button button-dark button-full" onClick={() => navigate('/login')}>
            Go to sign in <span aria-hidden="true">↗</span>
          </button>
        ) : (
          <form className="auth-form" onSubmit={verifyMobile}>
            <p className="verification-prompt">Enter the 6-digit code sent to your mobile number. In local mode, find it in the backend console.</p>
            <label>Mobile verification code<input type="text" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength="6" value={mobileCode} onChange={(event) => setMobileCode(event.target.value.replace(/\D/g, '').slice(0, 6))} required placeholder="000000" /></label>
            <button className="button button-dark button-full" type="submit" disabled={mobileBusy || mobileCode.length !== 6}>
              {mobileBusy ? 'Verifying…' : 'Verify mobile number'} <span aria-hidden="true">↗</span>
            </button>
            <div className="verification-help"><span>Code expired or missing?</span><button type="button" onClick={resendMobileCode} disabled={mobileBusy}>{mobileBusy ? 'Sending…' : 'Send a new code'}</button></div>
            {mobileNotice && <p className="form-success" role="status">{mobileNotice}</p>}
          </form>
        )
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
  const [activeTab, setActiveTab] = useState('overview');
  const [dataEventId, setDataEventId] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const profileMenuRef = useRef(null);
  const profileHeadingRef = useRef(null);
  const firstName = user.fullName.split(' ')[0];
  const dataModuleEnabled = user.dataModuleEnabled === true;

  useEffect(() => {
    if (!profileOpen) return undefined;
    profileHeadingRef.current?.focus();

    function closeFromOutside(event) {
      if (!profileMenuRef.current?.contains(event.target)) {
        setProfileOpen(false);
      }
    }

    function closeOnEscape(event) {
      if (event.key === 'Escape') {
        setProfileOpen(false);
        profileMenuRef.current?.querySelector('button')?.focus();
      }
    }

    window.addEventListener('pointerdown', closeFromOutside);
    window.addEventListener('keydown', closeOnEscape);
    return () => {
      window.removeEventListener('pointerdown', closeFromOutside);
      window.removeEventListener('keydown', closeOnEscape);
    };
  }, [profileOpen]);

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
          <div className="photographer-profile" ref={profileMenuRef}>
            <button
              className="photographer-profile-trigger"
              type="button"
              aria-expanded={profileOpen}
              aria-haspopup="dialog"
              aria-controls="photographer-profile-popover"
              aria-label="Show photographer profile"
              onClick={() => setProfileOpen((open) => !open)}
            >
              <span className="account-avatar" aria-hidden="true">{firstName.slice(0, 1).toUpperCase()}</span>
              <span className="account-name">{user.fullName}</span>
              <span className="profile-menu-chevron" aria-hidden="true">⌄</span>
            </button>
            {profileOpen && (
              <section
                className="photographer-profile-popover"
                id="photographer-profile-popover"
                role="dialog"
                aria-labelledby="photographer-profile-title"
              >
                <p className="photographer-profile-kicker">ACCOUNT DETAILS</p>
                <h2 id="photographer-profile-title" ref={profileHeadingRef} tabIndex={-1}>Photographer profile</h2>
                <dl className="photographer-profile-details">
                  <div><dt>Photographer ID</dt><dd className="photographer-profile-id">{user.id || 'Unavailable'}</dd></div>
                  <div><dt>Name</dt><dd>{user.fullName || 'Unavailable'}</dd></div>
                  <div><dt>Email</dt><dd>{user.email || 'Unavailable'}</dd></div>
                  <div><dt>Mobile number</dt><dd>{user.mobileNumber || 'Unavailable'}</dd></div>
                </dl>
              </section>
            )}
          </div>
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
        <button className={activeTab === 'overview' ? 'module-tab active' : 'module-tab'} onClick={() => setActiveTab('overview')} aria-pressed={activeTab === 'overview'}>
          Overview <span className="tab-arrow">↗</span>
        </button>
        <button className={activeTab === 'enquiries' ? 'module-tab active' : 'module-tab'} onClick={() => setActiveTab('enquiries')} aria-pressed={activeTab === 'enquiries'}>
          Enquiries <span className="tab-arrow">↗</span>
        </button>
        <button className={activeTab === 'customers' ? 'module-tab active' : 'module-tab'} onClick={() => setActiveTab('customers')} aria-pressed={activeTab === 'customers'}>
          Customers <span className="tab-arrow">↗</span>
        </button>
        <button className={activeTab === 'events' ? 'module-tab active' : 'module-tab'} onClick={() => setActiveTab('events')} aria-pressed={activeTab === 'events'}>
          Events <span className="tab-arrow">↗</span>
        </button>
        <button className={activeTab === 'team' ? 'module-tab active' : 'module-tab'} onClick={() => setActiveTab('team')} aria-pressed={activeTab === 'team'}>
          Team <span className="tab-arrow">↗</span>
        </button>
        <button
          className={activeTab === 'data' ? 'module-tab active' : 'module-tab'}
          onClick={() => setActiveTab('data')}
          aria-pressed={activeTab === 'data'}
          disabled={!dataModuleEnabled}
          title={dataModuleEnabled ? 'Open the Data module' : 'The Data module is not included with Bronze.'}
        >
          Data {!dataModuleEnabled && <span className="tab-lock">BRONZE</span>} <span className="tab-arrow">↗</span>
        </button>
      </nav>
      <p className="workflow-sequence">Photography workflow <b>Enquiries</b><span>→</span><b>Customers</b><span>→</span><b>Events</b><span>→</span><b>Team</b><span>→</span><b>Data</b></p>
      {activeTab === 'overview' ? <DashboardOverview user={user} /> : activeTab === 'enquiries' ? <EnquiryPanel user={user} /> : activeTab === 'customers' ? <CustomerPanel user={user} /> : activeTab === 'events' ? <EventPanel user={user} onOpenGallery={(event) => { setDataEventId(event.id); setActiveTab('data'); }} /> : activeTab === 'team' ? <TeamPanel user={user} /> : <DataPanel initialEventId={dataEventId} />}
      <footer className="dashboard-footer"><Brand /><span>Your growth counts.</span><span>© 2026 FrameBucket</span></footer>
    </main>
  );
}

function DashboardOverview({ user }) {
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    api.dashboardSummary()
      .then((data) => {
        if (!cancelled) setSummary(data);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  const activity = summary?.activityThisMonth;
  const activityItems = [
    { label: 'Enquiries', value: activity?.enquiries || 0, color: '#d2b36b' },
    { label: 'Customers', value: activity?.customers || 0, color: '#ad8748' },
    { label: 'Events', value: activity?.events || 0, color: '#79613c' },
  ];
  const revenueItems = (summary?.revenueByEventTypeFinancialYear || []).map((item, index) => ({
    label: item.eventTypeName,
    value: Number(item.revenue),
    color: ['#d2b36b', '#ad8748', '#79613c', '#abb18b', '#c6b99e', '#937853'][index % 6],
  }));
  const studioName = user.studioName || user.fullName;

  return (
    <section className="dashboard-overview" aria-labelledby="dashboard-overview-title">
      <div className="dashboard-overview-heading">
        <div>
          <p className="eyebrow">YOUR STUDIO AT A GLANCE</p>
          <h2 id="dashboard-overview-title">{studioName}</h2>
          <p>Monthly activity and event revenue in one place.</p>
        </div>
        {summary && <span className="overview-period">{summary.monthLabel}</span>}
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      {loading ? (
        <p className="overview-loading">Loading your studio overview…</p>
      ) : summary ? (
        <>
          <div className="overview-revenue-summary">
            <article>
              <span>REVENUE THIS MONTH</span>
              <strong>{formatInr(Number(summary.revenueThisMonth))}</strong>
              <small>Event bookings created in {summary.monthLabel}</small>
            </article>
            <article>
              <span>REVENUE THIS FINANCIAL YEAR</span>
              <strong>{formatInr(Number(summary.revenueThisFinancialYear))}</strong>
              <small>Since April 1, {summary.financialYearLabel.split('–')[0]}</small>
            </article>
          </div>
          <div className="overview-charts-grid">
            <PieChartCard
              title={"New activity · " + summary.monthLabel}
              description="Customers, events, and enquiries created this month."
              items={activityItems}
              formatValue={(value) => String(value)}
              emptyMessage="No new records this month yet."
            />
            <PieChartCard
              title={"Revenue by event type · FY " + summary.financialYearLabel}
              description="Event prices recorded on bookings created this financial year."
              items={revenueItems}
              formatValue={(value) => formatInr(value)}
              emptyMessage="No priced event bookings recorded this financial year yet."
            />
          </div>
          <p className="overview-revenue-note">Revenue is calculated from the event prices recorded in your bookings; payment tracking is not included yet.</p>
        </>
      ) : null}
    </section>
  );
}

function PieChartCard({ title, description, items, formatValue, emptyMessage }) {
  const total = items.reduce((sum, item) => sum + Math.max(0, Number(item.value) || 0), 0);
  const slices = items.filter((item) => Number(item.value) > 0);
  let angle = -Math.PI / 2;
  const chartSlices = slices.map((item) => {
    const value = Number(item.value);
    const sweep = total > 0 ? (value / total) * Math.PI * 2 : 0;
    const start = angle;
    angle += sweep;
    return { ...item, start, sweep };
  });

  return (
    <article className="overview-chart-card">
      <header>
        <h3>{title}</h3>
        <p>{description}</p>
      </header>
      <div className="overview-pie-layout">
        <svg className="overview-pie" viewBox="0 0 220 220" role="img" aria-label={title + " pie chart"}>
          <title>{title + ". Total " + formatValue(total) + "."}</title>
          {total <= 0 ? <circle cx="110" cy="110" r="98" fill="var(--surface-raised)" /> : chartSlices.length === 1 ? (
            <circle cx="110" cy="110" r="98" fill={chartSlices[0].color}>
              <title>{chartSlices[0].label + ": " + formatValue(chartSlices[0].value) + ", 100%"}</title>
            </circle>
          ) : chartSlices.map((slice) => {
            const radius = 98;
            const startX = 110 + radius * Math.cos(slice.start);
            const startY = 110 + radius * Math.sin(slice.start);
            const endAngle = slice.start + slice.sweep;
            const endX = 110 + radius * Math.cos(endAngle);
            const endY = 110 + radius * Math.sin(endAngle);
            const largeArc = slice.sweep > Math.PI ? 1 : 0;
            const path = 'M 110 110 L ' + startX + ' ' + startY + ' A ' + radius + ' ' + radius + ' 0 ' + largeArc + ' 1 ' + endX + ' ' + endY + ' Z';
            const percentage = Math.round((slice.value / total) * 100);
            return (
              <path d={path} fill={slice.color} stroke="var(--paper)" strokeWidth="2" key={slice.label}>
                <title>{slice.label + ': ' + formatValue(slice.value) + ', ' + percentage + '%'}</title>
              </path>
            );
          })}
        </svg>
        <ul className="overview-pie-legend">
          {items.length === 0
            ? <li className="overview-pie-empty">{emptyMessage}</li>
            : items.map((item) => {
              const value = Math.max(0, Number(item.value) || 0);
              const percentage = total > 0 ? Math.round((value / total) * 100) : 0;
              return (
                <li key={item.label}>
                  <span className="overview-legend-swatch" style={{ backgroundColor: item.color }} />
                  <span className="overview-legend-label">{item.label}</span>
                  <b>{formatValue(value)}</b>
                  <small>{percentage}%</small>
                </li>
              );
            })}
        </ul>
      </div>
      {total <= 0 && items.length > 0 && <p className="overview-chart-empty">{emptyMessage}</p>}
    </article>
  );
}

function EnquiryPanel({ user }) {
  const [enquiries, setEnquiries] = useState([]);
  const [eventTypes, setEventTypes] = useState([]);
  const [finalizedQuoteDrafts, setFinalizedQuoteDrafts] = useState({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [savingQuoteId, setSavingQuoteId] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [createEventInfo, setCreateEventInfo] = useState(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.enquiries(), api.eventTypes()])
      .then(([loadedEnquiries, loadedTypes]) => {
        if (cancelled) return;
        setEnquiries(loadedEnquiries);
        setEventTypes(loadedTypes);
        setFinalizedQuoteDrafts(Object.fromEntries(loadedEnquiries.map((item) => [item.id, item.finalizedQuote ?? ''])));
      })
      .catch((requestError) => { if (!cancelled) setError(requestError.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  async function createEnquiry(form) {
    setError('');
    setNotice('');
    setBusy(true);
    try {
      const created = await api.createEnquiry(form);
      setEnquiries((current) => [created, ...current]);
      setShowCreate(false);
      setNotice(`Enquiry ${created.enquiryId} was created. The customer notification contains the quotation link.`);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  async function createEventType(name) {
    const created = await api.createEventType(name);
    setEventTypes((current) => [...current, created].sort((left, right) => left.name.localeCompare(right.name)));
    return created;
  }

  async function saveFinalizedQuote(enquiry) {
    const draft = finalizedQuoteDrafts[enquiry.id] ?? enquiry.finalizedQuote ?? '';
    if (draft === '' || !Number.isFinite(Number(draft)) || Number(draft) < 0) {
      setError('Enter a valid finalized quote amount first.');
      return;
    }
    setSavingQuoteId(enquiry.id);
    setError('');
    try {
      const result = await api.saveEnquiryFinalizedQuote(enquiry.id, Number(draft));
      setEnquiries((current) => current.map((item) => item.id === enquiry.id ? { ...item, finalizedQuote: result.finalizedQuote } : item));
      setFinalizedQuoteDrafts((current) => ({ ...current, [enquiry.id]: String(result.finalizedQuote) }));
      setNotice(`Finalized quote for ${enquiry.name} was saved.`);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSavingQuoteId('');
    }
  }

  async function openCreateEvent(enquiry) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const setup = await api.enquiryEventSetup(enquiry.id);
      setCreateEventInfo({ enquiry, setup });
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  function hasSavedFinalizedQuote(enquiry) {
    const draft = finalizedQuoteDrafts[enquiry.id];
    return enquiry.finalizedQuote !== null && enquiry.finalizedQuote !== undefined
      && draft !== '' && draft !== undefined && Number(draft) === Number(enquiry.finalizedQuote);
  }

  async function createEventFromEnquiry(form) {
    setBusy(true);
    setError('');
    try {
      const result = await api.createEventFromEnquiry(createEventInfo.enquiry.id, form);
      setEnquiries((current) => current.map((item) => item.id === createEventInfo.enquiry.id
        ? {
          ...item,
          finalizedQuote: result.finalizedQuote,
          convertedCustomerCode: result.customerCode,
          createdEventId: result.eventId,
          createdEventCode: result.eventCode,
        }
        : item));
      setFinalizedQuoteDrafts((current) => ({ ...current, [createEventInfo.enquiry.id]: String(result.finalizedQuote) }));
      setCreateEventInfo(null);
      setNotice(`Event ${result.eventCode} was created for ${createEventInfo.enquiry.name}. The finalized quotation PDF is ready.`);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="enquiry-module" aria-labelledby="enquiry-list-title">
      <div className="customer-module-intro">
        <p className="eyebrow">THE FIRST STEP IN YOUR PHOTO WORKFLOW</p>
        <h2>Enquiries <em>start here.</em></h2>
        <p>Keep new requests together, send each customer a quotation link, save your negotiated amount, then create the event.</p>
      </div>
      {error && <p className="form-error customer-message" role="alert">{error}</p>}
      {notice && <p className="form-success customer-message" role="status">{notice}</p>}
      <section className="customer-list-section" aria-labelledby="enquiry-list-title">
        <div className="customer-list-heading">
          <div><p className="eyebrow">NEW CUSTOMER REQUESTS</p><h3 id="enquiry-list-title">Enquiries</h3><p>Enquiry IDs combine your studio sub ID with their creation time.</p></div>
          <button className="button button-dark" type="button" onClick={() => { setError(''); setNotice(''); setShowCreate(true); }}>＋ New enquiry</button>
        </div>
        {loading ? <p className="customer-empty">Loading enquiries…</p> : enquiries.length === 0 ? (
          <div className="customer-empty-state"><div className="upload-mark">＋</div><h4>Your enquiry list is ready.</h4><p>Create an enquiry to send a customer a secure quotation builder link.</p><button className="button button-dark" type="button" onClick={() => setShowCreate(true)}>Create your first enquiry <span aria-hidden="true">↗</span></button></div>
        ) : (
          <div className="customer-table-scroll">
            <table className="customer-table enquiry-table">
              <thead><tr><th scope="col">Enquiry ID</th><th scope="col">Enquiry Event</th><th scope="col">Enquiry Name</th><th scope="col">Enquiry Number</th><th scope="col">Enquiry Email ID</th><th scope="col">Enquiry Quotation</th><th scope="col">Finalized Quotation</th><th scope="col">Workflow</th></tr></thead>
              <tbody>{enquiries.map((enquiry) => (
                <tr key={enquiry.id}>
                  <th scope="row"><span className="event-code">{enquiry.enquiryId}</span></th>
                  <td>{enquiry.eventName}</td>
                  <td>{enquiry.name}</td>
                  <td>{enquiry.mobileNumber}</td>
                  <td>{enquiry.email}</td>
                  <td>{enquiry.quotationId
                    ? <a className="customer-id-link" href={api.enquiryQuotationUrl(enquiry.id)} target="_blank" rel="noreferrer">{formatInr(Number(enquiry.quotationPrice))} ↗</a>
                    : <span className="enquiry-pending">Waiting for customer</span>}</td>
                  <td className="finalized-quote-cell">{enquiry.createdEventId
                    ? <a className="customer-id-link finalized-quote-link" href={api.enquiryFinalizedQuotationUrl(enquiry.id)} target="_blank" rel="noreferrer">{formatInr(Number(enquiry.finalizedQuote))} ↗</a>
                    : <div className="finalized-quote-editor"><span>INR</span><input aria-label={`Finalized quote for ${enquiry.name}`} type="number" min="0" step="0.01" value={finalizedQuoteDrafts[enquiry.id] ?? ''} onChange={(event) => setFinalizedQuoteDrafts((current) => ({ ...current, [enquiry.id]: event.target.value }))} disabled={!enquiry.quotationId || savingQuoteId === enquiry.id} /><button className="button button-quiet finalized-quote-save" type="button" onClick={() => saveFinalizedQuote(enquiry)} disabled={!enquiry.quotationId || savingQuoteId === enquiry.id || finalizedQuoteDrafts[enquiry.id] === ''}>{savingQuoteId === enquiry.id ? 'Saving…' : 'Save'}</button></div>}</td>
                  <td>{enquiry.createdEventCode
                    ? <span className="enquiry-converted">Event {enquiry.createdEventCode}</span>
                    : enquiry.quotationId
                      ? hasSavedFinalizedQuote(enquiry)
                        ? <button className="button button-quiet enquiry-event-button" type="button" onClick={() => openCreateEvent(enquiry)} disabled={busy || savingQuoteId === enquiry.id}>Create Event</button>
                        : <span className="enquiry-pending">Save final quote first</span>
                      : <span className="enquiry-pending">After quotation</span>}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </section>
      {showCreate && <EnquiryRegistrationModal eventTypes={eventTypes} busy={busy} onClose={() => setShowCreate(false)} onCreate={createEnquiry} onCreateEventType={createEventType} />}
      {createEventInfo && <CreateEventFromEnquiryModal enquiry={createEventInfo.enquiry} setup={createEventInfo.setup} busy={busy} error={error} onClose={() => setCreateEventInfo(null)} onCreate={createEventFromEnquiry} />}
      <p className="event-module-caption">{user.studioName || user.fullName} · Next step: Event creation</p>
    </section>
  );
}

function EnquiryRegistrationModal({ eventTypes, busy, onClose, onCreate, onCreateEventType }) {
  const [form, setForm] = useState({ name: '', mobileNumber: '', email: '', eventTypeId: eventTypes[0]?.id || '' });
  const [showTypePopup, setShowTypePopup] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!form.eventTypeId && eventTypes.length > 0) {
      setForm((current) => ({ ...current, eventTypeId: eventTypes[0].id }));
    }
  }, [eventTypes, form.eventTypeId]);

  function update(event) {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  }

  function submit(event) {
    event.preventDefault();
    setError('');
    onCreate(form).catch((requestError) => setError(requestError.message));
  }

  async function addEventType(name) {
    const created = await onCreateEventType(name);
    setForm((current) => ({ ...current, eventTypeId: created.id }));
    setShowTypePopup(false);
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose(); }}>
      <section className="event-modal enquiry-modal" role="dialog" aria-modal="true" aria-labelledby="enquiry-registration-title">
        <div className="modal-heading"><div><p className="eyebrow">NEW CUSTOMER REQUEST</p><h2 id="enquiry-registration-title">Create enquiry</h2></div><button className="modal-close" type="button" onClick={onClose} aria-label="Close enquiry form" disabled={busy}>×</button></div>
        <p className="enquiry-modal-note">The customer will receive an email and WhatsApp message with a secure quotation link.</p>
        {error && <p className="form-error" role="alert">{error}</p>}
        <form className="event-form" onSubmit={submit}>
          <label className="event-form-label">Enquiry Name<input name="name" value={form.name} onChange={update} autoComplete="name" required maxLength="120" placeholder="Customer’s full name" /></label>
          <label className="event-form-label">Enquiry Number<input name="mobileNumber" type="tel" value={form.mobileNumber} onChange={update} required maxLength="13" pattern="([0-9]{10}|\+91[0-9]{10})" placeholder="9876543210" /><span className="field-hint">Enter 10 digits. You may also include +91.</span></label>
          <label className="event-form-label">Enquiry Email ID<input name="email" type="email" value={form.email} onChange={update} autoComplete="email" required maxLength="320" placeholder="customer@example.com" /></label>
          <label className="event-form-label">Enquiry Event<span className="event-type-picker"><select name="eventTypeId" value={form.eventTypeId} onChange={update} required><option value="" disabled>Select an event</option>{eventTypes.map((type) => <option value={type.id} key={type.id}>{type.name}</option>)}</select><button className="button button-quiet button-small" type="button" onClick={() => setShowTypePopup(true)}>＋ New event</button></span></label>
          <div className="event-form-actions"><button className="button button-quiet" type="button" onClick={onClose} disabled={busy}>Cancel</button><button className="button button-dark" type="submit" disabled={busy || !form.eventTypeId}>{busy ? 'Creating enquiry…' : 'Create enquiry'} <span aria-hidden="true">↗</span></button></div>
        </form>
        {showTypePopup && <EventTypeModal onClose={() => setShowTypePopup(false)} onCreate={addEventType} />}
      </section>
    </div>
  );
}

function EventDateCalendar({ blockedDates, selectedDate, onSelect }) {
  const [visibleMonth, setVisibleMonth] = useState(() => selectedDate
    ? new Date(`${selectedDate}T00:00:00`)
    : new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const blockedSet = new Set(blockedDates);
  const cells = getCalendarCells(visibleMonth);

  useEffect(() => {
    if (selectedDate) setVisibleMonth(new Date(`${selectedDate}T00:00:00`));
  }, [selectedDate]);

  return (
    <fieldset className="event-date-field">
      <legend>Event Date</legend>
      <div className="calendar-heading">
        <button type="button" className="calendar-nav" onClick={() => setVisibleMonth(new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() - 1, 1))} aria-label="Previous month">‹</button>
        <strong>{visibleMonth.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}</strong>
        <button type="button" className="calendar-nav" onClick={() => setVisibleMonth(new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() + 1, 1))} aria-label="Next month">›</button>
      </div>
      <div className="calendar-grid calendar-weekdays" aria-hidden="true">{['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'].map((day) => <span key={day}>{day}</span>)}</div>
      <div className="calendar-grid">
        {cells.map((date, index) => {
          if (!date) return <span className="calendar-empty" key={`empty-${index}`} />;
          const value = dateString(date);
          const booked = blockedSet.has(value);
          const selected = selectedDate === value;
          const today = value === dateString(new Date());
          return <button key={value} type="button" className={`calendar-day${booked ? ' booked' : ''}${selected ? ' selected' : ''}${today ? ' today' : ''}`} disabled={booked} aria-pressed={selected} title={booked ? 'Already booked' : value} onClick={() => onSelect(value)}>{date.getDate()}</button>;
        })}
      </div>
      <div className="calendar-legend"><span><i className="calendar-dot selected-dot" />Selected</span><span><i className="calendar-dot booked-dot" />Already booked</span></div>
      <label className="selected-date-label">Selected date<input type="text" value={selectedDate} readOnly required placeholder="Choose an available date above" /></label>
    </fieldset>
  );
}

function CreateEventFromEnquiryModal({ enquiry, setup, busy, error, onClose, onCreate }) {
  const firstAvailableSuggestion = setup.suggestedEventDate && !setup.blockedDates.includes(setup.suggestedEventDate)
    ? setup.suggestedEventDate
    : '';
  const [form, setForm] = useState({ eventDate: firstAvailableSuggestion, dateOfBirth: '', address: '' });

  function submit(event) {
    event.preventDefault();
    onCreate(form);
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose(); }}>
      <section className="event-modal enquiry-modal create-event-from-enquiry-modal" role="dialog" aria-modal="true" aria-labelledby="create-event-from-enquiry-title">
        <div className="modal-heading"><div><p className="eyebrow">ENQUIRY TO EVENT</p><h2 id="create-event-from-enquiry-title">Create event for {enquiry.name}</h2></div><button className="modal-close" type="button" onClick={onClose} aria-label="Close event form" disabled={busy}>×</button></div>
        <p className="enquiry-modal-note">The event will use the saved finalized quote and the {setup.eventTypeName} event type. The quotation PDF will be regenerated with this amount.</p>
        {error && <p className="form-error" role="alert">{error}</p>}
        <form className="event-form" onSubmit={submit}>
          <div className="event-id-preview"><span>FINALIZED QUOTE</span><b>{formatInr(Number(enquiry.finalizedQuote))}</b></div>
          <EventDateCalendar blockedDates={setup.blockedDates} selectedDate={form.eventDate} onSelect={(eventDate) => setForm((current) => ({ ...current, eventDate }))} />
          {setup.customerExists
            ? <p className="enquiry-modal-note existing-customer-note">Existing customer {setup.existingCustomerCode} will be linked to this event.</p>
            : <>
              <p className="enquiry-modal-note">No matching customer exists yet. Enter these details to create the customer record.</p>
              <label className="event-form-label">Date of birth<input type="date" value={form.dateOfBirth} onChange={(event) => setForm((current) => ({ ...current, dateOfBirth: event.target.value }))} required /></label>
              <label className="event-form-label">Address<textarea value={form.address} onChange={(event) => setForm((current) => ({ ...current, address: event.target.value }))} required maxLength="1000" rows="3" placeholder="Street, city, state and PIN code" /></label>
            </>}
          <div className="event-form-actions"><button className="button button-quiet" type="button" onClick={onClose} disabled={busy}>Cancel</button><button className="button button-dark" type="submit" disabled={busy || !form.eventDate || (!setup.customerExists && (!form.dateOfBirth || !form.address.trim()))}>{busy ? 'Creating event…' : 'Create Event'} <span aria-hidden="true">↗</span></button></div>
        </form>
      </section>
    </div>
  );
}

function CustomerPanel({ user }) {
  const [activeView, setActiveView] = useState('list');
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  async function loadCustomers() {
    setError('');
    setLoading(true);
    try {
      setCustomers(await api.customers());
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadCustomers();
  }, []);

  async function registerCustomer(form) {
    setError('');
    setNotice('');
    setBusy(true);
    try {
      const customer = await api.registerCustomer(form);
      setCustomers((currentCustomers) => [customer, ...currentCustomers]);
      setNotice(`${customer.name} has been added to your customer list.`);
      setActiveView('list');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="customer-module">
      <div className="customer-module-intro">
        <p className="eyebrow">YOUR PEOPLE, ALL IN ONE PLACE</p>
        <h2>Customer stories <em>start here.</em></h2>
        <p>Keep customer contact and delivery details together, then link each booking to the right customer record.</p>
      </div>
      <nav className="customer-subtabs" aria-label="Customer options">
        <button className={activeView === 'list' ? 'customer-subtab active' : 'customer-subtab'} onClick={() => { setActiveView('list'); setError(''); }} aria-pressed={activeView === 'list'}>Customer List</button>
        <button className={activeView === 'register' ? 'customer-subtab active' : 'customer-subtab'} onClick={() => { setActiveView('register'); setError(''); setNotice(''); }} aria-pressed={activeView === 'register'}>Customer Registration</button>
      </nav>
      {error && <p className="form-error customer-message" role="alert">{error}</p>}
      {notice && activeView === 'list' && <p className="form-success customer-message" role="status">{notice}</p>}
      {activeView === 'register' ? (
        <CustomerRegistrationForm onRegister={registerCustomer} busy={busy} />
      ) : (
        <section className="customer-list-section" aria-labelledby="customer-list-title">
          <div className="customer-list-heading">
            <div>
              <p className="eyebrow">YOUR CUSTOMER RECORDS</p>
              <h3 id="customer-list-title">Customer List</h3>
              <p>Customers registered by {user.studioName || user.fullName}.</p>
            </div>
            <button className="button button-dark" type="button" onClick={() => { setActiveView('register'); setError(''); setNotice(''); }}>＋ Register customer</button>
          </div>
          {loading ? (
            <p className="customer-empty">Loading customers…</p>
          ) : customers.length === 0 ? (
            <div className="customer-empty-state">
              <div className="upload-mark">＋</div>
              <h4>Your customer list is ready.</h4>
              <p>Register your first customer to keep their contact and delivery details here.</p>
              <button className="button button-dark" type="button" onClick={() => setActiveView('register')}>Register your first customer <span aria-hidden="true">↗</span></button>
            </div>
          ) : (
            <div className="customer-table-scroll">
              <table className="customer-table">
                <thead>
                  <tr>
                    <th scope="col">Customer ID</th>
                    <th scope="col">Name</th>
                    <th scope="col">Email</th>
                    <th scope="col">Mobile number</th>
                    <th scope="col">Date of birth</th>
                    <th scope="col">Address</th>
                    <th scope="col">Events booked</th>
                    <th scope="col">Total revenue</th>
                    <th scope="col">Registered</th>
                  </tr>
                </thead>
                <tbody>
                  {customers.map((customer) => (
                    <tr key={customer.id}>
                      <td><span className="customer-code">{customer.customerCode}</span></td>
                      <th scope="row">{customer.name}</th>
                      <td>{customer.email}</td>
                      <td>{customer.mobileNumber}</td>
                      <td>{new Date(`${customer.dateOfBirth}T00:00:00`).toLocaleDateString()}</td>
                      <td className="customer-address-cell">{customer.address}</td>
                      <td>{customer.eventsBooked}</td>
                      <td>{formatInr(Number(customer.totalRevenueGenerated || 0))}</td>
                      <td>{new Date(customer.createdAt).toLocaleDateString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </section>
  );
}

const teamLanguages = [
  ['KANNADA', 'Kannada'], ['ENGLISH', 'English'], ['HINDI', 'Hindi'],
  ['TAMIL', 'Tamil'], ['TELUGU', 'Telugu'], ['MALAYALAM', 'Malayalam'],
];

const teamSkills = [
  ['PHOTOGRAPHY', 'Photography'], ['VIDEOGRAPHY', 'Videography'],
  ['CANDID_PHOTOGRAPHY', 'Candid Photography'], ['CINEMATIC_VIDEOGRAPHY', 'Cinematic Videography'],
  ['PHOTO_EDITING', 'Photo editing'], ['VIDEO_EDITING', 'Video Editing'], ['ALBUM_DESIGNING', 'Album designing'],
];

function TeamPanel({ user }) {
  const [activeView, setActiveView] = useState('list');
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [invitation, setInvitation] = useState(null);

  async function loadMembers() {
    setLoading(true);
    setError('');
    try {
      setMembers(await api.teamMembers());
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadMembers(); }, []);

  async function saveMember(form, intent) {
    setError('');
    setNotice('');
    setInvitation(null);
    setBusy(true);
    try {
      if (intent === 'invite') {
        const result = await api.inviteTeamMember(form);
        setMembers((current) => [result.member, ...current]);
        setInvitation(result);
        setNotice(`Invitation prepared for ${result.member.name}. WhatsApp delivery depends on your studio’s WhatsApp setup.`);
      } else {
        const member = await api.createTeamMember(form);
        setMembers((current) => [member, ...current]);
        setNotice(`${member.name} has been added to your team.`);
      }
      setActiveView('list');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  async function resendInvite(member) {
    setError('');
    setNotice('');
    setBusy(true);
    try {
      const result = await api.resendTeamInvite(member.memberType, member.id);
      setInvitation(result);
      setNotice(`A fresh invitation link was prepared for ${result.member.name}.`);
      setMembers((current) => current.map((member) => member.id === result.member.id ? result.member : member));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  async function copyInvitation() {
    if (!invitation?.invitationUrl) return;
    try {
      await navigator.clipboard.writeText(invitation.invitationUrl);
      setNotice('Invitation link copied.');
    } catch {
      setNotice('Select and copy the invitation link below.');
    }
  }

  return (
    <section className="customer-module team-module">
      <div className="customer-module-intro">
        <p className="eyebrow">THE PEOPLE BEHIND YOUR STUDIO</p>
        <h2>Build your <em>team.</em></h2>
        <p>Keep your crew’s contact details, languages, and skills together. Send a secure link so each person can complete their own profile.</p>
      </div>
      <nav className="customer-subtabs" aria-label="Team options">
        <button className={activeView === 'list' ? 'customer-subtab active' : 'customer-subtab'} onClick={() => { setActiveView('list'); setError(''); }} aria-pressed={activeView === 'list'}>Team List</button>
        <button className={activeView === 'create' ? 'customer-subtab active' : 'customer-subtab'} onClick={() => { setActiveView('create'); setError(''); setNotice(''); setInvitation(null); }} aria-pressed={activeView === 'create'}>Create team member</button>
      </nav>
      {error && <p className="form-error customer-message" role="alert">{error}</p>}
      {notice && activeView === 'list' && <p className="form-success customer-message" role="status">{notice}</p>}
      {activeView === 'create' ? (
        <TeamMemberForm onSave={saveMember} busy={busy} />
      ) : (
        <section className="customer-list-section" aria-labelledby="team-list-title">
          <div className="customer-list-heading">
            <div>
              <p className="eyebrow">YOUR STUDIO CREW</p>
              <h3 id="team-list-title">Team List</h3>
              <p>Team members registered by {user.studioName || user.fullName}.</p>
            </div>
            <button className="button button-dark" type="button" onClick={() => { setActiveView('create'); setError(''); setNotice(''); setInvitation(null); }}>＋ Create team member</button>
          </div>
          {invitation && <div className="team-invite-share">
            <div><b>Invitation link</b><p>Share this secure link with {invitation.member.name}. It expires {new Date(invitation.member.inviteExpiresAt).toLocaleString()}.</p></div>
            <input aria-label="Team invitation link" readOnly value={invitation.invitationUrl} onFocus={(event) => event.target.select()} />
            <button className="button button-quiet button-small" type="button" onClick={copyInvitation}>Copy link</button>
          </div>}
          {loading ? (
            <p className="customer-empty">Loading team members…</p>
          ) : members.length === 0 ? (
            <div className="customer-empty-state">
              <div className="upload-mark">＋</div>
              <h4>Your team list is ready.</h4>
              <p>Add your first team member or freelancer to get started.</p>
              <button className="button button-dark" type="button" onClick={() => setActiveView('create')}>Create your first team member <span aria-hidden="true">↗</span></button>
            </div>
          ) : (
            <div className="team-profile-groups">
              <TeamProfilesTable title="Team members" members={members.filter((member) => member.memberType === 'TEAM_MEMBER')} busy={busy} onResend={resendInvite} />
              <TeamProfilesTable title="Freelancers" members={members.filter((member) => member.memberType === 'FREELANCER')} busy={busy} onResend={resendInvite} />
            </div>
          )}
        </section>
      )}
    </section>
  );
}

function TeamProfilesTable({ title, members, busy, onResend }) {
  return (
    <section className="team-profile-list" aria-label={title}>
      <h4>{title} <span>{members.length}</span></h4>
      {members.length === 0 ? <p className="team-profile-empty">No {title.toLowerCase()} added yet.</p> : (
        <div className="customer-table-scroll">
          <table className="customer-table team-table">
            <thead><tr><th scope="col">Name</th><th scope="col">Age</th><th scope="col">Mobile number</th><th scope="col">Languages known</th><th scope="col">Skills</th><th scope="col">Status</th><th scope="col">Added</th><th scope="col">Action</th></tr></thead>
            <tbody>{members.map((member) => <tr key={member.id}>
              <th scope="row">{member.name}</th>
              <td>{member.age ?? '—'}</td>
              <td>{member.mobileNumber}</td>
              <td>{member.languages.map(teamLanguageLabel).join(', ') || '—'}</td>
              <td>{member.skills.map(teamSkillLabel).join(', ') || '—'}</td>
              <td><span className={`team-status ${member.status === 'ACTIVE' ? 'active' : 'pending'}`}>{member.status === 'ACTIVE' ? 'Active' : 'Invitation pending'}</span></td>
              <td>{new Date(member.createdAt).toLocaleDateString()}</td>
              <td>{member.status === 'PENDING' ? <button className="customer-id-link team-resend" type="button" disabled={busy} onClick={() => onResend(member)}>Send link again</button> : '—'}</td>
            </tr>)}</tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function teamLanguageLabel(value) {
  return teamLanguages.find(([id]) => id === value)?.[1] || value;
}

function teamSkillLabel(value) {
  return teamSkills.find(([id]) => id === value)?.[1] || value;
}

function teamMemberTypeLabel(value) {
  return value === 'FREELANCER' ? 'Freelancer' : 'Team member';
}

function TeamChoiceGroup({ legend, options, values, onChange }) {
  return (
    <fieldset className="team-choice-field">
      <legend>{legend}</legend>
      <div className="team-choice-grid">{options.map(([value, label]) => <label className="team-choice" key={value}>
        <input type="checkbox" checked={values.includes(value)} onChange={() => onChange(values.includes(value) ? values.filter((item) => item !== value) : [...values, value])} />
        <span>{label}</span>
      </label>)}</div>
    </fieldset>
  );
}

function TeamMemberForm({ onSave, busy }) {
  const [form, setForm] = useState({ name: '', age: '', mobileNumber: '', languages: [], memberType: '', skills: [] });
  const [validationError, setValidationError] = useState('');

  function update(event) {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  }

  function submit(event) {
    event.preventDefault();
    const intent = event.nativeEvent.submitter?.value || 'create';
    if (intent === 'create' && (!form.age || form.languages.length === 0 || form.skills.length === 0)) {
      setValidationError('For direct registration, add the member’s age, at least one language, and at least one skill.');
      return;
    }
    setValidationError('');
    onSave({
      name: form.name.trim(),
      age: form.age ? Number(form.age) : null,
      mobileNumber: form.mobileNumber.trim(),
      languages: form.languages,
      memberType: form.memberType,
      skills: form.skills,
    }, intent);
  }

  return (
    <section className="customer-registration team-registration" aria-labelledby="team-registration-title">
      <div className="customer-list-heading"><div><p className="eyebrow">ADD TO YOUR CREW</p><h3 id="team-registration-title">Create team member</h3><p>Register a complete profile or send an invitation link to their mobile.</p></div></div>
      {validationError && <p className="form-error" role="alert">{validationError}</p>}
      <form className="customer-form team-form" onSubmit={submit}>
        <label>Name<input name="name" value={form.name} onChange={update} autoComplete="name" required maxLength="120" placeholder="Team member’s full name" /></label>
        <label>Age<input name="age" type="number" min="16" max="100" value={form.age} onChange={update} placeholder="Age" /></label>
        <label>Mobile number<input name="mobileNumber" type="tel" value={form.mobileNumber} onChange={update} autoComplete="tel" required maxLength="13" pattern="([0-9]{10}|\+91[0-9]{10})" placeholder="9876543210" /><span className="field-hint">Enter 10 digits. You may also include +91.</span></label>
        <label>Member type<select name="memberType" value={form.memberType} onChange={update} required><option value="">Choose member type</option><option value="TEAM_MEMBER">Team member</option><option value="FREELANCER">Freelancer</option></select></label>
        <TeamChoiceGroup legend="Language known" options={teamLanguages} values={form.languages} onChange={(languages) => setForm((current) => ({ ...current, languages }))} />
        <TeamChoiceGroup legend="Skills" options={teamSkills} values={form.skills} onChange={(skills) => setForm((current) => ({ ...current, skills }))} />
        <p className="customer-form-note">The member type is chosen by you and cannot be changed from the invitation link.</p>
        <div className="customer-form-actions team-form-actions">
          <button className="button button-quiet" type="submit" value="invite" disabled={busy}>{busy ? 'Sending…' : 'Send invite link'}</button>
          <button className="button button-dark" type="submit" value="create" disabled={busy}>{busy ? 'Saving…' : 'Save team member'} <span aria-hidden="true">↗</span></button>
        </div>
      </form>
    </section>
  );
}

function TeamInvitePage({ token }) {
  const [context, setContext] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [completed, setCompleted] = useState(null);
  const [form, setForm] = useState({ name: '', age: '', languages: [], skills: [] });

  useEffect(() => {
    let cancelled = false;
    api.publicTeamInvite(token)
      .then((invite) => {
        if (cancelled) return;
        setContext(invite);
        setForm({ name: invite.name || '', age: invite.age ?? '', languages: invite.languages || [], skills: invite.skills || [] });
      })
      .catch((requestError) => { if (!cancelled) setError(requestError.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [token]);

  function update(event) {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  }

  async function submit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      setCompleted(await api.completeTeamInvite(token, {
        name: form.name.trim(), age: Number(form.age), languages: form.languages, skills: form.skills,
      }));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="team-invite-page">
      <header className="team-invite-header"><Brand /><span>TEAM PROFILE</span></header>
      <section className="team-invite-card">
        {loading ? <p className="customer-empty">Loading invitation…</p> : completed ? <div className="team-invite-complete">
          <p className="eyebrow">PROFILE SAVED</p><h1>You’re on the team, <em>{completed.name}.</em></h1><p>Your details have been shared with {completed.studioName}.</p>
        </div> : context ? <>
          <p className="eyebrow">{context.studioName} · TEAM INVITATION</p>
          <h1>Complete your <em>profile.</em></h1>
          <p className="team-invite-description">Your photographer invited you to join as a <b>{teamMemberTypeLabel(context.memberType)}</b>. This member type is set by the photographer.</p>
          <p className="team-invite-expiry">Invitation expires {new Date(context.expiresAt).toLocaleString()}.</p>
          {error && <p className="form-error" role="alert">{error}</p>}
          <form className="customer-form team-form team-public-form" onSubmit={submit}>
            <label>Name<input name="name" value={form.name} onChange={update} required maxLength="120" /></label>
            <label>Age<input name="age" type="number" min="16" max="100" value={form.age} onChange={update} required /></label>
            <label>Mobile number<input type="tel" value={context.mobileNumber} readOnly /></label>
            <label>Member type<input value={teamMemberTypeLabel(context.memberType)} readOnly /></label>
            <TeamChoiceGroup legend="Language known" options={teamLanguages} values={form.languages} onChange={(languages) => setForm((current) => ({ ...current, languages }))} />
            <TeamChoiceGroup legend="Skills" options={teamSkills} values={form.skills} onChange={(skills) => setForm((current) => ({ ...current, skills }))} />
            {(!form.languages.length || !form.skills.length) && <p className="customer-form-note">Select at least one language and one skill to complete the profile.</p>}
            <div className="customer-form-actions"><button className="button button-dark" type="submit" disabled={busy || !form.languages.length || !form.skills.length}>{busy ? 'Saving profile…' : 'Complete profile'} <span aria-hidden="true">↗</span></button></div>
          </form>
        </> : <div className="team-invite-complete"><p className="eyebrow">INVITATION UNAVAILABLE</p><h1>This link can’t be <em>used.</em></h1><p>{error || 'Ask your photographer to send you a new invitation.'}</p></div>}
      </section>
      <footer className="team-invite-footer"><Brand /><span>FrameBucket · Photographer portal</span></footer>
    </main>
  );
}

function dateString(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function getCalendarCells(month) {
  const year = month.getFullYear();
  const monthIndex = month.getMonth();
  const firstWeekday = new Date(year, monthIndex, 1).getDay();
  const offset = (firstWeekday + 6) % 7;
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();
  const cells = Array.from({ length: offset }, () => null);
  for (let day = 1; day <= daysInMonth; day += 1) {
    cells.push(new Date(year, monthIndex, day));
  }
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}

function EventPanel({ user, onOpenGallery }) {
  const [events, setEvents] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [eventTypes, setEventTypes] = useState([]);
  const [teamMembers, setTeamMembers] = useState([]);
  const [blockedDates, setBlockedDates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [showRegistration, setShowRegistration] = useState(false);
  const [selectedCustomerId, setSelectedCustomerId] = useState('');
  const [updatingAssigneeId, setUpdatingAssigneeId] = useState('');
  const [assigneeSelections, setAssigneeSelections] = useState({});

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.events(), api.customers(), api.eventTypes(), api.blockedEventDates(), api.teamMembers()])
      .then(([loadedEvents, loadedCustomers, loadedTypes, loadedDates, loadedTeam]) => {
        if (cancelled) return;
        setEvents(loadedEvents);
        setCustomers(loadedCustomers);
        setEventTypes(loadedTypes);
        setBlockedDates(loadedDates);
        setTeamMembers(loadedTeam);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  async function createEvent(form) {
    setError('');
    setNotice('');
    setBusy(true);
    try {
      const createdEvent = await api.createEvent(form);
      setEvents((currentEvents) => [...currentEvents, createdEvent].sort((left, right) => left.eventDate.localeCompare(right.eventDate)));
      setBlockedDates((currentDates) => [...new Set([...currentDates, createdEvent.eventDate])]);
      setCustomers((currentCustomers) => currentCustomers.map((customer) => (
        customer.id === createdEvent.customerUuid ? { ...customer, eventsBooked: customer.eventsBooked + 1 } : customer
      )));
      setNotice(`Event ${createdEvent.eventCode} has been added.`);
      setShowRegistration(false);
    } catch (requestError) {
      const latestDates = await api.blockedEventDates().catch(() => null);
      if (latestDates) setBlockedDates(latestDates);
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  async function createEventType(name) {
    const createdType = await api.createEventType(name);
    setEventTypes((currentTypes) => [...currentTypes, createdType].sort((left, right) => left.name.localeCompare(right.name)));
    return createdType;
  }

  function assigneeValue(event) {
    if (Object.prototype.hasOwnProperty.call(assigneeSelections, event.id)) {
      return assigneeSelections[event.id];
    }
    return event.assigneeId ? `${event.assigneeType}:${event.assigneeId}` : '';
  }

  async function updateAssignee(event) {
    const selectedValue = assigneeValue(event);
    const savedValue = event.assigneeId ? `${event.assigneeType}:${event.assigneeId}` : '';
    if (selectedValue === savedValue) return;
    setError('');
    setNotice('');
    setUpdatingAssigneeId(event.id);
    try {
      const [assigneeType, assigneeId] = selectedValue ? selectedValue.split(':') : [null, null];
      const updatedEvent = await api.assignEvent(event.id, assigneeType, assigneeId);
      setEvents((current) => current.map((currentEvent) => currentEvent.id === event.id ? updatedEvent : currentEvent));
      setAssigneeSelections((current) => {
        const next = { ...current };
        delete next[event.id];
        return next;
      });
      setNotice('Assignment saved. WhatsApp notifications have been queued.');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setUpdatingAssigneeId('');
    }
  }

  const firstName = user.fullName.split(' ')[0];

  return (
    <section className="event-module" aria-labelledby="event-list-title">
      <div className="customer-module-intro">
        <p className="eyebrow">YOUR BOOKINGS, CLEARLY SCHEDULED</p>
        <h2>Events <em>at a glance.</em></h2>
        <p>Keep event dates and customers together, assign your crew, and see the location details customers share.</p>
      </div>
      {error && <p className="form-error customer-message" role="alert">{error}</p>}
      {notice && <p className="form-success customer-message" role="status">{notice}</p>}
      <section className="customer-list-section" aria-labelledby="event-list-title">
        <div className="customer-list-heading">
          <div>
            <p className="eyebrow">{user.studioName || user.fullName}</p>
            <h3 id="event-list-title">Events</h3>
            <p>Event IDs are assigned in the order bookings are created.</p>
          </div>
          <button className="button button-dark" type="button" onClick={() => { setError(''); setNotice(''); setShowRegistration(true); }} disabled={customers.length === 0}>
            ＋ Create new event
          </button>
        </div>
        {customers.length === 0 && !loading && <p className="event-form-note">Register a customer before creating an event.</p>}
        {loading ? (
          <p className="customer-empty">Loading events…</p>
        ) : events.length === 0 ? (
          <div className="customer-empty-state">
            <div className="upload-mark">＋</div>
            <h4>Your event list is ready.</h4>
            <p>Create an event to add a date to your studio schedule.</p>
            {customers.length > 0 && <button className="button button-dark" type="button" onClick={() => setShowRegistration(true)}>Create your first event <span aria-hidden="true">↗</span></button>}
          </div>
        ) : (
          <div className="customer-table-scroll">
            <table className="customer-table event-table">
              <thead>
                <tr>
                  <th scope="col">Event ID</th>
                  <th scope="col">Event Date</th>
                  <th scope="col">Event Type</th>
                  <th scope="col">Price</th>
                  <th scope="col">Customer Name</th>
                  <th scope="col">Assignee</th>
                  <th scope="col">Event Time</th>
                  <th scope="col">Location</th>
                  <th scope="col">Album Design Link</th>
                  <th scope="col">Creation Date</th>
                </tr>
              </thead>
              <tbody>
                {events.map((event) => (
                  <tr key={event.id}>
                    <th scope="row"><span className="event-code">{event.eventCode}</span></th>
                    <td>{new Date(`${event.eventDate}T00:00:00`).toLocaleDateString()}</td>
                    <td>{event.eventTypeName}</td>
                    <td>{event.enquiryId
                      ? <a className="customer-id-link finalized-quote-link" href={api.enquiryFinalizedQuotationUrl(event.enquiryId)} target="_blank" rel="noreferrer">{displayEventPrice(event.price)} ↗</a>
                      : displayEventPrice(event.price)}</td>
                    <td><a className="customer-id-link event-customer-link" href={`#customer-${event.customerUuid}`} onClick={(clickEvent) => { clickEvent.preventDefault(); setSelectedCustomerId(event.customerUuid); }}>{event.customerName}</a></td>
                    <td><div className="event-assignee-editor"><select className="event-assignee-select" aria-label={`Assign a team member to ${event.eventCode}`} value={assigneeValue(event)} disabled={updatingAssigneeId === event.id} onChange={(changeEvent) => setAssigneeSelections((current) => ({ ...current, [event.id]: changeEvent.target.value }))}>
                      <option value="">Unassigned</option>
                      {teamMembers.filter((member) => member.status === 'ACTIVE').map((member) => <option key={`${member.memberType}:${member.id}`} value={`${member.memberType}:${member.id}`}>{member.name} · {teamMemberTypeLabel(member.memberType)}</option>)}
                    </select><button className="button button-dark button-small event-assignee-save" type="button" disabled={updatingAssigneeId === event.id || assigneeValue(event) === (event.assigneeId ? `${event.assigneeType}:${event.assigneeId}` : '')} onClick={() => updateAssignee(event)}>{updatingAssigneeId === event.id ? 'Saving…' : 'Save'}</button></div></td>
                    <td>{formatEventTime(event.eventTime)}</td>
                    <td>{event.locationName ? <div className="event-location-cell">
                      {isGoogleMapsUrl(event.locationMapUrl) ? <a className="customer-id-link" href={event.locationMapUrl} target="_blank" rel="noreferrer">{event.locationName} ↗</a> : <span>{event.locationName}</span>}
                      {event.locationLandmark && <small>Landmark: {event.locationLandmark}</small>}
                      {event.locationNotes && <small>{event.locationNotes}</small>}
                    </div> : <span className="event-location-missing">Not provided</span>}</td>
                    <td className="album-link-cell">
                      <button className="button button-dark button-small event-gallery-button" type="button" disabled={!user.dataModuleEnabled} title={user.dataModuleEnabled ? 'Open this event gallery' : 'The Data module is not included with Bronze.'} onClick={() => onOpenGallery(event)}>Gallery</button>
                      {event.eventGallery && <a className="album-design-link" href={event.eventGallery} target="_blank" rel="noreferrer">Designer folder ↗</a>}
                    </td>
                    <td>{new Date(event.createdAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {showRegistration && (
        <EventRegistrationModal
          customers={customers}
          eventTypes={eventTypes}
          blockedDates={blockedDates}
          busy={busy}
          error={error}
          onClose={() => setShowRegistration(false)}
          onCreateEvent={createEvent}
          onCreateEventType={createEventType}
        />
      )}
      {selectedCustomerId && <CustomerDetailsModal customerId={selectedCustomerId} onClose={() => setSelectedCustomerId('')} />}
      <p className="event-module-caption">Welcome back, {firstName}. Event creation times are shown in your browser’s local time.</p>
    </section>
  );
}

function EventRegistrationModal({ customers, eventTypes, blockedDates, busy, error, onClose, onCreateEvent, onCreateEventType }) {
  const [form, setForm] = useState({ eventDate: '', eventTypeId: eventTypes[0]?.id || '', price: '', customerId: '' });
  const [eventIdPreview, setEventIdPreview] = useState('Loading…');
  const [visibleMonth, setVisibleMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [showTypePopup, setShowTypePopup] = useState(false);
  const blockedSet = new Set(blockedDates);
  const cells = getCalendarCells(visibleMonth);

  useEffect(() => {
    let cancelled = false;
    api.nextEventId()
      .then((preview) => { if (!cancelled) setEventIdPreview(preview.eventId); })
      .catch(() => { if (!cancelled) setEventIdPreview('Assigned when saved'); });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!form.eventTypeId && eventTypes.length > 0) {
      setForm((currentForm) => ({ ...currentForm, eventTypeId: eventTypes[0].id }));
    }
  }, [eventTypes, form.eventTypeId]);

  function update(event) {
    setForm((currentForm) => ({ ...currentForm, [event.target.name]: event.target.value }));
  }

  function submit(event) {
    event.preventDefault();
    onCreateEvent({ ...form, customerId: form.customerId });
  }

  async function addType(name) {
    const createdType = await onCreateEventType(name);
    setForm((currentForm) => ({ ...currentForm, eventTypeId: createdType.id }));
    setShowTypePopup(false);
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose(); }}>
      <section className="event-modal" role="dialog" aria-modal="true" aria-labelledby="event-registration-title">
        <div className="modal-heading">
          <div><p className="eyebrow">NEW BOOKING</p><h2 id="event-registration-title">Event registration</h2></div>
          <button className="modal-close" type="button" onClick={onClose} aria-label="Close event registration" disabled={busy}>×</button>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        <form className="event-form" onSubmit={submit}>
          <div className="event-id-preview"><span>EVENT ID</span><b>{eventIdPreview}</b></div>
          <fieldset className="event-date-field">
            <legend>Event Date</legend>
            <div className="calendar-heading">
              <button type="button" className="calendar-nav" onClick={() => setVisibleMonth(new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() - 1, 1))} aria-label="Previous month">‹</button>
              <strong>{visibleMonth.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}</strong>
              <button type="button" className="calendar-nav" onClick={() => setVisibleMonth(new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() + 1, 1))} aria-label="Next month">›</button>
            </div>
            <div className="calendar-grid calendar-weekdays" aria-hidden="true">{['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'].map((day) => <span key={day}>{day}</span>)}</div>
            <div className="calendar-grid">
              {cells.map((date, index) => {
                if (!date) return <span className="calendar-empty" key={`empty-${index}`} />;
                const value = dateString(date);
                const booked = blockedSet.has(value);
                const selected = form.eventDate === value;
                const today = value === dateString(new Date());
                return <button key={value} type="button" className={`calendar-day${booked ? ' booked' : ''}${selected ? ' selected' : ''}${today ? ' today' : ''}`} disabled={booked} aria-pressed={selected} title={booked ? 'Already booked' : value} onClick={() => setForm((currentForm) => ({ ...currentForm, eventDate: value }))}>{date.getDate()}</button>;
              })}
            </div>
            <div className="calendar-legend"><span><i className="calendar-dot selected-dot" />Selected</span><span><i className="calendar-dot booked-dot" />Already booked</span></div>
            <label className="selected-date-label">Selected date<input type="text" name="eventDate" value={form.eventDate} readOnly required placeholder="Choose a date above" /></label>
          </fieldset>
          <label className="event-form-label">Event Type
            <span className="event-type-picker"><select name="eventTypeId" value={form.eventTypeId} onChange={update} required>
              <option value="" disabled>Select an event type</option>
              {eventTypes.map((type) => <option value={type.id} key={type.id}>{type.name}</option>)}
            </select><button className="button button-quiet button-small" type="button" onClick={() => setShowTypePopup(true)}>＋ New type</button></span>
          </label>
          <label className="event-form-label">Price<input type="text" name="price" value={form.price} onChange={update} maxLength="1000" placeholder="Enter price" /></label>
          <label className="event-form-label">Customer ID<select name="customerId" value={form.customerId} onChange={update} required>
            <option value="" disabled>Select a customer</option>
            {customers.map((customer) => <option value={customer.id} key={customer.id}>{customer.customerCode} · {customer.name}</option>)}
          </select></label>
          <label className="event-form-label">Creation Date<input type="text" value={new Date().toLocaleString()} readOnly /></label>
          <div className="event-form-actions"><button className="button button-quiet" type="button" onClick={onClose} disabled={busy}>Cancel</button><button className="button button-dark" type="submit" disabled={busy || !form.eventDate || !form.eventTypeId || !form.customerId}>{busy ? 'Saving event…' : 'Save event'} <span aria-hidden="true">↗</span></button></div>
        </form>
        {showTypePopup && <EventTypeModal onClose={() => setShowTypePopup(false)} onCreate={addType} />}
      </section>
    </div>
  );
}

function EventTypeModal({ onClose, onCreate }) {
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await onCreate(name.trim());
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop nested-modal-backdrop" role="presentation">
      <section className="event-type-modal" role="dialog" aria-modal="true" aria-labelledby="event-type-title">
        <div className="modal-heading"><div><p className="eyebrow">CUSTOM EVENT</p><h2 id="event-type-title">Add event type</h2></div><button className="modal-close" type="button" onClick={onClose} aria-label="Close event type dialog" disabled={busy}>×</button></div>
        {error && <p className="form-error" role="alert">{error}</p>}
        <form className="event-type-form" onSubmit={submit}>
          <label className="event-form-label">Event type name<input value={name} onChange={(event) => setName(event.target.value)} required maxLength="100" autoFocus placeholder="For example, Graduation" /></label>
          <div className="event-form-actions"><button className="button button-quiet" type="button" onClick={onClose} disabled={busy}>Cancel</button><button className="button button-dark" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save type'}</button></div>
        </form>
      </section>
    </div>
  );
}

function CustomerDetailsModal({ customerId, onClose }) {
  const [customer, setCustomer] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api.customer(customerId)
      .then((details) => { if (!cancelled) setCustomer(details); })
      .catch((requestError) => { if (!cancelled) setError(requestError.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [customerId]);

  return (
    <div className="modal-backdrop detail-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section className="customer-detail-modal" role="dialog" aria-modal="true" aria-labelledby="customer-detail-title">
        <div className="modal-heading"><div><p className="eyebrow">CUSTOMER RECORD</p><h2 id="customer-detail-title">Customer details</h2></div><button className="modal-close" type="button" onClick={onClose} aria-label="Close customer details">×</button></div>
        {loading && <p className="customer-empty">Loading customer…</p>}
        {error && <p className="form-error" role="alert">{error}</p>}
        {customer && <dl className="customer-detail-list">
          <div><dt>Customer ID</dt><dd>{customer.customerCode}</dd></div>
          <div><dt>Name</dt><dd>{customer.name}</dd></div>
          <div><dt>Email</dt><dd>{customer.email}</dd></div>
          <div><dt>Mobile number</dt><dd>{customer.mobileNumber}</dd></div>
          <div><dt>Date of birth</dt><dd>{new Date(`${customer.dateOfBirth}T00:00:00`).toLocaleDateString()}</dd></div>
          <div><dt>Address</dt><dd>{customer.address}</dd></div>
          <div><dt>Events booked</dt><dd>{customer.eventsBooked}</dd></div>
          <div><dt>Total revenue</dt><dd>{formatInr(Number(customer.totalRevenueGenerated || 0))}</dd></div>
          <div><dt>Registered</dt><dd>{new Date(customer.createdAt).toLocaleString()}</dd></div>
        </dl>}
      </section>
    </div>
  );
}

function CustomerRegistrationForm({ onRegister, busy }) {
  const [form, setForm] = useState({ name: '', email: '', mobileNumber: '', dateOfBirth: '', address: '' });

  function update(event) {
    setForm((currentForm) => ({ ...currentForm, [event.target.name]: event.target.value }));
  }

  function submit(event) {
    event.preventDefault();
    onRegister(form);
  }

  return (
    <section className="customer-registration" aria-labelledby="customer-registration-title">
      <div className="customer-list-heading">
        <div>
          <p className="eyebrow">NEW CLIENT</p>
          <h3 id="customer-registration-title">Customer Registration</h3>
          <p>Add a customer record to your studio.</p>
        </div>
      </div>
      <form className="customer-form" onSubmit={submit}>
        <label>Name<input name="name" value={form.name} onChange={update} autoComplete="name" required maxLength="120" placeholder="Customer’s full name" /></label>
        <label>Email<input name="email" type="email" value={form.email} onChange={update} autoComplete="email" required maxLength="320" placeholder="customer@example.com" /></label>
        <label>Mobile number<input name="mobileNumber" type="tel" value={form.mobileNumber} onChange={update} autoComplete="tel" required maxLength="13" pattern="([0-9]{10}|\+91[0-9]{10})" placeholder="9876543210" aria-describedby="customer-mobile-hint" /><span className="field-hint" id="customer-mobile-hint">Enter 10 digits. You may also include +91.</span></label>
        <label>Date of birth<input name="dateOfBirth" type="date" value={form.dateOfBirth} onChange={update} required /></label>
        <label className="customer-address-field">Address <span className="optional-label">FOR DATA HAMPER DELIVERY</span><textarea name="address" value={form.address} onChange={update} required maxLength="1000" rows="4" placeholder="Street, city, state and PIN code" /></label>
        <p className="customer-form-note">A photographer scoped Customer ID is assigned automatically when this record is saved.</p>
        <div className="customer-form-actions">
          <button className="button button-dark" type="submit" disabled={busy}>{busy ? 'Saving customer…' : 'Save customer'} <span aria-hidden="true">↗</span></button>
        </div>
      </form>
    </section>
  );
}

const quoteFestivalDates = new Set([
  '2026-01-14', '2026-03-04', '2026-08-28', '2026-10-20', '2026-11-08',
  '2027-01-14', '2027-03-22', '2027-09-15', '2027-10-09', '2027-10-29',
  '2028-01-14', '2028-03-11', '2028-09-04', '2028-09-27', '2028-10-17',
]);

function QuotationBuilderPage({ token }) {
  const [context, setContext] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [form, setForm] = useState({ venue: '', eventDate: '', preferredTime: 'Morning', events: [], albumSheets: 0, couponCode: '' });
  const [couponInput, setCouponInput] = useState('');
  const [couponMessage, setCouponMessage] = useState('FESTFSK10 may be used on selected festival dates.');
  const [finalPrice, setFinalPrice] = useState(null);
  const [pricingBusy, setPricingBusy] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [pdfUrl, setPdfUrl] = useState('');

  useEffect(() => {
    let cancelled = false;
    api.publicQuoteContext(token)
      .then((quoteContext) => {
        if (cancelled) return;
        setContext(quoteContext);
        if (quoteContext.alreadySubmitted) {
          setSubmitted(true);
          setPdfUrl(api.publicQuotePdfUrl(token));
          setFinalPrice(quoteContext.submittedFinalPrice ?? null);
        }
      })
      .catch((requestError) => { if (!cancelled) setError(requestError.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [token]);

  useEffect(() => {
    if (!context || context.alreadySubmitted || form.events.length > 0 || context.eventTypes.length === 0) return;
    const preferredType = context.eventTypes.find((item) => item.name === context.enquiryEvent) || context.eventTypes[0];
    setForm((current) => ({ ...current, events: [{ eventTypeId: preferredType.id, serviceIds: [] }] }));
  }, [context, form.events.length]);

  useEffect(() => {
    if (!context || context.alreadySubmitted || form.events.length === 0) return undefined;
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setPricingBusy(true);
      api.calculateQuote(token, {
        venue: form.venue,
        eventDate: form.eventDate || null,
        preferredTime: form.preferredTime,
        events: form.events,
        albumSheets: Number(form.albumSheets) || 0,
        couponCode: form.couponCode || null,
      })
        .then((price) => { if (!cancelled) { setFinalPrice(price.finalPrice); setError(''); } })
        .catch((requestError) => { if (!cancelled) setError(requestError.message); })
        .finally(() => { if (!cancelled) setPricingBusy(false); });
    }, 250);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [context, form, token]);

  function updateEvent(index, update) {
    setForm((current) => ({
      ...current,
      events: current.events.map((item, itemIndex) => itemIndex === index ? { ...item, ...update } : item),
    }));
  }

  function setEventCount(count) {
    const safeCount = Math.min(8, Math.max(1, Number(count) || 1));
    setForm((current) => {
      const fallback = context?.eventTypes.find((item) => item.name === context.enquiryEvent)?.id || context?.eventTypes[0]?.id || '';
      const events = Array.from({ length: safeCount }, (_, index) => current.events[index] || { eventTypeId: fallback, serviceIds: [] });
      return { ...current, events };
    });
  }

  function updateDate(event) {
    setForm((current) => ({ ...current, eventDate: event.target.value, couponCode: '' }));
    if (form.couponCode) setCouponMessage('Coupon cleared because the event date changed.');
  }

  function applyCoupon() {
    const code = couponInput.trim().toUpperCase();
    if (code !== 'FESTFSK10') {
      setForm((current) => ({ ...current, couponCode: '' }));
      setCouponMessage('That coupon code is not valid.');
      return;
    }
    if (!quoteFestivalDates.has(form.eventDate)) {
      setForm((current) => ({ ...current, couponCode: '' }));
      setCouponMessage('This code is available only on configured festival dates.');
      return;
    }
    setForm((current) => ({ ...current, couponCode: code }));
    setCouponMessage('Festival offer applied to your estimate.');
  }

  async function submitQuotation(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const result = await api.submitQuote(token, {
        venue: form.venue,
        eventDate: form.eventDate || null,
        preferredTime: form.preferredTime,
        events: form.events,
        albumSheets: Number(form.albumSheets) || 0,
        couponCode: form.couponCode || null,
      });
      setFinalPrice(result.finalPrice);
      setPdfUrl(api.publicQuotePdfUrl(token));
      setSubmitted(true);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <LoadingPage />;
  if (error && !context) {
    return <main className="quote-builder-shell"><header className="quote-builder-header"><Brand /></header><div className="quote-error-card"><p className="eyebrow">QUOTATION LINK</p><h1>This link is unavailable.</h1><p>{error}</p></div></main>;
  }
  if (!context) return <LoadingPage />;

  if (submitted) {
    return (
      <main className="quote-builder-shell">
        <header className="quote-builder-header"><Brand /><span>{context.studioName}</span></header>
        <section className="quote-submitted-card">
          <div className="quote-submitted-mark">✓</div>
          <p className="eyebrow">QUOTATION SUBMITTED</p>
          <h1>Your estimate is ready.</h1>
          <p>The photographer has been notified. You can open your quotation PDF below.</p>
          {finalPrice !== null && <strong className="quote-final-amount">{formatInr(Number(finalPrice))}</strong>}
          <a className="button button-dark" href={pdfUrl || api.publicQuotePdfUrl(token)} target="_blank" rel="noreferrer">Open quotation PDF <span aria-hidden="true">↗</span></a>
        </section>
        <footer className="quote-builder-footer">© 2026 FrameBucket · {context.studioName}</footer>
      </main>
    );
  }

  const today = dateString(new Date());

  return (
    <main className="quote-builder-shell">
      <header className="quote-builder-header"><Brand /><span>{context.studioName} · Photography &amp; Films</span></header>
      <section className="quote-hero">
        <p className="eyebrow">YOUR CELEBRATION, BEAUTIFULLY TOLD</p>
        <h1>Every moment deserves<br /><em>to be remembered.</em></h1>
        <p>Build a photography package that fits your celebration. Review your final estimate, then submit it to your photographer.</p>
        <div className="quote-hero-tags"><span>✦ Made for your event</span><span>✦ Final estimate shown</span><span>✦ No component prices</span></div>
      </section>
      <div className="quote-builder-wrap">
        <div className="quote-steps"><div className="active"><b>01</b><span>Your details</span></div><div className="active"><b>02</b><span>Build your package</span></div><div className="active"><b>03</b><span>Review &amp; submit</span></div></div>
        {error && <p className="form-error quote-builder-error" role="alert">{error}</p>}
        <form onSubmit={submitQuotation}>
          <div className="quote-layout">
            <div className="quote-left-column">
              <section className="quote-card">
                <div className="quote-card-heading"><div><h2>Let’s get to know you</h2><p>Your enquiry details are ready for this quotation.</p></div><span>STEP 01</span></div>
                <div className="quote-contact-grid"><div><small>NAME</small><b>{context.customerName}</b></div><div><small>MOBILE</small><b>{context.customerMobile}</b></div><div><small>EMAIL</small><b>{context.customerEmail}</b></div><div><small>MAIN OCCASION</small><b>{context.enquiryEvent}</b></div></div>
              </section>
              <section className="quote-card">
                <div className="quote-card-heading"><div><h2>Build your event package</h2><p>Choose event coverage and services for each event.</p></div><span>STEP 02</span></div>
                <div className="quote-fields-grid">
                  <label>Number of events<select value={form.events.length || 1} onChange={(event) => setEventCount(event.target.value)}>{Array.from({ length: 8 }, (_, index) => <option value={index + 1} key={index}>{index + 1} event{index ? 's' : ''}</option>)}</select></label>
                  <label>Venue / city <span className="optional-label">OPTIONAL</span><input value={form.venue} onChange={(event) => setForm((current) => ({ ...current, venue: event.target.value }))} maxLength="240" placeholder="City or venue name" /></label>
                </div>
                <div className="quote-event-cards">
                  {form.events.map((quoteEvent, index) => (
                    <article className="quote-event-card" key={index}>
                      <div className="quote-event-heading"><h3><span>{String(index + 1).padStart(2, '0')}</span>Event {index + 1}</h3><label>Event type<select value={quoteEvent.eventTypeId} onChange={(event) => updateEvent(index, { eventTypeId: event.target.value })}>{context.eventTypes.map((type) => <option value={type.id} key={type.id}>{type.name}</option>)}</select></label></div>
                      <div className="quote-service-grid">
                        {context.services.map((service) => {
                          const checked = quoteEvent.serviceIds.includes(service.id);
                          return <label className={checked ? 'quote-service selected' : 'quote-service'} key={service.id}><input type="checkbox" checked={checked} onChange={(event) => updateEvent(index, { serviceIds: event.target.checked ? [...quoteEvent.serviceIds, service.id] : quoteEvent.serviceIds.filter((id) => id !== service.id) })} /><span><b>{service.name}</b><small>{service.description}</small></span></label>;
                        })}
                      </div>
                    </article>
                  ))}
                </div>
              </section>
              <section className="quote-card">
                <div className="quote-card-heading"><div><h2>Make it an album</h2><p>Add a printed album to preserve your favourite memories.</p></div><span>OPTIONAL</span></div>
                <div className="quote-album-row"><div><b>Premium photo album</b><p>Each sheet includes an average of 4–6 photos.</p></div><label>Number of sheets<input type="number" min="0" max="500" step="1" value={form.albumSheets} onChange={(event) => setForm((current) => ({ ...current, albumSheets: event.target.value }))} /></label></div>
              </section>
              <section className="quote-card">
                <div className="quote-card-heading"><div><h2>Check dates &amp; plan your booking</h2><p>Tell the studio your preferred date and time.</p></div><span>NEXT STEP</span></div>
                <div className="quote-fields-grid"><label>Main event date<input type="date" min={today} value={form.eventDate} onChange={updateDate} /></label><label>Preferred time<select value={form.preferredTime} onChange={(event) => setForm((current) => ({ ...current, preferredTime: event.target.value }))}><option>Morning</option><option>Afternoon</option><option>Evening</option><option>Full day</option></select></label></div>
                <div className="quote-coupon-row"><label>Festival coupon code<input value={couponInput} onChange={(event) => setCouponInput(event.target.value)} placeholder="Enter FESTFSK10" /></label><button className="button button-quiet" type="button" onClick={applyCoupon}>Apply code</button></div>
                <p className="quote-coupon-note">{couponMessage}</p>
              </section>
            </div>
            <aside className="quote-summary-card">
              <p className="quote-summary-kicker">YOUR PERSONALISED ESTIMATE</p><h2>Quotation summary</h2>
              <div className="quote-summary-total"><span>Estimated total</span>{pricingBusy ? <b>Updating…</b> : finalPrice === null ? <b>—</b> : <b>{formatInr(Number(finalPrice))}</b>}</div>
              <p className="quote-summary-note">This estimate is subject to date availability and final confirmation by {context.studioName}.</p>
              <button className="button button-light quote-submit-button" type="submit" disabled={submitting || pricingBusy || finalPrice === null || form.events.length === 0}>{submitting ? 'Submitting quotation…' : 'Submit quotation'} <span aria-hidden="true">↗</span></button>
              <p className="quote-summary-fineprint">Submitting sends the quotation PDF to your photographer. This estimate is not a confirmed booking.</p>
            </aside>
          </div>
        </form>
      </div>
      <footer className="quote-builder-footer">© 2026 FrameBucket · {context.studioName}</footer>
    </main>
  );
}

function CustomerPortalPage({ customer, onLogout }) {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [signingOut, setSigningOut] = useState(false);
  const [locationEvent, setLocationEvent] = useState(null);
  const [locationBusy, setLocationBusy] = useState(false);
  const [galleryEvent, setGalleryEvent] = useState(null);

  useEffect(() => {
    let cancelled = false;
    api.customerEvents()
      .then((eventList) => {
        if (!cancelled) setEvents(eventList);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  async function logout() {
    setSigningOut(true);
    setError('');
    try {
      await onLogout();
    } catch (requestError) {
      setError(requestError.message);
      setSigningOut(false);
    }
  }

  async function saveLocation(eventId, form) {
    setError('');
    setNotice('');
    setLocationBusy(true);
    try {
      const updatedEvent = await api.updateCustomerEventLocation(eventId, form);
      setEvents((current) => current.map((event) => event.eventId === eventId ? updatedEvent : event));
      setLocationEvent(null);
      setNotice('Event location and time have been shared with your photographer.');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLocationBusy(false);
    }
  }

  return (
    <main className="customer-portal-shell">
      <header className="customer-portal-header">
        <Brand />
        <div className="account-menu">
          <span className="account-avatar">{customer.name.slice(0, 1).toUpperCase()}</span>
          <span className="account-name">{customer.name}</span>
          <button className="button button-quiet button-small" type="button" onClick={logout} disabled={signingOut}>{signingOut ? 'Signing out…' : 'Sign out'}</button>
        </div>
      </header>
      <section className="customer-portal-welcome">
        <p className="eyebrow"><span className="eyebrow-dot" /> YOUR FRAMEBUCKET EVENTS</p>
        <h1>Welcome, <em>{customer.name.split(' ')[0]}.</em></h1>
        <p>See your booked photoshoot events and the photographer managing each one.</p>
      </section>
      {error && <p className="form-error" role="alert">{error}</p>}
      {notice && <p className="form-success" role="status">{notice}</p>}
      <section className="customer-event-section" aria-labelledby="booked-events-title">
        <div className="customer-event-heading">
          <div>
            <p className="eyebrow">YOUR BOOKINGS</p>
            <h2 id="booked-events-title">Booked events</h2>
          </div>
          {!loading && <span className="event-count">{events.length} {events.length === 1 ? 'EVENT' : 'EVENTS'}</span>}
        </div>
        {loading ? (
          <p className="customer-empty">Loading your events…</p>
        ) : events.length === 0 ? (
          <div className="customer-event-empty">
            <div className="upload-mark">✦</div>
            <h3>No booked events to show yet.</h3>
            <p>When your photographer adds an event, it will appear here with its event date, status, and photographer name.</p>
          </div>
        ) : (
          <div className="customer-table-scroll">
            <table className="customer-table customer-event-table">
              <thead><tr><th scope="col">Event</th><th scope="col">Photographer</th><th scope="col">Event date</th><th scope="col">Time</th><th scope="col">Status</th><th scope="col">Assignee</th><th scope="col">Location</th><th scope="col">Photo Gallery</th></tr></thead>
              <tbody>
                {events.map((event, index) => (
                  <tr key={event.eventId || `${event.eventName}-${event.eventDate}-${index}`}>
                    <th scope="row">{event.eventName}</th>
                    <td>{event.photographerName}</td>
                    <td>{event.eventDate ? new Date(`${event.eventDate}T00:00:00`).toLocaleDateString() : 'To be confirmed'}</td>
                    <td>{formatEventTime(event.eventTime)}</td>
                    <td><span className="event-status">{event.status}</span></td>
                    <td>{event.assigneeName || 'Not assigned yet'}</td>
                    <td><div className="customer-location-cell">
                      {event.locationName && (isGoogleMapsUrl(event.locationMapUrl)
                        ? <a className="customer-id-link" href={event.locationMapUrl} target="_blank" rel="noreferrer">{event.locationName} ↗</a>
                        : <span>{event.locationName}</span>)}
                      {event.locationLandmark && <small>Landmark: {event.locationLandmark}</small>}
                      <button className="customer-id-link" type="button" onClick={() => setLocationEvent(event)}>{event.locationName ? 'Update location' : 'Add location details'}</button>
                    </div></td>
                    <td><button className="button button-dark button-small event-gallery-button" type="button" onClick={() => setGalleryEvent(event)}>{event.albumSubmitted ? 'View gallery · sent' : 'Open gallery'}</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {locationEvent && <CustomerLocationModal event={locationEvent} busy={locationBusy} error={error} onClose={() => { if (!locationBusy) { setLocationEvent(null); setError(''); } }} onSave={saveLocation} />}
      {galleryEvent && <CustomerGalleryModal event={galleryEvent} onClose={() => setGalleryEvent(null)} onSubmitted={() => {
        setGalleryEvent(null);
        setEvents((current) => current.map((event) => event.eventId === galleryEvent.eventId ? { ...event, albumSubmitted: true } : event));
        setNotice(`Your selection for ${galleryEvent.eventName} was sent to your photographer.`);
      }} />}
      <footer className="customer-portal-footer"><Brand /><span>© 2026 FrameBucket</span></footer>
    </main>
  );
}

function CustomerGalleryModal({ event, onClose, onSubmitted }) {
  const [media, setMedia] = useState([]);
  const [folders, setFolders] = useState([]);
  const [folderId, setFolderId] = useState('__all');
  const [selectedIds, setSelectedIds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [savingSelection, setSavingSelection] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [view, setView] = useState('gallery');
  const [enlargedPhotoId, setEnlargedPhotoId] = useState(null);
  const selectionSaveQueue = useRef(Promise.resolve());
  const selectionRevision = useRef(0);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api.customerMedia(event.eventId),
      api.customerFolders(event.eventId),
      api.customerSelection(event.eventId),
    ])
      .then(([mediaResponse, folderResponse, selectionResponse]) => {
        if (cancelled) return;
        setMedia(mediaResponse.photos || []);
        setFolders(folderResponse.folders || []);
        setSelectedIds(selectionResponse.photo_ids || []);
      })
      .catch((requestError) => { if (!cancelled) setError(requestError.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [event.eventId]);

  async function submitSelection() {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await selectionSaveQueue.current.catch(() => {});
      await api.saveCustomerSelection(event.eventId, selectedIds);
      const result = await api.submitAlbum(event.eventId, selectedIds);
      onSubmitted(result);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  function togglePhoto(photoId, shouldSelect = !selectedIds.includes(photoId)) {
    const alreadySelected = selectedIds.includes(photoId);
    if (alreadySelected === shouldSelect) return;
    const nextIds = shouldSelect
      ? [...selectedIds, photoId]
      : selectedIds.filter((id) => id !== photoId);
    setSelectedIds(nextIds);
    setError('');
    setNotice('');
    setSavingSelection(true);
    const revision = ++selectionRevision.current;
    selectionSaveQueue.current = selectionSaveQueue.current
      .catch(() => {})
      .then(() => api.saveCustomerSelection(event.eventId, nextIds))
      .then(() => {
        if (revision === selectionRevision.current) setSavingSelection(false);
      })
      .catch((requestError) => {
        if (revision === selectionRevision.current) {
          setSavingSelection(false);
          setError(requestError.message);
        }
      });
  }

  async function finalizeSelection() {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await selectionSaveQueue.current.catch(() => {});
      await api.saveCustomerSelection(event.eventId, selectedIds);
      setView('review');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  async function downloadPhotos(photoIds) {
    setBusy(true);
    setError('');
    setNotice('');
    const safeEventName = String(event.eventName || 'gallery').replace(/[^a-z0-9._-]+/gi, '-');
    const singlePhoto = photoIds?.length === 1
      ? media.find((item) => item.photo_id === photoIds[0])
      : !photoIds?.length && media.length === 1 ? media[0] : null;
    const singleExtension = singlePhoto?.media_type === 'image'
      ? '.jpg'
      : singlePhoto?.original_filename?.match(/\.[^./\\]+$/)?.[0] || '';
    const singleFilename = singlePhoto
      ? `${String(singlePhoto.photo_number).padStart(5, '0')}_${String(singlePhoto.original_filename || 'photo').replace(/\.[^./\\]+$/, '')}${singleExtension}`
        .replace(/[^a-z0-9._-]+/gi, '_').replace(/^[._]+|[._]+$/g, '')
      : null;
    const filename = singleFilename || `FrameBucket-${safeEventName}-${photoIds?.length ? 'selected' : 'all'}-photos.zip`;
    try {
      await api.downloadCustomerGallery(event.eventId, photoIds || [], filename);
      setNotice(photoIds?.length === 1 ? 'Photo downloaded.' : photoIds?.length ? 'Selected photos downloaded.' : 'All gallery photos downloaded.');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  const visibleMedia = folderId === '__all'
    ? media
    : media.filter((item) => item.folder_id === folderId);
  const navigablePhotos = visibleMedia.filter((item) => item.media_type !== 'video');
  const selectedMedia = media.filter((item) => selectedIds.includes(item.photo_id));
  const folderNames = new Map(folders.map((folder) => [folder.folder_id, folder.name]));
  const selectedByFolder = folders
    .map((folder) => ({
      ...folder,
      photos: selectedMedia.filter((item) => item.folder_id === folder.folder_id)
        .sort((a, b) => a.photo_number - b.photo_number),
    }))
    .filter((folder) => folder.photos.length > 0);
  const enlargedPhoto = media.find((item) => item.photo_id === enlargedPhotoId);
  const enlargedPhotoIndex = navigablePhotos.findIndex((item) => item.photo_id === enlargedPhotoId);

  function moveEnlargedPhoto(direction) {
    if (navigablePhotos.length < 2 || enlargedPhotoIndex < 0) return;
    const nextIndex = (enlargedPhotoIndex + direction + navigablePhotos.length) % navigablePhotos.length;
    setEnlargedPhotoId(navigablePhotos[nextIndex].photo_id);
  }

  return (
    <div className="modal-backdrop gallery-modal-backdrop" role="presentation" onMouseDown={(clickEvent) => { if (clickEvent.target === clickEvent.currentTarget && !busy) onClose(); }}>
      <section className="gallery-modal" role="dialog" aria-modal="true" aria-labelledby="customer-gallery-title">
        <div className="modal-heading"><div><p className="eyebrow">YOUR EVENT PHOTOS</p><h2 id="customer-gallery-title">{view === 'review' ? 'Finalize selection' : `${event.eventName} gallery`}</h2></div><button className="modal-close" type="button" onClick={onClose} disabled={busy} aria-label="Close gallery">×</button></div>
        <p className="gallery-modal-note">{event.albumSubmitted ? 'Your selection has been sent to your photographer. You can still view and download this gallery.' : 'Choose photos for album design. Your selection is saved while you move between folders. Videos can be viewed and downloaded, but cannot be selected for album design.'}</p>
        {error && <p className="form-error" role="alert">{error}</p>}
        {notice && <p className="form-success" role="status">{notice}</p>}
        {loading ? <p className="customer-empty">Loading event gallery…</p> : media.length === 0 ? (
          <div className="gallery-empty"><span aria-hidden="true">✦</span><h3>Your photographer is preparing this gallery.</h3><p>Check back once the processed event photos are ready.</p></div>
        ) : view === 'review' ? (
          <div className="gallery-selection-review">
            <p>Review the photo numbers selected from each folder before sending your selection to the photographer.</p>
            {selectedByFolder.length === 0 ? <p className="customer-empty">No photos selected.</p> : selectedByFolder.map((folder) => (
              <section className="gallery-review-folder" key={folder.folder_id}>
                <h3>{folder.name}</h3>
                <ul>{folder.photos.map((item) => <li key={item.photo_id}><b>#{item.photo_number}</b><span>{item.original_filename}</span></li>)}</ul>
              </section>
            ))}
          </div>
        ) : (
          <>
            <div className="customer-gallery-toolbar">
              <label className="gallery-field">Folder
                <select value={folderId} onChange={(changeEvent) => setFolderId(changeEvent.target.value)}>
                  <option value="__all">All folders</option>
                  {folders.map((folder) => <option key={folder.folder_id} value={folder.folder_id}>{folder.name}</option>)}
                </select>
              </label>
              <div className="customer-gallery-downloads">
                <button className="button button-quiet" type="button" onClick={() => downloadPhotos([])} disabled={busy || media.length === 0}>Download all photos</button>
                <button className="button button-dark" type="button" onClick={() => downloadPhotos(selectedIds)} disabled={busy || selectedIds.length === 0}>Download selected</button>
              </div>
            </div>
            {savingSelection && <p className="gallery-selection-status" role="status">Saving selection to your temporary folder…</p>}
            {!savingSelection && !event.albumSubmitted && selectedIds.length > 0 && <p className="gallery-selection-status" role="status">{selectedIds.length} photo{selectedIds.length === 1 ? '' : 's'} staged for album design across your folders.</p>}
            {visibleMedia.length === 0 ? (
              <div className="gallery-empty"><span aria-hidden="true">✦</span><h3>This folder is ready.</h3><p>There are no customer photos in this folder yet.</p></div>
            ) : (
              <div className="gallery-grid customer-gallery-grid">
                {visibleMedia.map((item) => {
                  const selected = selectedIds.includes(item.photo_id);
                  return (
                    <article className={`gallery-card customer-gallery-card ${selected ? 'is-selected' : ''}`} key={item.photo_id}>
                      <div className="gallery-card-preview">
                        {item.media_type === 'video'
                          ? <video src={api.customerPreviewUrl(event.eventId, item.photo_id)} controls preload="metadata" aria-label={item.original_filename} />
                          : <button className="customer-photo-open" type="button" onClick={() => setEnlargedPhotoId(item.photo_id)} aria-label={`Enlarge photo ${item.photo_number}`}><img src={api.customerPreviewUrl(event.eventId, item.photo_id)} alt={item.original_filename} loading="lazy" /></button>}
                        <span className="gallery-photo-number">{item.media_type === 'video' ? 'VIDEO' : `#${item.photo_number}`}</span>
                        {item.media_type !== 'video' && !event.albumSubmitted && <label className={`gallery-photo-checkbox ${selected ? 'is-selected' : ''}`} title={`Select photo ${item.photo_number} for album design`}><input id={`customer-photo-select-${item.photo_id}`} type="checkbox" aria-label={`Select photo ${item.photo_number} for album design`} checked={selected} onChange={(changeEvent) => togglePhoto(item.photo_id, changeEvent.target.checked)} disabled={busy} /></label>}
                      </div>
                      <div className="gallery-card-copy"><b title={item.original_filename}>{item.original_filename}</b><span>{item.media_type === 'video' ? 'Video preview' : `${folderNames.get(item.folder_id) || 'Gallery'} · Processed photo`}</span></div>
                      {item.media_type !== 'video' && !event.albumSubmitted && <label className="gallery-select-caption" htmlFor={`customer-photo-select-${item.photo_id}`}>select for album design</label>}
                    </article>
                  );
                })}
              </div>
            )}
          </>
        )}
        <div className="gallery-modal-actions">
          <span>{event.albumSubmitted ? 'Selection already sent' : `${selectedIds.length} photos selected`}</span>
          {view === 'review' ? <>
            <button className="button button-quiet" type="button" onClick={() => setView('gallery')} disabled={busy}>Back to gallery</button>
            {!event.albumSubmitted && <button className="button button-dark" type="button" onClick={submitSelection} disabled={busy || selectedIds.length === 0}>{busy ? 'Submitting…' : 'Submit for Album design'}</button>}
          </> : <>
            <button className="button button-quiet" type="button" onClick={onClose} disabled={busy}>Close</button>
            {!event.albumSubmitted && <button className="button button-dark" type="button" onClick={finalizeSelection} disabled={busy || loading || selectedIds.length === 0 || savingSelection}>{busy ? 'Preparing…' : 'Finalize Selection'}</button>}
          </>}
        </div>
        {enlargedPhoto && <div className="gallery-lightbox" role="presentation" onMouseDown={(clickEvent) => { if (clickEvent.target === clickEvent.currentTarget) setEnlargedPhotoId(null); }}>
          <section className="gallery-lightbox-panel" role="dialog" aria-modal="true" aria-label={`Photo ${enlargedPhoto.photo_number}`}>
            <button className="gallery-lightbox-close" type="button" onClick={() => setEnlargedPhotoId(null)} aria-label="Close enlarged photo">×</button>
            <img src={api.customerPreviewUrl(event.eventId, enlargedPhoto.photo_id)} alt={enlargedPhoto.original_filename} />
            <div className="gallery-lightbox-actions">
              <div className="gallery-lightbox-navigation">
                <button className="button button-quiet" type="button" onClick={() => moveEnlargedPhoto(-1)} disabled={navigablePhotos.length < 2}>← Previous</button>
                <span className="gallery-lightbox-number">Photo {enlargedPhotoIndex + 1} of {navigablePhotos.length} · #{enlargedPhoto.photo_number}</span>
                <button className="button button-quiet" type="button" onClick={() => moveEnlargedPhoto(1)} disabled={navigablePhotos.length < 2}>Next →</button>
              </div>
              {!event.albumSubmitted && <label className={`gallery-select-photo gallery-lightbox-selection ${selectedIds.includes(enlargedPhoto.photo_id) ? 'is-selected' : ''}`}><input type="checkbox" checked={selectedIds.includes(enlargedPhoto.photo_id)} onChange={(changeEvent) => togglePhoto(enlargedPhoto.photo_id, changeEvent.target.checked)} disabled={busy} /> select for album design</label>}
              <button className="button button-dark" type="button" onClick={() => downloadPhotos([])} disabled={busy}>{busy ? 'Preparing download…' : 'Download all photos'}</button>
            </div>
          </section>
        </div>}
      </section>
    </div>
  );
}

function googleMapsSearchUrl(query) {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query || 'event venue')}`;
}

function isGoogleMapsUrl(value) {
  if (!value) return false;
  try {
    const url = new URL(value);
    const host = url.hostname.toLowerCase();
    return url.protocol === 'https:' && (
      host === 'maps.app.goo.gl' || host === 'google.com' || host === 'www.google.com'
      || host === 'maps.google.com' || host.endsWith('.google.com')
      || host === 'google.co.in' || host === 'www.google.co.in' || host === 'maps.google.co.in'
      || (host === 'goo.gl' && url.pathname.startsWith('/maps'))
    );
  } catch {
    return false;
  }
}

function formatEventTime(value) {
  if (!value) return 'Not provided';
  const match = /^(\d{2}):(\d{2})/.exec(value);
  if (!match) return value;
  return new Date(2000, 0, 1, Number(match[1]), Number(match[2])).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

function CustomerLocationModal({ event, busy, error, onClose, onSave }) {
  const [form, setForm] = useState({
    locationName: event.locationName || '',
    locationMapUrl: event.locationMapUrl || '',
    landmark: event.locationLandmark || '',
    notes: event.locationNotes || '',
    eventTime: event.eventTime ? event.eventTime.slice(0, 5) : '',
  });
  const [mapLinkError, setMapLinkError] = useState('');

  function update(changeEvent) {
    setForm((current) => ({ ...current, [changeEvent.target.name]: changeEvent.target.value }));
  }

  function submit(changeEvent) {
    changeEvent.preventDefault();
    if (form.locationMapUrl.trim() && !isGoogleMapsUrl(form.locationMapUrl.trim())) {
      setMapLinkError('Paste a secure Google Maps place or share link.');
      return;
    }
    setMapLinkError('');
    onSave(event.eventId, {
      locationName: form.locationName.trim(),
      locationMapUrl: form.locationMapUrl.trim() || null,
      landmark: form.landmark.trim() || null,
      notes: form.notes.trim() || null,
      eventTime: form.eventTime,
    });
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(clickEvent) => { if (clickEvent.target === clickEvent.currentTarget && !busy) onClose(); }}>
      <section className="event-modal customer-location-modal" role="dialog" aria-modal="true" aria-labelledby="customer-location-title">
        <div className="modal-heading"><div><p className="eyebrow">HELP YOUR PHOTOGRAPHER FIND YOU</p><h2 id="customer-location-title">Event location</h2></div><button className="modal-close" type="button" onClick={onClose} disabled={busy} aria-label="Close location details">×</button></div>
        <p className="event-form-note">Choose the venue in Google Maps, then share its link here. Your landmark and notes will help your crew find the exact entrance.</p>
        {error && <p className="form-error" role="alert">{error}</p>}
        <form className="event-form" onSubmit={submit}>
          <label className="event-form-label">Location or venue name<input name="locationName" value={form.locationName} onChange={update} required maxLength="240" placeholder="Venue name or full address" /></label>
          <a className="button button-quiet google-maps-picker" href={googleMapsSearchUrl(form.locationName)} target="_blank" rel="noreferrer">Choose in Google Maps <span aria-hidden="true">↗</span></a>
          <label className="event-form-label">Google Maps share link<input name="locationMapUrl" type="url" value={form.locationMapUrl} onChange={update} maxLength="2048" placeholder="https://maps.app.goo.gl/…" /></label>
          {mapLinkError && <p className="form-error" role="alert">{mapLinkError}</p>}
          <p className="customer-form-note">In Google Maps, select the venue, choose Share, and paste the copied link here. If you leave this blank, we’ll create a Google Maps search link from the venue name.</p>
          <label className="event-form-label">Landmark<input name="landmark" value={form.landmark} onChange={update} maxLength="240" placeholder="Near the main gate, opposite…" /></label>
          <label className="event-form-label">Notes for reaching the location<textarea name="notes" value={form.notes} onChange={update} maxLength="2000" rows="3" placeholder="Any directions or access details for your photographer" /></label>
          <label className="event-form-label">Event time<input name="eventTime" type="time" value={form.eventTime} onChange={update} required /></label>
          <p className="customer-form-note">Please update the time which Photographer should be ready</p>
          <div className="event-form-actions"><button className="button button-quiet" type="button" onClick={onClose} disabled={busy}>Cancel</button><button className="button button-dark" type="submit" disabled={busy}>{busy ? 'Saving details…' : 'Save event details'} <span aria-hidden="true">↗</span></button></div>
        </form>
      </section>
    </div>
  );
}

function DataPanel({ initialEventId = '' }) {
  const [events, setEvents] = useState([]);
  const [eventId, setEventId] = useState(initialEventId);
  const [folders, setFolders] = useState([]);
  const [folderId, setFolderId] = useState('root');
  const [media, setMedia] = useState([]);
  const [loadingEvents, setLoadingEvents] = useState(true);
  const [loadingGallery, setLoadingGallery] = useState(false);
  const [busy, setBusy] = useState(false);
  const [newFolder, setNewFolder] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    let cancelled = false;
    api.events()
      .then((loadedEvents) => {
        if (cancelled) return;
        setEvents(loadedEvents);
        setEventId((current) => loadedEvents.some((event) => event.id === initialEventId)
          ? initialEventId
          : loadedEvents.some((event) => event.id === current) ? current : loadedEvents[0]?.id || '');
      })
      .catch((requestError) => { if (!cancelled) setError(requestError.message); })
      .finally(() => { if (!cancelled) setLoadingEvents(false); });
    return () => { cancelled = true; };
  }, [initialEventId]);

  useEffect(() => {
    if (!eventId) {
      setFolders([]);
      setMedia([]);
      return undefined;
    }
    let cancelled = false;
    setLoadingGallery(true);
    setError('');
    Promise.all([api.dataFolders(eventId), api.photographerMedia(eventId, folderId)])
      .then(([folderResponse, mediaResponse]) => {
        if (cancelled) return;
        const loadedFolders = folderResponse.folders || [];
        setFolders(loadedFolders);
        if (!loadedFolders.some((folder) => folder.folder_id === folderId)) setFolderId('root');
        setMedia(mediaResponse.photos || []);
      })
      .catch((requestError) => { if (!cancelled) setError(requestError.message); })
      .finally(() => { if (!cancelled) setLoadingGallery(false); });
    return () => { cancelled = true; };
  }, [eventId, folderId]);

  async function addFolder(submitEvent) {
    submitEvent.preventDefault();
    if (!eventId || !newFolder.trim()) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const createdFolder = await api.createDataFolder(eventId, newFolder.trim());
      setFolders((current) => [...current, createdFolder]);
      setFolderId(createdFolder.folder_id);
      setNewFolder('');
      setNotice(`Folder “${createdFolder.name}” created.`);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  async function uploadFiles(changeEvent) {
    const files = Array.from(changeEvent.target.files || []);
    if (!eventId || files.length === 0) return;
    setBusy(true);
    setError('');
    setNotice('');
    let uploaded = 0;
    try {
      for (const file of files) {
        await api.uploadDataFile(eventId, folderId, file);
        uploaded += 1;
      }
      const response = await api.photographerMedia(eventId, folderId);
      setMedia(response.photos || []);
      setNotice(`${uploaded} ${uploaded === 1 ? 'file' : 'files'} uploaded. Originals are private to your photographer account.`);
    } catch (requestError) {
      setError(uploaded ? `${uploaded} file(s) uploaded; the next upload failed: ${requestError.message}` : requestError.message);
      api.photographerMedia(eventId, folderId).then((response) => setMedia(response.photos || [])).catch(() => {});
    } finally {
      setBusy(false);
      changeEvent.target.value = '';
    }
  }

  const selectedEvent = events.find((event) => event.id === eventId);
  return (
    <section className="gallery-module" aria-labelledby="data-gallery-title">
      <div className="gallery-intro">
        <div>
          <p className="eyebrow">PRIVATE ORIGINALS · CUSTOMER PREVIEWS</p>
          <h2 id="data-gallery-title">Event <em>galleries.</em></h2>
          <p>Upload images and videos, organize them into folders, and give customers access to processed previews. Original files stay private to your account.</p>
        </div>
        <span className="gallery-count">{media.length} {media.length === 1 ? 'ITEM' : 'ITEMS'}</span>
      </div>
      {error && <p className="form-error customer-message" role="alert">{error}</p>}
      {notice && <p className="form-success customer-message" role="status">{notice}</p>}
      {loadingEvents ? <p className="customer-empty">Loading your events…</p> : events.length === 0 ? (
        <div className="customer-empty-state"><div className="upload-mark">＋</div><h4>Create an event to start a gallery.</h4><p>Each event gets its own customer gallery and private photographer storage.</p></div>
      ) : (
        <>
          <div className="gallery-toolbar">
            <label className="gallery-field">Event
              <select value={eventId} onChange={(changeEvent) => { setEventId(changeEvent.target.value); setFolderId('root'); }}>
                {events.map((event) => <option key={event.id} value={event.id}>{event.eventCode} · {event.customerName}</option>)}
              </select>
            </label>
            <label className="gallery-field">Folder
              <select value={folderId} onChange={(changeEvent) => setFolderId(changeEvent.target.value)}>
                {folders.map((folder) => <option key={folder.folder_id} value={folder.folder_id}>{folder.name}</option>)}
                {folders.length === 0 && <option value="root">Gallery</option>}
              </select>
            </label>
            <form className="gallery-create-folder" onSubmit={addFolder}>
              <label className="gallery-field">New folder
                <input value={newFolder} onChange={(changeEvent) => setNewFolder(changeEvent.target.value)} maxLength="80" placeholder="For example: Ceremony" />
              </label>
              <button className="button button-quiet" type="submit" disabled={busy || !newFolder.trim()}>＋ Add folder</button>
            </form>
            <label className={`button button-dark gallery-upload ${busy ? 'is-busy' : ''}`}>
              {busy ? 'Uploading…' : '＋ Upload photos / videos'}
              <input type="file" multiple accept="image/*,.arw,.cr2,.cr3,.dng,.nef,.orf,.raf,.rw2,video/mp4,video/quicktime,video/webm,.avi,.m4v" onChange={uploadFiles} disabled={busy || !selectedEvent} />
            </label>
          </div>
          {selectedEvent && <p className="gallery-event-note"><b>{selectedEvent.eventCode}</b> · {selectedEvent.customerName} · Folder: {folders.find((folder) => folder.folder_id === folderId)?.name || 'Gallery'}</p>}
          {loadingGallery ? <p className="customer-empty">Loading gallery…</p> : media.length === 0 ? (
            <div className="gallery-empty"><span aria-hidden="true">✦</span><h3>This folder is ready.</h3><p>Upload images or videos to begin building the customer gallery.</p></div>
          ) : (
            <div className="gallery-grid">
              {media.map((item) => (
                <article className="gallery-card" key={item.photo_id}>
                  <div className="gallery-card-preview">
                    {item.media_type === 'video'
                      ? <video src={api.photographerPreviewUrl(eventId, item.photo_id)} controls preload="metadata" aria-label={item.original_filename} />
                      : <img src={api.photographerPreviewUrl(eventId, item.photo_id)} alt={item.original_filename} loading="lazy" />}
                    <span className="gallery-photo-number">{item.media_type === 'video' ? 'VIDEO' : `#${item.photo_number}`}</span>
                  </div>
                  <div className="gallery-card-copy"><b title={item.original_filename}>{item.original_filename}</b><span>{item.media_type === 'video' ? 'Customer video' : `${item.width || '—'} × ${item.height || '—'} · processed preview`}</span></div>
                  <a className="gallery-original-link" href={api.photographerOriginalUrl(eventId, item.photo_id)}>Download original ↗</a>
                </article>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  );
}

function LoadingPage() {
  return <main className="loading-shell"><Brand /><p>Opening your workspace…</p></main>;
}
