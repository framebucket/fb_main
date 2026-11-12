import { useEffect, useState } from 'react';
import { api } from './api.js';
import { Modal } from './Studio.jsx';
import './growth.css';

// Client portal: wedding invitation designer (details → 5 designs → regenerate → pick → animated video).

const KINDS = [['WEDDING', 'Wedding'], ['ENGAGEMENT', 'Engagement'], ['RECEPTION', 'Reception'], ['SAVE_THE_DATE', 'Save the date']];
const EMPTY_EVENT = { title: '', date: '', time: '', venue: '', address: '' };
const BLANK = { kind: 'WEDDING', bride: '', groom: '', bride_family: '', groom_family: '', invocation: '', message: '', hosts: '', rsvp: '', events: [{ ...EMPTY_EVENT }] };

export function InvitationModal({ event, onClose }) {
  const [state, setState] = useState(null);
  const [form, setForm] = useState(BLANK);
  const [step, setStep] = useState('details');
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [picked, setPicked] = useState(null);
  const [zoom, setZoom] = useState(null);

  function apply(next) {
    setState(next);
    if (next?.details) setForm({ ...BLANK, ...next.details, events: next.details.events?.length ? next.details.events : [{ ...EMPTY_EVENT }] });
  }

  useEffect(() => {
    api.invitation(event.eventId).then((data) => {
      setState(data);
      if (data.details) apply(data);
      else if (data.suggested) setForm({ ...BLANK, ...data.suggested, events: data.suggested.events?.length ? data.suggested.events : [{ ...EMPTY_EVENT }] });
      if (data.status === 'READY' || data.status === 'RENDERING') setStep('final');
      else if (data.designs?.length) setStep('designs');
    }).catch((e) => setError(e.message));
  }, [event.eventId]);

  useEffect(() => {
    if (state?.status !== 'RENDERING') return undefined;
    const timer = setInterval(() => api.invitation(event.eventId).then((d) => setState((s) => ({ ...s, ...d }))).catch(() => {}), 3000);
    return () => clearInterval(timer);
  }, [state?.status, event.eventId]);

  async function run(label, task) {
    setBusy(label); setError('');
    try { return await task(); } catch (e) { setError(e.message); return null; } finally { setBusy(''); }
  }

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });
  const setEvent = (i, key) => (e) => setForm({ ...form, events: form.events.map((ev, k) => (k === i ? { ...ev, [key]: e.target.value } : ev)) });

  async function saveAndDesign(e) {
    e.preventDefault();
    const saved = await run('Saving…', () => api.saveInvitation(event.eventId, form));
    if (!saved) return;
    apply(saved);
    const designed = await run('Designing 5 invitations…', () => api.generateInvitationDesigns(event.eventId));
    if (designed) { apply(designed); setPicked(null); setStep('designs'); }
  }

  async function uploadPhoto(file) {
    if (!file) return;
    let current = state;
    if (!current?.exists) {
      current = await run('Saving…', () => api.saveInvitation(event.eventId, form));
      if (!current) return;
      apply(current);
    }
    const r = await run('Uploading photo…', () => api.uploadInvitationPhoto(event.eventId, file));
    if (r) apply(r);
  }

  if (state && state.planAllows === false) {
    return (
      <Modal eyebrow={event.eventName} title="Wedding invitation" onClose={onClose}>
        <p className="customer-empty">Invitation designs aren't available from {event.photographerName} yet. Ask your studio about it!</p>
      </Modal>
    );
  }

  const version = state?.updated_at || '';
  return (
    <Modal eyebrow={`${event.eventName} · ${event.photographerName}`} title="Create your wedding invitation" onClose={onClose} wide>
      {!state ? <p className="customer-empty">{error || 'Loading…'}</p> : (
        <div className="growth-stack invite">
          <ol className="invite-steps">
            {[['details', '1 · Your details'], ['designs', '2 · Pick a design'], ['final', '3 · Video & sharing']].map(([key, label]) => (
              <li key={key} className={step === key ? 'is-active' : ''}>
                <button type="button" disabled={(key === 'designs' && !state.designs?.length) || (key === 'final' && !state.chosen)} onClick={() => setStep(key)}>{label}</button>
              </li>
            ))}
          </ol>
          {error && <p className="form-error" role="alert">{error}</p>}
          {busy && <p className="form-success" role="status"><span className="invite-spinner" aria-hidden="true" /> {busy}</p>}

          {step === 'details' && (
            <form className="studio-form invite-form" onSubmit={saveAndDesign}>
              <label className="event-form-label">Occasion<select value={form.kind} onChange={set('kind')}>{KINDS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
              <label className="event-form-label">Invocation (optional)<input maxLength="60" value={form.invocation} onChange={set('invocation')} placeholder="|| Shri Ganeshaya Namah ||" /></label>
              <label className="event-form-label">Bride's name<input required maxLength="40" value={form.bride} onChange={set('bride')} placeholder="Varsha" /></label>
              <label className="event-form-label">Groom's name<input required maxLength="40" value={form.groom} onChange={set('groom')} placeholder="Nitish" /></label>
              <label className="event-form-label">Bride's family (optional)<input maxLength="90" value={form.bride_family} onChange={set('bride_family')} placeholder="D/o Smt. Lakshmi & Sri. Ramesh, Mysuru" /></label>
              <label className="event-form-label">Groom's family (optional)<input maxLength="90" value={form.groom_family} onChange={set('groom_family')} placeholder="S/o Smt. Shobha & Sri. Prakash, Bengaluru" /></label>
              <label className="event-form-label studio-span-2">Invitation message<input maxLength="160" value={form.message} onChange={set('message')} placeholder="Together with their families, request the pleasure of your company to celebrate their union" /></label>
              {form.events.map((ev, i) => (
                <fieldset key={i} className="invite-event studio-span-2">
                  <legend>Function {i + 1}{i > 0 && <button type="button" className="customer-id-link" onClick={() => setForm({ ...form, events: form.events.filter((_, k) => k !== i) })}>Remove</button>}</legend>
                  <label className="event-form-label">Name<input maxLength="40" value={ev.title} onChange={setEvent(i, 'title')} placeholder={i === 0 ? 'Muhurtham' : 'Reception'} /></label>
                  <label className="event-form-label">Date<input required={i === 0} maxLength="40" value={ev.date} onChange={setEvent(i, 'date')} placeholder="Sunday, 25 January 2026" /></label>
                  <label className="event-form-label">Time<input maxLength="30" value={ev.time} onChange={setEvent(i, 'time')} placeholder="10:30 AM" /></label>
                  <label className="event-form-label">Venue<input required={i === 0} maxLength="70" value={ev.venue} onChange={setEvent(i, 'venue')} placeholder="Sri Krishna Kalyana Mantapa" /></label>
                  <label className="event-form-label studio-span-2">Address<input maxLength="110" value={ev.address} onChange={setEvent(i, 'address')} placeholder="12th Main, Jayanagar 4th Block, Bengaluru" /></label>
                </fieldset>
              ))}
              {form.events.length < 3 && <button type="button" className="button button-quiet button-small studio-span-2" onClick={() => setForm({ ...form, events: [...form.events, { ...EMPTY_EVENT }] })}>+ Add another function</button>}
              <label className="event-form-label">Compliments from (optional)<input maxLength="120" value={form.hosts} onChange={set('hosts')} placeholder="With best compliments from family & friends" /></label>
              <label className="event-form-label">RSVP (optional)<input maxLength="60" value={form.rsvp} onChange={set('rsvp')} placeholder="RSVP +91 98450 12345" /></label>
              <div className="invite-photo studio-span-2">
                <div>
                  <b>Your photo together (optional)</b>
                  <p className="event-form-note">It's framed in each design with your faces kept in view, and gently animated in the video.</p>
                </div>
                {state.has_photo
                  ? <span className="studio-pill">Photo added ✓ <button type="button" className="customer-id-link" onClick={async () => { const r = await run('Removing…', () => api.deleteInvitationPhoto(event.eventId)); if (r) apply(r); }}>Remove</button></span>
                  : <label className="button button-quiet button-small">Upload photo<input hidden type="file" accept="image/jpeg,image/png" onChange={(e) => uploadPhoto(e.target.files?.[0])} /></label>}
              </div>
              <p className="event-form-note studio-span-2">Names can be typed in English or in Kannada, Hindi, Tamil and other Indian scripts.</p>
              <div className="event-form-actions studio-span-2"><button className="button button-dark" type="submit" disabled={!!busy}>{state.designs?.length ? 'Save & make new designs' : 'Design my invitation ✦'}</button></div>
            </form>
          )}

          {step === 'designs' && (
            <>
              <div className="customer-list-heading"><div><h3 className="studio-h3">Pick the design you love</h3>
                <p className="event-form-note">Tap a design to see it larger. Not quite right? Get five new ones ({state.regenerations_left} left) or edit your details.</p></div></div>
              <div className="invite-grid">{state.designs.map((d) => (
                <figure key={`${version}-${d.index}`} className={`invite-card${picked === d.index ? ' is-picked' : ''}`}>
                  <button type="button" onClick={() => setZoom(d.index)}><img src={api.invitationDesignUrl(event.eventId, d.index, version)} alt={d.label} loading="lazy" /></button>
                  <figcaption><span>{d.label}</span><button type="button" className={`button button-small ${picked === d.index ? 'button-dark' : 'button-quiet'}`} onClick={() => setPicked(d.index)}>{picked === d.index ? 'Selected ✓' : 'Choose'}</button></figcaption>
                </figure>
              ))}</div>
              <div className="event-form-actions">
                <button className="button button-quiet" type="button" onClick={() => setStep('details')}>Edit details</button>
                <button className="button button-quiet" type="button" disabled={!!busy || state.regenerations_left <= 0} onClick={async () => { const r = await run('Designing 5 new invitations…', () => api.generateInvitationDesigns(event.eventId)); if (r) { apply(r); setPicked(null); } }}>↻ New designs</button>
                <button className="button button-dark" type="button" disabled={picked === null || !!busy} onClick={async () => { const r = await run('Starting your video…', () => api.finalizeInvitation(event.eventId, picked)); if (r) { apply(r); setStep('final'); } }}>Make my video</button>
              </div>
            </>
          )}

          {step === 'final' && (
            <div className="invite-final">
              {state.status === 'RENDERING' && (
                <div className="invite-rendering">
                  <h3 className="studio-h3">Animating your “{state.chosen?.label}” invitation…</h3>
                  <div className="growth-progress"><span style={{ width: `${Math.max(4, state.progress || 0)}%` }} /></div>
                  <p className="event-form-note">This takes about a minute. You can close this window and come back.</p>
                </div>
              )}
              {state.status === 'FAILED' && <p className="form-error">The video couldn't be made: {state.error}. Pick the design again to retry.</p>}
              {state.status === 'READY' && (
                <>
                  <video className="invite-video" src={api.invitationFileUrl(event.eventId, 'video', version)} poster={api.invitationFileUrl(event.eventId, 'poster', version)} controls playsInline loop autoPlay muted />
                  <div className="event-form-actions">
                    <a className="button button-dark" href={api.invitationFileUrl(event.eventId, 'video', version)} download="wedding-invitation.mp4">Download video (MP4)</a>
                    <a className="button button-quiet" href={api.invitationFileUrl(event.eventId, 'poster', version)} download="wedding-invitation.jpg">Download image</a>
                    <ShareButton eventId={event.eventId} version={version} />
                  </div>
                  <p className="event-form-note">Vertical 1080 × 1920 — ready for WhatsApp, WhatsApp status and Instagram stories.</p>
                </>
              )}
              <div className="event-form-actions"><button className="button button-quiet" type="button" onClick={() => setStep('designs')} disabled={!state.designs?.length || state.status === 'RENDERING'}>Choose a different design</button></div>
            </div>
          )}
        </div>
      )}
      {zoom !== null && state?.designs?.[zoom] && (
        <div className="invite-zoom" role="dialog" aria-label="Design preview" onClick={() => setZoom(null)}>
          <img src={api.invitationDesignUrl(event.eventId, zoom, version)} alt={state.designs[zoom].label} />
        </div>
      )}
    </Modal>
  );
}

function ShareButton({ eventId, version }) {
  const [error, setError] = useState('');
  if (typeof navigator === 'undefined' || !navigator.canShare) return null;
  return (
    <>
      <button className="button button-quiet" type="button" onClick={async () => {
        try {
          const blob = await fetch(api.invitationFileUrl(eventId, 'video', version), { credentials: 'include' }).then((r) => r.blob());
          const file = new File([blob], 'wedding-invitation.mp4', { type: 'video/mp4' });
          if (navigator.canShare({ files: [file] })) await navigator.share({ files: [file], title: 'Our wedding invitation' });
        } catch (e) { if (e.name !== 'AbortError') setError('Sharing isn’t supported here — download the video instead.'); }
      }}>Share…</button>
      {error && <span className="field-hint">{error}</span>}
    </>
  );
}
