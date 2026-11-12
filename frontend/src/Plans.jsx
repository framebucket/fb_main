import { useEffect, useState } from 'react';
import { api } from './api.js';
import './plans.css';

// Subscription plans: pricing table, plan & usage card, upgrade prompts.

export const PLAN_STATUS_LABELS = {
  TRIAL: 'Free trial',
  ACTIVE: 'Active',
  GRACE: 'Payment due',
  FREE: 'Free plan',
  TRIAL_ENDED: 'Trial ended',
  LAPSED: 'Expired',
  SUSPENDED: 'Suspended',
};

export const FEATURE_ORDER = ['CRM', 'QUOTATIONS', 'CUSTOM_PACKAGES', 'LEAD_CAPTURE', 'TEAM', 'GALLERY', 'DELIVERY_TRACKER',
  'BILLING', 'CONTRACTS', 'REMINDERS', 'PORTFOLIO', 'E_ALBUM', 'ALBUM_PROOFING', 'FILMS', 'WATERMARK', 'CREW_PAYOUTS', 'GEAR_COSTS',
  'WHATSAPP_AUTOMATION', 'FACE_RECOGNITION', 'PAID_DOWNLOADS', 'CUSTOM_BRANDING', 'AI_ALBUM_DESIGN', 'AI_INVITATIONS', 'INSIGHTS', 'CUSTOM_DOMAIN', 'PRIORITY_SUPPORT'];

export const LIMIT_LABELS = [
  ['customers', 'Customers'],
  ['upcomingEvents', 'Upcoming events at a time'],
  ['teamMembers', 'Team members'],
  ['storageGb', 'Photo storage'],
  ['whatsappMessagesPerMonth', 'WhatsApp messages / month'],
];

export function inrPlain(amount) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(Number(amount || 0));
}

export function formatBytes(bytes) {
  const value = Number(bytes || 0);
  if (value < 1024 ** 2) return `${Math.round(value / 1024)} KB`;
  if (value < 1024 ** 3) return `${(value / 1024 ** 2).toFixed(0)} MB`;
  return `${(value / 1024 ** 3).toFixed(value < 10 * 1024 ** 3 ? 1 : 0)} GB`;
}

export function limitText(key, value) {
  if (value === null || value === undefined) return 'Unlimited';
  if (key === 'storageGb') return value >= 1024 ? `${(value / 1024).toFixed(value % 1024 ? 1 : 0)} TB` : `${value} GB`;
  if (value === 0) return '—';
  return new Intl.NumberFormat('en-IN').format(value);
}

function daysUntil(instant) {
  if (!instant) return null;
  return Math.ceil((new Date(instant).getTime() - Date.now()) / 86400000);
}

export function hasFeature(user, feature) {
  return Boolean(user?.entitlements?.features?.includes(feature));
}

let publicPlansPromise = null;
export function loadPublicPlans() {
  if (!publicPlansPromise) publicPlansPromise = api.plans().catch((error) => { publicPlansPromise = null; throw error; });
  return publicPlansPromise;
}

/** The cheapest public plan that includes a feature, for "available on Solo and above" prompts. */
function cheapestPlanWith(plans, feature) {
  return plans.filter((plan) => plan.features.includes(feature) && Number(plan.monthlyPrice) > 0)
    .sort((a, b) => Number(a.monthlyPrice) - Number(b.monthlyPrice))[0];
}

/** Shown instead of a module the photographer's plan doesn't include. */
export function UpgradeNotice({ feature, title, children }) {
  const [plans, setPlans] = useState([]);
  useEffect(() => { loadPublicPlans().then(setPlans).catch(() => {}); }, []);
  const plan = cheapestPlanWith(plans, feature);
  return (
    <section className="upgrade-notice">
      <div className="upgrade-notice-icon" aria-hidden="true">✦</div>
      <div>
        <p className="eyebrow">Upgrade to unlock</p>
        <h2>{title}</h2>
        <p>{children}</p>
        {plan && <p className="upgrade-notice-plan">Included in <b>{plan.name}</b> and above, from {inrPlain(plan.monthlyPrice)}/month.</p>}
        <a className="button button-dark" href={plan ? `/payment?planCode=${plan.code}&billing=YEARLY` : '/#pricing'}>See plans</a>
      </div>
    </section>
  );
}

/** Upgrade message for an API error caused by a plan limit (error.code === 'PLAN_UPGRADE_REQUIRED'). */
export function PlanLimitMessage({ error }) {
  if (!error) return null;
  return (
    <p className="form-error" role="alert">
      {error.message}{error.code === 'PLAN_UPGRADE_REQUIRED' && <> <a className="customer-id-link" href="/#pricing">See plans ↗</a></>}
    </p>
  );
}

function UsageBar({ label, used, limit, format }) {
  const unlimited = limit === null || limit === undefined;
  const percent = unlimited || !limit ? 0 : Math.min(100, Math.round((used / limit) * 100));
  return (
    <div className={`usage-bar${percent >= 90 ? ' is-full' : percent >= 70 ? ' is-high' : ''}`}>
      <div className="usage-bar-label"><span>{label}</span><b>{format(used)} / {unlimited ? 'Unlimited' : format(limit)}</b></div>
      <div className="usage-bar-track"><span style={{ width: unlimited ? '4%' : `${percent}%` }} /></div>
    </div>
  );
}

/** Plan, status and usage, with upgrade / pending-request actions. Shown on the dashboard. */
export function PlanUsageCard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let cancelled = false;
    api.mySubscription()
      .then((result) => { if (!cancelled) setData(result); })
      .catch((requestError) => { if (!cancelled) setError(requestError.message); });
    return () => { cancelled = true; };
  }, [version]);

  if (error) return <p className="form-error dashboard-error">{error}</p>;
  if (!data) return null;
  const { entitlements: ent, usage, pendingRequest } = data;
  const limits = ent.limits || {};
  const trialDays = daysUntil(ent.trialEndsAt);
  const renewDays = daysUntil(ent.planExpiresAt);
  const statusTone = ['GRACE', 'LAPSED', 'TRIAL_ENDED', 'SUSPENDED'].includes(ent.status) ? 'warn' : ent.status === 'TRIAL' ? 'info' : 'ok';

  let note;
  if (ent.status === 'TRIAL') note = `${trialDays} day${trialDays === 1 ? '' : 's'} left in your free trial of every Studio feature.`;
  else if (ent.status === 'TRIAL_ENDED') note = 'Your trial has ended, so you are on the Free plan. Your data is safe; upgrade to unlock everything again.';
  else if (ent.status === 'LAPSED') note = `Your ${ent.subscribedPlanCode} plan has expired, so you are on the Free plan. Renew to unlock your features again.`;
  else if (ent.status === 'GRACE') note = 'Your plan period has ended. Renew in the next few days to keep your features.';
  else if (ent.status === 'ACTIVE' && ent.planExpiresAt) note = `Paid until ${new Date(ent.planExpiresAt).toLocaleDateString()}${renewDays <= 7 ? ' — renew soon' : ''}.`;
  else if (ent.status === 'FREE') note = 'Upgrade any time for unlimited bookings, billing, e-albums and more.';

  async function cancelRequest() {
    setBusy(true);
    try { await api.cancelPlanRequest(); setVersion((v) => v + 1); } catch (requestError) { setError(requestError.message); } finally { setBusy(false); }
  }

  return (
    <section className={`plan-card plan-card-${statusTone}`} aria-label="Your plan">
      <div className="plan-card-main">
        <p className="eyebrow">Your plan</p>
        <h2>{ent.planName} <span className={`plan-status plan-status-${statusTone}`}>{PLAN_STATUS_LABELS[ent.status] || ent.status}</span></h2>
        {note && <p className="plan-card-note">{note}</p>}
        {pendingRequest ? (
          <div className="plan-card-pending">
            <span>Requested <b>{pendingRequest.planName}</b> ({pendingRequest.billingCycle === 'YEARLY' ? 'yearly' : 'monthly'}, {inrPlain(pendingRequest.amount)} + GST). We’ll contact you to complete payment and activate it.</span>
            <button className="customer-id-link" type="button" onClick={cancelRequest} disabled={busy}>Cancel request</button>
          </div>
        ) : (
          ent.planCode !== 'BUSINESS' && <a className="button button-dark button-small" href="/#pricing">{ent.status === 'ACTIVE' || ent.status === 'GRACE' ? 'Change plan' : 'Upgrade'}</a>
        )}
      </div>
      <div className="plan-card-usage">
        <UsageBar label="Customers" used={usage.customers} limit={limits.customers} format={(v) => new Intl.NumberFormat('en-IN').format(v)} />
        <UsageBar label="Upcoming events" used={usage.upcomingEvents} limit={limits.upcomingEvents} format={(v) => new Intl.NumberFormat('en-IN').format(v)} />
        <UsageBar label="Team members" used={usage.teamMembers} limit={limits.teamMembers} format={(v) => String(v)} />
        <UsageBar label="Photo storage" used={usage.storageBytes} limit={limits.storageGb == null ? null : limits.storageGb * 1024 ** 3} format={formatBytes} />
      </div>
    </section>
  );
}

/** Public pricing: plan cards + comparison table. */
export function PricingPlans({ navigate }) {
  const [plans, setPlans] = useState([]);
  const [billing, setBilling] = useState('YEARLY');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    loadPublicPlans()
      .then((result) => { if (!cancelled) setPlans(result); })
      .catch((requestError) => { if (!cancelled) setError(requestError.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const labels = {};
  plans.forEach((plan) => plan.features.forEach((code, index) => { labels[code] = plan.featureLabels[index]; }));
  const featureRows = FEATURE_ORDER.filter((code) => labels[code]);

  return (
    <section className="pricing-section" id="pricing">
      <div className="pricing-heading">
        <div>
          <h2>Plans that grow with your studio</h2>
          <p>Start with a 14-day free trial of every Studio feature. Prices in INR, before 18% GST.</p>
        </div>
        <div className="billing-picker" role="group" aria-label="Pricing period">
          <button className={billing === 'MONTHLY' ? 'selected' : ''} onClick={() => setBilling('MONTHLY')} aria-pressed={billing === 'MONTHLY'}>Monthly</button>
          <button className={billing === 'YEARLY' ? 'selected' : ''} onClick={() => setBilling('YEARLY')} aria-pressed={billing === 'YEARLY'}>Yearly <span>2 months free</span></button>
        </div>
      </div>
      {loading && <p className="pricing-message">Loading plans…</p>}
      {error && <p className="form-error pricing-message">Could not load plans. {error}</p>}
      {!loading && !error && (
        <>
          <div className="pricing-grid pricing-grid-v2">
            {plans.map((plan) => {
              const free = Number(plan.monthlyPrice) === 0;
              const price = billing === 'YEARLY' ? plan.yearlyPrice : plan.monthlyPrice;
              const perMonth = billing === 'YEARLY' ? Number(plan.yearlyPrice) / 12 : Number(plan.monthlyPrice);
              return (
                <article className={`pricing-card${plan.highlighted ? ' is-highlighted' : ''}`} key={plan.code}>
                  {plan.highlighted && <span className="pricing-badge">{plan.tagline || 'Most popular'}</span>}
                  <h3>{plan.name}</h3>
                  {!plan.highlighted && plan.tagline && <p className="plan-tagline">{plan.tagline}</p>}
                  <p className="plan-description">{plan.description}</p>
                  <div className="plan-price">
                    <strong>{free ? '₹0' : inrPlain(perMonth)}</strong>
                    <span>{free ? 'forever' : '/month'}</span>
                  </div>
                  {!free && <p className="plan-savings">{billing === 'YEARLY' ? `${inrPlain(price)} billed yearly · save ${inrPlain(plan.yearlySavings)}` : 'Billed monthly'}</p>}
                  <ul className="plan-limits">
                    <li><b>{limitText('storageGb', plan.limits.storageGb)}</b> photo storage</li>
                    <li><b>{limitText('customers', plan.limits.customers)}</b> customers</li>
                    <li><b>{limitText('teamMembers', plan.limits.teamMembers)}</b> team {plan.limits.teamMembers === 1 ? 'member' : 'members'}</li>
                  </ul>
                  <ul>
                    {plan.featureLabels.map((label) => <li key={label}>{label}</li>)}
                  </ul>
                  <button
                    className={`button button-full ${plan.highlighted ? 'button-gold' : 'button-dark'}`}
                    onClick={() => navigate(free ? '/register' : `/payment?planCode=${encodeURIComponent(plan.code)}&billing=${billing}`)}
                  >
                    {free ? 'Start free' : `Choose ${plan.name}`}
                  </button>
                </article>
              );
            })}
          </div>
          <div className="comparison-section">
            <div className="comparison-heading"><h3>Compare plans</h3><p>Every paid plan includes unlimited galleries, quotations and bookings.</p></div>
            <div className="comparison-scroll">
              <table className="plan-comparison">
                <thead><tr><th scope="col">Plan</th>{plans.map((plan) => <th scope="col" key={plan.code}>{plan.name}</th>)}</tr></thead>
                <tbody>
                  <tr className="comparison-price-row"><th scope="row">Monthly</th>{plans.map((plan) => <td key={plan.code}>{inrPlain(plan.monthlyPrice)}</td>)}</tr>
                  <tr className="comparison-price-row"><th scope="row">Yearly <span>2 months free</span></th>{plans.map((plan) => <td key={plan.code}>{inrPlain(plan.yearlyPrice)}</td>)}</tr>
                  {LIMIT_LABELS.map(([key, label]) => (
                    <tr key={key}><th scope="row">{label}</th>{plans.map((plan) => <td key={plan.code}>{limitText(key, plan.limits[key])}</td>)}</tr>
                  ))}
                  {featureRows.map((code) => (
                    <tr key={code}>
                      <th scope="row">{labels[code]}</th>
                      {plans.map((plan) => {
                        const included = plan.features.includes(code);
                        return <td key={plan.code}><span className={included ? 'comparison-mark included' : 'comparison-mark excluded'} aria-label={included ? 'Included' : 'Not included'}>{included ? '✓' : '—'}</span></td>;
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </section>
  );
}

/** Checkout: until a payment gateway exists, a signed-in photographer sends a plan request. */
export function PlanCheckout({ navigate }) {
  const params = new URLSearchParams(window.location.search);
  const planCode = params.get('planCode') || '';
  const [billing, setBilling] = useState(params.get('billing') === 'MONTHLY' ? 'MONTHLY' : 'YEARLY');
  const [plans, setPlans] = useState(null);
  const [me, setMe] = useState(undefined);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [sent, setSent] = useState(null);

  useEffect(() => {
    loadPublicPlans().then(setPlans).catch((requestError) => setError(requestError.message));
    api.me().then(setMe).catch(() => setMe(null));
  }, []);

  const plan = plans?.find((item) => item.code === planCode);
  const subtotal = plan ? Number(billing === 'YEARLY' ? plan.yearlyPrice : plan.monthlyPrice) : 0;
  const gst = Math.round(subtotal * 18) / 100;

  async function submit() {
    setBusy(true); setError('');
    try { setSent(await api.requestPlan(plan.code, billing, message)); } catch (requestError) { setError(requestError.message); } finally { setBusy(false); }
  }

  return (
    <section className="payment-content">
      <p className="eyebrow">Choose your plan</p>
      <h1>Review your <em>plan.</em></h1>
      {!plans && !error && <p className="pricing-message">Loading plan details…</p>}
      {error && <p className="form-error" role="alert">{error}</p>}
      {plans && !plan && <p className="form-error" role="alert">That plan is unavailable. Return to pricing and choose an active plan.</p>}
      {plan && (
        <div className="checkout-grid">
          <section className="checkout-card" aria-label="Order summary">
            <p className="plan-code">{plan.code}</p>
            <h2>{plan.name}</h2>
            <label className="billing-select-label" htmlFor="billing-period">Billing period</label>
            <select id="billing-period" value={billing} onChange={(event) => setBilling(event.target.value)}>
              <option value="YEARLY">Yearly · 2 months free</option>
              <option value="MONTHLY">Monthly</option>
            </select>
            <dl className="price-breakdown">
              <div><dt>Plan price</dt><dd>{inrPlain(subtotal)}</dd></div>
              <div><dt>GST (18%)</dt><dd>{inrPlain(gst)}</dd></div>
              <div className="price-total"><dt>Total</dt><dd>{inrPlain(subtotal + gst)}</dd></div>
            </dl>
            <ul className="checkout-features">{plan.featureLabels.map((label) => <li key={label}>{label}</li>)}</ul>
          </section>
          <section className="gateway-placeholder">
            <div className="gateway-icon">₹</div>
            {sent ? (
              <>
                <h2>Request sent</h2>
                <p>Thank you! The FrameBucket team will contact you on your registered email and mobile number to complete payment (UPI or bank transfer). Your {plan.name} plan is activated as soon as payment is received.</p>
                <button className="button button-dark button-full" onClick={() => navigate('/dashboard')}>Back to your workspace</button>
              </>
            ) : me === undefined ? <p>Loading…</p> : me ? (
              <>
                <h2>Request {plan.name}</h2>
                <p>Online card/UPI checkout is coming soon. Send a request now and we’ll help you pay by UPI or bank transfer, then activate your plan the same day.</p>
                <label className="event-form-label">Message (optional)<textarea rows="3" maxLength="500" value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Best time to call, GST number for the invoice…" /></label>
                <button className="button button-dark button-full" onClick={submit} disabled={busy}>{busy ? 'Sending…' : `Request ${plan.name} (${billing === 'YEARLY' ? 'yearly' : 'monthly'})`}</button>
              </>
            ) : (
              <>
                <h2>Sign in to continue</h2>
                <p>Create a free account (it starts with a 14-day trial of every Studio feature) or sign in, then request this plan.</p>
                <button className="button button-dark button-full" onClick={() => navigate('/register')}>Create free account</button>
                <button className="button button-quiet button-full" onClick={() => navigate('/login')}>Sign in</button>
              </>
            )}
          </section>
        </div>
      )}
    </section>
  );
}
