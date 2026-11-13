import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api.js';
import { EnquiryBookingCell, useEnquiryTracking } from '../../Bookings.jsx';
import { navigate, useRoute } from '../router.js';
import { useEmbedClass } from '../layout.js';
import { useUser } from '../session.js';
import { ActionPill, Avatar, Button, Card, Chip, Empty, ErrorNote, Fab, Field, Icon, List, Row, Screen, Search, Section, Segmented, Sheet, Skeleton, useAction, useResource } from '../ui.jsx';
import { dayDate, inr, openFile, relativeDay, telLink, whatsappLink } from '../format.js';
import { NewEnquirySheet } from './common.jsx';

export function enquiryStage(enquiry) {
  if (enquiry.createdEventId) return { key: 'booked', label: 'Booked', tone: 'good' };
  if (enquiry.quotationId) return { key: 'quoted', label: enquiry.finalizedQuote != null ? 'Price agreed' : 'Quote received', tone: 'brass' };
  return { key: 'new', label: 'Waiting for quote', tone: 'neutral' };
}

export function EnquiriesScreen({ filter }) {
  const data = useResource(() => api.enquiries(), []);
  const [view, setView] = useState(['new', 'quoted', 'booked'].includes(filter) ? filter : 'all');
  const [query, setQuery] = useState('');
  const [creating, setCreating] = useState(false);
  const route = useRoute();
  useEffect(() => { if (['new', 'quoted', 'booked'].includes(filter)) setView(filter); }, [filter]);

  const counts = useMemo(() => {
    const result = { all: 0, new: 0, quoted: 0, booked: 0 };
    for (const enquiry of data.data || []) { result.all += 1; result[enquiryStage(enquiry).key] += 1; }
    return result;
  }, [data.data]);

  const list = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return (data.data || [])
      .filter((enquiry) => view === 'all' || enquiryStage(enquiry).key === view)
      .filter((enquiry) => !needle || [enquiry.name, enquiry.eventName, enquiry.mobileNumber, enquiry.email, enquiry.enquiryId].some((value) => String(value || '').toLowerCase().includes(needle)));
  }, [data.data, view, query]);

  return (
    <Screen large title="Enquiries" onRefresh={data.reload} fab={<Fab label="New enquiry" onClick={() => setCreating(true)} />}>
      <div className="m-sticky-tools">
        <Segmented value={view} onChange={setView} options={[['all', 'All', counts.all], ['new', 'New', counts.new], ['quoted', 'Quoted', counts.quoted], ['booked', 'Booked', counts.booked]]} />
        <Search value={query} onChange={setQuery} placeholder="Search name, phone, event…" />
      </div>
      <ErrorNote message={data.error} onRetry={data.reload} />
      {!data.data ? <Skeleton /> : !list.length ? (
        query ? <Empty icon="search" title="No matches" />
          : <Empty icon="inbox" title={view === 'all' ? 'No enquiries yet' : 'Nothing here'} action={view === 'all' && <Button onClick={() => setCreating(true)}>New enquiry</Button>}>
            {view === 'all' ? 'Add an enquiry and the customer gets a link to build their own quote. Leads from your enquiry form land here too.' : 'Enquiries move here as they progress.'}
          </Empty>
      ) : (
        <div className="m-list">
          {list.map((enquiry) => {
            const stage = enquiryStage(enquiry);
            const amount = enquiry.finalizedQuote ?? enquiry.quotationPrice;
            return (
              <Row key={enquiry.id} selected={route.parts[1] === String(enquiry.id)} leading={<Avatar name={enquiry.name} />} onClick={() => navigate(`/enquiries/${enquiry.id}`)}
                title={enquiry.name}
                subtitle={<>{enquiry.eventName}{enquiry.createdAt ? ` · ${relativeDay(enquiry.createdAt)}` : ''}</>}
                meta={<span className="m-row-meta-stack">{amount != null && <b>{inr(amount)}</b>}<Chip tone={stage.tone}>{stage.label}</Chip></span>}
                chevron={false} />
            );
          })}
        </div>
      )}
      <NewEnquirySheet open={creating} onClose={() => setCreating(false)} onCreated={(created) => { data.setData((current) => [created, ...(current || [])]); navigate(`/enquiries/${created.id}`); }} />
    </Screen>
  );
}

export function EnquiryDetailScreen({ enquiryId }) {
  const { user } = useUser();
  const data = useResource(() => api.enquiries().then((list) => {
    const found = list.find((item) => String(item.id) === String(enquiryId));
    if (!found) throw new Error('This enquiry no longer exists.');
    return found;
  }), [enquiryId]);
  const tracking = useEnquiryTracking(enquiryId);
  const embedClass = useEmbedClass();
  const enquiry = data.data;
  const [finalQuote, setFinalQuote] = useState(null);
  const [creatingEvent, setCreatingEvent] = useState(false);
  const [busy, run] = useAction();

  if (!enquiry) {
    return <Screen back backTo="/enquiries" title="Enquiry"><ErrorNote message={data.error} onRetry={data.reload} />{!data.error && <Skeleton />}</Screen>;
  }
  const stage = enquiryStage(enquiry);
  const draft = finalQuote ?? (enquiry.finalizedQuote != null ? String(enquiry.finalizedQuote) : '');
  const saved = enquiry.finalizedQuote != null && draft !== '' && Number(draft) === Number(enquiry.finalizedQuote);

  async function saveQuote() {
    if (draft === '' || !Number.isFinite(Number(draft)) || Number(draft) < 0) return;
    const result = await run(() => api.saveEnquiryFinalizedQuote(enquiry.id, Number(draft)), 'Final price saved.');
    if (result) { data.setData({ ...enquiry, finalizedQuote: result.finalizedQuote }); setFinalQuote(null); }
  }

  return (
    <Screen back backTo="/enquiries" title={enquiry.name} subtitle={enquiry.eventName} onRefresh={() => Promise.all([data.reload(), tracking.reload()])}>
      <div className="m-profile-head">
        <Avatar name={enquiry.name} size={64} />
        <h2>{enquiry.name}</h2>
        <p>{enquiry.eventName}</p>
        <Chip tone={stage.tone}>{stage.label}</Chip>
      </div>
      <div className="m-action-row">
        <ActionPill icon="phone" label="Call" href={telLink(enquiry.mobileNumber)} />
        <ActionPill icon="chat" label="WhatsApp" tone="whatsapp" href={whatsappLink(enquiry.mobileNumber, `Hi ${enquiry.name.split(' ')[0]}, `)} />
        <ActionPill icon="mail" label="Email" href={`mailto:${enquiry.email}`} />
      </div>

      <Section title="Quote">
        <Card className="m-quote-card">
          <div className="m-quote-line">
            <span><small>Customer's quote</small><b>{enquiry.quotationId ? inr(enquiry.quotationPrice) : 'Not built yet'}</b></span>
            {enquiry.quotationId && <button type="button" className="m-text-button" disabled={busy} onClick={() => run(() => openFile(api.enquiryQuotationUrl(enquiry.id), `Quotation-${enquiry.enquiryId}.pdf`))}><Icon name="file" size={16} />PDF</button>}
          </div>
          {!enquiry.quotationId && <p className="m-muted m-small">They got a link to choose services and see the price. You'll see it here once they submit.</p>}
          {enquiry.createdEventId ? (
            <div className="m-quote-line">
              <span><small>Final price</small><b>{inr(enquiry.finalizedQuote)}</b></span>
              <button type="button" className="m-text-button" disabled={busy} onClick={() => run(() => openFile(api.enquiryFinalizedQuotationUrl(enquiry.id), `Final-quotation-${enquiry.enquiryId}.pdf`))}><Icon name="file" size={16} />PDF</button>
            </div>
          ) : enquiry.quotationId && (
            <Field label="Final agreed price (₹)" hint={saved ? 'Saved. You can create the event now.' : 'Save the price you agreed with the customer.'}>
              <span className="m-input-with-action">
                <input type="number" inputMode="decimal" min="0" step="0.01" value={draft} onChange={(event) => setFinalQuote(event.target.value)} />
                <button type="button" disabled={busy || draft === '' || saved} onClick={saveQuote}>{saved ? 'Saved' : 'Save'}</button>
              </span>
            </Field>
          )}
        </Card>
      </Section>

      <Section title="Follow-up & contract">
        <Card className={`${embedClass} m-embed-compact`}>
          <EnquiryBookingCell enquiry={enquiry} tracking={tracking.map[enquiry.id]} user={user} onChanged={tracking.reload} />
        </Card>
      </Section>

      <Section title="Details">
        <List>
          <Row icon="phone" title={enquiry.mobileNumber} subtitle="Mobile" href={telLink(enquiry.mobileNumber)} chevron={false} />
          <Row icon="mail" title={enquiry.email} subtitle="Email" href={`mailto:${enquiry.email}`} chevron={false} />
          {enquiry.createdAt && <Row icon="clock" title={dayDate(enquiry.createdAt)} subtitle="Received" />}
          {enquiry.convertedCustomerCode && <Row icon="user" title={`Customer ${enquiry.convertedCustomerCode}`} subtitle="Linked customer record" />}
        </List>
      </Section>

      <div className="m-pad">
        {enquiry.createdEventId ? <Button block icon="calendar" onClick={() => navigate(`/events/${enquiry.createdEventId}`)}>Open event {enquiry.createdEventCode}</Button>
          : <Button block icon="calendar" disabled={!saved} onClick={() => setCreatingEvent(true)}>{enquiry.quotationId ? (saved ? 'Book it: create the event' : 'Save the final price to book') : 'Book after the customer quotes'}</Button>}
      </div>

      <CreateEventSheet open={creatingEvent} enquiry={enquiry} onClose={() => setCreatingEvent(false)} onCreated={(result) => {
        data.setData({ ...enquiry, finalizedQuote: result.finalizedQuote, convertedCustomerCode: result.customerCode, createdEventId: result.eventId, createdEventCode: result.eventCode });
        navigate(`/events/${result.eventId}`);
      }} />
    </Screen>
  );
}

function CreateEventSheet({ open, enquiry, onClose, onCreated }) {
  const setup = useResource(() => (open ? api.enquiryEventSetup(enquiry.id) : Promise.resolve(null)), [open, enquiry.id]);
  const [form, setForm] = useState({ eventDate: '', dateOfBirth: '', address: '' });
  const [busy, run] = useAction();
  const info = setup.data;
  const blocked = new Set(info?.blockedDates || []);
  const date = form.eventDate || (info?.suggestedEventDate && !blocked.has(info.suggestedEventDate) ? info.suggestedEventDate : '');
  async function submit(event) {
    event.preventDefault();
    const result = await run(() => api.createEventFromEnquiry(enquiry.id, { ...form, eventDate: date }), (created) => `Event ${created.eventCode} created.`);
    if (result) { onClose(); onCreated(result); }
  }
  return (
    <Sheet open={open} title="Create the event" onClose={onClose} tall>
      {!info ? <div className="m-sheet-loading">{setup.error ? <p className="m-form-error">{setup.error}</p> : <span className="m-spinner" />}</div> : (
        <form className="m-form" onSubmit={submit}>
          <p className="m-muted">{info.eventTypeName} for {enquiry.name} at {inr(enquiry.finalizedQuote)}. The quotation PDF is regenerated with this price.</p>
          <Field label="Event date" error={date && blocked.has(date) ? 'You already have an event on this date.' : ''}>
            <input type="date" value={date} onChange={(event) => setForm({ ...form, eventDate: event.target.value })} required />
          </Field>
          {info.customerExists ? <p className="m-muted">Existing customer {info.existingCustomerCode} will be linked.</p> : (
            <>
              <p className="m-muted">This creates their customer record too.</p>
              <Field label="Date of birth"><input type="date" value={form.dateOfBirth} onChange={(event) => setForm({ ...form, dateOfBirth: event.target.value })} required /></Field>
              <Field label="Address"><textarea rows="3" maxLength="1000" value={form.address} onChange={(event) => setForm({ ...form, address: event.target.value })} required /></Field>
            </>
          )}
          <Button type="submit" block busy={busy} disabled={!date || blocked.has(date) || (!info.customerExists && (!form.dateOfBirth || !form.address.trim()))}>Create event</Button>
        </form>
      )}
    </Sheet>
  );
}
