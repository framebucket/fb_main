import { useCallback, useEffect, useState } from 'react';
import { api } from './api.js';
import { ThemeToggle } from './theme.jsx';
import { FEATURE_ORDER, LIMIT_LABELS, PLAN_STATUS_LABELS, formatBytes, inrPlain, limitText } from './Plans.jsx';
import './admin.css';

// Owner/admin portal: platform overview, photographers, plan requests, plan catalogue and audit log.

const SECTIONS = [
  ['overview', 'Overview'],
  ['photographers', 'Photographers'],
  ['requests', 'Plan requests'],
  ['plans', 'Plans'],
  ['audit', 'Audit log'],
];

const STATUS_TONE = { TRIAL: 'info', ACTIVE: 'ok', GRACE: 'warn', FREE: 'muted', TRIAL_ENDED: 'warn', LAPSED: 'warn', SUSPENDED: 'danger' };

function num(value) {
  return new Intl.NumberFormat('en-IN').format(Number(value || 0));
}

function dateText(value, withTime = false) {
  if (!value) return '—';
  const date = new Date(value);
  return withTime ? date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : date.toLocaleDateString(undefined, { dateStyle: 'medium' });
}

function relative(value) {
  if (!value) return 'Never';
  const days = Math.floor((Date.now() - new Date(value).getTime()) / 86400000);
  if (days <= 0) return 'Today';
  if (days === 1) return 'Yesterday';
  if (days < 30) return `${days} days ago`;
  return dateText(value);
}

function StatusPill({ status }) {
  return <span className={`admin-pill admin-pill-${STATUS_TONE[status] || 'muted'}`}>{PLAN_STATUS_LABELS[status] || status}</span>;
}

function useLoad(loader, deps) {
  const [state, setState] = useState({ data: null, error: '', loading: true });
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let cancelled = false;
    setState((previous) => ({ ...previous, loading: true, error: '' }));
    loader()
      .then((data) => { if (!cancelled) setState({ data, error: '', loading: false }); })
      .catch((error) => { if (!cancelled) setState({ data: null, error: error.message, loading: false }); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);
  const reload = useCallback(() => setVersion((v) => v + 1), []);
  return { ...state, reload };
}

export default function AdminApp({ user, section, navigate, onLogout }) {
  const active = SECTIONS.some(([key]) => key === section) ? section : 'overview';
  const [busy, setBusy] = useState(false);

  async function logout() {
    setBusy(true);
    try { await onLogout(); } finally { setBusy(false); }
  }

  return (
    <main className="admin-shell">
      <header className="admin-header">
        <a className="brand" href="/admin" aria-label="FrameBucket admin home">
          <img className="brand-mark" src="/framebucket-mark.png" alt="" aria-hidden="true" />
          <span className="brand-name">FrameBucket</span>
          <span className="admin-badge">Admin</span>
        </a>
        <div className="account-menu">
          <a className="button button-quiet button-small" href="/dashboard">Studio workspace</a>
          <ThemeToggle />
          <span className="admin-user">{user.email}</span>
          <button className="button button-quiet button-small" onClick={logout} disabled={busy}>{busy ? 'Signing out…' : 'Sign out'}</button>
        </div>
      </header>
      <nav className="admin-nav" aria-label="Admin sections">
        {SECTIONS.map(([key, label]) => (
          <button key={key} className={active === key ? 'admin-nav-item active' : 'admin-nav-item'} aria-pressed={active === key}
            onClick={() => navigate(key === 'overview' ? '/admin' : `/admin/${key}`)}>{label}</button>
        ))}
      </nav>
      <div className="admin-stage" key={active}>
        {active === 'overview' && <OverviewSection navigate={navigate} />}
        {active === 'photographers' && <PhotographersSection />}
        {active === 'requests' && <RequestsSection />}
        {active === 'plans' && <PlansSection />}
        {active === 'audit' && <AuditSection />}
      </div>
    </main>
  );
}

// ---- Overview ----------------------------------------------------------------------------------

function Kpi({ label, value, hint, onClick }) {
  const Tag = onClick ? 'button' : 'div';
  return (
    <Tag className={`admin-kpi${onClick ? ' is-link' : ''}`} onClick={onClick} type={onClick ? 'button' : undefined}>
      <span>{label}</span>
      <strong>{value}</strong>
      {hint && <small>{hint}</small>}
    </Tag>
  );
}

function OverviewSection({ navigate }) {
  const { data, error, loading } = useLoad(() => api.adminOverview(), []);
  if (loading && !data) return <p className="admin-message">Loading overview…</p>;
  if (error) return <p className="form-error">{error}</p>;
  const max = Math.max(1, ...data.signupsLast30Days.map((d) => d.count));
  const paying = Object.values(data.payingByPlan).reduce((a, b) => a + b, 0);
  return (
    <>
      <div className="admin-kpis">
        <Kpi label="Monthly recurring revenue" value={inrPlain(data.monthlyRecurringRevenue)} hint={`${num(paying)} paying photographers`} />
        <Kpi label="Photographers" value={num(data.photographers)} hint={`${num(data.verifiedPhotographers)} verified · +${num(data.newLast7Days)} this week`} onClick={() => navigate('/admin/photographers')} />
        <Kpi label="Pending plan requests" value={num(data.pendingPlanRequests)} hint="Waiting for payment / approval" onClick={() => navigate('/admin/requests')} />
        <Kpi label="Trials ending in 7 days" value={num(data.trialsEndingIn7Days)} hint="Good time to follow up" onClick={() => navigate('/admin/photographers?status=TRIAL')} />
        <Kpi label="Subscription revenue (30 days)" value={inrPlain(data.subscriptionRevenueLast30Days)} hint="Recorded on plan activations" />
        <Kpi label="Storage used" value={formatBytes(data.storageBytes)} hint="Across all galleries" />
      </div>
      <div className="admin-grid-2">
        <section className="admin-card">
          <h2>Sign-ups, last 30 days</h2>
          <p className="admin-card-sub">{num(data.newLast30Days)} new photographers</p>
          <div className="admin-bars" role="img" aria-label="Daily sign-ups over the last 30 days">
            {data.signupsLast30Days.map((d) => (
              <span key={d.day} title={`${dateText(d.day)}: ${d.count}`} style={{ height: `${Math.max(3, (d.count / max) * 100)}%` }} className={d.count ? '' : 'is-zero'} />
            ))}
          </div>
          <div className="admin-bars-axis"><span>{dateText(data.signupsLast30Days[0]?.day)}</span><span>Today</span></div>
        </section>
        <section className="admin-card">
          <h2>Photographers by status</h2>
          <ul className="admin-breakdown">
            {Object.entries(data.byStatus).map(([status, count]) => (
              <li key={status}><StatusPill status={status} /><b>{num(count)}</b></li>
            ))}
          </ul>
          <h2 className="admin-card-gap">Paying by plan</h2>
          <ul className="admin-breakdown">
            {Object.keys(data.payingByPlan).length === 0 && <li className="admin-muted">No paying photographers yet.</li>}
            {Object.entries(data.payingByPlan).map(([plan, count]) => <li key={plan}><span>{plan}</span><b>{num(count)}</b></li>)}
          </ul>
        </section>
      </div>
      <section className="admin-card">
        <h2>Platform activity</h2>
        <div className="admin-kpis admin-kpis-compact">
          <Kpi label="Customers" value={num(data.customers)} />
          <Kpi label="Enquiries" value={num(data.enquiries)} />
          <Kpi label="Events" value={num(data.events)} hint={`${num(data.upcomingEvents)} upcoming`} />
          <Kpi label="Guests registered" value={num(data.guestsRegistered)} />
          <Kpi label="Client payments recorded" value={inrPlain(data.clientPaymentsRecorded)} />
        </div>
      </section>
    </>
  );
}

// ---- Photographers -----------------------------------------------------------------------------

function PhotographersSection() {
  const initial = new URLSearchParams(window.location.search);
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState({ query: '', status: initial.get('status') || '', plan: '', sort: '', page: 0 });
  const [plans, setPlans] = useState([]);
  const [openId, setOpenId] = useState(null);
  const { data, error, loading, reload } = useLoad(() => api.adminPhotographers({ ...filters, size: 25 }), [JSON.stringify(filters)]);

  useEffect(() => { api.adminPlans().then(setPlans).catch(() => {}); }, []);
  useEffect(() => {
    const timer = setTimeout(() => setFilters((f) => (f.query === search ? f : { ...f, query: search, page: 0 })), 300);
    return () => clearTimeout(timer);
  }, [search]);

  const pages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;
  return (
    <section className="admin-card">
      <div className="admin-toolbar">
        <input type="search" placeholder="Search name, studio, email or mobile" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search photographers" />
        <select value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value, page: 0 })} aria-label="Filter by status">
          <option value="">All statuses</option>
          {Object.entries(PLAN_STATUS_LABELS).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
        </select>
        <select value={filters.plan} onChange={(e) => setFilters({ ...filters, plan: e.target.value, page: 0 })} aria-label="Filter by plan">
          <option value="">All plans</option>
          {plans.map((plan) => <option key={plan.code} value={plan.code}>{plan.name}</option>)}
        </select>
        <select value={filters.sort} onChange={(e) => setFilters({ ...filters, sort: e.target.value, page: 0 })} aria-label="Sort">
          <option value="">Newest first</option>
          <option value="name">Name</option>
          <option value="lastLogin">Last active</option>
          <option value="trialEnds">Trial ending soonest</option>
        </select>
      </div>
      {error && <p className="form-error">{error}</p>}
      <div className="admin-table-scroll">
        <table className="admin-table">
          <thead>
            <tr><th>Photographer</th><th>Plan</th><th>Status</th><th>Renews / trial ends</th><th className="num">Customers</th><th className="num">Events</th><th className="num">Storage</th><th>Last active</th><th>Joined</th></tr>
          </thead>
          <tbody>
            {data?.items.map((row) => (
              <tr key={row.id} onClick={() => setOpenId(row.id)} tabIndex={0} onKeyDown={(e) => { if (e.key === 'Enter') setOpenId(row.id); }}>
                <td><b>{row.fullName}</b><small>{row.studioName || row.email}</small></td>
                <td>{row.planName}{row.billingCycle && <small>{row.billingCycle === 'YEARLY' ? 'Yearly' : 'Monthly'}</small>}</td>
                <td><StatusPill status={row.status} /></td>
                <td>{dateText(row.status === 'TRIAL' || row.status === 'TRIAL_ENDED' ? row.trialEndsAt : row.planExpiresAt)}</td>
                <td className="num">{num(row.customers)}</td>
                <td className="num">{num(row.events)}</td>
                <td className="num">{formatBytes(row.storageBytes)}</td>
                <td>{relative(row.lastLoginAt)}</td>
                <td>{dateText(row.createdAt)}</td>
              </tr>
            ))}
            {data && data.items.length === 0 && <tr><td colSpan="9" className="admin-empty">No photographers match these filters.</td></tr>}
          </tbody>
        </table>
      </div>
      {loading && <p className="admin-message">Loading…</p>}
      {data && (
        <div className="admin-pager">
          <span>{num(data.total)} photographers</span>
          <button className="button button-quiet button-small" disabled={filters.page === 0} onClick={() => setFilters({ ...filters, page: filters.page - 1 })}>Previous</button>
          <span>Page {filters.page + 1} of {pages}</span>
          <button className="button button-quiet button-small" disabled={filters.page + 1 >= pages} onClick={() => setFilters({ ...filters, page: filters.page + 1 })}>Next</button>
        </div>
      )}
      {openId && <PhotographerDrawer id={openId} plans={plans} onClose={() => setOpenId(null)} onChanged={reload} />}
    </section>
  );
}

function PhotographerDrawer({ id, plans, onClose, onChanged }) {
  const { data, error, loading, reload } = useLoad(() => api.adminPhotographer(id), [id]);
  const [action, setAction] = useState('');
  const [message, setMessage] = useState('');
  const [actionError, setActionError] = useState('');
  const [busy, setBusy] = useState(false);
  const [notes, setNotes] = useState(null);
  const [planForm, setPlanForm] = useState({ planCode: 'STUDIO', billingCycle: 'YEARLY', months: 12, amount: '', reference: '', note: '' });
  const [trialDays, setTrialDays] = useState(7);
  const [suspendReason, setSuspendReason] = useState('');

  useEffect(() => {
    function onKey(event) { if (event.key === 'Escape') onClose(); }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  useEffect(() => {
    if (data && notes === null) setNotes(data.adminNotes || '');
  }, [data, notes]);

  async function run(task, done) {
    setBusy(true); setActionError(''); setMessage('');
    try { await task(); setMessage(done); setAction(''); reload(); onChanged(); } catch (requestError) { setActionError(requestError.message); } finally { setBusy(false); }
  }

  const p = data?.photographer;
  const selectedPlan = plans.find((plan) => plan.code === planForm.planCode);
  const suggestedAmount = selectedPlan ? (planForm.billingCycle === 'YEARLY' ? Number(selectedPlan.yearlyPrice) * Math.max(1, Math.round(planForm.months / 12)) : Number(selectedPlan.monthlyPrice) * planForm.months) : 0;

  return (
    <div className="admin-drawer-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <aside className="admin-drawer" role="dialog" aria-modal="true" aria-label="Photographer details">
        <button className="admin-drawer-close" onClick={onClose} aria-label="Close">×</button>
        {loading && !data && <p className="admin-message">Loading…</p>}
        {error && <p className="form-error">{error}</p>}
        {p && (
          <>
            <p className="eyebrow">Photographer</p>
            <h2>{p.fullName} <StatusPill status={data.entitlements.status} /></h2>
            <p className="admin-muted">{p.studioName || 'No studio name'} · {p.email} {p.emailVerified ? '✓' : '(unverified)'} · {p.mobileNumber} {p.mobileVerified ? '✓' : '(unverified)'}</p>
            {data.suspendedReason && <p className="form-error">Suspended: {data.suspendedReason}</p>}
            {message && <p className="form-success" role="status">{message}</p>}
            {actionError && <p className="form-error" role="alert">{actionError}</p>}

            <dl className="admin-facts">
              <div><dt>Plan</dt><dd>{data.entitlements.planName}{data.entitlements.billingCycle ? ` · ${data.entitlements.billingCycle.toLowerCase()}` : ''}</dd></div>
              <div><dt>Trial ends</dt><dd>{dateText(p.trialEndsAt)}</dd></div>
              <div><dt>Paid until</dt><dd>{dateText(p.planExpiresAt)}</dd></div>
              <div><dt>Joined</dt><dd>{dateText(p.createdAt)}</dd></div>
              <div><dt>Last active</dt><dd>{relative(p.lastLoginAt)}</dd></div>
              <div><dt>Storage</dt><dd>{formatBytes(data.usage.storageBytes)} / {limitText('storageGb', data.entitlements.limits.storageGb)}</dd></div>
            </dl>

            <div className="admin-actions">
              <button className="button button-dark button-small" onClick={() => setAction(action === 'plan' ? '' : 'plan')}>Change plan</button>
              <button className="button button-quiet button-small" onClick={() => setAction(action === 'trial' ? '' : 'trial')}>Extend trial</button>
              {data.entitlements.status === 'SUSPENDED'
                ? <button className="button button-quiet button-small" disabled={busy} onClick={() => run(() => api.adminReactivate(id), 'Account reactivated.')}>Reactivate</button>
                : <button className="button button-quiet button-small admin-danger" onClick={() => setAction(action === 'suspend' ? '' : 'suspend')}>Suspend</button>}
            </div>

            {action === 'plan' && (
              <form className="admin-inline-form" onSubmit={(e) => { e.preventDefault(); run(() => api.adminChangePlan(id, { ...planForm, amount: planForm.amount === '' ? null : Number(planForm.amount), months: Number(planForm.months) }), 'Plan updated.'); }}>
                <label>Plan<select value={planForm.planCode} onChange={(e) => setPlanForm({ ...planForm, planCode: e.target.value })}>
                  {plans.filter((plan) => plan.active).map((plan) => <option key={plan.code} value={plan.code}>{plan.name}</option>)}
                </select></label>
                <label>Billing<select value={planForm.billingCycle} onChange={(e) => setPlanForm({ ...planForm, billingCycle: e.target.value, months: e.target.value === 'YEARLY' ? 12 : 1 })}>
                  <option value="YEARLY">Yearly</option><option value="MONTHLY">Monthly</option>
                </select></label>
                <label>Months<input type="number" min="1" max="60" value={planForm.months} onChange={(e) => setPlanForm({ ...planForm, months: e.target.value })} /></label>
                <label>Amount received (₹)<input type="number" min="0" step="0.01" placeholder={suggestedAmount ? String(suggestedAmount) : '0'} value={planForm.amount} onChange={(e) => setPlanForm({ ...planForm, amount: e.target.value })} /></label>
                <label>Payment reference<input maxLength="120" placeholder="UPI ref / invoice no." value={planForm.reference} onChange={(e) => setPlanForm({ ...planForm, reference: e.target.value })} /></label>
                <label className="wide">Note<input maxLength="500" value={planForm.note} onChange={(e) => setPlanForm({ ...planForm, note: e.target.value })} /></label>
                <button className="button button-dark button-small" disabled={busy}>{busy ? 'Saving…' : 'Apply plan'}</button>
              </form>
            )}
            {action === 'trial' && (
              <form className="admin-inline-form" onSubmit={(e) => { e.preventDefault(); run(() => api.adminExtendTrial(id, Number(trialDays), ''), `Trial extended by ${trialDays} days.`); }}>
                <label>Extra days<input type="number" min="1" max="90" value={trialDays} onChange={(e) => setTrialDays(e.target.value)} /></label>
                <button className="button button-dark button-small" disabled={busy}>Extend trial</button>
              </form>
            )}
            {action === 'suspend' && (
              <form className="admin-inline-form" onSubmit={(e) => { e.preventDefault(); run(() => api.adminSuspend(id, suspendReason), 'Account suspended.'); }}>
                <label className="wide">Reason (shown to the photographer)<input required maxLength="500" value={suspendReason} onChange={(e) => setSuspendReason(e.target.value)} /></label>
                <button className="button button-small admin-danger-solid" disabled={busy}>Suspend account</button>
              </form>
            )}

            <h3>Usage</h3>
            <dl className="admin-facts">
              {Object.entries(data.counts).map(([key, value]) => (
                <div key={key}><dt>{key.replace(/([A-Z])/g, ' $1').toLowerCase()}</dt><dd>{typeof value === 'number' && /payments|expenses/i.test(key) ? inrPlain(value) : num(value)}</dd></div>
              ))}
            </dl>

            <h3>Studio setup</h3>
            <dl className="admin-facts">
              {Object.entries(data.studio || {}).map(([key, value]) => (
                <div key={key}><dt>{key.replace(/([A-Z])/g, ' $1').toLowerCase()}</dt><dd>{typeof value === 'boolean' ? (value ? 'Yes' : 'No') : (value || '—')}</dd></div>
              ))}
            </dl>

            <h3>Admin notes</h3>
            <textarea className="admin-notes" rows="3" maxLength="5000" value={notes ?? ''} onChange={(e) => setNotes(e.target.value)} placeholder="Private notes — calls, payment follow-ups…" />
            <button className="button button-quiet button-small" disabled={busy} onClick={() => run(() => api.adminSaveNotes(id, notes), 'Notes saved.')}>Save notes</button>

            <h3>Subscription history</h3>
            <ul className="admin-timeline">
              {data.history.length === 0 && <li className="admin-muted">No changes yet.</li>}
              {data.history.map((event) => (
                <li key={event.id}>
                  <b>{event.action.replaceAll('_', ' ').toLowerCase()}</b> {event.fromPlanCode && `${event.fromPlanCode} → `}{event.toPlanCode}
                  {event.amount != null && ` · ${inrPlain(event.amount)}`}{event.reference && ` · ${event.reference}`}
                  <small>{dateText(event.createdAt, true)} · {event.actor}{event.note ? ` · ${event.note}` : ''}</small>
                </li>
              ))}
            </ul>

            <h3>Recent events</h3>
            <ul className="admin-timeline">
              {data.recentEvents.length === 0 && <li className="admin-muted">No events yet.</li>}
              {data.recentEvents.map((event) => (
                <li key={event.id}><b>{event.eventCode}</b> {event.eventType} · {event.customerName}<small>{dateText(event.eventDate)}{event.price ? ` · ${event.price}` : ''}</small></li>
              ))}
            </ul>
          </>
        )}
      </aside>
    </div>
  );
}

// ---- Plan requests -----------------------------------------------------------------------------

function RequestsSection() {
  const [status, setStatus] = useState('PENDING');
  const { data, error, loading, reload } = useLoad(() => api.adminPlanRequests(status), [status]);
  const [working, setWorking] = useState(null);
  const [form, setForm] = useState({ months: '', amount: '', reference: '', note: '' });
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState('');

  function open(request, mode) {
    setWorking({ id: request.id, mode });
    setForm({ months: request.billingCycle === 'YEARLY' ? 12 : 1, amount: request.amount ?? '', reference: '', note: '' });
    setActionError('');
  }

  async function submit(event) {
    event.preventDefault();
    setBusy(true); setActionError('');
    try {
      if (working.mode === 'approve') {
        await api.adminApproveRequest(working.id, { months: Number(form.months), amount: form.amount === '' ? null : Number(form.amount), reference: form.reference, note: form.note });
      } else {
        await api.adminRejectRequest(working.id, form.note);
      }
      setWorking(null); reload();
    } catch (requestError) { setActionError(requestError.message); } finally { setBusy(false); }
  }

  return (
    <section className="admin-card">
      <div className="admin-toolbar">
        <h2>Plan requests</h2>
        <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Request status">
          <option value="PENDING">Pending</option><option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option><option value="CANCELLED">Cancelled</option><option value="ALL">All</option>
        </select>
      </div>
      <p className="admin-card-sub">Photographers request a plan from the pricing page. Collect payment (UPI / bank transfer), then approve to activate it — they get an email either way.</p>
      {error && <p className="form-error">{error}</p>}
      {loading && !data && <p className="admin-message">Loading…</p>}
      {data && data.length === 0 && <p className="admin-empty">No {status === 'ALL' ? '' : status.toLowerCase()} requests.</p>}
      <ul className="admin-requests">
        {data?.map((request) => (
          <li key={request.id} className="admin-request">
            <div>
              <b>{request.photographerName}</b> {request.studioName && <span className="admin-muted">· {request.studioName}</span>}
              <p>{request.currentPlanCode} → <b>{request.planName}</b> ({request.billingCycle.toLowerCase()}) · {inrPlain(request.amount)} + GST</p>
              <small>{request.email} · {request.mobileNumber} · requested {dateText(request.createdAt, true)}</small>
              {request.message && <blockquote>{request.message}</blockquote>}
              {request.status !== 'PENDING' && <small>{request.status.toLowerCase()} by {request.handledBy || '—'} {request.handledAt && `on ${dateText(request.handledAt)}`}{request.adminNote ? ` · ${request.adminNote}` : ''}</small>}
            </div>
            {request.status === 'PENDING' && (
              <div className="admin-actions">
                <button className="button button-dark button-small" onClick={() => open(request, 'approve')}>Approve</button>
                <button className="button button-quiet button-small" onClick={() => open(request, 'reject')}>Reject</button>
              </div>
            )}
            {working?.id === request.id && (
              <form className="admin-inline-form wide-row" onSubmit={submit}>
                {working.mode === 'approve' && (
                  <>
                    <label>Months<input type="number" min="1" max="60" value={form.months} onChange={(e) => setForm({ ...form, months: e.target.value })} /></label>
                    <label>Amount received (₹)<input type="number" min="0" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} /></label>
                    <label>Payment reference<input maxLength="120" value={form.reference} onChange={(e) => setForm({ ...form, reference: e.target.value })} /></label>
                  </>
                )}
                <label className="wide">{working.mode === 'approve' ? 'Note' : 'Reason (emailed to the photographer)'}<input maxLength="500" value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} /></label>
                {actionError && <p className="form-error wide">{actionError}</p>}
                <button className="button button-dark button-small" disabled={busy}>{busy ? 'Saving…' : working.mode === 'approve' ? 'Activate plan' : 'Reject request'}</button>
                <button type="button" className="button button-quiet button-small" onClick={() => setWorking(null)}>Cancel</button>
              </form>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

// ---- Plans -------------------------------------------------------------------------------------

function PlansSection() {
  const { data: plans, error, reload } = useLoad(() => api.adminPlans(), []);
  const [features, setFeatures] = useState([]);
  const [editing, setEditing] = useState(null);
  useEffect(() => { api.adminFeatures().then(setFeatures).catch(() => {}); }, []);

  if (error) return <p className="form-error">{error}</p>;
  if (!plans) return <p className="admin-message">Loading plans…</p>;
  const ordered = [...features].sort((a, b) => FEATURE_ORDER.indexOf(a.code) - FEATURE_ORDER.indexOf(b.code));

  return (
    <>
      <section className="admin-card">
        <h2>Plan catalogue</h2>
        <p className="admin-card-sub">Prices, features and limits apply immediately to everyone on the plan. Leave a limit blank for unlimited.</p>
        <div className="admin-table-scroll">
          <table className="admin-table admin-plan-matrix">
            <thead><tr><th />{plans.map((plan) => (
              <th key={plan.code}>
                {plan.name}{plan.highlighted && ' ★'}
                <small>{plan.code}{!plan.active ? ' · inactive' : !plan.publicPlan ? ' · hidden' : ''}</small>
                <small>{num(plan.subscriberCount)} on plan</small>
                <button className="customer-id-link" onClick={() => setEditing(plan)}>Edit</button>
              </th>
            ))}</tr></thead>
            <tbody>
              <tr><th>Monthly / yearly</th>{plans.map((plan) => <td key={plan.code}>{inrPlain(plan.monthlyPrice)} / {inrPlain(plan.yearlyPrice)}</td>)}</tr>
              {plans.some((plan) => plan.trialDays) && <tr><th>Trial days</th>{plans.map((plan) => <td key={plan.code}>{plan.trialDays || '—'}</td>)}</tr>}
              {LIMIT_LABELS.map(([key, label]) => <tr key={key}><th>{label}</th>{plans.map((plan) => <td key={plan.code}>{limitText(key, plan.limits?.[key])}</td>)}</tr>)}
              {ordered.map((feature) => (
                <tr key={feature.code}><th>{feature.label}</th>{plans.map((plan) => <td key={plan.code}>{plan.features.includes(feature.code) ? '✓' : <span className="admin-muted">—</span>}</td>)}</tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      {editing && <PlanEditor plan={editing} features={ordered} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); reload(); }} />}
    </>
  );
}

function PlanEditor({ plan, features, onClose, onSaved }) {
  const [form, setForm] = useState(() => ({
    name: plan.name, tagline: plan.tagline || '', description: plan.description || '',
    monthlyPrice: plan.monthlyPrice, yearlyPrice: plan.yearlyPrice, highlighted: plan.highlighted, publicPlan: plan.publicPlan,
    active: plan.active, sortOrder: plan.sortOrder, trialDays: plan.trialDays, features: [...plan.features],
    limits: Object.fromEntries(LIMIT_LABELS.map(([key]) => [key, plan.limits?.[key] ?? ''])),
  }));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  function toggle(code) {
    setForm((f) => ({ ...f, features: f.features.includes(code) ? f.features.filter((c) => c !== code) : [...f.features, code] }));
  }

  async function save(event) {
    event.preventDefault();
    setBusy(true); setError('');
    try {
      const limits = Object.fromEntries(Object.entries(form.limits).map(([key, value]) => [key, value === '' || value === null ? null : Number(value)]));
      await api.adminUpdatePlan(plan.code, { ...form, monthlyPrice: Number(form.monthlyPrice), yearlyPrice: Number(form.yearlyPrice), sortOrder: Number(form.sortOrder), trialDays: Number(form.trialDays), limits });
      onSaved();
    } catch (requestError) { setError(requestError.message); } finally { setBusy(false); }
  }

  return (
    <div className="admin-drawer-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <aside className="admin-drawer" role="dialog" aria-modal="true" aria-label={`Edit ${plan.name}`}>
        <button className="admin-drawer-close" onClick={onClose} aria-label="Close">×</button>
        <p className="eyebrow">Edit plan · {plan.code}</p>
        <h2>{plan.name}</h2>
        <form className="admin-inline-form admin-plan-form" onSubmit={save}>
          <label>Name<input required maxLength="100" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
          <label>Tagline<input maxLength="160" value={form.tagline} onChange={(e) => setForm({ ...form, tagline: e.target.value })} /></label>
          <label className="wide">Description<textarea rows="2" maxLength="500" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
          <label>Monthly price (₹)<input type="number" min="0" step="1" value={form.monthlyPrice} onChange={(e) => setForm({ ...form, monthlyPrice: e.target.value })} /></label>
          <label>Yearly price (₹)<input type="number" min="0" step="1" value={form.yearlyPrice} onChange={(e) => setForm({ ...form, yearlyPrice: e.target.value })} /></label>
          <label>Sort order<input type="number" min="0" max="1000" value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: e.target.value })} /></label>
          <label>Trial days<input type="number" min="0" max="365" value={form.trialDays} onChange={(e) => setForm({ ...form, trialDays: e.target.value })} /></label>
          <fieldset className="wide admin-checks">
            <legend>Visibility</legend>
            <label><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active</label>
            <label><input type="checkbox" checked={form.publicPlan} onChange={(e) => setForm({ ...form, publicPlan: e.target.checked })} /> Shown on pricing page</label>
            <label><input type="checkbox" checked={form.highlighted} onChange={(e) => setForm({ ...form, highlighted: e.target.checked })} /> Highlight as “most popular”</label>
          </fieldset>
          <fieldset className="wide admin-checks">
            <legend>Limits (blank = unlimited)</legend>
            {LIMIT_LABELS.map(([key, label]) => (
              <label key={key} className="admin-limit">{label}{key === 'storageGb' ? ' (GB)' : ''}
                <input type="number" min="0" value={form.limits[key]} onChange={(e) => setForm({ ...form, limits: { ...form.limits, [key]: e.target.value } })} placeholder="Unlimited" />
              </label>
            ))}
          </fieldset>
          <fieldset className="wide admin-checks">
            <legend>Features</legend>
            {features.map((feature) => (
              <label key={feature.code}><input type="checkbox" checked={form.features.includes(feature.code)} onChange={() => toggle(feature.code)} /> {feature.label}</label>
            ))}
          </fieldset>
          {error && <p className="form-error wide">{error}</p>}
          <button className="button button-dark button-small" disabled={busy}>{busy ? 'Saving…' : 'Save plan'}</button>
          <button type="button" className="button button-quiet button-small" onClick={onClose}>Cancel</button>
        </form>
      </aside>
    </div>
  );
}

// ---- Audit -------------------------------------------------------------------------------------

function AuditSection() {
  const { data, error, loading } = useLoad(() => api.adminAudit(200), []);
  return (
    <section className="admin-card">
      <h2>Audit log</h2>
      <p className="admin-card-sub">Every admin action is recorded here.</p>
      {error && <p className="form-error">{error}</p>}
      {loading && !data && <p className="admin-message">Loading…</p>}
      <div className="admin-table-scroll">
        <table className="admin-table">
          <thead><tr><th>When</th><th>Admin</th><th>Action</th><th>Target</th><th>Details</th></tr></thead>
          <tbody>
            {data?.map((entry) => (
              <tr key={entry.id} className="no-hover">
                <td>{dateText(entry.createdAt, true)}</td>
                <td>{entry.adminEmail}</td>
                <td><code>{entry.action}</code></td>
                <td>{entry.targetName || entry.targetId || '—'}<small>{entry.targetType}</small></td>
                <td className="admin-details">{entry.details}</td>
              </tr>
            ))}
            {data && data.length === 0 && <tr><td colSpan="5" className="admin-empty">No admin actions yet.</td></tr>}
          </tbody>
        </table>
      </div>
    </section>
  );
}
