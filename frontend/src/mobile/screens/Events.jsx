import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api.js';
import { navigate } from '../router.js';
import { Empty, ErrorNote, Fab, Icon, Screen, Search, Segmented, Skeleton, useResource } from '../ui.jsx';
import { dayDate, isoDate, todayIso } from '../format.js';
import { EventCard, NewEventSheet } from './common.jsx';

function useEventsData() {
  return useResource(() => Promise.all([api.events(), api.financeSummary().catch(() => null)])
    .then(([events, finance]) => ({ events, balances: new Map((finance?.events || []).map((row) => [row.eventId, Number(row.balance)])) })), []);
}

function monthHeading(value) {
  return new Date(`${value.slice(0, 7)}-01T00:00:00`).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
}

export function EventsScreen({ view: initialView }) {
  const data = useEventsData();
  const [view, setView] = useState(initialView === 'past' || initialView === 'calendar' ? initialView : 'upcoming');
  const [query, setQuery] = useState('');
  const [creating, setCreating] = useState(false);
  const today = todayIso();
  useEffect(() => { if (['upcoming', 'past', 'calendar'].includes(initialView)) setView(initialView); }, [initialView]);

  const groups = useMemo(() => {
    const events = data.data?.events || [];
    const needle = query.trim().toLowerCase();
    const matches = events.filter((event) => !needle || [event.customerName, event.eventTypeName, event.eventCode, event.locationName, event.assigneeName]
      .some((value) => String(value || '').toLowerCase().includes(needle)));
    const list = view === 'past'
      ? matches.filter((event) => event.eventDate < today).sort((left, right) => right.eventDate.localeCompare(left.eventDate))
      : matches.filter((event) => event.eventDate >= today).sort((left, right) => left.eventDate.localeCompare(right.eventDate));
    const byMonth = [];
    for (const event of list) {
      const key = event.eventDate.slice(0, 7);
      if (!byMonth.length || byMonth[byMonth.length - 1].key !== key) byMonth.push({ key, events: [] });
      byMonth[byMonth.length - 1].events.push(event);
    }
    return { byMonth, upcomingCount: events.filter((event) => event.eventDate >= today).length, pastCount: events.filter((event) => event.eventDate < today).length };
  }, [data.data, query, view, today]);

  return (
    <Screen large title="Events" onRefresh={data.reload} fab={<Fab label="New event" onClick={() => setCreating(true)} />}>
      <div className="m-sticky-tools">
        <Segmented value={view} onChange={setView} options={[['upcoming', 'Upcoming', groups.upcomingCount], ['past', 'Past', groups.pastCount], ['calendar', 'Calendar']]} />
        {view !== 'calendar' && <Search value={query} onChange={setQuery} placeholder="Search customer, type, place…" />}
      </div>
      <ErrorNote message={data.error} onRetry={data.reload} />
      {view === 'calendar' ? <CalendarView events={data.data?.events} balances={data.data?.balances} /> : !data.data ? <Skeleton /> : groups.byMonth.length === 0 ? (
        query ? <Empty icon="search" title="No matches">Try a different name or event type.</Empty>
          : <Empty icon="calendar" title={view === 'past' ? 'No past events' : 'Nothing booked yet'} action={view === 'upcoming' && <button type="button" className="m-button m-button-primary" onClick={() => setCreating(true)}><span>Create an event</span></button>}>
            {view === 'upcoming' ? 'Events you create, or book from an enquiry, show up here.' : 'Completed shoots appear here.'}
          </Empty>
      ) : groups.byMonth.map((group) => (
        <section key={group.key} className="m-month-group">
          <h2 className="m-month-heading">{monthHeading(group.key)}</h2>
          <div className="m-stack">{group.events.map((event) => <EventCard key={event.id} event={event} balance={data.data.balances.get(event.id)} />)}</div>
        </section>
      ))}
      <NewEventSheet open={creating} onClose={() => setCreating(false)} onCreated={(created) => navigate(`/events/${created.id}`)} />
    </Screen>
  );
}

export function CalendarView({ events, balances }) {
  const [month, setMonth] = useState(() => { const now = new Date(); return new Date(now.getFullYear(), now.getMonth(), 1); });
  const [selected, setSelected] = useState(todayIso());
  const byDay = useMemo(() => {
    const map = new Map();
    for (const event of events || []) {
      if (!map.has(event.eventDate)) map.set(event.eventDate, []);
      map.get(event.eventDate).push(event);
    }
    return map;
  }, [events]);
  const cells = useMemo(() => {
    const offset = (month.getDay() + 6) % 7;
    const count = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
    const list = Array.from({ length: offset }, () => null);
    for (let day = 1; day <= count; day += 1) list.push(isoDate(new Date(month.getFullYear(), month.getMonth(), day)));
    while (list.length % 7) list.push(null);
    return list;
  }, [month]);
  const today = todayIso();
  const dayEvents = byDay.get(selected) || [];
  const monthCount = (events || []).filter((event) => event.eventDate.startsWith(isoDate(month).slice(0, 7))).length;

  if (!events) return <Skeleton />;
  return (
    <div className="m-calendar">
      <div className="m-calendar-card">
        <div className="m-calendar-head">
          <button type="button" className="m-icon-button" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))} aria-label="Previous month"><Icon name="back" /></button>
          <div><b>{month.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })}</b><span>{monthCount} {monthCount === 1 ? 'event' : 'events'}</span></div>
          <button type="button" className="m-icon-button" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))} aria-label="Next month"><Icon name="chevron" /></button>
        </div>
        <div className="m-calendar-grid">
          {['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((day, index) => <span key={index} className="m-calendar-weekday">{day}</span>)}
          {cells.map((date, index) => {
            if (!date) return <span key={`blank-${index}`} />;
            const count = (byDay.get(date) || []).length;
            const classes = ['m-calendar-day'];
            if (date === today) classes.push('is-today');
            if (date === selected) classes.push('is-selected');
            if (date < today) classes.push('is-past');
            return (
              <button key={date} type="button" className={classes.join(' ')} onClick={() => setSelected(date)} aria-label={`${dayDate(date)}, ${count} events`}>
                <span>{Number(date.slice(8))}</span>
                {count > 0 && <i className="m-calendar-dots">{Array.from({ length: Math.min(count, 3) }, (_, dot) => <em key={dot} />)}</i>}
              </button>
            );
          })}
        </div>
      </div>
      <h2 className="m-month-heading">{dayDate(selected, { weekday: 'long', day: 'numeric', month: 'long' })}</h2>
      {dayEvents.length ? <div className="m-stack">{dayEvents.map((event) => <EventCard key={event.id} event={event} balance={balances?.get(event.id)} />)}</div>
        : <p className="m-muted m-pad">Nothing booked on this day.</p>}
    </div>
  );
}

export function CalendarScreen() {
  const data = useEventsData();
  return (
    <Screen back title="Calendar" onRefresh={data.reload}>
      <ErrorNote message={data.error} onRetry={data.reload} />
      <CalendarView events={data.data?.events} balances={data.data?.balances} />
    </Screen>
  );
}
