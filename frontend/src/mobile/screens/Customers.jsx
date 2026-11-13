import { useMemo, useState } from 'react';
import { api } from '../../api.js';
import { navigate, useRoute } from '../router.js';
import { ActionPill, Avatar, Button, Empty, ErrorNote, Fab, KeyValue, Row, Screen, Search, Section, Skeleton, Stat, useResource } from '../ui.jsx';
import { dayDate, inr, inrShort, plural, telLink, whatsappLink } from '../format.js';
import { EventCard, NewCustomerSheet, NewEventSheet } from './common.jsx';

export function CustomersScreen() {
  const data = useResource(() => api.customers(), []);
  const [query, setQuery] = useState('');
  const [creating, setCreating] = useState(false);
  const route = useRoute();
  const list = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return [...(data.data || [])]
      .filter((customer) => !needle || [customer.name, customer.mobileNumber, customer.email, customer.customerCode].some((value) => String(value || '').toLowerCase().includes(needle)))
      .sort((left, right) => left.name.localeCompare(right.name));
  }, [data.data, query]);

  return (
    <Screen back backTo="/more" title="Customers" subtitle={data.data ? plural(data.data.length, 'customer') : ''} onRefresh={data.reload}
      fab={<Fab label="New customer" onClick={() => setCreating(true)} />}>
      <div className="m-sticky-tools"><Search value={query} onChange={setQuery} placeholder="Search name, phone, email…" /></div>
      <ErrorNote message={data.error} onRetry={data.reload} />
      {!data.data ? <Skeleton /> : !list.length ? (
        query ? <Empty icon="search" title="No matches" /> : <Empty icon="users" title="No customers yet" action={<Button onClick={() => setCreating(true)}>Add a customer</Button>}>Customers are also created automatically when you book an enquiry.</Empty>
      ) : (
        <div className="m-list">
          {list.map((customer) => (
            <Row key={customer.id} selected={route.parts[1] === String(customer.id)} leading={<Avatar name={customer.name} />} onClick={() => navigate(`/customers/${customer.id}`)}
              title={customer.name} subtitle={customer.mobileNumber}
              meta={<span className="m-row-meta-stack"><b>{plural(customer.eventsBooked || 0, 'event')}</b>{Number(customer.totalRevenueGenerated) > 0 && <small>{inrShort(customer.totalRevenueGenerated)}</small>}</span>} />
          ))}
        </div>
      )}
      <NewCustomerSheet open={creating} onClose={() => setCreating(false)} onCreated={(customer) => { data.setData((current) => [customer, ...(current || [])]); navigate(`/customers/${customer.id}`); }} />
    </Screen>
  );
}

export function CustomerDetailScreen({ customerId }) {
  const customer = useResource(() => api.customer(customerId), [customerId]);
  const events = useResource(() => api.events().then((list) => list.filter((event) => String(event.customerUuid) === String(customerId)).sort((left, right) => right.eventDate.localeCompare(left.eventDate))), [customerId]);
  const [creating, setCreating] = useState(false);
  const data = customer.data;
  return (
    <Screen back backTo="/customers" title={data?.name || 'Customer'} subtitle={data?.customerCode} onRefresh={() => Promise.all([customer.reload(), events.reload()])}>
      <ErrorNote message={customer.error} onRetry={customer.reload} />
      {!data ? <Skeleton /> : (
        <>
          <div className="m-profile-head">
            <Avatar name={data.name} size={64} />
            <h2>{data.name}</h2>
            <p>{data.customerCode} · since {dayDate(data.createdAt, { month: 'short', year: 'numeric' })}</p>
          </div>
          <div className="m-action-row">
            <ActionPill icon="phone" label="Call" href={telLink(data.mobileNumber)} />
            <ActionPill icon="chat" label="WhatsApp" tone="whatsapp" href={whatsappLink(data.mobileNumber, `Hi ${data.name.split(' ')[0]}, `)} />
            <ActionPill icon="mail" label="Email" href={`mailto:${data.email}`} />
            <ActionPill icon="plus" label="New event" onClick={() => setCreating(true)} />
          </div>
          <div className="m-stat-row">
            <Stat label="Events" value={data.eventsBooked || 0} icon="calendar" />
            <Stat label="Total business" value={inr(data.totalRevenueGenerated || 0)} icon="rupee" tone="good" />
          </div>
          <Section title="Contact">
            <div className="m-card"><KeyValue items={[['Mobile', data.mobileNumber], ['Email', data.email], ['Birthday', dayDate(data.dateOfBirth, { day: 'numeric', month: 'long' })], ['Address', data.address]]} /></div>
          </Section>
          <Section title="Events">
            {!events.data ? <Skeleton rows={2} /> : events.data.length ? <div className="m-stack">{events.data.map((event) => <EventCard key={event.id} event={event} />)}</div>
              : <p className="m-muted m-pad">No events yet.</p>}
          </Section>
        </>
      )}
      <NewEventSheet open={creating} customerId={customerId} onClose={() => setCreating(false)} onCreated={(created) => navigate(`/events/${created.id}`)} />
    </Screen>
  );
}
