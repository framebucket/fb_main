import { useEffect, useRef, useState } from 'react';
import { api } from './api.js';
import { UpgradeNotice, hasFeature } from './Plans.jsx';
import { Modal, PanelIntro, inr, shortDate, useLoad } from './Studio.jsx';
import './growth.css';

// Bookings: per-studio packages & add-ons, enquiry links (lead capture), quote follow-ups,
// e-signed booking agreements with advance collection, and the public enquiry form.

export const SOURCE_LABELS = {
  MANUAL: 'Added by you', WEBSITE: 'Website', INSTAGRAM: 'Instagram', WHATSAPP: 'WhatsApp', FACEBOOK: 'Facebook',
  GOOGLE: 'Google', REFERRAL: 'Referral', OTHER: 'Enquiry form',
};

/** CSS variable overrides so client-facing pages use the studio's brand colour. */
export function brandStyle(brand) {
  if (!brand?.color) return undefined;
  return { '--fb-brass': brand.color, '--fb-brass-strong': brand.color, '--fb-brass-line': `${brand.color}66`, '--fb-brass-soft': `${brand.color}1f` };
}

export function StudioMark({ brand, fallback }) {
  if (brand?.logoUrl) return <img className="growth-studio-logo" src={api.assetUrl(brand.logoUrl)} alt={brand.studioName || ''} />;
  return fallback || null;
}

function CopyField({ label, value, multiline }) {
  const [copied, setCopied] = useState(false);
  async function copy() {
    try { await navigator.clipboard.writeText(value); setCopied(true); setTimeout(() => setCopied(false), 1600); } catch { /* clipboard blocked */ }
  }
  return (
    <div className="growth-copy">
      <span>{label}</span>
      <div>
        {multiline ? <textarea readOnly rows="3" value={value} onFocus={(e) => e.target.select()} /> : <input readOnly value={value} onFocus={(e) => e.target.select()} />}
        <button className="button button-quiet button-small" type="button" onClick={copy}>{copied ? 'Copied' : 'Copy'}</button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Settings: packages & pricing, enquiry links
// ---------------------------------------------------------------------------------------------

export function QuoteCatalogSettings({ user }) {
  const catalog = useLoad(() => api.quoteCatalog(), []);
  const [data, setData] = useState(null);
  const [settings, setSettings] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [newItem, setNewItem] = useState({ kind: 'ADDON', name: '', description: '', price: '' });
  const [packageForm, setPackageForm] = useState(null);
  const canEdit = hasFeature(user, 'CUSTOM_PACKAGES');

  useEffect(() => {
    if (!catalog.data) return;
    setData(catalog.data);
    setSettings({ albumSheetPrice: catalog.data.albumSheetPrice, albumSheetsPerEvent: catalog.data.albumSheetsPerEvent ?? 30, discountPercent: catalog.data.discountPercent, advancePercent: catalog.data.advancePercent, contractTerms: catalog.data.contractTerms });
  }, [catalog.data]);

  async function run(task, done) {
    setBusy(true); setError(''); setNotice('');
    try { const result = await task(); setData(result); setNotice(done); return true; } catch (requestError) { setError(requestError.message); return false; } finally { setBusy(false); }
  }

  if (!data || !settings) return <p className="customer-empty">{catalog.error || 'Loading your catalogue…'}</p>;

  const itemRow = (item) => <CatalogItemRow key={item.id} item={item} disabled={!canEdit || busy}
    onSave={(body) => run(() => api.updateQuoteItem(item.id, body), `${body.name} saved.`)}
    onRemove={() => run(() => api.removeQuoteItem(item.id), `${item.name} switched off.`)} />;

  return (
    <div className="growth-stack">
      {error && <p className="form-error customer-message" role="alert">{error}</p>}
      {notice && <p className="form-success customer-message" role="status">{notice}</p>}
      <form className="customer-list-section studio-settings" onSubmit={(e) => { e.preventDefault(); run(() => api.saveQuoteSettings({ ...settings, albumSheetPrice: Number(settings.albumSheetPrice), albumSheetsPerEvent: Number(settings.albumSheetsPerEvent) || 30, discountPercent: Number(settings.discountPercent), advancePercent: Number(settings.advancePercent) }), 'Pricing settings saved.'); }}>
        <fieldset><legend>Pricing</legend>
          <label className="event-form-label">Album price per sheet (₹)<input type="number" min="0" step="1" value={settings.albumSheetPrice} onChange={(e) => setSettings({ ...settings, albumSheetPrice: e.target.value })} disabled={!canEdit} /><small className="field-hint">Never shown to customers.</small></label>
          <label className="event-form-label">Album sheets per event<input type="number" min="1" max="200" step="1" value={settings.albumSheetsPerEvent} onChange={(e) => setSettings({ ...settings, albumSheetsPerEvent: e.target.value })} disabled={!canEdit} /><small className="field-hint">When a customer ticks “Album”, each event adds {Number(settings.albumSheetsPerEvent) || 30} sheets = {inr((Number(settings.albumSheetsPerEvent) || 30) * (Number(settings.albumSheetPrice) || 0))} per event.</small></label>
          <label className="event-form-label">Standard discount on quotes (%)<input type="number" min="0" max="90" step="0.5" value={settings.discountPercent} onChange={(e) => setSettings({ ...settings, discountPercent: e.target.value })} disabled={!canEdit} /></label>
          <label className="event-form-label">Advance to confirm a booking (%)<input type="number" min="0" max="100" step="1" value={settings.advancePercent} onChange={(e) => setSettings({ ...settings, advancePercent: e.target.value })} disabled={!canEdit} /></label>
          <label className="event-form-label studio-span-2">Booking agreement terms<textarea rows="8" maxLength="20000" value={settings.contractTerms} onChange={(e) => setSettings({ ...settings, contractTerms: e.target.value })} disabled={!canEdit} /><span className="field-hint">Used for every new contract; you can still edit terms per client before sending.</span></label>
          <div className="event-form-actions studio-span-2"><button className="button button-dark" type="submit" disabled={!canEdit || busy}>Save pricing & terms</button></div>
        </fieldset>
      </form>

      <section className="customer-list-section">
        <div className="customer-list-heading">
          <div><p className="eyebrow">Fixed-price offers</p><h3>Packages</h3><p>Clients can pick a package on the quotation page and add services or add-ons on top.</p></div>
          <button className="button button-dark" type="button" disabled={!canEdit} onClick={() => setPackageForm({ name: '', description: '', inclusions: '', price: '', highlighted: false, active: true, sortOrder: (data.packages.length + 1) * 10 })}>＋ New package</button>
        </div>
        {!data.packages.length ? <p className="customer-empty">No packages yet. Add your most-booked bundles, e.g. “Gold Wedding — 2 days, candid + film + 40-sheet album”.</p> : (
          <div className="growth-cards">{data.packages.map((pkg) => (
            <article className={`growth-card${pkg.active ? '' : ' is-muted'}`} key={pkg.id}>
              <header><b>{pkg.name}</b>{pkg.highlighted && <span className="studio-pill">Featured</span>}{!pkg.active && <span className="studio-pill">Hidden</span>}</header>
              <strong>{inr(pkg.price)}</strong>
              {pkg.description && <p>{pkg.description}</p>}
              {pkg.inclusions.length > 0 && <ul>{pkg.inclusions.map((line) => <li key={line}>{line}</li>)}</ul>}
              <footer>
                <button className="customer-id-link" type="button" disabled={!canEdit} onClick={() => setPackageForm({ ...pkg, inclusions: pkg.inclusions.join('\n') })}>Edit</button>
                <button className="customer-id-link studio-danger-link" type="button" disabled={!canEdit || busy} onClick={() => { if (window.confirm(`Delete ${pkg.name}?`)) run(() => api.deleteQuotePackage(pkg.id), 'Package deleted.'); }}>Delete</button>
              </footer>
            </article>
          ))}</div>
        )}
      </section>

      <section className="customer-list-section">
        <div className="customer-list-heading"><div><p className="eyebrow">Per event</p><h3>Services</h3><p>Clients choose these for each event in their quote. Prices stay hidden from clients; they only see the total.</p></div></div>
        <div className="customer-table-scroll"><table className="customer-table growth-table"><thead><tr><th>Service</th><th>Description</th><th>Price (₹)</th><th>Shown</th><th /></tr></thead>
          <tbody>{data.services.map(itemRow)}</tbody></table></div>
      </section>

      <section className="customer-list-section">
        <div className="customer-list-heading"><div><p className="eyebrow">One-time extras</p><h3>Add-ons</h3><p>Pre-wedding shoots, drone reels, extra albums — anything added once per booking.</p></div></div>
        <div className="customer-table-scroll"><table className="customer-table growth-table"><thead><tr><th>Add-on</th><th>Description</th><th>Price (₹)</th><th>Shown</th><th /></tr></thead>
          <tbody>{data.addons.map(itemRow)}</tbody></table></div>
        <form className="studio-form studio-form-inline growth-gap" onSubmit={async (e) => {
          e.preventDefault();
          if (await run(() => api.addQuoteItem({ ...newItem, price: Number(newItem.price), active: true, sortOrder: 500 }), `${newItem.name} added.`)) setNewItem({ kind: newItem.kind, name: '', description: '', price: '' });
        }}>
          <label className="event-form-label">Type<select value={newItem.kind} onChange={(e) => setNewItem({ ...newItem, kind: e.target.value })}><option value="ADDON">Add-on</option><option value="SERVICE">Service (per event)</option></select></label>
          <label className="event-form-label">Name<input required maxLength="120" value={newItem.name} onChange={(e) => setNewItem({ ...newItem, name: e.target.value })} placeholder="Drone reel" /></label>
          <label className="event-form-label">Description<input maxLength="300" value={newItem.description} onChange={(e) => setNewItem({ ...newItem, description: e.target.value })} /></label>
          <label className="event-form-label">Price (₹)<input required type="number" min="0" value={newItem.price} onChange={(e) => setNewItem({ ...newItem, price: e.target.value })} /></label>
          <button className="button button-dark" type="submit" disabled={!canEdit || busy}>Add</button>
        </form>
      </section>
      {!canEdit && <UpgradeNotice feature="CUSTOM_PACKAGES" title="Your own packages and prices">Set your own services, add-ons and packages for the quotation builder.</UpgradeNotice>}
      {packageForm && (
        <Modal eyebrow="Package" title={packageForm.id ? `Edit ${packageForm.name}` : 'New package'} onClose={() => setPackageForm(null)}>
          <form className="studio-form" onSubmit={async (e) => {
            e.preventDefault();
            const body = { name: packageForm.name, description: packageForm.description, price: Number(packageForm.price), highlighted: packageForm.highlighted, active: packageForm.active, sortOrder: Number(packageForm.sortOrder) || 10,
              inclusions: packageForm.inclusions.split('\n').map((line) => line.trim()).filter(Boolean) };
            if (await run(() => (packageForm.id ? api.updateQuotePackage(packageForm.id, body) : api.addQuotePackage(body)), 'Package saved.')) setPackageForm(null);
          }}>
            <label className="event-form-label">Name<input required maxLength="120" value={packageForm.name} onChange={(e) => setPackageForm({ ...packageForm, name: e.target.value })} placeholder="Gold Wedding" /></label>
            <label className="event-form-label">Price (₹)<input required type="number" min="0" value={packageForm.price} onChange={(e) => setPackageForm({ ...packageForm, price: e.target.value })} /></label>
            <label className="event-form-label studio-span-2">Short description<input maxLength="500" value={packageForm.description || ''} onChange={(e) => setPackageForm({ ...packageForm, description: e.target.value })} placeholder="Two-day wedding coverage" /></label>
            <label className="event-form-label studio-span-2">What's included (one per line)<textarea rows="5" value={packageForm.inclusions} onChange={(e) => setPackageForm({ ...packageForm, inclusions: e.target.value })} placeholder={'Candid photography, both days\nCinematic highlights film\n40-sheet premium album'} /></label>
            <label className="studio-check"><input type="checkbox" checked={packageForm.highlighted} onChange={(e) => setPackageForm({ ...packageForm, highlighted: e.target.checked })} />Feature this package</label>
            <label className="studio-check"><input type="checkbox" checked={packageForm.active} onChange={(e) => setPackageForm({ ...packageForm, active: e.target.checked })} />Show to clients</label>
            <div className="event-form-actions"><button className="button button-dark" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save package'}</button></div>
          </form>
        </Modal>
      )}
    </div>
  );
}

function CatalogItemRow({ item, disabled, onSave, onRemove }) {
  const [form, setForm] = useState(item);
  useEffect(() => setForm(item), [item]);
  const dirty = form.name !== item.name || String(form.price) !== String(item.price) || (form.description || '') !== (item.description || '') || form.active !== item.active;
  return (
    <tr className={item.active ? '' : 'is-muted'}>
      <td><input className="growth-cell-input" maxLength="120" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} disabled={disabled} /></td>
      <td><input className="growth-cell-input" maxLength="300" value={form.description || ''} onChange={(e) => setForm({ ...form, description: e.target.value })} disabled={disabled} /></td>
      <td><input className="growth-cell-input growth-cell-num" type="number" min="0" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} disabled={disabled} /></td>
      <td><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} disabled={disabled} aria-label="Show to clients" /></td>
      <td className="growth-row-actions">
        <button className="button button-quiet button-small" type="button" disabled={disabled || !dirty} onClick={() => onSave({ kind: item.kind, name: form.name, description: form.description, price: Number(form.price), active: form.active, sortOrder: item.sortOrder })}>Save</button>
        {item.active && <button className="customer-id-link studio-danger-link" type="button" disabled={disabled} onClick={onRemove}>Hide</button>}
      </td>
    </tr>
  );
}

export function LeadCaptureSettings() {
  const links = useLoad(() => api.leadCapture(), []);
  const data = links.data;
  if (links.loading) return <p className="customer-empty">Loading…</p>;
  if (links.error) return <p className="form-error">{links.error}</p>;
  if (!data.ready) return <div className="customer-list-section"><p className="customer-empty">{data.message}</p></div>;
  return (
    <section className="customer-list-section growth-stack">
      <div><p className="eyebrow">Turn followers into enquiries</p><h3>Your enquiry links</h3>
        <p className="event-form-note">Every enquiry lands in Enquiries with its source, and the client instantly gets your quotation link.</p></div>
      <div className="growth-link-grid">
        <CopyField label="Instagram bio link" value={data.instagramUrl} />
        <CopyField label="WhatsApp click-to-chat (creates the enquiry, then opens WhatsApp)" value={data.whatsappUrl} />
        <CopyField label="Facebook page" value={data.facebookUrl} />
        <CopyField label="Website link" value={data.websiteUrl} />
      </div>
      <CopyField label="Embed on your website (paste into your site's HTML)" value={data.embedCode} multiline />
      <a className="button button-quiet button-small" href={data.formUrl} target="_blank" rel="noreferrer">Preview your form ↗</a>
    </section>
  );
}

// ---------------------------------------------------------------------------------------------
// Enquiries list: follow-up + contract column
// ---------------------------------------------------------------------------------------------

export function useEnquiryTracking(key) {
  const tracking = useLoad(() => api.enquiryTracking().catch(() => []), [key]);
  const map = Object.fromEntries((tracking.data || []).map((item) => [item.enquiryId, item]));
  return { map, reload: tracking.reload };
}

export function EnquiryBookingCell({ enquiry, tracking, user, onChanged }) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  if (!tracking) return <span className="enquiry-pending">—</span>;
  const contract = tracking.contract;
  async function remind() {
    setBusy(true); setMessage('');
    try { await api.remindEnquiry(enquiry.id); setMessage('Reminder sent'); onChanged(); } catch (error) { setMessage(error.message); } finally { setBusy(false); }
  }
  return (
    <div className="growth-booking-cell">
      {tracking.source && tracking.source !== 'MANUAL' && <span className={`growth-source growth-source-${tracking.source.toLowerCase()}`}>{SOURCE_LABELS[tracking.source] || tracking.source}</span>}
      {!enquiry.quotationId && <small>{tracking.quoteOpenedAt ? 'Quote link opened' : 'Link not opened yet'}{tracking.quoteReminderSentAt ? ` · reminded ${shortDate(tracking.quoteReminderSentAt.slice(0, 10))}` : ''}</small>}
      {contract && contract.status !== 'CANCELLED' && (
        <small className={contract.status === 'SIGNED' ? 'studio-paid' : ''}>
          {contract.status === 'SIGNED' ? `Signed by ${contract.signerName}` : 'Contract sent'}
          {contract.status === 'SIGNED' && (contract.advanceConfirmedAt ? ' · advance received' : contract.advanceReportedAt ? ` · advance paid? (${contract.advanceReference})` : ' · advance due')}
        </small>
      )}
      <div className="growth-inline-actions">
        {enquiry.quotationId && !enquiry.createdEventId && <button className="button button-quiet button-small" type="button" onClick={() => setOpen(true)}>{!contract || contract.status === 'CANCELLED' ? 'Send contract' : 'Contract'}</button>}
        {enquiry.createdEventId && contract && <a className="customer-id-link" href={api.contractPdfUrl(enquiry.id)} target="_blank" rel="noreferrer">Agreement ↗</a>}
        {tracking.canRemind && !enquiry.createdEventId && (!contract || contract.status === 'SENT' || !enquiry.quotationId) && (
          <button className="customer-id-link" type="button" disabled={busy} onClick={remind}>{busy ? 'Sending…' : 'Remind'}</button>
        )}
      </div>
      {message && <small className="growth-cell-message">{message}</small>}
      {open && <ContractModal enquiry={enquiry} user={user} onClose={() => setOpen(false)} onChanged={onChanged} />}
    </div>
  );
}

function ContractModal({ enquiry, user, onClose, onChanged }) {
  const draft = useLoad(() => api.contractDraft(enquiry.id), [enquiry.id]);
  const [form, setForm] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [advance, setAdvance] = useState({ method: 'UPI', paidOn: new Date().toISOString().slice(0, 10) });
  useEffect(() => {
    if (draft.data) setForm({ eventDate: draft.data.eventDate || '', totalAmount: draft.data.totalAmount, advanceAmount: draft.data.advanceAmount, terms: draft.data.terms });
  }, [draft.data]);
  const current = draft.data?.current;
  const live = current && current.status !== 'CANCELLED' ? current : null;

  async function run(task, done) {
    setBusy(true); setError(''); setNotice('');
    try { const result = await task(); setNotice(done(result)); draft.reload(); onChanged(); } catch (requestError) { setError(requestError.message); } finally { setBusy(false); }
  }

  if (!hasFeature(user, 'CONTRACTS')) {
    return <Modal eyebrow={enquiry.name} title="Booking agreement" onClose={onClose}><UpgradeNotice feature="CONTRACTS" title="E-signed contracts">Send a booking agreement your client signs online, hold the date automatically and collect the advance by UPI.</UpgradeNotice></Modal>;
  }
  const blocked = form && draft.data?.blockedDates?.includes(form.eventDate);
  return (
    <Modal eyebrow={`${enquiry.name} · ${enquiry.eventName}`} title="Booking agreement" onClose={onClose} wide>
      {error && <p className="form-error" role="alert">{error}</p>}
      {notice && <p className="form-success" role="status">{notice}</p>}
      {!form ? <p className="customer-empty">{draft.error || 'Loading…'}</p> : live?.status === 'SIGNED' ? (
        <div className="studio-billing-grid">
          <div>
            <div className="studio-stats studio-stats-compact">
              <div className="studio-stat"><span>Event date</span><b>{shortDate(live.eventDate)}</b></div>
              <div className="studio-stat"><span>Total</span><b>{inr(live.totalAmount)}</b></div>
              <div className="studio-stat studio-stat-good"><span>Advance</span><b>{inr(live.advanceAmount)}</b></div>
            </div>
            <p className="event-form-note">Signed by <b>{live.signerName}</b> on {new Date(live.signedAt).toLocaleString()}. The date is on hold in your calendar until you create the event.</p>
            <a className="button button-quiet button-small" href={api.contractPdfUrl(enquiry.id)} target="_blank" rel="noreferrer">Download signed agreement ↗</a>
          </div>
          <div className="studio-qr-box">
            <h3 className="studio-h3">Advance</h3>
            {live.advanceConfirmedAt ? <p className="studio-paid">Received ✓ It's added as a payment with a receipt when you create the event.</p> : (
              <>
                <p className="event-form-note">{live.advanceReportedAt ? `The client says they paid (reference ${live.advanceReference}). Check your account, then confirm.` : 'Waiting for the client to pay.'}</p>
                <label className="event-form-label">Paid via<select value={advance.method} onChange={(e) => setAdvance({ ...advance, method: e.target.value })}><option value="UPI">UPI</option><option value="BANK_TRANSFER">Bank transfer</option><option value="CASH">Cash</option><option value="CARD">Card</option><option value="CHEQUE">Cheque</option></select></label>
                <label className="event-form-label">Received on<input type="date" value={advance.paidOn} onChange={(e) => setAdvance({ ...advance, paidOn: e.target.value })} /></label>
                <button className="button button-dark button-small" type="button" disabled={busy} onClick={() => run(() => api.confirmAdvance(enquiry.id, advance), () => 'Advance confirmed. The client has been emailed.')}>Confirm advance received</button>
              </>
            )}
            <button className="customer-id-link studio-danger-link" type="button" disabled={busy} onClick={() => { if (window.confirm('Cancel this signed agreement and release the date?')) run(() => api.cancelContract(enquiry.id), () => 'Agreement cancelled and date released.'); }}>Cancel agreement</button>
          </div>
        </div>
      ) : (
        <form className="studio-form" onSubmit={(e) => { e.preventDefault(); run(() => api.sendContract(enquiry.id, { ...form, totalAmount: Number(form.totalAmount), advanceAmount: Number(form.advanceAmount) }), (result) => (result.emailed ? 'Sent! The client was emailed a link to review and sign.' : 'Contract ready.') + (result.shareLink ? ` You can also share: ${result.shareLink}` : '')); }}>
          {live?.status === 'SENT' && <p className="event-form-note studio-span-2">Sent {new Date(live.sentAt).toLocaleString()} — waiting for the client to sign. Saving again re-sends it.</p>}
          <label className="event-form-label">Event date<input type="date" required value={form.eventDate} onChange={(e) => setForm({ ...form, eventDate: e.target.value })} />{blocked && <span className="studio-due">This date is already booked or on hold.</span>}</label>
          <label className="event-form-label">Agreed total (₹)<input type="number" min="0" step="1" required value={form.totalAmount} onChange={(e) => setForm({ ...form, totalAmount: e.target.value })} /></label>
          <label className="event-form-label">Advance to confirm (₹)<input type="number" min="0" step="1" required value={form.advanceAmount} onChange={(e) => setForm({ ...form, advanceAmount: e.target.value })} /></label>
          <span />
          <label className="event-form-label studio-span-2">Terms<textarea rows="9" value={form.terms} onChange={(e) => setForm({ ...form, terms: e.target.value })} /></label>
          <div className="event-form-actions">
            {live?.status === 'SENT' && <button className="button button-quiet" type="button" disabled={busy} onClick={() => run(() => api.cancelContract(enquiry.id), () => 'Contract withdrawn.')}>Withdraw</button>}
            <button className="button button-dark" type="submit" disabled={busy || blocked}>{busy ? 'Sending…' : live ? 'Update & re-send' : 'Send for e-signature'}</button>
          </div>
        </form>
      )}
    </Modal>
  );
}

// ---------------------------------------------------------------------------------------------
// Quotation page (client): packages & add-ons, contract signing, advance
// ---------------------------------------------------------------------------------------------

export function QuotePackagePicker({ context, form, setForm }) {
  if (!context.packages?.length && !context.addons?.length) return null;
  const toggleAddon = (id, on) => setForm((current) => ({ ...current, addonIds: on ? [...(current.addonIds || []), id] : (current.addonIds || []).filter((x) => x !== id) }));
  return (
    <>
      {context.packages?.length > 0 && (
        <section className="quote-card">
          <div className="quote-card-heading"><div><h2>Start with a package</h2><p>Pick a ready package, then add anything else you need below.</p></div><span>Optional</span></div>
          <div className="growth-package-grid">
            <label className={!form.packageId ? 'growth-package selected' : 'growth-package'}>
              <input type="radio" name="package" checked={!form.packageId} onChange={() => setForm((c) => ({ ...c, packageId: null }))} />
              <b>Build my own</b><small>Choose services event by event</small>
            </label>
            {context.packages.map((pkg) => (
              <label key={pkg.id} className={`growth-package${form.packageId === pkg.id ? ' selected' : ''}${pkg.highlighted ? ' featured' : ''}`}>
                <input type="radio" name="package" checked={form.packageId === pkg.id} onChange={() => setForm((c) => ({ ...c, packageId: pkg.id }))} />
                {pkg.highlighted && <span className="growth-package-badge">Popular</span>}
                <b>{pkg.name}</b>
                <strong>{inr(pkg.price)}</strong>
                {pkg.description && <small>{pkg.description}</small>}
                {pkg.inclusions.length > 0 && <ul>{pkg.inclusions.map((line) => <li key={line}>{line}</li>)}</ul>}
              </label>
            ))}
          </div>
        </section>
      )}
      {context.addons?.length > 0 && (
        <section className="quote-card">
          <div className="quote-card-heading"><div><h2>Add something special</h2><p>One-time extras for your booking.</p></div><span>Optional</span></div>
          <div className="quote-service-grid">
            {context.addons.map((addon) => {
              const checked = (form.addonIds || []).includes(addon.id);
              return <label className={checked ? 'quote-service selected' : 'quote-service'} key={addon.id}><input type="checkbox" checked={checked} onChange={(e) => toggleAddon(addon.id, e.target.checked)} /><span><b>{addon.name}</b><small>{addon.description}</small></span></label>;
            })}
          </div>
        </section>
      )}
    </>
  );
}

function SignaturePad({ onChange }) {
  const canvasRef = useRef(null);
  const drawing = useRef(false);
  const hasInk = useRef(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    const ratio = window.devicePixelRatio || 1;
    canvas.width = canvas.offsetWidth * ratio;
    canvas.height = canvas.offsetHeight * ratio;
    const ctx = canvas.getContext('2d');
    ctx.scale(ratio, ratio);
    ctx.lineWidth = 2.4;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.strokeStyle = '#1b1f3b';
  }, []);

  function point(event) {
    const rect = canvasRef.current.getBoundingClientRect();
    return [event.clientX - rect.left, event.clientY - rect.top];
  }
  function down(event) {
    drawing.current = true;
    canvasRef.current.setPointerCapture?.(event.pointerId);
    const ctx = canvasRef.current.getContext('2d');
    ctx.beginPath();
    ctx.moveTo(...point(event));
  }
  function move(event) {
    if (!drawing.current) return;
    const ctx = canvasRef.current.getContext('2d');
    ctx.lineTo(...point(event));
    ctx.stroke();
    hasInk.current = true;
  }
  function up() {
    if (!drawing.current) return;
    drawing.current = false;
    onChange(hasInk.current ? canvasRef.current.toDataURL('image/png') : '');
  }
  function clear() {
    const canvas = canvasRef.current;
    canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
    hasInk.current = false;
    onChange('');
  }
  return (
    <div className="growth-signature">
      <canvas ref={canvasRef} onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerLeave={up} aria-label="Draw your signature" />
      <div><span>Sign above with your finger or mouse</span><button className="customer-id-link" type="button" onClick={clear}>Clear</button></div>
    </div>
  );
}

export function QuoteContractSection({ token, contract: initial }) {
  const [contract, setContract] = useState(initial);
  const [name, setName] = useState('');
  const [signature, setSignature] = useState('');
  const [agree, setAgree] = useState(false);
  const [reference, setReference] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  if (!contract) return null;

  async function sign(event) {
    event.preventDefault();
    setBusy(true); setError('');
    try { setContract(await api.signContract(token, { signerName: name, signature, agree })); } catch (requestError) { setError(requestError.message); } finally { setBusy(false); }
  }
  async function reportPaid(event) {
    event.preventDefault();
    setBusy(true); setError('');
    try { setContract(await api.reportAdvance(token, reference)); } catch (requestError) { setError(requestError.message); } finally { setBusy(false); }
  }

  return (
    <section className="quote-card growth-contract">
      <div className="quote-card-heading"><div><p className="eyebrow">Booking agreement</p><h2>{contract.status === 'SIGNED' ? 'Your booking is on hold' : 'Review & sign to book your date'}</h2></div><span>{contract.status === 'SIGNED' ? 'Signed' : 'Action needed'}</span></div>
      <div className="studio-stats studio-stats-compact">
        <div className="studio-stat"><span>Event date</span><b>{shortDate(contract.eventDate)}</b></div>
        <div className="studio-stat"><span>Total</span><b>{inr(contract.totalAmount)}</b></div>
        <div className="studio-stat"><span>Advance to confirm</span><b>{inr(contract.advanceAmount)}</b></div>
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      {contract.status === 'SENT' ? (
        <form className="growth-sign-form" onSubmit={sign}>
          <div className="growth-terms" tabIndex={0}>{contract.terms.split('\n').map((line, i) => <p key={i}>{line}</p>)}</div>
          <a className="customer-id-link" href={api.publicContractPdfUrl(token)} target="_blank" rel="noreferrer">Download agreement (PDF) ↗</a>
          <label className="event-form-label">Your full name<input required maxLength="120" value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" /></label>
          <SignaturePad onChange={setSignature} />
          <label className="studio-check"><input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />I have read and agree to these terms with {contract.studioName}.</label>
          <button className="button button-dark" type="submit" disabled={busy || !name.trim() || !signature || !agree}>{busy ? 'Signing…' : 'Sign agreement'}</button>
          <p className="form-footnote">Your signature, name, time and device are recorded with the agreement.</p>
        </form>
      ) : (
        <div className="studio-billing-grid">
          <div>
            <p className="studio-paid">Signed by {contract.signerName} on {new Date(contract.signedAt).toLocaleString()}.</p>
            <p className="event-form-note">{contract.advanceConfirmedAt ? 'Advance received — your date is confirmed. 🎉' : `${contract.studioName} is holding ${shortDate(contract.eventDate)} for you. Pay the advance to confirm.`}</p>
            <a className="button button-quiet button-small" href={api.publicContractPdfUrl(token)} target="_blank" rel="noreferrer">Download signed agreement ↗</a>
          </div>
          {!contract.advanceConfirmedAt && (
            <div className="studio-qr-box">
              {contract.upiAvailable ? (
                <>
                  <h3 className="studio-h3">Pay advance {inr(contract.advanceAmount)}</h3>
                  <img src={api.advanceQrUrl(token)} alt="UPI QR code for the advance" />
                  <p className="event-form-note">Scan with any UPI app, or pay to <b>{contract.upiId}</b>.</p>
                </>
              ) : <p className="event-form-note">{contract.studioName} will share payment details with you.</p>}
              {contract.advanceReportedAt ? <p className="studio-paid">Thanks! We've told {contract.studioName} (reference {contract.advanceReference}).</p> : (
                <form onSubmit={reportPaid} className="growth-inline-form">
                  <label className="event-form-label">Paid? Enter the UPI / bank reference<input required maxLength="120" value={reference} onChange={(e) => setReference(e.target.value)} placeholder="e.g. 412345678901" /></label>
                  <button className="button button-dark button-small" type="submit" disabled={busy}>I've paid</button>
                </form>
              )}
            </div>
          )}
        </div>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------------------------
// Public enquiry form (/enquire/{slug}; embeddable)
// ---------------------------------------------------------------------------------------------

export function EnquireFormPage({ slug }) {
  const params = new URLSearchParams(window.location.search);
  const embed = params.get('embed') === '1';
  const source = (params.get('source') || 'website').toUpperCase();
  const chat = params.get('chat') === '1';
  const [form, setForm] = useState({ name: '', mobileNumber: '', email: '', eventTypeId: '', preferredDate: '', message: '', website: '' });
  const [studio, setStudio] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(null);

  useEffect(() => {
    api.publicEnquiryForm(slug).then((data) => {
      setStudio(data);
      setForm((current) => ({ ...current, eventTypeId: data.eventTypes.find((t) => t.name === 'Wedding')?.id || data.eventTypes[0]?.id || '' }));
    }).catch((requestError) => setError(requestError.message));
  }, [slug]);

  async function submit(event) {
    event.preventDefault();
    setBusy(true); setError('');
    try {
      const result = await api.submitPublicEnquiry(slug, { ...form, preferredDate: form.preferredDate || null, source });
      setDone(result);
      if (chat && result.whatsappUrl) window.open(result.whatsappUrl, embed ? '_blank' : '_self');
    } catch (requestError) { setError(requestError.message); } finally { setBusy(false); }
  }

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });
  const today = new Date().toISOString().slice(0, 10);
  return (
    <main className={`growth-enquire${embed ? ' is-embed' : ''}`} style={brandStyle(studio?.brand)}>
      <section className="growth-enquire-card">
        {!studio ? <p className="customer-empty">{error || 'Loading…'}</p> : done ? (
          <div className="growth-enquire-done">
            <span className="guest-checkin-mark" aria-hidden="true">✓</span>
            <h1>Thank you!</h1>
            <p>{done.message}</p>
            {done.whatsappUrl && <a className="button button-dark" href={done.whatsappUrl} target="_blank" rel="noreferrer">Continue on WhatsApp</a>}
          </div>
        ) : (
          <>
            <header className="growth-enquire-head">
              <StudioMark brand={studio.brand} />
              <div><p className="eyebrow">Enquire</p><h1>{studio.studioName}</h1><p>Tell us about your celebration. You'll get a link to build your quote instantly.</p></div>
            </header>
            {error && <p className="form-error" role="alert">{error}</p>}
            <form className="auth-form" onSubmit={submit}>
              <label>Your name<input required maxLength="120" value={form.name} onChange={set('name')} autoComplete="name" /></label>
              <label>Mobile (WhatsApp)<input required type="tel" pattern="([0-9]{10}|\+91[0-9]{10})" maxLength="13" value={form.mobileNumber} onChange={set('mobileNumber')} placeholder="9876543210" autoComplete="tel" /></label>
              <label>Email<input required type="email" maxLength="320" value={form.email} onChange={set('email')} autoComplete="email" /></label>
              <label>Occasion<select value={form.eventTypeId} onChange={set('eventTypeId')}>{studio.eventTypes.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label>
              <label>Event date <span className="optional-label">Optional</span><input type="date" min={today} value={form.preferredDate} onChange={set('preferredDate')} /></label>
              <label>Anything we should know? <span className="optional-label">Optional</span><textarea rows="3" maxLength="1000" value={form.message} onChange={set('message')} placeholder="Venue, number of days, guest count…" /></label>
              <input className="growth-honeypot" tabIndex={-1} autoComplete="off" aria-hidden="true" value={form.website} onChange={set('website')} name="website" />
              <button className="button button-dark button-full" type="submit" disabled={busy}>{busy ? 'Sending…' : chat ? 'Send & continue on WhatsApp' : 'Send enquiry'}</button>
            </form>
            {studio.brand?.poweredByFrameBucket !== false && <p className="form-footnote">Powered by FrameBucket</p>}
          </>
        )}
      </section>
    </main>
  );
}
