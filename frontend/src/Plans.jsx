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
  ['editingPresets', 'Editing presets'],
  ['albumReferences', 'Reference album designs'],
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
export function UpgradeNotice({ feature, limitKey, title, children }) {
  const [plans, setPlans] = useState([]);
  useEffect(() => { loadPublicPlans().then(setPlans).catch(() => {}); }, []);
  const plan = limitKey
    ? plans.filter((item) => Number(item.monthlyPrice) > 0 && item.limits?.[limitKey] !== 0)
      .sort((a, b) => Number(a.monthlyPrice) - Number(b.monthlyPrice))[0]
    : cheapestPlanWith(plans, feature);
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

// Short names for the pricing cards and comparison (the long labels from the server stay on checkout).
const SHORT_LABELS = {
  CRM: 'Enquiries, bookings & calendar',
  QUOTATIONS: 'Online quotes & PDFs',
  CUSTOM_PACKAGES: 'Your own packages & add-ons',
  LEAD_CAPTURE: 'Enquiry forms for web & Instagram',
  TEAM: 'Team & freelancers',
  GALLERY: 'Online galleries & photo selection',
  DELIVERY_TRACKER: 'Delivery tracker for clients',
  BILLING: 'Payments, receipts & UPI QR',
  CONTRACTS: 'E-signed contracts & advances',
  REMINDERS: 'Day-before reminders',
  PORTFOLIO: 'Portfolio website',
  E_ALBUM: 'E-album flipbooks',
  ALBUM_PROOFING: 'Album proofing with comments',
  FILMS: 'Film & video delivery',
  WATERMARK: 'Watermarked previews',
  CREW_PAYOUTS: 'Crew scheduling & payouts',
  GEAR_COSTS: 'Gear & profit per event',
  WHATSAPP_AUTOMATION: 'WhatsApp notifications',
  FACE_RECOGNITION: 'AI face recognition',
  PAID_DOWNLOADS: 'Paid photo downloads',
  CUSTOM_BRANDING: 'Your branding & AI editing style',
  AI_ALBUM_DESIGN: 'AI album design',
  AI_INVITATIONS: 'AI wedding invitations',
  INSIGHTS: 'Business insights',
  CUSTOM_DOMAIN: 'Your own domain',
  PRIORITY_SUPPORT: 'Priority support',
};

// What each card leads with: the features that matter most, in this order.
const HIGHLIGHT_ORDER = ['CRM', 'QUOTATIONS', 'GALLERY', 'LEAD_CAPTURE',
  'BILLING', 'CONTRACTS', 'E_ALBUM', 'WHATSAPP_AUTOMATION', 'ALBUM_PROOFING', 'FILMS',
  'FACE_RECOGNITION', 'AI_ALBUM_DESIGN', 'AI_INVITATIONS', 'CUSTOM_BRANDING', 'PAID_DOWNLOADS', 'INSIGHTS',
  'CUSTOM_DOMAIN', 'PRIORITY_SUPPORT'];
const HIGHLIGHTS_PER_CARD = 4;

// The comparison shows the features studios ask about, grouped; "Show all features" lists everything.
const COMPARE_GROUPS = [
  ['Bookings & clients', ['CRM', 'LEAD_CAPTURE', 'BILLING', 'CONTRACTS']],
  ['Delivery', ['GALLERY', 'E_ALBUM', 'FILMS', 'PAID_DOWNLOADS']],
  ['AI tools', ['FACE_RECOGNITION', 'AI_ALBUM_DESIGN', 'AI_INVITATIONS', 'CUSTOM_BRANDING']],
  ['Running the studio', ['WHATSAPP_AUTOMATION', 'CREW_PAYOUTS', 'INSIGHTS', 'CUSTOM_DOMAIN', 'PRIORITY_SUPPORT']],
];
const COMPARE_LIMITS = [
  ['storageGb', 'Photo storage'],
  ['customers', 'Customers'],
  ['upcomingEvents', 'Upcoming events'],
  ['teamMembers', 'Team members'],
  ['whatsappMessagesPerMonth', 'WhatsApp messages / month'],
  ['editingPresets', 'Editing presets (AI picks per photo)'],
  ['albumReferences', 'Reference album designs'],
];

function LimitValue({ name, value }) {
  const text = limitText(name, value);
  // "Unlimited" doesn't fit narrow phone columns; show ∞ there.
  if (text === 'Unlimited') return <><span className="pp-long">Unlimited</span><span className="pp-short" title="Unlimited">∞</span></>;
  return text;
}

function Mark({ included }) {
  return included
    ? <span className="pp-mark is-yes" aria-label="Included">✓</span>
    : <span className="pp-mark is-no" aria-label="Not included">–</span>;
}

/** Public pricing: plan cards + comparison table. */
export function PricingPlans({ navigate }) {
  const [plans, setPlans] = useState([]);
  const [billing, setBilling] = useState('YEARLY');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showAll, setShowAll] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadPublicPlans()
      .then((result) => { if (!cancelled) setPlans(result); })
      .catch((requestError) => { if (!cancelled) setError(requestError.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const longLabels = {};
  plans.forEach((plan) => plan.features.forEach((code, index) => { longLabels[code] = plan.featureLabels[index]; }));
  const label = (code) => SHORT_LABELS[code] || longLabels[code] || code;
  const allRows = FEATURE_ORDER.filter((code) => longLabels[code]);
  const groups = showAll
    ? [['All features', allRows]]
    : COMPARE_GROUPS.map(([title, codes]) => [title, codes.filter((code) => longLabels[code])]).filter(([, codes]) => codes.length);

  const choose = (plan) => {
    const free = Number(plan.monthlyPrice) === 0;
    navigate(free ? '/register' : `/payment?planCode=${encodeURIComponent(plan.code)}&billing=${billing}`);
  };
  const perMonth = (plan) => (billing === 'YEARLY' ? Number(plan.yearlyPrice) / 12 : Number(plan.monthlyPrice));

  return (
    <section className="pricing-section pp" id="pricing">
      <div className="pp-heading">
        <div>
          <h2>Plans that grow with your studio</h2>
          <p>14-day free trial of every Studio feature. Prices in INR, before 18% GST.</p>
        </div>
        <div className="pp-billing" role="group" aria-label="Pricing period">
          <button type="button" className={billing === 'MONTHLY' ? 'is-active' : ''} onClick={() => setBilling('MONTHLY')} aria-pressed={billing === 'MONTHLY'}>Monthly</button>
          <button type="button" className={billing === 'YEARLY' ? 'is-active' : ''} onClick={() => setBilling('YEARLY')} aria-pressed={billing === 'YEARLY'}>Yearly <span>2 months free</span></button>
        </div>
      </div>
      {loading && <p className="pricing-message">Loading plans…</p>}
      {error && <p className="form-error pricing-message">Could not load plans. {error}</p>}
      {!loading && !error && (
        <>
          <div className="pp-cards">
            {plans.map((plan, index) => {
              const free = Number(plan.monthlyPrice) === 0;
              const previous = plans[index - 1];
              const fresh = plan.features.filter((code) => !previous || !previous.features.includes(code));
              const ranked = HIGHLIGHT_ORDER.filter((code) => fresh.includes(code));
              const highlights = (ranked.length ? ranked : fresh).slice(0, HIGHLIGHTS_PER_CARD);
              const more = fresh.length - highlights.length;
              return (
                <article className={`pp-card${plan.highlighted ? ' is-highlighted' : ''}`} key={plan.code}>
                  {plan.highlighted && <span className="pp-badge">Most popular</span>}
                  <header>
                    <h3>{plan.name}</h3>
                    <p className="pp-tagline">{plan.highlighted ? 'For growing studios' : plan.tagline}</p>
                  </header>
                  <div className="pp-price">
                    <strong>{free ? '₹0' : inrPlain(perMonth(plan))}</strong>
                    <span>{free ? 'free forever' : '/ month'}</span>
                  </div>
                  <p className="pp-billed">{free ? 'No card needed' : billing === 'YEARLY' ? `${inrPlain(plan.yearlyPrice)} a year · save ${inrPlain(plan.yearlySavings)}` : 'Billed monthly'}</p>
                  <ul className="pp-limits">
                    <li><b>{limitText('storageGb', plan.limits.storageGb)}</b><span>storage</span></li>
                    <li><b><LimitValue name="customers" value={plan.limits.customers} /></b><span>customers</span></li>
                    <li><b>{limitText('teamMembers', plan.limits.teamMembers)}</b><span>{plan.limits.teamMembers === 1 ? 'seat' : 'seats'}</span></li>
                  </ul>
                  <p className="pp-includes">{previous ? <>Everything in {previous.name}, plus</> : 'Includes'}</p>
                  <ul className="pp-highlights">
                    {highlights.map((code) => <li key={code}>{label(code)}</li>)}
                    {more > 0 && <li className="pp-more">+ {more} more</li>}
                  </ul>
                  <button type="button" className={`pp-cta${plan.highlighted ? ' is-primary' : ''}`} onClick={() => choose(plan)}>
                    {free ? 'Start free' : `Choose ${plan.name}`}
                  </button>
                </article>
              );
            })}
          </div>

          <div className="pp-compare">
            <div className="pp-compare-heading">
              <h3>Compare plans</h3>
              <button type="button" className="pp-link" onClick={() => setShowAll((value) => !value)}>{showAll ? 'Show key features' : `Show all ${allRows.length} features`}</button>
            </div>
            <div className="pp-table-wrap">
              <table className="pp-table">
                <colgroup><col className="pp-col-label" />{plans.map((plan) => <col key={plan.code} className={plan.highlighted ? 'is-highlighted' : ''} />)}</colgroup>
                <thead>
                  <tr>
                    <th scope="col"><span className="pp-visually-hidden">Feature</span></th>
                    {plans.map((plan) => (
                      <th scope="col" key={plan.code} className={plan.highlighted ? 'is-highlighted' : ''}>
                        <b>{plan.name}</b>
                        <small>{Number(plan.monthlyPrice) === 0 ? '₹0' : `${inrPlain(perMonth(plan))}/mo`}</small>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  <tr className="pp-group"><th colSpan={plans.length + 1} scope="colgroup">Limits</th></tr>
                  {COMPARE_LIMITS.map(([key, text]) => (
                    <tr key={key}>
                      <th scope="row">{text}</th>
                      {plans.map((plan) => <td key={plan.code} className={plan.highlighted ? 'is-highlighted' : ''}><LimitValue name={key} value={plan.limits[key]} /></td>)}
                    </tr>
                  ))}
                  {groups.map(([title, codes]) => [
                    <tr className="pp-group" key={`g-${title}`}><th colSpan={plans.length + 1} scope="colgroup">{title}</th></tr>,
                    ...codes.map((code) => (
                      <tr key={code}>
                        <th scope="row">{showAll ? longLabels[code] : label(code)}</th>
                        {plans.map((plan) => <td key={plan.code} className={plan.highlighted ? 'is-highlighted' : ''}><Mark included={plan.features.includes(code)} /></td>)}
                      </tr>
                    )),
                  ])}
                </tbody>
                <tfoot>
                  <tr>
                    <th scope="row" />
                    {plans.map((plan) => (
                      <td key={plan.code} className={plan.highlighted ? 'is-highlighted' : ''}>
                        <button type="button" className={`pp-cta is-small${plan.highlighted ? ' is-primary' : ''}`} onClick={() => choose(plan)}>{Number(plan.monthlyPrice) === 0 ? 'Start' : 'Choose'}</button>
                      </td>
                    ))}
                  </tr>
                </tfoot>
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
