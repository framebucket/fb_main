import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api.js';
import { hasFeature, UpgradeNotice } from '../../Plans.jsx';
import { navigate } from '../router.js';
import { useUser } from '../session.js';
import { Button, Chip, Empty, ErrorNote, Fab, Field, List, Row, Screen, Section, Segmented, Sheet, Skeleton, Stat, useAction, useResource } from '../ui.jsx';
import { dayDate, inr, inrShort, openFile, todayIso } from '../format.js';
import { saveBlob } from '../../nativeSave.js';

const METHODS = [['UPI', 'UPI'], ['CASH', 'Cash'], ['BANK_TRANSFER', 'Bank transfer'], ['CARD', 'Card'], ['CHEQUE', 'Cheque'], ['OTHER', 'Other']];
const PURPOSES = [['ADVANCE', 'Advance'], ['BALANCE', 'Balance'], ['DOWNLOAD', 'Photo downloads'], ['OTHER', 'Other']];
const CATEGORIES = ['Travel', 'Equipment rental', 'Assistant / freelancer', 'Printing & album', 'Food', 'Marketing', 'Software', 'Other'];

function label(list, key) {
  return list.find(([value]) => value === key)?.[1] || key;
}

export function MoneyScreen({ view: initialView }) {
  const { user } = useUser();
  const summary = useResource(() => (hasFeature(user, 'BILLING') ? api.financeSummary() : Promise.resolve(null)), []);
  const [view, setView] = useState(['due', 'all', 'expenses'].includes(initialView) ? initialView : 'due');
  const [addingExpense, setAddingExpense] = useState(false);
  useEffect(() => { if (['due', 'all', 'expenses'].includes(initialView)) setView(initialView); }, [initialView]);
  const expenses = useResource(() => (view === 'expenses' && hasFeature(user, 'BILLING') ? api.expenses() : Promise.resolve(null)), [view]);
  const data = summary.data;

  const rows = useMemo(() => {
    const list = [...(data?.events || [])];
    if (view === 'due') return list.filter((row) => Number(row.balance) > 0).sort((left, right) => left.eventDate.localeCompare(right.eventDate));
    return list.sort((left, right) => right.eventDate.localeCompare(left.eventDate));
  }, [data, view]);

  if (!hasFeature(user, 'BILLING')) {
    return <Screen large title="Payments"><div className="m-embed"><UpgradeNotice feature="BILLING" title="Payments, receipts & expenses">Record client payments, share UPI payment QR codes, send GST receipts and track studio expenses.</UpgradeNotice></div></Screen>;
  }

  return (
    <Screen large title="Payments" onRefresh={() => Promise.all([summary.reload(), expenses.reload()])}
      fab={view === 'expenses' ? <Fab label="Add expense" onClick={() => setAddingExpense(true)} /> : null}>
      <ErrorNote message={summary.error} onRetry={summary.reload} />
      {data ? (
        <div className="m-money-hero">
          <div className="m-money-main"><small>Outstanding</small><b>{inr(data.outstanding)}</b><span>{rows.length && view === 'due' ? `${rows.length} ${rows.length === 1 ? 'event' : 'events'} with a balance` : 'All settled'}</span></div>
          <div className="m-stat-row is-tight">
            <Stat label="Received" value={inrShort(data.received)} tone="good" />
            <Stat label="Billed" value={inrShort(data.billed)} />
            <Stat label="Expenses" value={inrShort(data.expenses)} />
            <Stat label="Net" value={inrShort(data.net)} tone={Number(data.net) >= 0 ? 'good' : 'warn'} />
          </div>
        </div>
      ) : <Skeleton rows={2} />}
      <div className="m-sticky-tools">
        <Segmented value={view} onChange={setView} options={[['due', 'Due'], ['all', 'All events'], ['expenses', 'Expenses']]} />
      </div>
      {view === 'expenses' ? (
        !expenses.data ? <Skeleton /> : !expenses.data.length ? <Empty icon="receipt" title="No expenses yet" action={<Button onClick={() => setAddingExpense(true)}>Add an expense</Button>}>Link costs to an event to see the real profit on each shoot.</Empty> : (
          <div className="m-list">
            {expenses.data.map((expense) => (
              <Row key={expense.id} icon="receipt" title={expense.category} subtitle={`${dayDate(expense.spentOn)}${expense.eventCode ? ` · ${expense.eventCode}` : ''}${expense.vendor ? ` · ${expense.vendor}` : ''}`}
                meta={<b>{inr(expense.amount)}</b>} chevron={false} />
            ))}
          </div>
        )
      ) : !data ? <Skeleton /> : !rows.length ? (
        <Empty icon="check" title={view === 'due' ? 'Nothing outstanding' : 'No events yet'}>{view === 'due' ? 'Every event is paid in full.' : 'Create an event to start billing.'}</Empty>
      ) : (
        <div className="m-list">
          {rows.map((row) => (
            <Row key={row.eventId} onClick={() => navigate(`/events/${row.eventId}/money`)}
              icon="wallet" tone={Number(row.balance) > 0 ? (row.eventDate < todayIso() ? 'danger' : 'warn') : 'good'}
              title={`${row.customerName}`} subtitle={`${row.eventTypeName} · ${dayDate(row.eventDate)}`}
              meta={<span className="m-row-meta-stack">{Number(row.balance) > 0 ? <b className="is-due">{inr(row.balance)}</b> : <Chip tone="good">Paid</Chip>}<small>of {inr(row.total)}</small></span>} />
          ))}
        </div>
      )}
      <ExpenseSheet open={addingExpense} events={data?.events || []} onClose={() => setAddingExpense(false)} onSaved={() => { expenses.reload(); summary.reload(); }} />
    </Screen>
  );
}

function ExpenseSheet({ open, events, onClose, onSaved, eventId }) {
  const empty = { eventId: eventId || '', category: CATEGORIES[0], amount: '', spentOn: todayIso(), vendor: '', notes: '' };
  const [form, setForm] = useState(empty);
  const [busy, run] = useAction();
  async function submit(event) {
    event.preventDefault();
    const ok = await run(() => api.createExpense({ ...form, eventId: form.eventId || null, amount: Number(form.amount) }), 'Expense added.');
    if (ok) { setForm(empty); onClose(); onSaved(); }
  }
  return (
    <Sheet open={open} title="Add an expense" onClose={onClose} tall>
      <form className="m-form" onSubmit={submit}>
        <Field label="Amount (₹)"><input type="number" inputMode="decimal" min="0.01" step="0.01" required value={form.amount} onChange={(event) => setForm({ ...form, amount: event.target.value })} autoFocus /></Field>
        <div className="m-choice-wrap">{CATEGORIES.map((category) => <button key={category} type="button" className={form.category === category ? 'is-active' : ''} onClick={() => setForm({ ...form, category })}>{category}</button>)}</div>
        <Field label="Date"><input type="date" required value={form.spentOn} onChange={(event) => setForm({ ...form, spentOn: event.target.value })} /></Field>
        <Field label="Event (optional)">
          <select value={form.eventId} onChange={(event) => setForm({ ...form, eventId: event.target.value })}>
            <option value="">General studio expense</option>
            {events.map((row) => <option key={row.eventId} value={row.eventId}>{row.eventCode} · {row.customerName}</option>)}
          </select>
        </Field>
        <Field label="Paid to"><input maxLength="160" value={form.vendor} onChange={(event) => setForm({ ...form, vendor: event.target.value })} /></Field>
        <Button type="submit" block busy={busy}>Add expense</Button>
      </form>
    </Sheet>
  );
}

// ------------------------------------------------------------------------------------- one event's money

export function EventMoneyScreen({ eventId, record }) {
  const { user } = useUser();
  const billing = useResource(() => api.eventBilling(eventId), [eventId]);
  const [sheet, setSheet] = useState(record ? 'payment' : '');
  const [busy, run] = useAction();
  const data = billing.data;

  if (!hasFeature(user, 'BILLING')) {
    return <Screen back title="Payments"><div className="m-embed"><UpgradeNotice feature="BILLING" title="Payments & receipts">Record client payments, share UPI payment QR codes and send receipts.</UpgradeNotice></div></Screen>;
  }

  const eventLabel = data ? `${data.eventCode || ''}${data.customerName ? ` · ${data.customerName}` : ''}` : '';

  async function shareQr() {
    const response = await fetch(api.paymentQrUrl(eventId, ''), { credentials: 'include' });
    if (!response.ok) throw new Error('The QR code could not be created.');
    await saveBlob(await response.blob(), `UPI-${data?.eventCode || 'payment'}.png`);
  }

  return (
    <Screen back backTo={`/events/${eventId}`} title="Payments" subtitle={eventLabel} onRefresh={billing.reload}
      fab={<Fab extended icon="plus" label="Record payment" onClick={() => setSheet('payment')} />}>
      <ErrorNote message={billing.error} onRetry={billing.reload} />
      {!data ? <Skeleton /> : (
        <>
          <div className="m-money-hero">
            <div className="m-money-main"><small>Balance due</small><b className={Number(data.balance) > 0 ? 'is-due' : 'is-paid'}>{Number(data.balance) > 0 ? inr(data.balance) : 'Paid in full'}</b>
              <span>{inr(data.paid)} received of {inr(data.total)}</span></div>
            {Number(data.total) > 0 && <div className="m-progress is-money"><span style={{ width: `${Math.min(100, (Number(data.paid) / Number(data.total)) * 100)}%` }} /></div>}
          </div>

          <Section title="Get paid">
            <List>
              {data.upiConfigured
                ? <Row icon="qr" tone="brass" title="Share UPI QR code" subtitle={Number(data.balance) > 0 ? `For the balance of ${inr(data.balance)}` : 'Customer scans and pays to your UPI ID'} onClick={() => run(shareQr)} />
                : <Row icon="qr" title="Add your UPI ID" subtitle="To show a payment QR code" onClick={() => navigate('/more/studio')} />}
            </List>
            {data.upiConfigured && <img className="m-qr" src={api.paymentQrUrl(eventId, '')} alt="UPI payment QR code" />}
          </Section>

          <Section title="Receipts">
            {!data.payments.length ? <p className="m-muted m-pad">No payments recorded yet.</p> : (
              <div className="m-list">
                {data.payments.map((payment) => (
                  <Row key={payment.id} icon="receipt" tone="good" title={inr(payment.amount)}
                    subtitle={`${dayDate(payment.paidOn)} · ${label(PURPOSES, payment.purpose)} · ${label(METHODS, payment.method)}${payment.reference ? ` · ${payment.reference}` : ''}`}
                    meta={<small>{payment.receiptNumber}</small>}
                    onClick={() => run(() => openFile(api.receiptUrl(eventId, payment.id), `${payment.receiptNumber}.pdf`))} />
                ))}
              </div>
            )}
          </Section>

          <Section title="Customer photo downloads">
            <DownloadPolicy eventId={eventId} policy={data.downloadPolicy} onSaved={billing.reload} />
          </Section>
        </>
      )}
      <PaymentSheet open={sheet === 'payment'} eventId={eventId} balance={data?.balance} onClose={() => setSheet('')} onSaved={billing.reload} />
    </Screen>
  );
}

function PaymentSheet({ open, eventId, balance, onClose, onSaved }) {
  const [form, setForm] = useState({ amount: '', paidOn: todayIso(), method: 'UPI', purpose: 'ADVANCE', reference: '', notes: '' });
  const [busy, run] = useAction();
  async function submit(event) {
    event.preventDefault();
    const ok = await run(() => api.recordPayment(eventId, { ...form, amount: Number(form.amount) }), 'Payment recorded. The receipt is ready.');
    if (ok) { setForm({ ...form, amount: '', reference: '', notes: '' }); onClose(); onSaved(); }
  }
  return (
    <Sheet open={open} title="Record a payment" onClose={onClose} tall>
      <form className="m-form" onSubmit={submit}>
        <Field label="Amount (₹)" hint={Number(balance) > 0 ? <button type="button" className="m-link" onClick={() => setForm({ ...form, amount: String(Number(balance)), purpose: 'BALANCE' })}>Use full balance: {inr(balance)}</button> : null}>
          <input type="number" inputMode="decimal" min="1" step="0.01" required value={form.amount} onChange={(event) => setForm({ ...form, amount: event.target.value })} autoFocus />
        </Field>
        <span className="m-field-label">Towards</span>
        <div className="m-choice-wrap">{PURPOSES.map(([key, text]) => <button key={key} type="button" className={form.purpose === key ? 'is-active' : ''} onClick={() => setForm({ ...form, purpose: key })}>{text}</button>)}</div>
        <span className="m-field-label">Method</span>
        <div className="m-choice-wrap">{METHODS.map(([key, text]) => <button key={key} type="button" className={form.method === key ? 'is-active' : ''} onClick={() => setForm({ ...form, method: key })}>{text}</button>)}</div>
        <Field label="Date"><input type="date" required value={form.paidOn} onChange={(event) => setForm({ ...form, paidOn: event.target.value })} /></Field>
        <Field label="Reference / UTR"><input maxLength="120" value={form.reference} onChange={(event) => setForm({ ...form, reference: event.target.value })} placeholder="Optional" /></Field>
        <Button type="submit" block busy={busy}>Record payment</Button>
      </form>
    </Sheet>
  );
}

function DownloadPolicy({ eventId, policy, onSaved }) {
  const [form, setForm] = useState({ mode: policy.mode, price: policy.price ?? '', unlocked: policy.unlocked });
  const [busy, run] = useAction();
  const dirty = form.mode !== policy.mode || String(form.price ?? '') !== String(policy.price ?? '') || form.unlocked !== policy.unlocked;
  return (
    <div className="m-card m-form">
      <div className="m-choice-row">
        {[['FREE', 'Free'], ['PAID', 'Paid'], ['DISABLED', 'Off']].map(([key, text]) => <button key={key} type="button" className={form.mode === key ? 'is-active' : ''} onClick={() => setForm({ ...form, mode: key })}>{text}</button>)}
      </div>
      {form.mode === 'PAID' && (
        <>
          <Field label="Download price (₹)"><input type="number" inputMode="decimal" min="1" value={form.price} onChange={(event) => setForm({ ...form, price: event.target.value })} /></Field>
          <label className="m-switch-row"><span><b>Payment received</b><small>Unlock downloads for the customer</small></span><input type="checkbox" className="m-switch" checked={form.unlocked} onChange={(event) => setForm({ ...form, unlocked: event.target.checked })} /></label>
        </>
      )}
      <p className="m-muted m-small">{form.mode === 'FREE' ? 'Customers can download their photos.' : form.mode === 'PAID' ? 'Recording a payment towards “Photo downloads” unlocks them automatically.' : 'Customers can view but not download.'}</p>
      {dirty && <Button block busy={busy} onClick={() => run(() => api.saveDownloadPolicy(eventId, { mode: form.mode, price: form.mode === 'PAID' ? Number(form.price) : null, unlocked: form.mode === 'PAID' && form.unlocked }), 'Download settings saved.').then((ok) => ok && onSaved())}>Save</Button>}
    </div>
  );
}

