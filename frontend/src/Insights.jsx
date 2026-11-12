import { api } from './api.js';
import { UpgradeNotice, hasFeature } from './Plans.jsx';
import { PanelIntro, Stat, inr, shortDate, useLoad } from './Studio.jsx';
import { SOURCE_LABELS } from './Bookings.jsx';
import './growth.css';

// Business insights: bookings and shoots by month, seasonality, lead sources, AOV, outstanding balances, profit.

function Bars({ title, items, format = (v) => v, valueKey = 'count', highlightLast }) {
  const max = Math.max(1, ...items.map((item) => Number(item[valueKey])));
  return (
    <section className="growth-chart">
      <h3 className="studio-h3">{title}</h3>
      <div className="growth-bars" role="img" aria-label={title}>
        {items.map((item, index) => {
          const value = Number(item[valueKey]);
          return (
            <div key={item.month} className={`growth-bar${highlightLast && index === items.length - 1 ? ' is-current' : ''}`} title={`${item.month}: ${format(value)}`}>
              <span className="growth-bar-value">{value ? format(value) : ''}</span>
              <span className="growth-bar-fill" style={{ height: `${Math.max(value ? 4 : 1, (value / max) * 100)}%` }} />
              <span className="growth-bar-label">{item.month}</span>
            </div>
          );
        })}
      </div>
    </section>
  );
}

const compactInr = (value) => (value >= 100000 ? `₹${(value / 100000).toFixed(value >= 1000000 ? 0 : 1)}L` : value >= 1000 ? `₹${Math.round(value / 1000)}k` : `₹${value}`);

export function InsightsPanel({ user }) {
  const insights = useLoad(() => (hasFeature(user, 'INSIGHTS') ? api.insights() : Promise.resolve(null)), []);
  if (!hasFeature(user, 'INSIGHTS')) {
    return <section className="event-module studio-panel"><UpgradeNotice feature="INSIGHTS" title="Business insights">See your busiest months, which lead sources book, average order value, outstanding balances and profit per shoot.</UpgradeNotice></section>;
  }
  const d = insights.data;
  return (
    <section className="event-module studio-panel">
      <PanelIntro eyebrow="Last 12 months" title="Business" accent="insights.">Where your bookings come from, when you're busiest, and what each shoot really earns.</PanelIntro>
      {!d ? <p className="customer-empty">{insights.error || 'Crunching your numbers…'}</p> : (
        <div className="growth-stack">
          <div className="studio-stats">
            <Stat label="Enquiries" value={d.enquiries12m} />
            <Stat label="Enquiry → booking" value={`${d.conversionPercent}%`} tone={Number(d.conversionPercent) >= 30 ? 'good' : undefined} />
            <Stat label="Average order value" value={inr(d.averageOrderValue)} />
            <Stat label="Received" value={inr(d.revenue12m)} tone="good" />
            <Stat label="Costs (expenses + crew paid)" value={inr(d.costs12m)} />
            <Stat label="Net" value={inr(d.profit12m)} tone={Number(d.profit12m) >= 0 ? 'good' : 'warn'} />
            <Stat label="Outstanding balances" value={inr(d.outstandingTotal)} tone={Number(d.outstandingTotal) > 0 ? 'warn' : 'good'} />
          </div>
          <div className="growth-chart-grid">
            <Bars title="Bookings made per month" items={d.bookingsByMonth} highlightLast />
            <Bars title="Shoots per month (6 months back, 6 ahead)" items={d.shootsByMonth} />
            <Bars title="Money received per month" items={d.revenueByMonth} valueKey="amount" format={compactInr} highlightLast />
            <Bars title="Seasonality (all shoots by month)" items={d.seasonality} />
          </div>
          <section className="customer-list-section">
            <h3 className="studio-h3">Lead sources</h3>
            {!d.sources.length ? <p className="customer-empty">No enquiries in the last 12 months. Share your enquiry links from Settings.</p> : (
              <div className="customer-table-scroll"><table className="customer-table growth-table"><thead><tr><th>Source</th><th>Enquiries</th><th>Booked</th><th>Conversion</th></tr></thead>
                <tbody>{d.sources.map((s) => (
                  <tr key={s.source}><td><span className={`growth-source growth-source-${s.source.toLowerCase()}`}>{SOURCE_LABELS[s.source] || s.source}</span></td><td>{s.enquiries}</td><td>{s.booked}</td>
                    <td><div className="growth-meter"><span style={{ width: `${s.conversionPercent}%` }} /></div>{s.conversionPercent}%</td></tr>
                ))}</tbody></table></div>
            )}
          </section>
          <div className="studio-billing-grid">
            <section className="customer-list-section">
              <h3 className="studio-h3">Outstanding balances</h3>
              {!d.outstanding.length ? <p className="customer-empty">Everyone's paid up. 🎉</p> : (
                <ul className="studio-receipts">{d.outstanding.map((o) => (
                  <li key={o.eventId}><div><b>{o.customerName} · {inr(o.balance)} due</b><span>{o.eventCode} · {o.eventType} · {shortDate(o.eventDate)} · paid {inr(o.received)} of {inr(o.agreed)}</span></div></li>
                ))}</ul>
              )}
            </section>
            <section className="customer-list-section">
              <h3 className="studio-h3">Profit per shoot</h3>
              {!d.profitByEvent.length ? <p className="customer-empty">Completed shoots from the last 12 months appear here.</p> : (
                <div className="customer-table-scroll"><table className="customer-table growth-table growth-table-compact"><thead><tr><th>Shoot</th><th>Price</th><th>Costs</th><th>Profit</th></tr></thead>
                  <tbody>{d.profitByEvent.map((e) => (
                    <tr key={e.eventId}><td><b>{e.customerName}</b><span className="studio-cell-sub">{e.eventType} · {shortDate(e.eventDate)}</span></td><td>{inr(e.agreed)}</td><td>{inr(e.costs)}</td>
                      <td className={Number(e.profit) >= 0 ? 'studio-paid' : 'studio-due'}>{inr(e.profit)}</td></tr>
                  ))}</tbody></table></div>
              )}
              <p className="event-form-note">Costs include expenses linked to the event and crew fees. Add them from Events → Manage → Gear &amp; costs.</p>
            </section>
          </div>
        </div>
      )}
    </section>
  );
}
