import { useEffect, useState } from 'react';
import { api } from '../../api.js';
import { hasFeature, UpgradeNotice } from '../../Plans.jsx';
import { DeliveryTab, FilmsTab, ProofsTab } from '../../Delivery.jsx';
import { CrewTab, GearCostsTab } from '../../Crew.jsx';
import { navigate } from '../router.js';
import { useUser } from '../session.js';
import { ActionPill, Button, Chip, Empty, ErrorNote, Field, Icon, KeyValue, List, ProgressBar, Row, Screen, Section, Segmented, Sheet, Skeleton, useAction, useResource } from '../ui.jsx';
import { dayDate, eventPrice, inr, mapsLink, relativeDay, telLink, timeLabel, whatsappLink } from '../format.js';

const STEP_STATUS = [['NOT_STARTED', 'Not started'], ['IN_PROGRESS', 'In progress'], ['DONE', 'Done']];

export function useEvent(eventId) {
  return useResource(() => api.events().then((events) => {
    const event = events.find((item) => String(item.id) === String(eventId));
    if (!event) throw new Error('This event no longer exists.');
    return event;
  }), [eventId]);
}

export default function EventDetailScreen({ eventId }) {
  const { user } = useUser();
  const event = useEvent(eventId);
  const billing = useResource(() => (hasFeature(user, 'BILLING') ? api.eventBilling(eventId).catch(() => null) : Promise.resolve(null)), [eventId]);
  const steps = useResource(() => api.deliverySteps(eventId).catch(() => null), [eventId]);
  const folders = useResource(() => (user.dataModuleEnabled ? api.dataFolders(eventId).then((result) => result.folders || []).catch(() => null) : Promise.resolve(null)), [eventId]);
  const [sheet, setSheet] = useState('');
  const [step, setStep] = useState(null);
  const [busy, run] = useAction();

  const refresh = () => Promise.all([event.reload(), billing.reload(), steps.reload(), folders.reload()]);
  const data = event.data;
  const done = (steps.data || []).filter((item) => item.status === 'DONE').length;
  const photoCount = (folders.data || []).reduce((sum, folder) => sum + Number(folder.media_count ?? folder.photo_count ?? 0), 0);

  return (
    <Screen back backTo="/events" title={data?.eventTypeName || 'Event'} subtitle={data?.eventCode} onRefresh={refresh}
      actions={data && <button type="button" className="m-icon-button" aria-label="Event QR code" disabled={busy}
        onClick={() => run(() => api.downloadEventQrCode(data.id, data.eventCode))}><Icon name="qr" /></button>}>
      <ErrorNote message={event.error} onRetry={event.reload} />
      {!data ? <Skeleton rows={5} /> : (
        <>
          <div className="m-hero">
            <div className="m-hero-date">
              <Chip tone={relativeDay(data.eventDate) === 'Today' ? 'brass' : 'neutral'}>{relativeDay(data.eventDate)}</Chip>
              <h2>{dayDate(data.eventDate, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</h2>
              <p>{timeLabel(data.eventTime) || 'Time not set yet'}{data.locationName ? ` · ${data.locationName}` : ''}</p>
            </div>
            <button type="button" className="m-hero-customer" onClick={() => navigate(`/customers/${data.customerUuid}`)}>
              <span><small>Customer</small><b>{data.customerName}</b></span>
              <span className="m-hero-price"><small>Price</small><b>{eventPrice(data.price)}</b></span>
              <Icon name="chevron" size={18} />
            </button>
          </div>

          <CustomerActions event={data} />

          <Section title="Details">
            <List>
              <Row icon="user" title={data.assigneeName || 'Assign a photographer'} subtitle={data.assigneeName ? 'Assigned · tap to change' : 'They get a WhatsApp message with the details'} tone={data.assigneeName ? undefined : 'warn'} onClick={() => setSheet('assignee')} />
              <Row icon="clock" title={timeLabel(data.eventTime) || 'Time not shared yet'} subtitle="The customer sets this from their portal" />
              <Row icon="pin" multiline title={data.locationName || 'Location not shared yet'}
                subtitle={[data.locationLandmark && `Landmark: ${data.locationLandmark}`, data.locationNotes].filter(Boolean).join(' · ') || (data.locationName ? 'Open in Maps' : 'The customer adds this from their portal')}
                href={mapsLink(data) || undefined} />
              {data.eventGallery && <Row icon="folder" title="Designer folder" subtitle="Shared album design folder" href={data.eventGallery} />}
            </List>
          </Section>

          <Section title="Gallery">
            {user.dataModuleEnabled ? (
              <List>
                <Row icon="image" tone="brass" title="Photos & videos" subtitle={folders.data ? `${(folders.data || []).length} ${(folders.data || []).length === 1 ? 'folder' : 'folders'}${photoCount ? ` · ${photoCount} items` : ''}` : 'Open gallery'} onClick={() => navigate(`/events/${data.id}/gallery`)} />
                <Row icon="upload" title="Upload from this phone" onClick={() => navigate(`/events/${data.id}/gallery?upload=1`)} />
              </List>
            ) : <List><Row icon="image" title="Online galleries" subtitle="Not in your current plan" onClick={() => navigate('/more/plan')} /></List>}
          </Section>

          <Section title="Payments" action={billing.data && <button type="button" className="m-text-button" onClick={() => navigate(`/events/${data.id}/money`)}>Open</button>}>
            {billing.data ? (
              <button type="button" className="m-money-card" onClick={() => navigate(`/events/${data.id}/money`)}>
                <span><small>Received</small><b>{inr(billing.data.paid)}</b></span>
                <span><small>Balance</small><b className={Number(billing.data.balance) > 0 ? 'is-due' : 'is-paid'}>{Number(billing.data.balance) > 0 ? inr(billing.data.balance) : 'Paid'}</b></span>
                <span className="m-money-cta"><Icon name="plus" size={16} />Record</span>
              </button>
            ) : <List><Row icon="wallet" title="Payments & receipts" subtitle={hasFeature(user, 'BILLING') ? 'Loading…' : 'Not in your current plan'} onClick={() => navigate(hasFeature(user, 'BILLING') ? `/events/${data.id}/money` : '/more/plan')} /></List>}
          </Section>

          <Section title="Delivery" action={steps.data && <span className="m-section-note">{done}/{steps.data.length} done</span>}>
            {!steps.data ? <p className="m-muted m-pad">Loading…</p> : (
              <div className="m-card m-steps">
                <ProgressBar value={steps.data.length ? done / steps.data.length : 0} />
                {steps.data.map((item) => (
                  <button key={item.id} type="button" className={`m-step m-step-${item.status.toLowerCase()}`} onClick={() => setStep(item)}>
                    <span className="m-step-dot">{item.status === 'DONE' && <Icon name="check" size={14} stroke={3} />}</span>
                    <span className="m-step-text"><b>{item.title}</b><small>{STEP_STATUS.find(([key]) => key === item.status)?.[1]}{item.dueDate ? ` · due ${dayDate(item.dueDate, { day: 'numeric', month: 'short' })}` : ''}</small></span>
                    <Icon name="chevron" size={16} />
                  </button>
                ))}
                {!steps.data.length && <p className="m-muted">No delivery steps yet.</p>}
              </div>
            )}
          </Section>

          <Section title="More for this event">
            <List>
              <Row icon="album" title="Album design & proofs" subtitle="AI album design, send proofs, client comments" onClick={() => navigate(`/events/${data.id}/manage?tab=proofs`)} />
              <Row icon="film" title="Films" subtitle="Teasers, highlights and full films" onClick={() => navigate(`/events/${data.id}/manage?tab=films`)} />
              <Row icon="team" title="Crew" subtitle="Second shooters, call times, fees" onClick={() => navigate(`/events/${data.id}/manage?tab=crew`)} />
              <Row icon="box" title="Gear & costs" subtitle="Packing list, travel costs, profit" onClick={() => navigate(`/events/${data.id}/manage?tab=costs`)} />
              <Row icon="edit" title="Delivery checklist" subtitle="Add, rename or remove steps" onClick={() => navigate(`/events/${data.id}/manage?tab=delivery`)} />
            </List>
          </Section>
        </>
      )}
      {data && <AssigneeSheet open={sheet === 'assignee'} event={data} onClose={() => setSheet('')} onSaved={(updated) => event.setData(updated)} />}
      <StepSheet step={step} eventId={eventId} onClose={() => setStep(null)} onSaved={(list) => steps.setData(list)} />
    </Screen>
  );
}

function CustomerActions({ event }) {
  const customer = useResource(() => api.customer(event.customerUuid).catch(() => null), [event.customerUuid]);
  const mobile = customer.data?.mobileNumber;
  const maps = mapsLink(event);
  return (
    <div className="m-action-row">
      <ActionPill icon="phone" label="Call" href={mobile ? telLink(mobile) : undefined} />
      <ActionPill icon="chat" label="WhatsApp" tone="whatsapp" href={mobile ? whatsappLink(mobile, `Hi ${event.customerName.split(' ')[0]}, `) : undefined} />
      <ActionPill icon="pin" label="Directions" href={maps || undefined} />
      <ActionPill icon="wallet" label="Payments" onClick={() => navigate(`/events/${event.id}/money`)} />
    </div>
  );
}

function AssigneeSheet({ open, event, onClose, onSaved }) {
  const team = useResource(() => (open ? api.teamMembers() : Promise.resolve(null)), [open]);
  const current = event.assigneeId ? `${event.assigneeType}:${event.assigneeId}` : '';
  const [choice, setChoice] = useState(current);
  const [busy, run] = useAction();
  const active = (team.data || []).filter((member) => member.status === 'ACTIVE');
  async function save() {
    const [type, id] = choice ? choice.split(':') : [null, null];
    const updated = await run(() => api.assignEvent(event.id, type, id), 'Saved. They will get a WhatsApp message.');
    if (updated) { onSaved(updated); onClose(); }
  }
  return (
    <Sheet open={open} title="Assign photographer" onClose={onClose}
      footer={<Button block busy={busy} disabled={choice === current} onClick={save}>Save</Button>}>
      {!team.data ? <div className="m-sheet-loading"><span className="m-spinner" /></div> : (
        <div className="m-radio-list">
          <label className={choice === '' ? 'is-checked' : ''}><input type="radio" name="assignee" checked={choice === ''} onChange={() => setChoice('')} /><span><b>Unassigned</b></span></label>
          {active.map((member) => {
            const value = `${member.memberType}:${member.id}`;
            return (
              <label key={value} className={choice === value ? 'is-checked' : ''}>
                <input type="radio" name="assignee" checked={choice === value} onChange={() => setChoice(value)} />
                <span><b>{member.name}</b><small>{member.memberType === 'FREELANCER' ? 'Freelancer' : 'Team member'} · {member.mobileNumber}</small></span>
              </label>
            );
          })}
          {!active.length && <p className="m-muted">No active team members yet. Add them under More → Team.</p>}
        </div>
      )}
    </Sheet>
  );
}

function StepSheet({ step, eventId, onClose, onSaved }) {
  const [form, setForm] = useState(null);
  const [busy, run] = useAction();
  const open = Boolean(step);
  if (open && (!form || form.id !== step.id)) setForm({ ...step, note: step.note || '', dueDate: step.dueDate || '', notifyClient: false });
  if (!open && form) setForm(null);
  async function save() {
    const list = await run(() => api.updateDeliveryStep(eventId, step.id, { title: form.title, kind: step.kind, status: form.status, dueDate: form.dueDate || null, note: form.note, notifyClient: form.notifyClient }), form.notifyClient ? 'Saved and the client was emailed.' : 'Saved.');
    if (list) { onSaved(list); onClose(); }
  }
  return (
    <Sheet open={open} title={step?.title || 'Step'} onClose={onClose} footer={form && <Button block busy={busy} onClick={save}>Save</Button>}>
      {form && (
        <div className="m-form">
          <div className="m-choice-row">
            {STEP_STATUS.map(([key, label]) => (
              <button key={key} type="button" className={form.status === key ? 'is-active' : ''} onClick={() => setForm({ ...form, status: key, notifyClient: key === 'DONE' && step.status !== 'DONE' })}>{label}</button>
            ))}
          </div>
          <Field label="Due date"><input type="date" value={form.dueDate} onChange={(event) => setForm({ ...form, dueDate: event.target.value })} /></Field>
          <Field label="Note for the client"><input value={form.note} maxLength="500" onChange={(event) => setForm({ ...form, note: event.target.value })} placeholder="Optional" /></Field>
          {form.status === 'DONE' && step.status !== 'DONE' && (
            <label className="m-switch-row"><span><b>Email the client</b><small>Let them know this is ready</small></span><input type="checkbox" className="m-switch" checked={form.notifyClient} onChange={(event) => setForm({ ...form, notifyClient: event.target.checked })} /></label>
          )}
        </div>
      )}
    </Sheet>
  );
}

// ------------------------------------------------------------------------------------- full event tools

const MANAGE_TABS = [['proofs', 'Album'], ['films', 'Films'], ['crew', 'Crew'], ['costs', 'Gear & costs'], ['delivery', 'Delivery']];

export function EventManageScreen({ eventId, tab: initialTab }) {
  const { user } = useUser();
  const event = useEvent(eventId);
  const [tab, setTab] = useState(MANAGE_TABS.some(([key]) => key === initialTab) ? initialTab : 'proofs');
  const data = event.data;
  useEffect(() => { if (MANAGE_TABS.some(([key]) => key === initialTab)) setTab(initialTab); }, [initialTab]);
  return (
    <Screen back backTo={`/events/${eventId}`} title={data ? `${data.eventTypeName} · ${data.customerName}` : 'Event'} subtitle={data?.eventCode}>
      <div className="m-sticky-tools m-scroll-tabs">
        <Segmented value={tab} onChange={setTab} options={MANAGE_TABS} />
      </div>
      <ErrorNote message={event.error} />
      {!data ? <Skeleton /> : (
        <div className="m-embed" key={tab}>
          {tab === 'delivery' && <DeliveryTab eventId={data.id} />}
          {tab === 'films' && (hasFeature(user, 'FILMS') ? <FilmsTab eventId={data.id} user={user} /> : <UpgradeNotice feature="FILMS" title="Film delivery">Deliver teasers, highlights and full films alongside photos.</UpgradeNotice>)}
          {tab === 'proofs' && (hasFeature(user, 'ALBUM_PROOFING') ? <ProofsTab eventId={data.id} user={user} /> : <UpgradeNotice feature="ALBUM_PROOFING" title="Album proofing">Share album designs, collect comments per spread and get approval in one place.</UpgradeNotice>)}
          {tab === 'crew' && (hasFeature(user, 'CREW_PAYOUTS') ? <CrewTab eventId={data.id} /> : <UpgradeNotice feature="CREW_PAYOUTS" title="Crew scheduling">Assign second shooters and freelancers, spot clashes and track payouts.</UpgradeNotice>)}
          {tab === 'costs' && (hasFeature(user, 'GEAR_COSTS') ? <GearCostsTab eventId={data.id} /> : <UpgradeNotice feature="GEAR_COSTS" title="Gear & profit">Pack lists, travel costs and real profit per booking.</UpgradeNotice>)}
        </div>
      )}
    </Screen>
  );
}

export function EventNotFound() {
  return <Empty icon="calendar" title="Event not found" />;
}
