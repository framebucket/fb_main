import { useEffect, useState } from 'react';
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
        <button className={activeTab === 'overview' ? 'module-tab active' : 'module-tab'} onClick={() => setActiveTab('overview')} aria-pressed={activeTab === 'overview'}>
          <span className="tab-index">00</span> Overview <span className="tab-arrow">↗</span>
        </button>
        <button className={activeTab === 'enquiries' ? 'module-tab active' : 'module-tab'} onClick={() => setActiveTab('enquiries')} aria-pressed={activeTab === 'enquiries'}>
          <span className="tab-index">01</span> Enquiries <span className="tab-arrow">↗</span>
        </button>
        <button className={activeTab === 'customers' ? 'module-tab active' : 'module-tab'} onClick={() => setActiveTab('customers')} aria-pressed={activeTab === 'customers'}>
          <span className="tab-index">02</span> Customers <span className="tab-arrow">↗</span>
        </button>
        <button className={activeTab === 'events' ? 'module-tab active' : 'module-tab'} onClick={() => setActiveTab('events')} aria-pressed={activeTab === 'events'}>
          <span className="tab-index">03</span> Events <span className="tab-arrow">↗</span>
        </button>
        <button
          className={activeTab === 'data' ? 'module-tab active' : 'module-tab'}
          onClick={() => setActiveTab('data')}
          aria-pressed={activeTab === 'data'}
          disabled={!dataModuleEnabled}
          title={dataModuleEnabled ? 'Open the Data module' : 'The Data module is not included with Bronze.'}
        >
          <span className="tab-index">04</span> Data {!dataModuleEnabled && <span className="tab-lock">BRONZE</span>} <span className="tab-arrow">↗</span>
        </button>
      </nav>
      <p className="workflow-sequence">Photography workflow <b>Enquiries</b><span>→</span><b>Customers</b><span>→</span><b>Events</b><span>→</span><b>Data</b></p>
      {activeTab === 'overview' ? <DashboardOverview user={user} /> : activeTab === 'enquiries' ? <EnquiryPanel user={user} /> : activeTab === 'customers' ? <CustomerPanel user={user} /> : activeTab === 'events' ? <EventPanel user={user} /> : <DataPanel />}
      <footer className="dashboard-footer"><Brand /><span>Make space for good work.</span><span>© 2026 FrameBucket</span></footer>
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
    { label: 'Enquiries', value: activity?.enquiries || 0, color: '#9fbbe9' },
    { label: 'Customers', value: activity?.customers || 0, color: '#688fc9' },
    { label: 'Events', value: activity?.events || 0, color: '#45628f' },
  ];
  const revenueItems = (summary?.revenueByEventTypeFinancialYear || []).map((item, index) => ({
    label: item.eventTypeName,
    value: Number(item.revenue),
    color: ['#9fbbe9', '#688fc9', '#45628f', '#70b9b5', '#9b8bd0', '#d5a16c'][index % 6],
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
          {total <= 0 ? <circle cx="110" cy="110" r="98" fill="#18243a" /> : chartSlices.length === 1 ? (
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
              <path d={path} fill={slice.color} stroke="#0b1020" strokeWidth="2" key={slice.label}>
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

function EventPanel({ user }) {
  const [events, setEvents] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [eventTypes, setEventTypes] = useState([]);
  const [blockedDates, setBlockedDates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [showRegistration, setShowRegistration] = useState(false);
  const [selectedCustomerId, setSelectedCustomerId] = useState('');

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.events(), api.customers(), api.eventTypes(), api.blockedEventDates()])
      .then(([loadedEvents, loadedCustomers, loadedTypes, loadedDates]) => {
        if (cancelled) return;
        setEvents(loadedEvents);
        setCustomers(loadedCustomers);
        setEventTypes(loadedTypes);
        setBlockedDates(loadedDates);
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

  const firstName = user.fullName.split(' ')[0];

  return (
    <section className="event-module" aria-labelledby="event-list-title">
      <div className="customer-module-intro">
        <p className="eyebrow">YOUR BOOKINGS, CLEARLY SCHEDULED</p>
        <h2>Events <em>at a glance.</em></h2>
        <p>Keep each event date, type, and customer together. A booked date is held for one event in your studio calendar.</p>
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
                  <th scope="col">Customer ID</th>
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
                    <td><a className="customer-id-link" href={`#customer-${event.customerUuid}`} onClick={(clickEvent) => { clickEvent.preventDefault(); setSelectedCustomerId(event.customerUuid); }}>{event.customerCode}</a><small className="event-customer-name">{event.customerName}</small></td>
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
  const [signingOut, setSigningOut] = useState(false);

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
              <thead><tr><th scope="col">Event</th><th scope="col">Photographer</th><th scope="col">Event date</th><th scope="col">Status</th></tr></thead>
              <tbody>
                {events.map((event, index) => (
                  <tr key={`${event.eventName}-${event.eventDate}-${index}`}>
                    <th scope="row">{event.eventName}</th>
                    <td>{event.photographerName}</td>
                    <td>{event.eventDate ? new Date(`${event.eventDate}T00:00:00`).toLocaleDateString() : 'To be confirmed'}</td>
                    <td><span className="event-status">{event.status}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <footer className="customer-portal-footer"><Brand /><span>© 2026 FrameBucket</span></footer>
    </main>
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
