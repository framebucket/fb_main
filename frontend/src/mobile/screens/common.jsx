import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api.js';
import { navigate, useRoute } from '../router.js';
import { Button, Chip, Field, Icon, Sheet, useAction } from '../ui.jsx';
import { dateParts, eventPrice, relativeDay, timeLabel, todayIso } from '../format.js';

// ------------------------------------------------------------------------------------- event card

export function EventCard({ event, balance, compact }) {
  const parts = dateParts(event.eventDate);
  const past = event.eventDate < todayIso();
  const route = useRoute();
  const selected = route.parts[0] === 'events' && route.parts[1] === String(event.id);
  return (
    <button type="button" className={`m-event-card${past ? ' is-past' : ''}${compact ? ' is-compact' : ''}${selected ? ' is-selected' : ''}`}
      aria-current={selected ? 'true' : undefined} onClick={() => navigate(`/events/${event.id}`)}>
      <span className="m-date-block">
        <small>{parts.month}</small>
        <b>{parts.day}</b>
        <small>{parts.weekday}</small>
      </span>
      <span className="m-event-text">
        <span className="m-event-title">{event.eventTypeName}</span>
        <span className="m-event-customer">{event.customerName}</span>
        {!compact && (
          <span className="m-event-meta">
            <span><Icon name="clock" size={14} />{timeLabel(event.eventTime) || relativeDay(event.eventDate)}</span>
            {event.locationName && <span><Icon name="pin" size={14} />{event.locationName}</span>}
          </span>
        )}
        <span className="m-event-chips">
          {!event.assigneeName && !past && <Chip tone="warn">Unassigned</Chip>}
          {event.assigneeName && <Chip icon="user">{event.assigneeName}</Chip>}
          {Number(balance) > 0 && (event.eventDate <= todayIso()
            ? <Chip tone="danger">Due {eventPrice(balance)}</Chip>
            : <Chip>Balance {eventPrice(balance)}</Chip>)}
        </span>
      </span>
    </button>
  );
}

// ------------------------------------------------------------------------------------- event types

export function EventTypeField({ value, onChange, eventTypes, onAddType }) {
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [busy, run] = useAction();
  if (adding) {
    return (
      <Field label="New event type">
        <span className="m-input-with-action">
          <input value={name} onChange={(event) => setName(event.target.value)} maxLength="80" placeholder="Pre-wedding shoot" autoFocus />
          <button type="button" disabled={busy || !name.trim()} onClick={async () => {
            const created = await run(() => api.createEventType(name.trim()));
            if (created) { onAddType(created); onChange(created.id); setAdding(false); setName(''); }
          }}>{busy ? '…' : 'Add'}</button>
        </span>
      </Field>
    );
  }
  return (
    <Field label="Event type">
      <select value={value} onChange={(event) => (event.target.value === '__new' ? setAdding(true) : onChange(event.target.value))} required>
        <option value="" disabled>Choose…</option>
        {eventTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}
        <option value="__new">＋ New event type…</option>
      </select>
    </Field>
  );
}

// ------------------------------------------------------------------------------------- new enquiry

export function NewEnquirySheet({ open, onClose, onCreated }) {
  const [eventTypes, setEventTypes] = useState([]);
  const [form, setForm] = useState({ name: '', mobileNumber: '', email: '', eventTypeId: '' });
  const [busy, run] = useAction();
  useEffect(() => {
    if (!open) return;
    api.eventTypes().then((types) => {
      setEventTypes(types);
      setForm((current) => ({ ...current, eventTypeId: current.eventTypeId || types[0]?.id || '' }));
    }).catch(() => {});
  }, [open]);
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  async function submit(event) {
    event.preventDefault();
    const created = await run(() => api.createEnquiry(form), `Enquiry created. The quote link is on its way to ${form.name.split(' ')[0]}.`);
    if (created) {
      setForm({ name: '', mobileNumber: '', email: '', eventTypeId: eventTypes[0]?.id || '' });
      onClose();
      onCreated?.(created);
    }
  }
  return (
    <Sheet open={open} title="New enquiry" onClose={onClose}>
      <form className="m-form" onSubmit={submit}>
        <p className="m-muted">They get an email and WhatsApp message with a link to build their own quote.</p>
        <Field label="Name"><input value={form.name} onChange={set('name')} autoComplete="name" required maxLength="120" placeholder="Customer's full name" /></Field>
        <Field label="Mobile number" hint="10 digits, +91 optional"><input type="tel" inputMode="tel" value={form.mobileNumber} onChange={set('mobileNumber')} required maxLength="13" pattern="([0-9]{10}|\+91[0-9]{10})" placeholder="9876543210" /></Field>
        <Field label="Email"><input type="email" inputMode="email" value={form.email} onChange={set('email')} required maxLength="320" placeholder="customer@example.com" /></Field>
        <EventTypeField value={form.eventTypeId} onChange={(eventTypeId) => setForm({ ...form, eventTypeId })} eventTypes={eventTypes} onAddType={(type) => setEventTypes([...eventTypes, type])} />
        <Button type="submit" block busy={busy} disabled={!form.eventTypeId}>Create enquiry</Button>
      </form>
    </Sheet>
  );
}

// ------------------------------------------------------------------------------------- new customer

export function NewCustomerSheet({ open, onClose, onCreated, initial }) {
  const empty = { name: '', email: '', mobileNumber: '', dateOfBirth: '', address: '' };
  const [form, setForm] = useState(empty);
  const [busy, run] = useAction();
  useEffect(() => { if (open) setForm({ ...empty, ...(initial || {}) }); // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);
  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  async function submit(event) {
    event.preventDefault();
    const created = await run(() => api.registerCustomer(form), (result) => `${result.name} added.`);
    if (created) { onClose(); onCreated?.(created); }
  }
  return (
    <Sheet open={open} title="New customer" onClose={onClose} tall>
      <form className="m-form" onSubmit={submit}>
        <Field label="Name"><input value={form.name} onChange={set('name')} autoComplete="name" required maxLength="120" /></Field>
        <Field label="Mobile number"><input type="tel" inputMode="tel" value={form.mobileNumber} onChange={set('mobileNumber')} required maxLength="13" pattern="([0-9]{10}|\+91[0-9]{10})" placeholder="9876543210" /></Field>
        <Field label="Email"><input type="email" inputMode="email" value={form.email} onChange={set('email')} required maxLength="320" /></Field>
        <Field label="Date of birth"><input type="date" value={form.dateOfBirth} onChange={set('dateOfBirth')} required /></Field>
        <Field label="Address" hint="Used for album and data hamper delivery"><textarea value={form.address} onChange={set('address')} required maxLength="1000" rows="3" /></Field>
        <Button type="submit" block busy={busy}>Save customer</Button>
      </form>
    </Sheet>
  );
}

// ------------------------------------------------------------------------------------- new event

export function NewEventSheet({ open, onClose, onCreated, customerId }) {
  const [setup, setSetup] = useState(null);
  const [form, setForm] = useState({ eventDate: '', eventTypeId: '', price: '', customerId: customerId || '' });
  const [addingCustomer, setAddingCustomer] = useState(false);
  const [busy, run] = useAction();
  useEffect(() => {
    if (!open) return;
    Promise.all([api.customers(), api.eventTypes(), api.blockedEventDates(), api.nextEventId().catch(() => null)])
      .then(([customers, eventTypes, blockedDates, next]) => {
        setSetup({ customers, eventTypes, blockedDates, nextId: next?.eventId });
        setForm((current) => ({ ...current, eventTypeId: current.eventTypeId || eventTypes[0]?.id || '', customerId: customerId || current.customerId }));
      })
      .catch(() => setSetup({ customers: [], eventTypes: [], blockedDates: [], nextId: null }));
  }, [open, customerId]);
  const blocked = useMemo(() => new Set(setup?.blockedDates || []), [setup]);
  const dateTaken = form.eventDate && blocked.has(form.eventDate);
  async function submit(event) {
    event.preventDefault();
    const created = await run(() => api.createEvent(form), (result) => `Event ${result.eventCode} added.`);
    if (created) { onClose(); onCreated?.(created); }
  }
  return (
    <>
      <Sheet open={open} title="New event" onClose={onClose} tall>
        {!setup ? <div className="m-sheet-loading"><span className="m-spinner" /></div> : (
          <form className="m-form" onSubmit={submit}>
            {setup.nextId && <p className="m-muted">This will be event <b>{setup.nextId}</b>.</p>}
            <Field label="Customer">
              <span className="m-input-with-action">
                <select value={form.customerId} onChange={(event) => setForm({ ...form, customerId: event.target.value })} required>
                  <option value="" disabled>Choose a customer…</option>
                  {setup.customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.name} · {customer.mobileNumber}</option>)}
                </select>
                <button type="button" onClick={() => setAddingCustomer(true)}>New</button>
              </span>
            </Field>
            <EventTypeField value={form.eventTypeId} onChange={(eventTypeId) => setForm({ ...form, eventTypeId })} eventTypes={setup.eventTypes} onAddType={(type) => setSetup({ ...setup, eventTypes: [...setup.eventTypes, type] })} />
            <Field label="Date" error={dateTaken ? 'You already have an event on this date.' : ''}>
              <input type="date" value={form.eventDate} onChange={(event) => setForm({ ...form, eventDate: event.target.value })} required />
            </Field>
            <Field label="Price" hint="Amount in ₹, or a note like “On request”"><input inputMode="decimal" value={form.price} onChange={(event) => setForm({ ...form, price: event.target.value })} maxLength="60" placeholder="150000" /></Field>
            <Button type="submit" block busy={busy} disabled={!form.customerId || !form.eventTypeId || !form.eventDate || dateTaken}>Create event</Button>
          </form>
        )}
      </Sheet>
      <NewCustomerSheet open={open && addingCustomer} onClose={() => setAddingCustomer(false)} onCreated={(customer) => {
        setSetup((current) => ({ ...current, customers: [customer, ...current.customers] }));
        setForm((current) => ({ ...current, customerId: customer.id }));
      }} />
    </>
  );
}

// ------------------------------------------------------------------------------------- pick an event

export function EventPickerSheet({ open, title, onClose, onPick }) {
  const [events, setEvents] = useState(null);
  useEffect(() => {
    if (open) api.events().then(setEvents).catch(() => setEvents([]));
  }, [open]);
  const today = todayIso();
  const sorted = useMemo(() => {
    const list = [...(events || [])];
    // Most relevant first: today and recent shoots, then upcoming, then older.
    const score = (event) => Math.abs(new Date(event.eventDate) - new Date(today)) + (event.eventDate > today ? 4 * 86400000 : 0);
    return list.sort((left, right) => score(left) - score(right));
  }, [events, today]);
  return (
    <Sheet open={open} title={title} onClose={onClose} tall>
      {!events ? <div className="m-sheet-loading"><span className="m-spinner" /></div> : !events.length ? <p className="m-muted">No events yet.</p> : (
        <div className="m-picker-list">
          {sorted.map((event) => (
            <button key={event.id} type="button" className="m-picker-row" onClick={() => onPick(event)}>
              <b>{event.eventTypeName} · {event.customerName}</b>
              <span>{relativeDay(event.eventDate)} · {event.eventCode}</span>
            </button>
          ))}
        </div>
      )}
    </Sheet>
  );
}
