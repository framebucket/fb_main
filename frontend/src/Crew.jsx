import { useEffect, useState } from 'react';
import { api } from './api.js';
import { UpgradeNotice, hasFeature } from './Plans.jsx';
import { PanelIntro, Stat, inr, shortDate, useLoad } from './Studio.jsx';
import './growth.css';

// Crew scheduling & payouts, gear checklists, event costs & profit.

const METHODS = [['UPI', 'UPI'], ['CASH', 'Cash'], ['BANK_TRANSFER', 'Bank transfer'], ['CARD', 'Card'], ['CHEQUE', 'Cheque'], ['OTHER', 'Other']];
const COST_CATEGORIES = ['Travel', 'Accommodation', 'Food', 'Equipment rental', 'Assistant / freelancer', 'Printing & album', 'Other'];
const GEAR_CATEGORIES = ['Camera', 'Lens', 'Lighting', 'Drone', 'Audio', 'Gimbal / support', 'Storage', 'Other'];

function today() {
  return new Date().toISOString().slice(0, 10);
}

function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  async function run(task, done) {
    setBusy(true); setError(''); setNotice('');
    try { const r = await task(); if (done) setNotice(done); return r; } catch (requestError) { setError(requestError.message); return null; } finally { setBusy(false); }
  }
  return { busy, run, messages: <>{error && <p className="form-error" role="alert">{error}</p>}{notice && <p className="form-success" role="status">{notice}</p>}</> };
}

const memberKey = (m) => `${m.memberType}:${m.memberId}`;

// ---------------------------------------------------------------------------------------------
// Event workspace tabs
// ---------------------------------------------------------------------------------------------

export function CrewTab({ eventId }) {
  const crew = useLoad(() => api.eventCrew(eventId), [eventId]);
  const members = useLoad(() => api.crewMembers(), []);
  const [list, setList] = useState(null);
  const [form, setForm] = useState({ member: '', role: '', callTime: '', fee: '', notes: '' });
  const [warnings, setWarnings] = useState([]);
  const [editing, setEditing] = useState(null);
  const { busy, run, messages } = useAction();
  useEffect(() => { if (crew.data) setList(crew.data); }, [crew.data]);

  async function pick(value) {
    setForm({ ...form, member: value });
    setWarnings([]);
    if (!value) return;
    const [memberType, memberId] = value.split(':');
    try { setWarnings((await api.crewCheck(eventId, memberType, memberId)).warnings); } catch { /* ignore */ }
  }

  if (!list) return <p className="customer-empty">{crew.error || 'Loading…'}</p>;
  const assigned = new Set(list.map((c) => `${c.memberType}:${c.memberId}`));
  const total = list.reduce((sum, c) => sum + Number(c.fee || 0), 0);
  return (
    <div className="growth-stack">
      {messages}
      {!list.length ? <p className="customer-empty">No crew yet. Add team members and freelancers from your Team tab.</p> : (
        <div className="customer-table-scroll"><table className="customer-table growth-table">
          <thead><tr><th>Name</th><th>Role</th><th>Call time</th><th>Fee</th><th>Notes</th><th /></tr></thead>
          <tbody>{list.map((c) => editing?.id === c.id ? (
            <tr key={c.id}>
              <td><b>{c.name}</b></td>
              <td><input className="growth-cell-input" value={editing.role} onChange={(e) => setEditing({ ...editing, role: e.target.value })} /></td>
              <td><input className="growth-cell-input" type="time" value={editing.callTime || ''} onChange={(e) => setEditing({ ...editing, callTime: e.target.value })} /></td>
              <td><input className="growth-cell-input growth-cell-num" type="number" min="0" value={editing.fee} onChange={(e) => setEditing({ ...editing, fee: e.target.value })} /></td>
              <td><input className="growth-cell-input" value={editing.notes || ''} onChange={(e) => setEditing({ ...editing, notes: e.target.value })} /></td>
              <td className="growth-row-actions"><button className="button button-quiet button-small" type="button" disabled={busy} onClick={async () => {
                const r = await run(() => api.updateCrew(eventId, c.id, { memberType: c.memberType, memberId: c.memberId, role: editing.role, callTime: editing.callTime || null, fee: Number(editing.fee) || 0, notes: editing.notes }), 'Saved.');
                if (r) { setList(r); setEditing(null); }
              }}>Save</button><button className="customer-id-link" type="button" onClick={() => setEditing(null)}>Cancel</button></td>
            </tr>
          ) : (
            <tr key={c.id}>
              <td><b>{c.name}</b><span className="studio-cell-sub">{c.memberType === 'TEAM' ? 'Team' : 'Freelancer'} · {c.mobileNumber}</span>
                {c.warnings.map((w) => <span key={w} className="growth-warning">⚠ {w}</span>)}</td>
              <td>{c.role}</td>
              <td>{c.callTime ? c.callTime.slice(0, 5) : '—'}</td>
              <td>{inr(c.fee)}</td>
              <td>{c.notes || '—'}</td>
              <td className="growth-row-actions">
                <button className="customer-id-link" type="button" onClick={() => setEditing({ ...c, callTime: c.callTime ? c.callTime.slice(0, 5) : '' })}>Edit</button>
                <button className="customer-id-link studio-danger-link" type="button" disabled={busy} onClick={async () => { const r = await run(() => api.removeCrew(eventId, c.id), 'Removed.'); if (r) setList(r); }}>Remove</button>
              </td>
            </tr>
          ))}</tbody>
          <tfoot><tr><td colSpan="3">Crew cost</td><td><b>{inr(total)}</b></td><td colSpan="2" /></tr></tfoot>
        </table></div>
      )}
      <form className="studio-form studio-form-inline customer-list-section" onSubmit={async (e) => {
        e.preventDefault();
        const [memberType, memberId] = form.member.split(':');
        const r = await run(() => api.addCrew(eventId, { memberType, memberId, role: form.role, callTime: form.callTime || null, fee: Number(form.fee) || 0, notes: form.notes }), 'Added to crew.');
        if (r) { setList(r); setForm({ member: '', role: '', callTime: '', fee: '', notes: '' }); setWarnings([]); }
      }}>
        <label className="event-form-label">Person<select required value={form.member} onChange={(e) => pick(e.target.value)}>
          <option value="">Choose…</option>
          {(members.data || []).filter((m) => !assigned.has(memberKey(m))).map((m) => <option key={memberKey(m)} value={memberKey(m)}>{m.name} ({m.memberType === 'TEAM' ? 'team' : 'freelancer'})</option>)}
        </select></label>
        <label className="event-form-label">Role<input required maxLength="80" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })} placeholder="Second shooter" /></label>
        <label className="event-form-label">Call time<input type="time" value={form.callTime} onChange={(e) => setForm({ ...form, callTime: e.target.value })} /></label>
        <label className="event-form-label">Fee (₹)<input type="number" min="0" value={form.fee} onChange={(e) => setForm({ ...form, fee: e.target.value })} /></label>
        <button className="button button-dark" type="submit" disabled={busy}>Add</button>
        {warnings.map((w) => <p key={w} className="growth-warning studio-span-2">⚠ {w}</p>)}
      </form>
    </div>
  );
}

export function GearCostsTab({ eventId }) {
  const gear = useLoad(() => api.eventGear(eventId), [eventId]);
  const inventory = useLoad(() => api.gear(), []);
  const costs = useLoad(() => api.eventCosts(eventId), [eventId]);
  const [list, setList] = useState(null);
  const [summary, setSummary] = useState(null);
  const [pickGear, setPickGear] = useState('');
  const [cost, setCost] = useState({ category: 'Travel', amount: '', spentOn: today(), vendor: '', notes: '' });
  const { busy, run, messages } = useAction();
  useEffect(() => { if (gear.data) setList(gear.data); }, [gear.data]);
  useEffect(() => { if (costs.data) setSummary(costs.data); }, [costs.data]);
  if (!list || !summary) return <p className="customer-empty">{gear.error || costs.error || 'Loading…'}</p>;
  const onEvent = new Set(list.map((g) => g.gearId));
  const packed = list.filter((g) => g.packed).length;
  return (
    <div className="studio-billing-grid">
      <div className="growth-stack">
        {messages}
        <h3 className="studio-h3">Profit for this event</h3>
        <div className="studio-stats">
          <Stat label="Agreed price" value={inr(summary.agreedPrice)} />
          <Stat label="Received" value={inr(summary.received)} tone="good" />
          <Stat label="Costs" value={inr(summary.totalCosts)} />
          <Stat label={`Profit${summary.marginPercent != null ? ` · ${summary.marginPercent}%` : ''}`} value={inr(summary.profit)} tone={Number(summary.profit) >= 0 ? 'good' : 'warn'} />
        </div>
        <p className="event-form-note">Costs = expenses below + crew fees ({inr(summary.crewFees)}).</p>
        {summary.expenses.length > 0 && <ul className="studio-receipts">{summary.expenses.map((x) => (
          <li key={x.id}><div><b>{x.category} · {inr(x.amount)}</b><span>{shortDate(x.spentOn)}{x.vendor ? ` · ${x.vendor}` : ''}{x.notes ? ` · ${x.notes}` : ''}</span></div>
            <button className="customer-id-link studio-danger-link" type="button" disabled={busy} onClick={async () => { const r = await run(() => api.deleteEventCost(eventId, x.id)); if (r) setSummary(r); }}>Delete</button></li>
        ))}</ul>}
        <form className="studio-form" onSubmit={async (e) => {
          e.preventDefault();
          const r = await run(() => api.addEventCost(eventId, { ...cost, amount: Number(cost.amount) }), 'Cost added.');
          if (r) { setSummary(r); setCost({ ...cost, amount: '', vendor: '', notes: '' }); }
        }}>
          <label className="event-form-label">Category<select value={cost.category} onChange={(e) => setCost({ ...cost, category: e.target.value })}>{COST_CATEGORIES.map((c) => <option key={c}>{c}</option>)}</select></label>
          <label className="event-form-label">Amount (₹)<input required type="number" min="1" value={cost.amount} onChange={(e) => setCost({ ...cost, amount: e.target.value })} /></label>
          <label className="event-form-label">Date<input required type="date" value={cost.spentOn} onChange={(e) => setCost({ ...cost, spentOn: e.target.value })} /></label>
          <label className="event-form-label">Details<input maxLength="500" value={cost.notes} onChange={(e) => setCost({ ...cost, notes: e.target.value })} placeholder="Cab BLR → Mysuru, hotel 2 nights…" /></label>
          <div className="event-form-actions"><button className="button button-dark button-small" type="submit" disabled={busy}>Add cost</button></div>
        </form>
      </div>
      <div className="growth-stack">
        <h3 className="studio-h3">Gear checklist {list.length > 0 && <span className="studio-pill">{packed}/{list.length} packed</span>}</h3>
        {!list.length ? <p className="customer-empty">Nothing on the list yet.</p> : (
          <ul className="growth-checklist">{list.map((g) => (
            <li key={g.gearId}>
              <div><b>{g.name}</b><span>{g.category}{g.serialNumber ? ` · ${g.serialNumber}` : ''}</span></div>
              <label className="studio-check"><input type="checkbox" checked={g.packed} onChange={async (e) => { const r = await run(() => api.updateEventGear(eventId, g.gearId, { packed: e.target.checked })); if (r) setList(r); }} />Packed</label>
              <label className="studio-check"><input type="checkbox" checked={g.returned} onChange={async (e) => { const r = await run(() => api.updateEventGear(eventId, g.gearId, { returned: e.target.checked })); if (r) setList(r); }} />Back</label>
              <button className="customer-id-link studio-danger-link" type="button" onClick={async () => { const r = await run(() => api.removeEventGear(eventId, g.gearId)); if (r) setList(r); }} aria-label={`Remove ${g.name}`}>×</button>
            </li>
          ))}</ul>
        )}
        <div className="growth-inline-form">
          <select value={pickGear} onChange={(e) => setPickGear(e.target.value)} aria-label="Gear item">
            <option value="">Add gear…</option>
            {(inventory.data || []).filter((g) => g.active && !onEvent.has(g.id)).map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
          </select>
          <button className="button button-quiet button-small" type="button" disabled={!pickGear || busy} onClick={async () => { const r = await run(() => api.addEventGear(eventId, { gearIds: [pickGear] })); if (r) { setList(r); setPickGear(''); } }}>Add</button>
          <button className="button button-quiet button-small" type="button" disabled={busy} onClick={async () => { const r = await run(() => api.addEventGear(eventId, { defaultKit: true }), 'Default kit added.'); if (r) setList(r); }}>Add default kit</button>
        </div>
        {(inventory.data || []).length === 0 && <p className="event-form-note">Add your cameras, lenses and lights under Crew &amp; gear → Gear.</p>}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Dashboard tab: crew balances, payouts, availability, gear inventory
// ---------------------------------------------------------------------------------------------

export function CrewPanel({ user }) {
  const [tab, setTab] = useState('payouts');
  if (!hasFeature(user, 'CREW_PAYOUTS') && !hasFeature(user, 'GEAR_COSTS')) {
    return <section className="event-module studio-panel"><UpgradeNotice feature="CREW_PAYOUTS" title="Crew & gear">Schedule your crew, see who's owed what, and keep a packing list for every shoot.</UpgradeNotice></section>;
  }
  return (
    <section className="event-module studio-panel">
      <PanelIntro eyebrow="Your people and kit" title="Crew &" accent="gear.">Who's owed what, who's unavailable when, and everything you own. Assign crew and pack gear per event from Events → Manage.</PanelIntro>
      <div className="studio-subtabs">{[['payouts', 'Payouts'], ['availability', 'Availability'], ['gear', 'Gear']].map(([key, label]) => <button key={key} type="button" className={tab === key ? 'active' : ''} onClick={() => setTab(key)}>{label}</button>)}</div>
      {tab === 'payouts' && <PayoutsSection />}
      {tab === 'availability' && <AvailabilitySection />}
      {tab === 'gear' && <GearInventory />}
    </section>
  );
}

function PayoutsSection() {
  const balances = useLoad(() => api.crewBalances(), []);
  const payouts = useLoad(() => api.crewPayouts(), []);
  const events = useLoad(() => api.events().catch(() => []), []);
  const [form, setForm] = useState({ member: '', eventId: '', amount: '', paidOn: today(), method: 'UPI', reference: '' });
  const { busy, run, messages } = useAction();
  const totalDue = (balances.data || []).reduce((sum, b) => sum + Math.max(0, Number(b.due)), 0);
  return (
    <div className="growth-stack">
      {messages}
      <div className="studio-stats studio-stats-compact">
        <Stat label="Owed to crew now" value={inr(totalDue)} tone={totalDue > 0 ? 'warn' : 'good'} />
        <Stat label="Upcoming fees" value={inr((balances.data || []).reduce((s, b) => s + Number(b.upcoming), 0))} />
        <Stat label="Paid (all time)" value={inr((balances.data || []).reduce((s, b) => s + Number(b.paid), 0))} tone="good" />
      </div>
      <div className="customer-table-scroll"><table className="customer-table growth-table">
        <thead><tr><th>Name</th><th>Shoots</th><th>Earned (done shoots)</th><th>Upcoming</th><th>Paid</th><th>Due</th></tr></thead>
        <tbody>{(balances.data || []).map((b) => (
          <tr key={`${b.memberType}:${b.memberId}`}><td><b>{b.name}</b><span className="studio-cell-sub">{b.memberType === 'TEAM' ? 'Team' : 'Freelancer'}</span></td>
            <td>{b.assignments}</td><td>{inr(b.earned)}</td><td>{inr(b.upcoming)}</td><td>{inr(b.paid)}</td>
            <td className={Number(b.due) > 0 ? 'studio-due' : 'studio-paid'}>{inr(b.due)}</td></tr>
        ))}</tbody></table></div>
      <form className="studio-form studio-form-inline customer-list-section" onSubmit={async (e) => {
        e.preventDefault();
        const [memberType, memberId] = form.member.split(':');
        const r = await run(() => api.addCrewPayout({ memberType, memberId, eventId: form.eventId || null, amount: Number(form.amount), paidOn: form.paidOn, method: form.method, reference: form.reference }), 'Payout recorded.');
        if (r) { payouts.reload(); balances.reload(); setForm({ ...form, amount: '', reference: '' }); }
      }}>
        <h3 className="studio-h3 studio-span-2">Record a payout</h3>
        <label className="event-form-label">Paid to<select required value={form.member} onChange={(e) => setForm({ ...form, member: e.target.value })}>
          <option value="">Choose…</option>{(balances.data || []).map((b) => <option key={memberKey(b)} value={memberKey(b)}>{b.name}{Number(b.due) > 0 ? ` (due ${inr(b.due)})` : ''}</option>)}</select></label>
        <label className="event-form-label">For event (optional)<select value={form.eventId} onChange={(e) => setForm({ ...form, eventId: e.target.value })}>
          <option value="">—</option>{(events.data || []).map((ev) => <option key={ev.id} value={ev.id}>{ev.eventCode} · {ev.customerName}</option>)}</select></label>
        <label className="event-form-label">Amount (₹)<input required type="number" min="1" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} /></label>
        <label className="event-form-label">Date<input required type="date" value={form.paidOn} onChange={(e) => setForm({ ...form, paidOn: e.target.value })} /></label>
        <label className="event-form-label">Method<select value={form.method} onChange={(e) => setForm({ ...form, method: e.target.value })}>{METHODS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
        <label className="event-form-label">Reference<input maxLength="120" value={form.reference} onChange={(e) => setForm({ ...form, reference: e.target.value })} /></label>
        <button className="button button-dark" type="submit" disabled={busy}>Record</button>
      </form>
      <h3 className="studio-h3">Payout history</h3>
      {!(payouts.data || []).length ? <p className="customer-empty">No payouts yet.</p> : (
        <ul className="studio-receipts">{payouts.data.map((p) => (
          <li key={p.id}><div><b>{p.name} · {inr(p.amount)}</b><span>{shortDate(p.paidOn)} · {METHODS.find(([v]) => v === p.method)?.[1]}{p.eventCode ? ` · ${p.eventCode}` : ''}{p.reference ? ` · ${p.reference}` : ''}</span></div>
            <button className="customer-id-link studio-danger-link" type="button" disabled={busy} onClick={async () => { if (window.confirm('Delete this payout?')) { const r = await run(() => api.deleteCrewPayout(p.id)); if (r) { payouts.reload(); balances.reload(); } } }}>Delete</button></li>
        ))}</ul>
      )}
    </div>
  );
}

function AvailabilitySection() {
  const unavailable = useLoad(() => api.crewUnavailable(), []);
  const members = useLoad(() => api.crewMembers(), []);
  const [form, setForm] = useState({ member: '', date: today(), note: '' });
  const { busy, run, messages } = useAction();
  return (
    <div className="growth-stack">
      {messages}
      <p className="event-form-note">Mark days your crew can't work. You'll see a warning when you assign them, and FrameBucket also warns if a freelancer is already booked by another studio on that date.</p>
      <form className="studio-form studio-form-inline" onSubmit={async (e) => {
        e.preventDefault();
        const [memberType, memberId] = form.member.split(':');
        const r = await run(() => api.addCrewUnavailable({ memberType, memberId, date: form.date, note: form.note }), 'Saved.');
        if (r) { unavailable.reload(); setForm({ ...form, note: '' }); }
      }}>
        <label className="event-form-label">Person<select required value={form.member} onChange={(e) => setForm({ ...form, member: e.target.value })}><option value="">Choose…</option>{(members.data || []).map((m) => <option key={memberKey(m)} value={memberKey(m)}>{m.name}</option>)}</select></label>
        <label className="event-form-label">Date<input required type="date" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} /></label>
        <label className="event-form-label">Reason<input maxLength="200" value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} placeholder="Family function" /></label>
        <button className="button button-dark" type="submit" disabled={busy}>Mark unavailable</button>
      </form>
      {!(unavailable.data || []).length ? <p className="customer-empty">Everyone's available.</p> : (
        <ul className="studio-receipts">{unavailable.data.map((u) => (
          <li key={u.id}><div><b>{u.name} · {shortDate(u.date)}</b><span>{u.note || 'Unavailable'}</span></div>
            <button className="customer-id-link studio-danger-link" type="button" onClick={async () => { const r = await run(() => api.deleteCrewUnavailable(u.id)); if (r) unavailable.reload(); }}>Remove</button></li>
        ))}</ul>
      )}
    </div>
  );
}

function GearInventory() {
  const gear = useLoad(() => api.gear(), []);
  const [list, setList] = useState(null);
  const [form, setForm] = useState({ name: '', category: 'Camera', serialNumber: '', inDefaultKit: true, active: true });
  const { busy, run, messages } = useAction();
  useEffect(() => { if (gear.data) setList(gear.data); }, [gear.data]);
  if (!list) return <p className="customer-empty">{gear.error || 'Loading…'}</p>;
  return (
    <div className="growth-stack">
      {messages}
      <form className="studio-form studio-form-inline" onSubmit={async (e) => {
        e.preventDefault();
        const r = await run(() => api.addGear(form), `${form.name} added.`);
        if (r) { setList(r); setForm({ ...form, name: '', serialNumber: '' }); }
      }}>
        <label className="event-form-label">Item<input required maxLength="120" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Sony A7 IV" /></label>
        <label className="event-form-label">Category<select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>{GEAR_CATEGORIES.map((c) => <option key={c}>{c}</option>)}</select></label>
        <label className="event-form-label">Serial no.<input maxLength="80" value={form.serialNumber} onChange={(e) => setForm({ ...form, serialNumber: e.target.value })} /></label>
        <label className="studio-check"><input type="checkbox" checked={form.inDefaultKit} onChange={(e) => setForm({ ...form, inDefaultKit: e.target.checked })} />In my default kit</label>
        <button className="button button-dark" type="submit" disabled={busy}>Add</button>
      </form>
      {!list.length ? <p className="customer-empty">Add your gear once; then pack it per event in one click.</p> : (
        <div className="customer-table-scroll"><table className="customer-table growth-table">
          <thead><tr><th>Item</th><th>Category</th><th>Serial</th><th>Default kit</th><th>In use</th><th /></tr></thead>
          <tbody>{list.map((g) => (
            <tr key={g.id} className={g.active ? '' : 'is-muted'}>
              <td><b>{g.name}</b></td><td>{g.category}</td><td>{g.serialNumber || '—'}</td>
              <td><input type="checkbox" checked={g.inDefaultKit} onChange={async (e) => { const r = await run(() => api.updateGear(g.id, { ...g, inDefaultKit: e.target.checked })); if (r) setList(r); }} aria-label="In default kit" /></td>
              <td><input type="checkbox" checked={g.active} onChange={async (e) => { const r = await run(() => api.updateGear(g.id, { ...g, active: e.target.checked })); if (r) setList(r); }} aria-label="In use" /></td>
              <td><button className="customer-id-link studio-danger-link" type="button" onClick={async () => { if (window.confirm(`Delete ${g.name}?`)) { const r = await run(() => api.deleteGear(g.id)); if (r) setList(r); } }}>Delete</button></td>
            </tr>
          ))}</tbody></table></div>
      )}
    </div>
  );
}
