import { useEffect, useMemo, useState } from 'react';
import { api } from './api.js';
import './studio.css';

// Studio features: calendar, billing (payments, UPI QR, receipts, paid downloads), expenses,
// portfolio, e-album flipbooks and studio settings, plus the matching customer/public pages.

const PAYMENT_METHODS = [['UPI', 'UPI'], ['CASH', 'Cash'], ['BANK_TRANSFER', 'Bank transfer'], ['CARD', 'Card'], ['CHEQUE', 'Cheque'], ['OTHER', 'Other']];
const PAYMENT_PURPOSES = [['ADVANCE', 'Advance'], ['BALANCE', 'Balance'], ['DOWNLOAD', 'Photo downloads'], ['OTHER', 'Other']];
const EXPENSE_CATEGORIES = ['Travel', 'Equipment rental', 'Assistant / freelancer', 'Printing & album', 'Food', 'Marketing', 'Software', 'Other'];

export function inr(amount) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(Number(amount || 0));
}

function today() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

export function shortDate(value) {
  return value ? new Date(`${value}T00:00:00`).toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' }) : '—';
}

function timeLabel(value) {
  const match = /^(\d{2}):(\d{2})/.exec(value || '');
  return match ? new Date(2000, 0, 1, Number(match[1]), Number(match[2])).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) : '';
}

export function useLoad(loader, deps) {
  const [state, setState] = useState({ data: null, loading: true, error: '' });
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let cancelled = false;
    setState((current) => ({ ...current, loading: true, error: '' }));
    loader()
      .then((data) => { if (!cancelled) setState({ data, loading: false, error: '' }); })
      .catch((error) => { if (!cancelled) setState({ data: null, loading: false, error: error.message }); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);
  return { ...state, reload: () => setVersion((value) => value + 1) };
}

export function PanelIntro({ eyebrow, title, accent, children }) {
  return (
    <div className="customer-module-intro">
      <p className="eyebrow">{eyebrow}</p>
      <h2>{title} <em>{accent}</em></h2>
      <p>{children}</p>
    </div>
  );
}

export function Modal({ eyebrow, title, onClose, wide, children }) {
  return (
    <div className="modal-backdrop detail-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section className={`customer-detail-modal studio-modal${wide ? ' studio-modal-wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-heading"><div><p className="eyebrow">{eyebrow}</p><h2>{title}</h2></div><button className="modal-close" type="button" onClick={onClose} aria-label="Close">×</button></div>
        {children}
      </section>
    </div>
  );
}

export function Stat({ label, value, tone }) {
  return <div className={`studio-stat${tone ? ` studio-stat-${tone}` : ''}`}><span>{label}</span><b>{value}</b></div>;
}

// ---------------------------------------------------------------------------------------------
// 13. Event calendar & reminders
// ---------------------------------------------------------------------------------------------

export function CalendarPanel({ user }) {
  const events = useLoad(() => api.events(), []);
  const finance = useLoad(() => api.financeSummary().catch(() => null), []);
  const [month, setMonth] = useState(() => { const now = new Date(); return new Date(now.getFullYear(), now.getMonth(), 1); });
  const [selectedDay, setSelectedDay] = useState(today());

  const balances = useMemo(() => new Map((finance.data?.events || []).map((row) => [row.eventId, row.balance])), [finance.data]);
  const byDay = useMemo(() => {
    const map = new Map();
    for (const event of events.data || []) {
      if (!map.has(event.eventDate)) map.set(event.eventDate, []);
      map.get(event.eventDate).push(event);
    }
    for (const list of map.values()) list.sort((a, b) => String(a.eventTime || '').localeCompare(String(b.eventTime || '')));
    return map;
  }, [events.data]);

  const cells = useMemo(() => {
    const offset = (month.getDay() + 6) % 7; // Monday first
    const daysInMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
    const list = [];
    for (let index = 0; index < offset; index += 1) list.push(null);
    for (let day = 1; day <= daysInMonth; day += 1) {
      list.push(`${month.getFullYear()}-${String(month.getMonth() + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`);
    }
    while (list.length % 7 !== 0) list.push(null);
    return list;
  }, [month]);

  const upcoming = (events.data || []).filter((event) => event.eventDate >= today()).slice(0, 6);
  const dayEvents = byDay.get(selectedDay) || [];

  return (
    <section className="event-module studio-panel">
      <PanelIntro eyebrow="Your schedule" title="Event" accent="calendar.">
        See every booking by month. You and your customers get an automatic reminder email the day before each event (turn this off in Studio settings).
      </PanelIntro>
      {events.error && <p className="form-error customer-message" role="alert">{events.error}</p>}
      <div className="studio-calendar-layout">
        <section className="customer-list-section studio-calendar">
          <div className="studio-calendar-head">
            <button className="button button-quiet button-small" type="button" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))} aria-label="Previous month">‹</button>
            <h3>{month.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}</h3>
            <button className="button button-quiet button-small" type="button" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))} aria-label="Next month">›</button>
          </div>
          <div className="studio-calendar-grid">
            {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((day) => <span key={day} className="studio-calendar-weekday">{day}</span>)}
            {cells.map((date, index) => {
              if (!date) return <span key={`blank-${index}`} className="studio-calendar-cell is-blank" />;
              const items = byDay.get(date) || [];
              const classes = ['studio-calendar-cell'];
              if (date === today()) classes.push('is-today');
              if (date === selectedDay) classes.push('is-selected');
              if (items.length) classes.push('has-events');
              return (
                <button key={date} type="button" className={classes.join(' ')} onClick={() => setSelectedDay(date)} aria-label={`${shortDate(date)}, ${items.length} events`}>
                  <span className="studio-calendar-day">{Number(date.slice(8))}</span>
                  {items.slice(0, 2).map((event) => <span key={event.id} className="studio-calendar-chip">{event.eventTypeName}</span>)}
                  {items.length > 2 && <span className="studio-calendar-more">+{items.length - 2} more</span>}
                </button>
              );
            })}
          </div>
        </section>
        <aside className="customer-list-section studio-calendar-side">
          <p className="eyebrow">{shortDate(selectedDay)}</p>
          <h3>{dayEvents.length ? `${dayEvents.length} ${dayEvents.length === 1 ? 'event' : 'events'}` : 'No events'}</h3>
          <EventAgenda events={dayEvents} balances={balances} />
          <p className="eyebrow studio-gap">Coming up</p>
          {events.loading ? <p className="customer-empty">Loading…</p> : <EventAgenda events={upcoming} balances={balances} showDate />}
        </aside>
      </div>
      <p className="event-module-caption">{user.studioName || user.fullName} · reminders go out at 9:00 AM IST the day before.</p>
    </section>
  );
}

function EventAgenda({ events, balances, showDate }) {
  if (!events.length) return <p className="customer-empty">Nothing scheduled.</p>;
  return (
    <ul className="studio-agenda">
      {events.map((event) => (
        <li key={event.id}>
          <b>{event.eventTypeName} · {event.customerName}</b>
          <span>{showDate ? `${shortDate(event.eventDate)} · ` : ''}{timeLabel(event.eventTime) || 'Time not set'} · {event.eventCode}</span>
          <span>{event.locationName || 'Location not shared yet'} · {event.assigneeName || 'Unassigned'}</span>
          {Number(balances.get(event.id)) > 0 && <span className="studio-due">Balance due {inr(balances.get(event.id))}</span>}
        </li>
      ))}
    </ul>
  );
}

// ---------------------------------------------------------------------------------------------
// 8, 9, 10, 12. Billing: payments, UPI QR codes, receipts, paid downloads and expenses
// ---------------------------------------------------------------------------------------------

export function BillingPanel({ user }) {
  const [view, setView] = useState('payments');
  const summary = useLoad(() => api.financeSummary(), []);
  const [billingEvent, setBillingEvent] = useState(null);
  const data = summary.data;

  return (
    <section className="event-module studio-panel">
      <PanelIntro eyebrow="Money in, money out" title="Billing" accent="& expenses.">
        Record advances and balance payments, share a UPI QR code, send receipts, charge for downloads and track what each shoot costs you.
      </PanelIntro>
      {summary.error && <p className="form-error customer-message" role="alert">{summary.error}</p>}
      {data && (
        <div className="studio-stats">
          <Stat label="Billed" value={inr(data.billed)} />
          <Stat label="Received" value={inr(data.received)} tone="good" />
          <Stat label="Outstanding" value={inr(data.outstanding)} tone={Number(data.outstanding) > 0 ? 'warn' : undefined} />
          <Stat label="Expenses" value={inr(data.expenses)} />
          <Stat label="Net (received − expenses)" value={inr(data.net)} tone={Number(data.net) >= 0 ? 'good' : 'warn'} />
        </div>
      )}
      <div className="studio-subtabs" role="tablist">
        <button type="button" role="tab" aria-selected={view === 'payments'} className={view === 'payments' ? 'active' : ''} onClick={() => setView('payments')}>Payments & receipts</button>
        <button type="button" role="tab" aria-selected={view === 'expenses'} className={view === 'expenses' ? 'active' : ''} onClick={() => setView('expenses')}>Expenses</button>
      </div>
      {view === 'payments' ? (
        <section className="customer-list-section">
          <div className="customer-list-heading"><div><p className="eyebrow">{user.studioName || user.fullName}</p><h3>Event accounts</h3><p>Totals come from each event’s price. Download fees are charged on top of the event price.</p></div></div>
          {summary.loading ? <p className="customer-empty">Loading…</p> : !data?.events.length ? <p className="customer-empty">Create an event to start billing.</p> : (
            <div className="customer-table-scroll">
              <table className="customer-table">
                <thead><tr><th scope="col">Event</th><th scope="col">Date</th><th scope="col">Customer</th><th scope="col">Price</th><th scope="col">Received</th><th scope="col">Balance</th><th scope="col">Expenses</th><th scope="col">Actions</th></tr></thead>
                <tbody>
                  {data.events.map((row) => (
                    <tr key={row.eventId}>
                      <th scope="row"><span className="event-code">{row.eventCode}</span><small className="studio-cell-sub">{row.eventTypeName}</small></th>
                      <td>{shortDate(row.eventDate)}</td>
                      <td>{row.customerName}</td>
                      <td>{inr(row.total)}</td>
                      <td>{inr(row.paid)}</td>
                      <td>{Number(row.balance) > 0 ? <span className="studio-due">{inr(row.balance)}</span> : <span className="studio-paid">Paid</span>}</td>
                      <td>{inr(row.expenses)}</td>
                      <td><button className="button button-dark button-small" type="button" onClick={() => setBillingEvent(row)}>Manage</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : <ExpensesSection events={data?.events || []} onChanged={summary.reload} />}
      {billingEvent && <EventBillingModal eventRow={billingEvent} onClose={() => { setBillingEvent(null); summary.reload(); }} />}
    </section>
  );
}

function EventBillingModal({ eventRow, onClose }) {
  const billing = useLoad(() => api.eventBilling(eventRow.eventId), [eventRow.eventId]);
  const [form, setForm] = useState({ amount: '', paidOn: today(), method: 'UPI', purpose: 'ADVANCE', reference: '', notes: '' });
  const [policy, setPolicy] = useState(null);
  const [qrAmount, setQrAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const data = billing.data;

  useEffect(() => {
    if (data) setPolicy({ mode: data.downloadPolicy.mode, price: data.downloadPolicy.price ?? '', unlocked: data.downloadPolicy.unlocked });
  }, [data]);

  async function run(action, message) {
    setBusy(true); setError(''); setNotice('');
    try { await action(); setNotice(message); billing.reload(); } catch (requestError) { setError(requestError.message); } finally { setBusy(false); }
  }

  function recordPayment(event) {
    event.preventDefault();
    run(async () => {
      await api.recordPayment(eventRow.eventId, { ...form, amount: Number(form.amount) });
      setForm((current) => ({ ...current, amount: '', reference: '', notes: '' }));
    }, 'Payment recorded. The receipt is ready to download or share.');
  }

  function savePolicy(event) {
    event.preventDefault();
    run(() => api.saveDownloadPolicy(eventRow.eventId, { mode: policy.mode, price: policy.mode === 'PAID' ? Number(policy.price) : null, unlocked: policy.mode === 'PAID' && policy.unlocked }), 'Download settings saved.');
  }

  return (
    <Modal eyebrow={`${eventRow.eventCode} · ${eventRow.customerName}`} title="Payments & receipts" onClose={onClose} wide>
      {error && <p className="form-error" role="alert">{error}</p>}
      {notice && <p className="form-success" role="status">{notice}</p>}
      {billing.loading && !data ? <p className="customer-empty">Loading…</p> : billing.error ? <p className="form-error">{billing.error}</p> : data && (
        <div className="studio-billing-grid">
          <div>
            <div className="studio-stats studio-stats-compact">
              <Stat label="Event price" value={inr(data.total)} />
              <Stat label="Received" value={inr(data.paid)} tone="good" />
              <Stat label="Balance" value={inr(data.balance)} tone={Number(data.balance) > 0 ? 'warn' : 'good'} />
            </div>
            {Number(data.total) === 0 && <p className="event-form-note">This event’s price isn’t a plain amount, so the balance can’t be worked out. Payments and receipts still work.</p>}
            <h3 className="studio-h3">Record a payment</h3>
            <form className="studio-form" onSubmit={recordPayment}>
              <label className="event-form-label">Amount (₹)<input type="number" min="1" step="0.01" required value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} /></label>
              <label className="event-form-label">Date<input type="date" required value={form.paidOn} onChange={(e) => setForm({ ...form, paidOn: e.target.value })} /></label>
              <label className="event-form-label">Method<select value={form.method} onChange={(e) => setForm({ ...form, method: e.target.value })}>{PAYMENT_METHODS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
              <label className="event-form-label">Towards<select value={form.purpose} onChange={(e) => setForm({ ...form, purpose: e.target.value })}>{PAYMENT_PURPOSES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
              <label className="event-form-label">Reference / UTR<input maxLength="120" value={form.reference} onChange={(e) => setForm({ ...form, reference: e.target.value })} /></label>
              <label className="event-form-label">Notes<input maxLength="500" value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} /></label>
              <div className="event-form-actions"><button className="button button-dark" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Record payment'}</button></div>
            </form>
            <h3 className="studio-h3">Receipts</h3>
            {!data.payments.length ? <p className="customer-empty">No payments recorded yet.</p> : (
              <ul className="studio-receipts">
                {data.payments.map((payment) => (
                  <li key={payment.id}>
                    <div><b>{payment.receiptNumber} · {inr(payment.amount)}</b><span>{shortDate(payment.paidOn)} · {PAYMENT_PURPOSES.find(([v]) => v === payment.purpose)?.[1]} · {PAYMENT_METHODS.find(([v]) => v === payment.method)?.[1]}{payment.reference ? ` · ${payment.reference}` : ''}</span></div>
                    <a className="customer-id-link" href={api.receiptUrl(eventRow.eventId, payment.id)} target="_blank" rel="noreferrer">Receipt PDF ↗</a>
                    <button className="customer-id-link studio-danger-link" type="button" disabled={busy} onClick={() => { if (window.confirm(`Delete ${payment.receiptNumber}? The receipt number will not be reused.`)) run(() => api.deletePayment(eventRow.eventId, payment.id), 'Payment deleted.'); }}>Delete</button>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div>
            <h3 className="studio-h3">UPI payment QR</h3>
            {data.upiConfigured ? (
              <div className="studio-qr-box">
                <img src={api.paymentQrUrl(eventRow.eventId, qrAmount)} alt="UPI payment QR code" />
                <label className="event-form-label">Amount on the QR (blank = balance due)<input type="number" min="1" step="0.01" value={qrAmount} onChange={(e) => setQrAmount(e.target.value)} /></label>
                <a className="button button-quiet button-small" href={api.paymentQrUrl(eventRow.eventId, qrAmount)} download={`UPI-${eventRow.eventCode}.png`}>Download QR</a>
                <p className="event-form-note">Money goes straight to your UPI ID. Record the payment here once it reaches your account. Customers also see this QR in their portal.</p>
              </div>
            ) : <p className="event-form-note">Add your UPI ID in Studio settings to show a payment QR code.</p>}
            <h3 className="studio-h3">Customer photo downloads</h3>
            {policy && (
              <form className="studio-form" onSubmit={savePolicy}>
                <div className="studio-radio-row">
                  {[['FREE', 'Free'], ['PAID', 'Paid'], ['DISABLED', 'Off']].map(([value, label]) => (
                    <label key={value} className={`studio-radio${policy.mode === value ? ' active' : ''}`}><input type="radio" name="download-mode" value={value} checked={policy.mode === value} onChange={() => setPolicy({ ...policy, mode: value })} />{label}</label>
                  ))}
                </div>
                {policy.mode === 'PAID' && <>
                  <label className="event-form-label">Download price (₹)<input type="number" min="1" step="0.01" required value={policy.price} onChange={(e) => setPolicy({ ...policy, price: e.target.value })} /></label>
                  <label className="studio-check"><input type="checkbox" checked={policy.unlocked} onChange={(e) => setPolicy({ ...policy, unlocked: e.target.checked })} />Payment received — unlock downloads</label>
                  <p className="event-form-note">Recording a payment “Towards: Photo downloads” unlocks them automatically.</p>
                </>}
                <div className="event-form-actions"><button className="button button-dark" type="submit" disabled={busy}>Save download settings</button></div>
              </form>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
}

function ExpensesSection({ events, onChanged }) {
  const expenses = useLoad(() => api.expenses(), []);
  const empty = { eventId: '', category: EXPENSE_CATEGORIES[0], amount: '', spentOn: today(), vendor: '', notes: '' };
  const [form, setForm] = useState(empty);
  const [editingId, setEditingId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [month, setMonth] = useState('');

  async function save(event) {
    event.preventDefault();
    setBusy(true); setError('');
    const payload = { ...form, eventId: form.eventId || null, amount: Number(form.amount) };
    try {
      if (editingId) await api.updateExpense(editingId, payload); else await api.createExpense(payload);
      setForm(empty); setEditingId(''); expenses.reload(); onChanged();
    } catch (requestError) { setError(requestError.message); } finally { setBusy(false); }
  }

  async function remove(expense) {
    if (!window.confirm(`Delete this ${expense.category} expense of ${inr(expense.amount)}?`)) return;
    try { await api.deleteExpense(expense.id); expenses.reload(); onChanged(); } catch (requestError) { setError(requestError.message); }
  }

  const list = (expenses.data || []).filter((expense) => !month || expense.spentOn.startsWith(month));
  const byCategory = list.reduce((map, expense) => map.set(expense.category, (map.get(expense.category) || 0) + Number(expense.amount)), new Map());
  const total = list.reduce((sum, expense) => sum + Number(expense.amount), 0);

  return (
    <section className="customer-list-section">
      <div className="customer-list-heading"><div><p className="eyebrow">Track your costs</p><h3>{editingId ? 'Edit expense' : 'Add an expense'}</h3><p>Link an expense to an event to see the real profit on that shoot.</p></div></div>
      {error && <p className="form-error" role="alert">{error}</p>}
      <form className="studio-form studio-form-inline" onSubmit={save}>
        <label className="event-form-label">Category<select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>{EXPENSE_CATEGORIES.map((c) => <option key={c}>{c}</option>)}</select></label>
        <label className="event-form-label">Amount (₹)<input type="number" min="0.01" step="0.01" required value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} /></label>
        <label className="event-form-label">Date<input type="date" required value={form.spentOn} onChange={(e) => setForm({ ...form, spentOn: e.target.value })} /></label>
        <label className="event-form-label">Event (optional)<select value={form.eventId} onChange={(e) => setForm({ ...form, eventId: e.target.value })}><option value="">General studio expense</option>{events.map((row) => <option key={row.eventId} value={row.eventId}>{row.eventCode} · {row.customerName}</option>)}</select></label>
        <label className="event-form-label">Paid to<input maxLength="160" value={form.vendor} onChange={(e) => setForm({ ...form, vendor: e.target.value })} /></label>
        <label className="event-form-label">Notes<input maxLength="500" value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} /></label>
        <div className="event-form-actions">
          {editingId && <button className="button button-quiet" type="button" onClick={() => { setEditingId(''); setForm(empty); }}>Cancel</button>}
          <button className="button button-dark" type="submit" disabled={busy}>{busy ? 'Saving…' : editingId ? 'Save changes' : 'Add expense'}</button>
        </div>
      </form>
      <div className="studio-expense-filter">
        <label className="event-form-label">Month<input type="month" value={month} onChange={(e) => setMonth(e.target.value)} /></label>
        <b>Total {inr(total)}</b>
        {[...byCategory.entries()].map(([category, amount]) => <span key={category} className="studio-pill">{category}: {inr(amount)}</span>)}
      </div>
      {expenses.loading ? <p className="customer-empty">Loading…</p> : !list.length ? <p className="customer-empty">No expenses recorded{month ? ' for this month' : ''}.</p> : (
        <div className="customer-table-scroll">
          <table className="customer-table">
            <thead><tr><th scope="col">Date</th><th scope="col">Category</th><th scope="col">Amount</th><th scope="col">Event</th><th scope="col">Paid to</th><th scope="col">Notes</th><th scope="col">Actions</th></tr></thead>
            <tbody>{list.map((expense) => (
              <tr key={expense.id}>
                <td>{shortDate(expense.spentOn)}</td><td>{expense.category}</td><td>{inr(expense.amount)}</td><td>{expense.eventCode || '—'}</td><td>{expense.vendor || '—'}</td><td>{expense.notes || '—'}</td>
                <td><button className="customer-id-link" type="button" onClick={() => { setEditingId(expense.id); setForm({ eventId: expense.eventId || '', category: expense.category, amount: expense.amount, spentOn: expense.spentOn, vendor: expense.vendor || '', notes: expense.notes || '' }); }}>Edit</button> <button className="customer-id-link studio-danger-link" type="button" onClick={() => remove(expense)}>Delete</button></td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------------------------
// 11 & 14. Showcase: photography portfolio and e-album flipbooks
// ---------------------------------------------------------------------------------------------

export function ShowcasePanel({ user }) {
  const events = useLoad(() => api.events(), []);
  const profile = useLoad(() => api.studioProfile(), []);
  const items = useLoad(() => api.portfolioItems(), []);
  const [picker, setPicker] = useState(null);
  const [flipEventId, setFlipEventId] = useState('');
  const [flipVersion, setFlipVersion] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const dataEnabled = user.dataModuleEnabled === true;
  const portfolioUrl = profile.data?.portfolioSlug ? `${window.location.origin}/portfolio/${profile.data.portfolioSlug}` : '';

  async function act(action, message) {
    setError(''); setNotice('');
    try { await action(); if (message) setNotice(message); items.reload(); } catch (requestError) { setError(requestError.message); }
  }

  function move(item, direction) {
    const list = items.data || [];
    const index = list.findIndex((entry) => entry.id === item.id);
    const other = list[index + direction];
    if (!other) return;
    // Rewrite the order as a clean sequence so equal sort values can't get stuck.
    const reordered = [...list];
    reordered[index] = other;
    reordered[index + direction] = item;
    act(async () => {
      for (let position = 0; position < reordered.length; position += 1) {
        const entry = reordered[position];
        if (entry.sortOrder !== position + 1) await api.updatePortfolioItem(entry.id, { caption: entry.caption, sortOrder: position + 1 });
      }
    });
  }

  return (
    <section className="event-module studio-panel">
      <PanelIntro eyebrow="Show your best work" title="Portfolio" accent="& e-albums.">
        Pick standout shots from your galleries for a public portfolio page, and turn an event’s photos into a page-turning e-album you can share by link.
      </PanelIntro>
      {!dataEnabled && <p className="event-form-note">Portfolio and e-albums use photos from the Data module, which isn’t included with your current plan.</p>}
      {error && <p className="form-error customer-message" role="alert">{error}</p>}
      {notice && <p className="form-success customer-message" role="status">{notice}</p>}

      <section className="customer-list-section">
        <div className="customer-list-heading">
          <div>
            <p className="eyebrow">{profile.data?.portfolioPublic ? 'Published' : 'Not published yet'}</p>
            <h3>Photography portfolio</h3>
            <p>{portfolioUrl ? <>Your page: <a className="customer-id-link" href={portfolioUrl} target="_blank" rel="noreferrer">{portfolioUrl} ↗</a>{!profile.data?.portfolioPublic && ' (publish it in Studio settings)'}</> : 'Choose a portfolio address in Studio settings to publish your page.'}</p>
          </div>
          <button className="button button-dark" type="button" disabled={!dataEnabled || !(events.data || []).length} onClick={() => setPicker({ mode: 'portfolio' })}>＋ Add photos</button>
        </div>
        <p className="event-form-note">Only add photos your clients have agreed can be shown publicly.</p>
        {items.loading ? <p className="customer-empty">Loading…</p> : !(items.data || []).length ? <p className="customer-empty">No portfolio photos yet.</p> : (
          <ul className="studio-portfolio-grid">
            {items.data.map((item, index) => (
              <li key={item.id}>
                <img src={api.apiUrl(item.imageUrl)} alt={item.caption || `Portfolio photo ${index + 1}`} loading="lazy" />
                <input aria-label="Caption" placeholder="Caption" maxLength="240" defaultValue={item.caption || ''} onBlur={(e) => { if ((e.target.value || '') !== (item.caption || '')) act(() => api.updatePortfolioItem(item.id, { caption: e.target.value, sortOrder: item.sortOrder }), 'Caption saved.'); }} />
                <div className="studio-portfolio-actions">
                  <button type="button" className="customer-id-link" disabled={index === 0} onClick={() => move(item, -1)} aria-label="Move earlier">←</button>
                  <span>{item.eventCode}</span>
                  <button type="button" className="customer-id-link" disabled={index === items.data.length - 1} onClick={() => move(item, 1)} aria-label="Move later">→</button>
                  <button type="button" className="customer-id-link studio-danger-link" onClick={() => act(() => api.removePortfolioItem(item.id), 'Removed from portfolio.')}>Remove</button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="customer-list-section">
        <div className="customer-list-heading">
          <div><p className="eyebrow">E-album flipbooks</p><h3>Share an album that turns like a book</h3><p>Anyone with the link can view it — no login needed. Customers also see it in their portal.</p></div>
          <label className="event-form-label studio-inline-select">Event<select value={flipEventId} onChange={(e) => setFlipEventId(e.target.value)}><option value="">Choose an event</option>{(events.data || []).map((event) => <option key={event.id} value={event.id}>{event.eventCode} · {event.customerName}</option>)}</select></label>
        </div>
        {flipEventId && <FlipbookList key={`${flipEventId}-${flipVersion}`} eventId={flipEventId} dataEnabled={dataEnabled} onCreate={() => setPicker({ mode: 'flipbook', eventId: flipEventId })} />}
      </section>

      {picker && <MediaPickerModal
        mode={picker.mode}
        events={events.data || []}
        initialEventId={picker.eventId || ''}
        onClose={() => setPicker(null)}
        onDone={(message) => { setPicker(null); setNotice(message); items.reload(); setFlipVersion((value) => value + 1); }}
      />}
    </section>
  );
}

function FlipbookList({ eventId, dataEnabled, onCreate }) {
  const list = useLoad(() => api.flipbooks(eventId), [eventId]);
  const [copied, setCopied] = useState('');
  return (
    <div>
      {list.error && <p className="form-error">{list.error}</p>}
      <div className="event-form-actions"><button className="button button-dark" type="button" disabled={!dataEnabled} onClick={onCreate}>＋ Create e-album</button></div>
      {list.loading ? <p className="customer-empty">Loading…</p> : !(list.data || []).length ? <p className="customer-empty">No e-albums for this event yet.</p> : (
        <ul className="studio-receipts">
          {list.data.map((book) => (
            <li key={book.id}>
              <div><b>{book.title}</b><span>{book.pageCount} pages · created {new Date(book.createdAt).toLocaleDateString()}</span></div>
              <a className="customer-id-link" href={book.shareUrl} target="_blank" rel="noreferrer">Open ↗</a>
              <button className="customer-id-link" type="button" onClick={() => navigator.clipboard?.writeText(book.shareUrl).then(() => { setCopied(book.id); setTimeout(() => setCopied(''), 1500); })}>{copied === book.id ? 'Copied' : 'Copy link'}</button>
              <button className="customer-id-link studio-danger-link" type="button" onClick={async () => { if (window.confirm(`Delete “${book.title}”? Its link will stop working.`)) { await api.deleteFlipbook(eventId, book.id); list.reload(); } }}>Delete</button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function MediaPickerModal({ mode, events, initialEventId, onClose, onDone }) {
  const [eventId, setEventId] = useState(initialEventId || events[0]?.id || '');
  const media = useLoad(() => (eventId ? api.photographerMedia(eventId) : Promise.resolve({ photos: [] })), [eventId]);
  const [selected, setSelected] = useState([]);
  const [title, setTitle] = useState('');
  const [caption, setCaption] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const photos = (media.data?.photos || []).filter((item) => item.media_type !== 'video' && !['QUEUED', 'PROCESSING', 'FAILED'].includes(item.status));
  const event = events.find((entry) => entry.id === eventId);

  useEffect(() => { setSelected([]); if (mode === 'flipbook' && event) setTitle(`${event.eventTypeName} · ${event.customerName}`); }, [eventId]); // eslint-disable-line react-hooks/exhaustive-deps

  function toggle(id) {
    setSelected((current) => (current.includes(id) ? current.filter((value) => value !== id) : [...current, id]));
  }

  async function useCustomerSelection() {
    setError('');
    try {
      const ids = await api.flipbookCustomerSelection(eventId);
      if (!ids.length) setError('The customer hasn’t selected any photos for this event yet.');
      setSelected(ids);
    } catch (requestError) { setError(requestError.message); }
  }

  async function submit() {
    setBusy(true); setError('');
    try {
      if (mode === 'portfolio') {
        await api.addPortfolioItems({ eventId, mediaIds: selected, caption });
        onDone(`${selected.length} ${selected.length === 1 ? 'photo' : 'photos'} added to your portfolio.`);
      } else {
        await api.createFlipbook(eventId, { title, mediaIds: selected });
        onDone('E-album created. Copy its link to share it.');
      }
    } catch (requestError) { setError(requestError.message); setBusy(false); }
  }

  const limit = mode === 'portfolio' ? 200 : 400;
  return (
    <Modal eyebrow={mode === 'portfolio' ? 'Portfolio' : 'E-album flipbook'} title={mode === 'portfolio' ? 'Add photos to your portfolio' : 'Create an e-album'} onClose={onClose} wide>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="studio-form studio-form-inline">
        <label className="event-form-label">Event<select value={eventId} onChange={(e) => setEventId(e.target.value)} disabled={mode === 'flipbook' && Boolean(initialEventId)}>{events.map((entry) => <option key={entry.id} value={entry.id}>{entry.eventCode} · {entry.customerName}</option>)}</select></label>
        {mode === 'flipbook'
          ? <label className="event-form-label">Album title<input maxLength="160" value={title} onChange={(e) => setTitle(e.target.value)} required /></label>
          : <label className="event-form-label">Caption (optional, applies to all)<input maxLength="240" value={caption} onChange={(e) => setCaption(e.target.value)} /></label>}
        {mode === 'flipbook' && <div className="event-form-actions"><button className="button button-quiet button-small" type="button" onClick={useCustomerSelection}>Use customer’s album selection</button></div>}
      </div>
      <p className="event-form-note">{selected.length} selected{mode === 'flipbook' ? ' · pages appear in the order you pick them' : ''} · up to {limit}</p>
      {media.loading ? <p className="customer-empty">Loading photos…</p> : media.error ? <p className="form-error">{media.error}</p> : !photos.length ? <p className="customer-empty">This event has no processed photos yet.</p> : (
        <ul className="studio-picker-grid">
          {photos.map((item) => {
            const order = selected.indexOf(item.photo_id);
            return (
              <li key={item.photo_id}>
                <button type="button" className={order >= 0 ? 'is-selected' : ''} onClick={() => toggle(item.photo_id)} aria-pressed={order >= 0} aria-label={`Photo ${item.photo_number}`}>
                  <img src={api.photographerPreviewUrl(eventId, item.photo_id)} alt="" loading="lazy" />
                  <span>{order >= 0 ? order + 1 : `#${item.photo_number}`}</span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
      <div className="event-form-actions">
        <button className="button button-quiet" type="button" onClick={onClose} disabled={busy}>Cancel</button>
        <button className="button button-dark" type="button" disabled={busy || !selected.length || selected.length > limit || (mode === 'flipbook' && !title.trim())} onClick={submit}>{busy ? 'Saving…' : mode === 'portfolio' ? 'Add to portfolio' : 'Create e-album'}</button>
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------------------------------------
// Studio settings (UPI, receipts, portfolio page, reminders)
// ---------------------------------------------------------------------------------------------

export function StudioSettingsPanel() {
  const profile = useLoad(() => api.studioProfile(), []);
  const [form, setForm] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    if (profile.data) {
      const p = profile.data;
      setForm({ upiId: p.upiId || '', upiPayeeName: p.upiPayeeName || p.studioName || '', businessAddress: p.businessAddress || '', gstin: p.gstin || '', receiptFooter: p.receiptFooter || '', portfolioSlug: p.portfolioSlug || '', portfolioHeadline: p.portfolioHeadline || '', portfolioBio: p.portfolioBio || '', portfolioPublic: p.portfolioPublic, remindersEnabled: p.remindersEnabled });
    }
  }, [profile.data]);

  async function save(event) {
    event.preventDefault();
    setBusy(true); setError(''); setNotice('');
    const blankToNull = (value) => (value && value.trim() ? value.trim() : null);
    try {
      await api.saveStudioProfile({ ...form, upiId: blankToNull(form.upiId), gstin: blankToNull(form.gstin?.toUpperCase()), portfolioSlug: blankToNull(form.portfolioSlug?.toLowerCase()) });
      setNotice('Studio settings saved.');
      profile.reload();
    } catch (requestError) {
      setError(requestError.message && requestError.message !== 'The request could not be completed.'
        ? requestError.message
        : 'Could not save. Check the UPI ID, GSTIN and portfolio address (it may already be taken).');
    } finally { setBusy(false); }
  }

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  return (
    <section className="event-module studio-panel">
      <PanelIntro eyebrow="Set up once" title="Studio" accent="settings.">Payment details for UPI QR codes and receipts, your public portfolio page, and event reminders.</PanelIntro>
      {error && <p className="form-error customer-message" role="alert">{error}</p>}
      {notice && <p className="form-success customer-message" role="status">{notice}</p>}
      {!form ? <p className="customer-empty">{profile.error || 'Loading…'}</p> : (
        <form className="customer-list-section studio-settings" onSubmit={save}>
          <fieldset><legend>Payments & receipts</legend>
            <label className="event-form-label">UPI ID<input value={form.upiId} onChange={set('upiId')} placeholder="yourstudio@okhdfcbank" pattern="[A-Za-z0-9.\-_]{2,256}@[A-Za-z][A-Za-z0-9]{1,63}" /></label>
            <label className="event-form-label">Name shown in UPI apps<input maxLength="120" value={form.upiPayeeName} onChange={set('upiPayeeName')} /></label>
            <label className="event-form-label">Business address (on receipts)<input maxLength="500" value={form.businessAddress} onChange={set('businessAddress')} /></label>
            <label className="event-form-label">GSTIN (optional)<input maxLength="15" value={form.gstin} onChange={set('gstin')} /></label>
            <label className="event-form-label studio-span-2">Receipt footer note<input maxLength="500" value={form.receiptFooter} onChange={set('receiptFooter')} placeholder="Thank you for choosing us!" /></label>
          </fieldset>
          <fieldset><legend>Portfolio page</legend>
            <label className="event-form-label">Portfolio address<span className="studio-prefix-input"><span>{window.location.host}/portfolio/</span><input maxLength="60" value={form.portfolioSlug} onChange={set('portfolioSlug')} placeholder="your-studio" pattern="[a-z0-9][a-z0-9\-]{1,58}[a-z0-9]" /></span></label>
            <label className="event-form-label">Headline<input maxLength="160" value={form.portfolioHeadline} onChange={set('portfolioHeadline')} placeholder="Candid wedding stories from Bengaluru" /></label>
            <label className="event-form-label studio-span-2">About your studio<textarea maxLength="2000" rows="4" value={form.portfolioBio} onChange={set('portfolioBio')} /></label>
            <label className="studio-check studio-span-2"><input type="checkbox" checked={form.portfolioPublic} onChange={set('portfolioPublic')} />Publish my portfolio (shows your studio name, email and mobile number)</label>
          </fieldset>
          <fieldset><legend>Reminders</legend>
            <label className="studio-check studio-span-2"><input type="checkbox" checked={form.remindersEnabled} onChange={set('remindersEnabled')} />Send day-before event reminders to me and my customers</label>
          </fieldset>
          <div className="event-form-actions"><button className="button button-dark" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save settings'}</button></div>
        </form>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------------------------
// Customer portal: payments, receipts, UPI QR, download status and shared e-albums
// ---------------------------------------------------------------------------------------------

export function CustomerBillingModal({ event, onClose }) {
  const billing = useLoad(() => api.customerBilling(event.eventId), [event.eventId]);
  const data = billing.data;
  const downloadDue = data && data.downloadPolicy.mode === 'PAID' && !data.downloadPolicy.unlocked;
  return (
    <Modal eyebrow={`${event.eventName} · ${event.photographerName}`} title="Payments & albums" onClose={onClose} wide>
      {billing.loading ? <p className="customer-empty">Loading…</p> : billing.error ? <p className="form-error">{billing.error}</p> : (
        <div className="studio-billing-grid">
          <div>
            <div className="studio-stats studio-stats-compact">
              <Stat label="Event price" value={inr(data.total)} />
              <Stat label="Paid" value={inr(data.paid)} tone="good" />
              <Stat label="Balance" value={inr(data.balance)} tone={Number(data.balance) > 0 ? 'warn' : 'good'} />
            </div>
            <h3 className="studio-h3">Receipts</h3>
            {!data.payments.length ? <p className="customer-empty">No payments recorded yet.</p> : (
              <ul className="studio-receipts">{data.payments.map((payment) => (
                <li key={payment.id}><div><b>{payment.receiptNumber} · {inr(payment.amount)}</b><span>{shortDate(payment.paidOn)}</span></div><a className="customer-id-link" href={api.customerReceiptUrl(event.eventId, payment.id)} target="_blank" rel="noreferrer">Download ↗</a></li>
              ))}</ul>
            )}
            <h3 className="studio-h3">E-albums</h3>
            {!data.flipbooks.length ? <p className="customer-empty">Your photographer hasn’t shared an e-album yet.</p> : (
              <ul className="studio-receipts">{data.flipbooks.map((book) => (
                <li key={book.url}><div><b>{book.title}</b><span>{book.pageCount} pages</span></div><a className="customer-id-link" href={book.url} target="_blank" rel="noreferrer">Open album ↗</a></li>
              ))}</ul>
            )}
          </div>
          <div>
            {data.upiConfigured && Number(data.balance) > 0 && <PayByUpi title={`Pay balance ${inr(data.balance)}`} src={api.customerPaymentQrUrl(event.eventId, 'BALANCE')} />}
            {data.upiConfigured && downloadDue && <PayByUpi title={`Unlock photo downloads ${inr(data.downloadPolicy.price)}`} src={api.customerPaymentQrUrl(event.eventId, 'DOWNLOAD')} />}
            {!data.upiConfigured && (Number(data.balance) > 0 || downloadDue) && <p className="event-form-note">Contact your photographer for payment details.</p>}
            <p className="event-form-note">Photo downloads: {data.downloadPolicy.mode === 'FREE' ? 'free' : data.downloadPolicy.mode === 'DISABLED' ? 'turned off by your photographer' : data.downloadPolicy.unlocked ? 'unlocked' : `locked until ${inr(data.downloadPolicy.price)} is paid`}.</p>
          </div>
        </div>
      )}
    </Modal>
  );
}

function PayByUpi({ title, src }) {
  return (
    <div className="studio-qr-box">
      <h3 className="studio-h3">{title}</h3>
      <img src={src} alt="UPI payment QR code" />
      <p className="event-form-note">Scan with any UPI app (GPay, PhonePe, Paytm, BHIM). Your photographer confirms the payment and your receipt appears here.</p>
    </div>
  );
}

/** Download rules for the customer gallery: whether downloads are allowed and the message to show if not. */
export function useCustomerDownloadPolicy(eventId) {
  const billing = useLoad(() => api.customerBilling(eventId).catch(() => null), [eventId]);
  const policy = billing.data?.downloadPolicy;
  const allowed = !policy || policy.mode === 'FREE' || (policy.mode === 'PAID' && policy.unlocked);
  const message = !policy || allowed ? '' : policy.mode === 'DISABLED'
    ? 'Your photographer has turned off downloads for this gallery.'
    : `Downloads are paid for this gallery (${inr(policy.price)}). Pay from “Payments & albums” and your photographer will unlock them.`;
  return { allowed, message };
}

// ---------------------------------------------------------------------------------------------
// Public pages
// ---------------------------------------------------------------------------------------------

export function PublicPortfolioPage({ slug }) {
  const portfolio = useLoad(() => api.publicPortfolio(slug), [slug]);
  const [open, setOpen] = useState(-1);
  const data = portfolio.data;
  useEffect(() => { if (data) document.title = `${data.studioName} · Portfolio`; }, [data]);
  if (portfolio.loading) return <main className="studio-public"><p className="customer-empty">Loading…</p></main>;
  if (!data) return <main className="studio-public"><h1>Portfolio not found</h1><p>This page isn’t published.</p></main>;
  return (
    <main className="studio-public">
      <header className="studio-public-header">
        <p className="eyebrow">Photography portfolio</p>
        <h1>{data.studioName}</h1>
        {data.headline && <p className="studio-public-headline">{data.headline}</p>}
        {data.bio && <p className="studio-public-bio">{data.bio}</p>}
        <p className="studio-public-contact">{data.contactMobile && <a href={`tel:${data.contactMobile}`}>{data.contactMobile}</a>}{data.contactEmail && <a href={`mailto:${data.contactEmail}`}>{data.contactEmail}</a>}</p>
      </header>
      {!data.items.length ? <p className="customer-empty">Photos coming soon.</p> : (
        <ul className="studio-public-grid">{data.items.map((item, index) => (
          <li key={item.id}><button type="button" onClick={() => setOpen(index)}><img src={api.apiUrl(item.imageUrl)} alt={item.caption || `${data.studioName} photo ${index + 1}`} loading="lazy" /></button>{item.caption && <span>{item.caption}</span>}</li>
        ))}</ul>
      )}
      {open >= 0 && (
        <div className="studio-lightbox" role="dialog" aria-modal="true" onClick={() => setOpen(-1)}>
          <img src={api.apiUrl(data.items[open].imageUrl)} alt={data.items[open].caption || ''} />
          {data.items[open].caption && <p>{data.items[open].caption}</p>}
        </div>
      )}
      {data.poweredByFrameBucket !== false && <footer className="studio-public-footer">Powered by FrameBucket</footer>}
    </main>
  );
}

export function FlipbookPage({ token }) {
  const book = useLoad(() => api.publicFlipbook(token), [token]);
  const [spread, setSpread] = useState(0); // 0 = cover; on wide screens spread n shows pages 2n-1 and 2n
  const [turning, setTurning] = useState('');
  const [narrow, setNarrow] = useState(() => window.innerWidth < 760);
  const data = book.data;
  const pageCount = data?.pageCount || 0;
  const maxSpread = narrow ? pageCount : Math.ceil(pageCount / 2);

  useEffect(() => { if (data) document.title = `${data.title} · E-album`; }, [data]);
  useEffect(() => {
    const onResize = () => { setNarrow(window.innerWidth < 760); setSpread(0); };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  function go(direction) {
    const next = Math.min(maxSpread, Math.max(0, spread + direction));
    if (next === spread || turning) return;
    setTurning(direction > 0 ? 'next' : 'prev');
    setTimeout(() => { setSpread(next); setTurning(''); }, 420);
  }

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'ArrowRight') go(1);
      if (event.key === 'ArrowLeft') go(-1);
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  if (book.loading) return <main className="studio-flipbook-shell"><p className="customer-empty">Opening album…</p></main>;
  if (!data) return <main className="studio-flipbook-shell"><h1>Album not found</h1><p>This link may have been removed.</p></main>;

  const page = (index) => (index >= 0 && index < pageCount ? <img src={api.publicFlipbookPageUrl(token, index)} alt={`Page ${index + 1}`} /> : <div className="studio-page-blank" />);
  // Preload the next pages so turning feels instant.
  const preload = narrow ? [spread] : [spread * 2, spread * 2 + 1];

  return (
    <main className="studio-flipbook-shell">
      <header><p className="eyebrow">{data.studioName}</p><h1>{data.title}</h1><p>{data.eventTypeName}{data.eventDate ? ` · ${shortDate(data.eventDate)}` : ''} · {pageCount} pages</p></header>
      <div
        className={`studio-book${narrow ? ' is-single' : ''}${turning ? ` turning-${turning}` : ''}`}
        onClick={(e) => { const rect = e.currentTarget.getBoundingClientRect(); go(e.clientX - rect.left > rect.width / 2 ? 1 : -1); }}
        onTouchStart={(e) => { e.currentTarget.dataset.x = e.touches[0].clientX; }}
        onTouchEnd={(e) => { const dx = e.changedTouches[0].clientX - Number(e.currentTarget.dataset.x || 0); if (Math.abs(dx) > 40) { e.preventDefault(); go(dx < 0 ? 1 : -1); } }}
      >
        {spread === 0 ? (
          <div className="studio-book-cover"><span>{data.studioName}</span><b>{data.title}</b><small>Tap or press → to open</small></div>
        ) : narrow ? (
          <div className="studio-book-page">{page(spread - 1)}</div>
        ) : (
          <>
            <div className="studio-book-page left">{page(spread * 2 - 2)}</div>
            <div className="studio-book-page right">{page(spread * 2 - 1)}</div>
          </>
        )}
      </div>
      <div className="studio-book-controls">
        <button className="button button-quiet" type="button" onClick={() => go(-1)} disabled={spread === 0}>‹ Previous</button>
        <span>{spread === 0 ? 'Cover' : narrow ? `Page ${spread} of ${pageCount}` : `Pages ${spread * 2 - 1}–${Math.min(spread * 2, pageCount)} of ${pageCount}`}</span>
        <button className="button button-dark" type="button" onClick={() => go(1)} disabled={spread >= maxSpread}>Next ›</button>
      </div>
      <div hidden>{preload.filter((i) => i < pageCount).map((i) => <img key={i} src={api.publicFlipbookPageUrl(token, i)} alt="" />)}</div>
      {data.poweredByFrameBucket !== false && <footer className="studio-public-footer">Powered by FrameBucket</footer>}
    </main>
  );
}
