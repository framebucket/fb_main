import { useMemo, useState } from 'react';
import { api } from '../../api.js';
import { navigate } from '../router.js';
import { useUser } from '../session.js';
import { Avatar, Card, ErrorNote, Icon, Layer, List, QuickAction, Row, Screen, Section, Skeleton, Stat, useResource } from '../ui.jsx';
import { inr, inrShort, plural, relativeDay, todayIso } from '../format.js';
import { EventCard, EventPickerSheet, NewEnquirySheet, NewEventSheet } from './common.jsx';

function greeting() {
  const hour = new Date().getHours();
  return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
}

export default function HomeScreen() {
  const { user } = useUser();
  const data = useResource(() => Promise.all([
    api.events(),
    api.enquiries().catch(() => []),
    api.financeSummary().catch(() => null),
    api.dashboardSummary().catch(() => null),
  ]).then(([events, enquiries, finance, summary]) => ({ events, enquiries, finance, summary })), []);
  const [sheet, setSheet] = useState('');
  const today = todayIso();
  const firstName = (user.fullName || '').split(' ')[0];

  const view = useMemo(() => {
    if (!data.data) return null;
    const { events, enquiries, finance } = data.data;
    const balances = new Map((finance?.events || []).map((row) => [row.eventId, Number(row.balance)]));
    const sorted = [...events].sort((left, right) => left.eventDate.localeCompare(right.eventDate) || String(left.eventTime || '').localeCompare(String(right.eventTime || '')));
    const todays = sorted.filter((event) => event.eventDate === today);
    const upcoming = sorted.filter((event) => event.eventDate > today).slice(0, 5);
    const inTwoWeeks = new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10);
    const unassigned = sorted.filter((event) => event.eventDate >= today && event.eventDate <= inTwoWeeks && !event.assigneeName);
    const waitingQuote = enquiries.filter((enquiry) => !enquiry.quotationId && !enquiry.createdEventId);
    const readyToBook = enquiries.filter((enquiry) => enquiry.quotationId && !enquiry.createdEventId);
    const pastDue = (finance?.events || []).filter((row) => Number(row.balance) > 0 && row.eventDate < today);
    return { balances, todays, upcoming, unassigned, waitingQuote, readyToBook, pastDue };
  }, [data.data, today]);

  const finance = data.data?.finance;
  const summary = data.data?.summary;

  return (
    <Screen large title={`${greeting()}, ${firstName}`} subtitle={user.studioName || 'Your studio today'} onRefresh={data.reload}
      actions={<button type="button" className="m-avatar-button" onClick={() => navigate('/more')} aria-label="Account"><Avatar name={user.fullName} size={34} /></button>}>
      <ErrorNote message={data.error} onRetry={data.reload} />
      <div className="m-quick-grid">
        <QuickAction icon="inbox" label="Enquiry" onClick={() => setSheet('enquiry')} />
        <QuickAction icon="calendar" label="Event" onClick={() => setSheet('event')} />
        <QuickAction icon="upload" label="Upload" onClick={() => setSheet(user.dataModuleEnabled ? 'upload' : 'upgrade')} />
        <QuickAction icon="rupee" label="Payment" onClick={() => setSheet('payment')} />
      </div>

      {!view ? <Skeleton rows={3} /> : (
        <>
          <Section title="Today" action={<span className="m-section-note">{new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}</span>}>
            {view.todays.length ? (
              <div className="m-stack">{view.todays.map((event) => <EventCard key={event.id} event={event} balance={view.balances.get(event.id)} />)}</div>
            ) : (
              <Card className="m-today-empty">
                <Icon name="sparkle" size={22} />
                <div>
                  <b>No shoots today</b>
                  <span>{view.upcoming[0] ? `Next: ${view.upcoming[0].eventTypeName} for ${view.upcoming[0].customerName}, ${relativeDay(view.upcoming[0].eventDate).toLowerCase()}.` : 'Nothing scheduled yet.'}</span>
                </div>
              </Card>
            )}
          </Section>

          <div className="m-stat-row">
            <Stat icon="wallet" label="Outstanding" value={finance ? inrShort(finance.outstanding) : '—'} tone={finance && Number(finance.outstanding) > 0 ? 'warn' : undefined} onClick={() => navigate('/money')} />
            <Stat icon="rupee" label="Received" value={finance ? inrShort(finance.received) : '—'} tone="good" onClick={() => navigate('/money')} />
            <Stat icon="chart" label="Booked this month" value={summary ? inrShort(summary.revenueThisMonth) : '—'} onClick={() => navigate('/more/insights')} />
          </div>

          {(view.waitingQuote.length + view.readyToBook.length + view.unassigned.length + view.pastDue.length) > 0 && (
            <Section title="Needs your attention">
              <List>
                {view.readyToBook.length > 0 && <Row icon="file" tone="brass" title={`${plural(view.readyToBook.length, 'quote')} ready to book`} subtitle="Agree the final price and create the event" onClick={() => navigate('/enquiries?filter=quoted')} />}
                {view.waitingQuote.length > 0 && <Row icon="inbox" title={`${plural(view.waitingQuote.length, 'enquiry', 'enquiries')} waiting for a quote`} subtitle="The customer hasn't built their quote yet" onClick={() => navigate('/enquiries?filter=new')} />}
                {view.unassigned.length > 0 && <Row icon="user" tone="warn" title={`${plural(view.unassigned.length, 'shoot')} without a photographer`} subtitle="In the next two weeks" onClick={() => navigate(`/events/${view.unassigned[0].id}`)} />}
                {view.pastDue.length > 0 && <Row icon="wallet" tone="danger" title={`${plural(view.pastDue.length, 'balance')} due`} subtitle={`${inr(view.pastDue.reduce((sum, row) => sum + Number(row.balance), 0))} from past shoots`} onClick={() => navigate('/money?view=due')} />}
              </List>
            </Section>
          )}

          <Section title="Coming up" action={<button type="button" className="m-text-button" onClick={() => navigate('/events')}>All events</button>}>
            {view.upcoming.length ? <div className="m-stack">{view.upcoming.map((event) => <EventCard key={event.id} event={event} balance={view.balances.get(event.id)} />)}</div>
              : <p className="m-muted m-pad">No upcoming events. Tap “Event” above to add one.</p>}
          </Section>
        </>
      )}

      <NewEnquirySheet open={sheet === 'enquiry'} onClose={() => setSheet('')} onCreated={(created) => navigate(`/enquiries/${created.id}`)} />
      <NewEventSheet open={sheet === 'event'} onClose={() => setSheet('')} onCreated={(created) => navigate(`/events/${created.id}`)} />
      <EventPickerSheet open={sheet === 'upload'} title="Upload photos to…" onClose={() => setSheet('')} onPick={(event) => { setSheet(''); navigate(`/events/${event.id}/gallery?upload=1`); }} />
      <EventPickerSheet open={sheet === 'payment'} title="Record a payment for…" onClose={() => setSheet('')} onPick={(event) => { setSheet(''); navigate(`/events/${event.id}/money?record=1`); }} />
      {sheet === 'upgrade' && <UpgradeSheet onClose={() => setSheet('')} />}
    </Screen>
  );
}

function UpgradeSheet({ onClose }) {
  return (
    <Layer>
    <div className="m-sheet-backdrop" role="presentation" onClick={onClose}>
      <section className="m-sheet" role="dialog" aria-modal="true">
        <span className="m-sheet-grip" />
        <div className="m-sheet-body">
          <h2 className="m-sheet-title">Online galleries</h2>
          <p className="m-muted">Uploading photos and sharing galleries isn't part of your current plan. See plans under More → Plan &amp; usage.</p>
          <button type="button" className="m-button m-button-primary is-block" onClick={() => navigate('/more/plan')}>See plans</button>
        </div>
      </section>
    </div>
    </Layer>
  );
}
