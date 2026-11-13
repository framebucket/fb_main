import { useEffect, useState } from 'react';
import { api } from './api.js';
import { UpgradeNotice, hasFeature } from './Plans.jsx';
import { Modal, inr, shortDate, useLoad } from './Studio.jsx';
import { StudioMark, brandStyle } from './Bookings.jsx';
import { CrewTab, GearCostsTab } from './Crew.jsx';
import './growth.css';

// Delivery: per-event checklist, films, album proofing (photographer + client), branding & watermark settings.

const STEP_STATUS = [['NOT_STARTED', 'Not started'], ['IN_PROGRESS', 'In progress'], ['DONE', 'Done']];
const STEP_KINDS = [['TEASER', 'Teaser'], ['HIGHLIGHTS', 'Highlights film'], ['FULL_FILM', 'Full film'], ['GALLERY', 'Photo gallery'], ['ALBUM_DESIGN', 'Album design'], ['PRINTED_ALBUM', 'Printed album'], ['OTHER', 'Other']];
const FILM_KINDS = [['HIGHLIGHTS', 'Highlights'], ['TEASER', 'Teaser'], ['FULL_FILM', 'Full film'], ['REEL', 'Reel'], ['OTHER', 'Other']];
const PROOF_STATUS = { SENT: 'Waiting for client', CHANGES_REQUESTED: 'Changes requested', APPROVED: 'Approved', SUPERSEDED: 'Replaced' };

function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  async function run(task, done) {
    setBusy(true); setError(''); setNotice('');
    try { const result = await task(); if (done) setNotice(typeof done === 'function' ? done(result) : done); return result; } catch (requestError) { setError(requestError.message); return null; } finally { setBusy(false); }
  }
  const messages = <>{error && <p className="form-error" role="alert">{error}</p>}{notice && <p className="form-success" role="status">{notice}</p>}</>;
  return { busy, run, messages, setError };
}

async function ensureFolder(eventId, name) {
  const result = await api.dataFolders(eventId);
  const folders = result?.folders || [];
  const existing = folders.find((folder) => folder.name === name);
  if (existing) return existing.folder_id;
  const created = await api.createDataFolder(eventId, name);
  return created.folder_id || created.folder?.folder_id;
}

// ---------------------------------------------------------------------------------------------
// Event workspace (photographer)
// ---------------------------------------------------------------------------------------------

export function EventWorkspaceModal({ event, user, onClose }) {
  const [tab, setTab] = useState('delivery');
  const tabs = [['delivery', 'Delivery'], ['films', 'Films'], ['proofs', 'Album proofs'], ['crew', 'Crew'], ['costs', 'Gear & costs']];
  return (
    <Modal eyebrow={`${event.eventCode} · ${event.customerName} · ${shortDate(event.eventDate)}`} title={event.eventTypeName || 'Event'} onClose={onClose} wide>
      <div className="studio-subtabs" role="tablist">{tabs.map(([key, label]) => <button key={key} type="button" className={tab === key ? 'active' : ''} onClick={() => setTab(key)}>{label}</button>)}</div>
      {tab === 'delivery' && <DeliveryTab eventId={event.id} />}
      {tab === 'films' && (hasFeature(user, 'FILMS') ? <FilmsTab eventId={event.id} user={user} /> : <UpgradeNotice feature="FILMS" title="Film delivery">Deliver teasers, highlights and full films alongside photos.</UpgradeNotice>)}
      {tab === 'proofs' && (hasFeature(user, 'ALBUM_PROOFING') ? <ProofsTab eventId={event.id} user={user} /> : <UpgradeNotice feature="ALBUM_PROOFING" title="Album proofing">Share album designs, collect comments per spread and get approval in one place.</UpgradeNotice>)}
      {tab === 'crew' && (hasFeature(user, 'CREW_PAYOUTS') ? <CrewTab eventId={event.id} /> : <UpgradeNotice feature="CREW_PAYOUTS" title="Crew scheduling">Assign second shooters and freelancers, spot clashes and track payouts.</UpgradeNotice>)}
      {tab === 'costs' && (hasFeature(user, 'GEAR_COSTS') ? <GearCostsTab eventId={event.id} /> : <UpgradeNotice feature="GEAR_COSTS" title="Gear & profit">Pack lists, travel costs and real profit per booking.</UpgradeNotice>)}
    </Modal>
  );
}

export function DeliveryTab({ eventId }) {
  const steps = useLoad(() => api.deliverySteps(eventId), [eventId]);
  const [list, setList] = useState(null);
  const [newStep, setNewStep] = useState({ title: '', kind: 'OTHER', dueDate: '' });
  const { busy, run, messages } = useAction();
  useEffect(() => { if (steps.data) setList(steps.data); }, [steps.data]);
  if (!list) return <p className="customer-empty">{steps.error || 'Loading…'}</p>;
  const done = list.filter((s) => s.status === 'DONE').length;
  return (
    <div className="growth-stack">
      {messages}
      <p className="event-form-note">Your client sees this checklist in their portal. Mark a step done with “Notify client” to email them. {done}/{list.length} done.</p>
      <div className="growth-progress"><span style={{ width: `${list.length ? (done / list.length) * 100 : 0}%` }} /></div>
      <ul className="growth-steps">{list.map((step) => <StepRow key={step.id} step={step} busy={busy}
        onSave={async (body) => { const r = await run(() => api.updateDeliveryStep(eventId, step.id, body), 'Saved.'); if (r) setList(r); }}
        onDelete={async () => { const r = await run(() => api.deleteDeliveryStep(eventId, step.id), 'Step removed.'); if (r) setList(r); }} />)}</ul>
      <form className="studio-form studio-form-inline" onSubmit={async (e) => {
        e.preventDefault();
        const r = await run(() => api.addDeliveryStep(eventId, { ...newStep, dueDate: newStep.dueDate || null }), 'Step added.');
        if (r) { setList(r); setNewStep({ title: '', kind: 'OTHER', dueDate: '' }); }
      }}>
        <label className="event-form-label">New step<input required maxLength="120" value={newStep.title} onChange={(e) => setNewStep({ ...newStep, title: e.target.value })} placeholder="Parents' album" /></label>
        <label className="event-form-label">Type<select value={newStep.kind} onChange={(e) => setNewStep({ ...newStep, kind: e.target.value })}>{STEP_KINDS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
        <label className="event-form-label">Due<input type="date" value={newStep.dueDate} onChange={(e) => setNewStep({ ...newStep, dueDate: e.target.value })} /></label>
        <button className="button button-dark" type="submit" disabled={busy}>Add step</button>
      </form>
    </div>
  );
}

function StepRow({ step, busy, onSave, onDelete }) {
  const [form, setForm] = useState({ ...step, dueDate: step.dueDate || '', note: step.note || '', notifyClient: false });
  useEffect(() => setForm({ ...step, dueDate: step.dueDate || '', note: step.note || '', notifyClient: false }), [step]);
  const dirty = form.status !== step.status || (form.dueDate || '') !== (step.dueDate || '') || (form.note || '') !== (step.note || '') || form.title !== step.title;
  return (
    <li className={`growth-step growth-step-${step.status.toLowerCase()}`}>
      <span className="growth-step-dot" aria-hidden="true">{step.status === 'DONE' ? '✓' : ''}</span>
      <div className="growth-step-body">
        <input className="growth-cell-input growth-step-title" value={form.title} maxLength="120" onChange={(e) => setForm({ ...form, title: e.target.value })} aria-label="Step title" />
        <div className="growth-step-fields">
          <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value, notifyClient: e.target.value === 'DONE' })} aria-label="Status">{STEP_STATUS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>
          <input type="date" value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} aria-label="Due date" />
          <input className="growth-step-note" value={form.note} maxLength="500" onChange={(e) => setForm({ ...form, note: e.target.value })} placeholder="Note for the client" />
        </div>
        {step.completedAt && <small>Completed {new Date(step.completedAt).toLocaleDateString()}</small>}
      </div>
      <div className="growth-row-actions">
        {form.status === 'DONE' && step.status !== 'DONE' && <label className="studio-check"><input type="checkbox" checked={form.notifyClient} onChange={(e) => setForm({ ...form, notifyClient: e.target.checked })} />Notify client</label>}
        <button className="button button-quiet button-small" type="button" disabled={busy || !dirty} onClick={() => onSave({ title: form.title, kind: step.kind, status: form.status, dueDate: form.dueDate || null, note: form.note, notifyClient: form.notifyClient })}>Save</button>
        <button className="customer-id-link studio-danger-link" type="button" disabled={busy} onClick={onDelete}>Remove</button>
      </div>
    </li>
  );
}

function FilmPlayer({ film, src }) {
  if (film.source === 'UPLOAD') return <video className="growth-video" controls preload="metadata" src={src} />;
  if (film.embedUrl) return <iframe className="growth-video" src={film.embedUrl} title={film.title} allow="autoplay; fullscreen; picture-in-picture" allowFullScreen loading="lazy" />;
  return <a className="button button-quiet button-small" href={film.externalUrl} target="_blank" rel="noreferrer">Watch {film.title} ↗</a>;
}

export function FilmsTab({ eventId, user }) {
  const films = useLoad(() => api.films(eventId), [eventId]);
  const videos = useLoad(() => api.filmVideos(eventId).catch(() => []), [eventId]);
  const [list, setList] = useState(null);
  const [form, setForm] = useState({ title: 'Highlights', kind: 'HIGHLIGHTS', source: 'UPLOAD', mediaId: '', externalUrl: '', notifyClient: true });
  const [progress, setProgress] = useState(null);
  const { busy, run, messages, setError } = useAction();
  useEffect(() => { if (films.data) setList(films.data); }, [films.data]);

  async function upload(file) {
    if (!file) return;
    setError('');
    try {
      setProgress(0);
      const folderId = await ensureFolder(eventId, 'Films');
      const result = await api.uploadDataFile(eventId, folderId, file, (fraction) => setProgress(Math.round((fraction || 0) * 100)));
      videos.reload();
      setForm((current) => ({ ...current, source: 'UPLOAD', mediaId: result?.photo_id || current.mediaId }));
    } catch (requestError) { setError(requestError.message); } finally { setProgress(null); }
  }

  if (!list) return <p className="customer-empty">{films.error || 'Loading…'}</p>;
  return (
    <div className="growth-stack">
      {messages}
      {!list.length ? <p className="customer-empty">No films yet. Upload a video or paste a YouTube, Vimeo or Google Drive link.</p> : (
        <div className="growth-films">{list.map((film) => (
          <article key={film.id} className="growth-film">
            <FilmPlayer film={film} src={film.mediaId ? api.photographerPreviewUrl(eventId, film.mediaId) : null} />
            <div><b>{film.title}</b><span>{FILM_KINDS.find(([v]) => v === film.kind)?.[1]} · {film.source === 'UPLOAD' ? 'Uploaded' : 'Link'}</span></div>
            <button className="customer-id-link studio-danger-link" type="button" disabled={busy} onClick={async () => { const r = await run(() => api.deleteFilm(eventId, film.id), 'Film removed.'); if (r) setList(r); }}>Remove</button>
          </article>
        ))}</div>
      )}
      <form className="studio-form customer-list-section" onSubmit={async (e) => {
        e.preventDefault();
        const body = { title: form.title, kind: form.kind, notifyClient: form.notifyClient, mediaId: form.source === 'UPLOAD' ? form.mediaId : null, externalUrl: form.source === 'LINK' ? form.externalUrl : null };
        const r = await run(() => api.addFilm(eventId, body), 'Film added. Your client can watch it in their portal.');
        if (r) { setList(r); setForm({ ...form, title: '', mediaId: '', externalUrl: '' }); }
      }}>
        <h3 className="studio-h3 studio-span-2">Add a film</h3>
        <label className="event-form-label">Title<input required maxLength="120" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></label>
        <label className="event-form-label">Type<select value={form.kind} onChange={(e) => setForm({ ...form, kind: e.target.value })}>{FILM_KINDS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
        <div className="studio-radio-row">
          <label className={`studio-radio${form.source === 'UPLOAD' ? ' active' : ''}`}><input type="radio" checked={form.source === 'UPLOAD'} onChange={() => setForm({ ...form, source: 'UPLOAD' })} />Upload / gallery video</label>
          <label className={`studio-radio${form.source === 'LINK' ? ' active' : ''}`}><input type="radio" checked={form.source === 'LINK'} onChange={() => setForm({ ...form, source: 'LINK' })} />YouTube / Vimeo / Drive link</label>
        </div>
        {form.source === 'UPLOAD' ? (
          <>
            <label className="event-form-label">Video from this event's gallery<select required value={form.mediaId} onChange={(e) => setForm({ ...form, mediaId: e.target.value })}>
              <option value="">Choose a video…</option>
              {(videos.data || []).map((v) => <option key={v.mediaId} value={v.mediaId}>{v.filename}</option>)}
            </select></label>
            <label className="event-form-label">…or upload a new one (MP4/MOV)<input type="file" accept="video/mp4,video/quicktime,video/webm,.mp4,.mov,.m4v,.webm" disabled={progress !== null || !hasFeature(user, 'GALLERY')} onChange={(e) => upload(e.target.files?.[0])} />{progress !== null && <span className="field-hint">Uploading… {progress}%</span>}</label>
          </>
        ) : (
          <label className="event-form-label studio-span-2">Video link<input required type="url" pattern="https://.*" value={form.externalUrl} onChange={(e) => setForm({ ...form, externalUrl: e.target.value })} placeholder="https://youtu.be/…" /></label>
        )}
        <label className="studio-check"><input type="checkbox" checked={form.notifyClient} onChange={(e) => setForm({ ...form, notifyClient: e.target.checked })} />Email the client that it's ready</label>
        <div className="event-form-actions"><button className="button button-dark" type="submit" disabled={busy || progress !== null}>Add film</button></div>
      </form>
    </div>
  );
}

const DESIGN_STATUS = { QUEUED: 'Waiting to start', PROCESSING: 'Designing', READY: 'Ready', FAILED: 'Failed' };
const RECIPE_LABEL = { panorama: 'Panorama', 'hero-ghost': 'Feature photo', 'two-heroes': 'Two features', 'hero-page': 'Feature + collage', 'two-grids': 'Collage', grid: 'Collage' };

// AI album auto-design: lay out the client's submitted selection into print-ready spreads.
function AutoDesignPanel({ eventId, user, onSent }) {
  const allowed = hasFeature(user, 'AI_ALBUM_DESIGN');
  const designs = useLoad(() => (allowed ? api.albumDesigns(eventId) : Promise.resolve(null)), [eventId, allowed]);
  const [data, setData] = useState(null);
  const [form, setForm] = useState({ submissionId: '', size: '12x36', custom: '', sheets: 30, dpi: 300 });
  const [openDesign, setOpenDesign] = useState(null);
  const [spread, setSpread] = useState(0);
  const [message, setMessage] = useState('');
  const { busy, run, messages } = useAction();
  useEffect(() => {
    if (!designs.data) return;
    setData(designs.data);
    const first = designs.data.submissions?.[0];
    if (first) setForm((f) => (f.submissionId ? f : { ...f, submissionId: first.submission_id, sheets: Math.max(10, Math.min(40, Math.round(first.photo_count / 5))) }));
  }, [designs.data]);
  const working = (data?.submissions || []).flatMap((s) => s.designs).some((d) => d.status === 'QUEUED' || d.status === 'PROCESSING');
  useEffect(() => {
    if (!working) return undefined;
    const timer = setInterval(() => api.albumDesigns(eventId).then(setData).catch(() => {}), 4000);
    return () => clearInterval(timer);
  }, [working, eventId]);

  if (!allowed) return <UpgradeNotice feature="AI_ALBUM_DESIGN" title="AI album design">Turn your client's album selection into print-ready spreads in minutes — preview JPEGs, print files and layered Photoshop files to fine-tune.</UpgradeNotice>;
  if (!data) return <p className="customer-empty">{designs.error || 'Loading album selections…'}</p>;
  const submissions = data.submissions || [];
  const all = submissions.flatMap((s) => s.designs.map((d) => ({ ...d, photoCount: s.photo_count })));
  const shown = all.find((d) => d.design_id === openDesign) || all.find((d) => d.status === 'READY');
  const size = form.size === 'custom' ? form.custom : form.size;
  const [h, w] = size.split(/x/i).map(Number);
  const selected = submissions.find((s) => s.submission_id === form.submissionId);
  const perSpread = selected ? (selected.photo_count / Math.max(1, form.sheets)).toFixed(1) : null;

  return (
    <section className="customer-list-section growth-autodesign">
      <div className="customer-list-heading"><div><p className="eyebrow">AI album design</p><h3 className="studio-h3">Design the album from the client's selection</h3>
        <p className="event-form-note">Photos are placed in time order, scenes kept together, faces never cropped or put on the centre fold. Your editing style is applied; the logo is not.</p></div></div>
      {messages}
      {submissions.length === 0 ? <p className="customer-empty">Your client hasn't submitted their album selection yet. Once they do, you can design the album here.</p> : (
        <form className="studio-form" onSubmit={async (e) => {
          e.preventDefault();
          const r = await run(() => api.createAlbumDesign(eventId, { submissionId: form.submissionId, size, sheets: Number(form.sheets), dpi: Number(form.dpi) }), 'Designing your album — this takes about a minute per 10 spreads.');
          if (r) { setOpenDesign(r.design_id); api.albumDesigns(eventId).then(setData); }
        }}>
          <label className="event-form-label">Album selection<select required value={form.submissionId} onChange={(e) => setForm({ ...form, submissionId: e.target.value })}>
            {submissions.map((s) => <option key={s.submission_id} value={s.submission_id}>{shortDate(s.created_at.slice(0, 10))} · {s.photo_count} photos</option>)}
          </select></label>
          <label className="event-form-label">Album size (height × open spread, inches)<select value={form.size} onChange={(e) => setForm({ ...form, size: e.target.value })}>
            {(data.sizes || []).map((s) => <option key={s} value={s}>{s.replace('x', ' × ')}</option>)}
            <option value="custom">Custom…</option>
          </select></label>
          {form.size === 'custom' && <label className="event-form-label">Custom size<input required pattern="\d{1,2}(\.\d)?\s*[xX]\s*\d{2}(\.\d)?" placeholder="12x40" value={form.custom} onChange={(e) => setForm({ ...form, custom: e.target.value })} /></label>}
          <label className="event-form-label">Sheets (1 sheet = 1 spread)<input type="number" min="1" max="120" required value={form.sheets} onChange={(e) => setForm({ ...form, sheets: e.target.value })} />{perSpread && <span className="field-hint">≈ {perSpread} photos per spread</span>}</label>
          <label className="event-form-label">Print resolution<select value={form.dpi} onChange={(e) => setForm({ ...form, dpi: e.target.value })}>
            <option value="300">300 ppi (lab quality)</option><option value="240">240 ppi</option><option value="200">200 ppi (smaller files)</option>
          </select>{h && w ? <span className="field-hint">Each spread {Math.round(w * form.dpi)} × {Math.round(h * form.dpi)} px</span> : null}</label>
          <div className="event-form-actions studio-span-2"><button className="button button-dark" type="submit" disabled={busy || working || !form.submissionId}>{working ? 'Designing…' : 'Design album'}</button></div>
        </form>
      )}
      {all.length > 0 && (
        <div className="growth-design-list">{all.map((d) => (
          <button key={d.design_id} type="button" className={`growth-design-chip${shown?.design_id === d.design_id ? ' is-active' : ''}`} onClick={() => { setOpenDesign(d.design_id); setSpread(0); }}>
            v{d.version} · {d.size_label} · {d.sheets} sheets · <strong>{DESIGN_STATUS[d.status]}{d.status === 'PROCESSING' ? ` ${d.progress}%` : ''}</strong>
          </button>
        ))}</div>
      )}
      {all.filter((d) => d.status === 'PROCESSING' || d.status === 'QUEUED').map((d) => (
        <div key={d.design_id} className="growth-progress"><span style={{ width: `${Math.max(3, d.progress)}%` }} /></div>
      ))}
      {all.filter((d) => d.status === 'FAILED').slice(0, 1).map((d) => <p key={d.design_id} className="form-error">Version {d.version} failed: {d.error}</p>)}
      {shown && shown.status === 'READY' && (
        <div className="growth-design-view">
          <div className="growth-review-stage">
            <button className="button button-quiet button-small" type="button" disabled={spread <= 0} onClick={() => setSpread(spread - 1)} aria-label="Previous spread">‹</button>
            <img src={api.albumDesignSpreadUrl(eventId, shown.design_id, spread)} alt={`Spread ${spread + 1} of ${shown.spreads.length}`} />
            <button className="button button-quiet button-small" type="button" disabled={spread >= shown.spreads.length - 1} onClick={() => setSpread(spread + 1)} aria-label="Next spread">›</button>
          </div>
          <p className="event-form-note">Spread {spread + 1} of {shown.spreads.length} · {RECIPE_LABEL[shown.spreads[spread]?.recipe] || 'Collage'} · photos {shown.spreads[spread]?.photos.join(', ')}</p>
          <div className="growth-design-thumbs">{shown.spreads.map((s, i) => (
            <button key={i} type="button" className={i === spread ? 'is-active' : ''} onClick={() => setSpread(i)}><img loading="lazy" src={api.albumDesignSpreadUrl(eventId, shown.design_id, i)} alt={`Spread ${i + 1}`} /></button>
          ))}</div>
          <div className="event-form-actions">
            <a className="button button-quiet" href={api.albumDesignDownloadUrl(eventId, shown.design_id, 'jpeg')}>Download print JPEGs (ZIP)</a>
            <a className="button button-quiet" href={api.albumDesignDownloadUrl(eventId, shown.design_id, 'psd')}>Download Photoshop PSDs (ZIP)</a>
          </div>
          {hasFeature(user, 'ALBUM_PROOFING') && (
            <form className="studio-form" onSubmit={async (e) => {
              e.preventDefault();
              const r = await run(() => api.sendAlbumDesign(eventId, shown.design_id, message), (res) => `Sent to your client as proof version ${res[0].version}.`);
              if (r) { onSent(r); setMessage(''); }
            }}>
              <label className="event-form-label studio-span-2">Send this design to the client for review<input maxLength="1000" value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Here's the first draft of your album — comment on any spread." /></label>
              <div className="event-form-actions studio-span-2"><button className="button button-dark" type="submit" disabled={busy}>Send to client</button></div>
            </form>
          )}
        </div>
      )}
    </section>
  );
}

export function ProofsTab({ eventId, user }) {
  const proofs = useLoad(() => api.proofs(eventId), [eventId]);
  const folders = useLoad(() => api.dataFolders(eventId).then((r) => r?.folders || []).catch(() => []), [eventId]);
  const [list, setList] = useState(null);
  const [folderId, setFolderId] = useState('');
  const [message, setMessage] = useState('');
  const [progress, setProgress] = useState(null);
  const [replyTo, setReplyTo] = useState({});
  const { busy, run, messages, setError } = useAction();
  useEffect(() => { if (proofs.data) setList(proofs.data); }, [proofs.data]);
  // The client approves or asks for changes from their portal: refresh so the status here follows.
  useEffect(() => {
    const refresh = () => api.proofs(eventId).then(setList).catch(() => {});
    const timer = setInterval(refresh, 20000);
    window.addEventListener('focus', refresh);
    return () => { clearInterval(timer); window.removeEventListener('focus', refresh); };
  }, [eventId]);

  async function uploadSpreads(files) {
    if (!files?.length) return;
    setError('');
    try {
      const name = `Album v${(list?.[0]?.version || 0) + 1}`;
      const target = await ensureFolder(eventId, name);
      const sorted = Array.from(files).sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
      for (let i = 0; i < sorted.length; i++) {
        setProgress(`${i + 1}/${sorted.length}`);
        await api.uploadDataFile(eventId, target, sorted[i], () => {});
      }
      folders.reload();
      setFolderId(target);
    } catch (requestError) { setError(requestError.message); } finally { setProgress(null); }
  }

  if (!list) return <p className="customer-empty">{proofs.error || 'Loading…'}</p>;
  const latest = list[0];
  return (
    <div className="growth-stack">
      <AutoDesignPanel eventId={eventId} user={user} onSent={setList} />
      {messages}
      <form className="studio-form customer-list-section" onSubmit={async (e) => {
        e.preventDefault();
        const r = await run(() => api.sendProof(eventId, { folderId, message }), (res) => `Version ${res[0].version} sent to your client.`);
        if (r) { setList(r); setMessage(''); }
      }}>
        <h3 className="studio-h3 studio-span-2">{latest ? `Send version ${latest.version + 1}` : 'Send your album design'}</h3>
        <label className="event-form-label">Upload spreads (JPG/PNG, in order)<input type="file" multiple accept="image/jpeg,image/png" disabled={progress !== null || !hasFeature(user, 'GALLERY')} onChange={(e) => uploadSpreads(e.target.files)} />{progress && <span className="field-hint">Uploading {progress}…</span>}</label>
        <label className="event-form-label">…or use a gallery folder<select required value={folderId} onChange={(e) => setFolderId(e.target.value)}>
          <option value="">Choose a folder…</option>
          {(folders.data || []).map((f) => <option key={f.folder_id} value={f.folder_id}>{f.name}</option>)}
        </select></label>
        <label className="event-form-label studio-span-2">Message to the client<input maxLength="1000" value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Here's the first draft — comment on any spread you'd like changed." /></label>
        <p className="event-form-note studio-span-2">Spreads are shown in file-name order (spread-2 before spread-10). Previews follow your watermark setting.</p>
        <div className="event-form-actions"><button className="button button-dark" type="submit" disabled={busy || !folderId || progress !== null}>Send for review</button></div>
      </form>
      {list.map((proof) => (
        <section key={proof.id} className={`customer-list-section growth-proof${proof.status === 'SUPERSEDED' ? ' is-muted' : ''}`}>
          <div className="customer-list-heading">
            <div><p className="eyebrow">Version {proof.version} · {shortDate(proof.createdAt.slice(0, 10))}</p><h3>{PROOF_STATUS[proof.status]}</h3>{proof.customerNote && <p>Client: “{proof.customerNote}”</p>}</div>
            <span className="studio-pill">{proof.spreadCount} spreads · {proof.comments.filter((c) => c.author === 'CUSTOMER' && !c.resolved).length} open comments</span>
          </div>
          <div className="growth-spreads">{proof.mediaIds.map((mediaId, index) => {
            const comments = proof.comments.filter((c) => c.spreadIndex === index);
            return (
              <figure key={mediaId} className="growth-spread">
                <img src={api.photographerPreviewUrl(eventId, mediaId)} alt={`Spread ${index + 1}`} loading="lazy" />
                <figcaption>Spread {index + 1}</figcaption>
                {comments.map((c) => (
                  <p key={c.id} className={`growth-comment growth-comment-${c.author.toLowerCase()}${c.resolved ? ' is-resolved' : ''}`}>
                    <b>{c.author === 'CUSTOMER' ? 'Client' : 'You'}:</b> {c.body}
                    {c.author === 'CUSTOMER' && proof.status !== 'SUPERSEDED' && <button className="customer-id-link" type="button" onClick={async () => { const r = await run(() => api.resolveProofComment(eventId, proof.id, c.id, !c.resolved)); if (r) setList(r); }}>{c.resolved ? 'Reopen' : 'Resolve'}</button>}
                  </p>
                ))}
                {proof.status !== 'SUPERSEDED' && proof.status !== 'APPROVED' && comments.length > 0 && (
                  <form className="growth-reply" onSubmit={async (e) => {
                    e.preventDefault();
                    const body = replyTo[`${proof.id}-${index}`];
                    const r = await run(() => api.replyProof(eventId, proof.id, { spreadIndex: index, body }));
                    if (r) { setList(r); setReplyTo({ ...replyTo, [`${proof.id}-${index}`]: '' }); }
                  }}>
                    <input required maxLength="2000" placeholder="Reply…" value={replyTo[`${proof.id}-${index}`] || ''} onChange={(e) => setReplyTo({ ...replyTo, [`${proof.id}-${index}`]: e.target.value })} />
                  </form>
                )}
              </figure>
            );
          })}</div>
        </section>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Client portal: delivery tracker, films, album review
// ---------------------------------------------------------------------------------------------

export function CustomerDeliveryModal({ event, onClose }) {
  const delivery = useLoad(() => api.customerDelivery(event.eventId), [event.eventId]);
  const [proof, setProof] = useState(null);
  useEffect(() => { if (delivery.data) setProof(delivery.data.proof); }, [delivery.data]);
  const data = delivery.data;
  return (
    <div style={brandStyle(data?.brand)}>
      <Modal eyebrow={`${event.eventName} · ${event.photographerName}`} title="Your delivery" onClose={onClose} wide>
        {!data ? <p className="customer-empty">{delivery.error || 'Loading…'}</p> : (
          <div className="growth-stack">
            {data.brand?.logoUrl && <div className="growth-brand-row"><StudioMark brand={data.brand} /><b>{data.brand.studioName}</b></div>}
            {proof && proof.status === 'SENT' && <CustomerProofReview eventId={event.eventId} proof={proof} onChange={setProof} />}
            <section>
              <h3 className="studio-h3">Progress</h3>
              {!data.steps.length ? <p className="customer-empty">Your photographer will share delivery milestones here.</p> : (
                <ol className="growth-timeline">{data.steps.map((step) => (
                  <li key={step.id} className={`growth-timeline-${step.status.toLowerCase()}`}>
                    <span className="growth-step-dot" aria-hidden="true">{step.status === 'DONE' ? '✓' : ''}</span>
                    <div><b>{step.title}</b>
                      <small>{step.status === 'DONE' ? `Ready${step.completedAt ? ` · ${new Date(step.completedAt).toLocaleDateString()}` : ''}` : step.status === 'IN_PROGRESS' ? 'In progress' : 'Coming up'}{step.dueDate && step.status !== 'DONE' ? ` · expected by ${shortDate(step.dueDate)}` : ''}</small>
                      {step.note && <p>{step.note}</p>}
                    </div>
                  </li>
                ))}</ol>
              )}
            </section>
            {data.films.length > 0 && (
              <section>
                <h3 className="studio-h3">Films</h3>
                <div className="growth-films">{data.films.map((film) => (
                  <article key={film.id} className="growth-film">
                    <FilmPlayer film={film} src={api.customerFilmUrl(event.eventId, film.id)} />
                    <div><b>{film.title}</b><span>{FILM_KINDS.find(([v]) => v === film.kind)?.[1]}</span></div>
                  </article>
                ))}</div>
              </section>
            )}
            {proof && proof.status !== 'SENT' && <CustomerProofReview eventId={event.eventId} proof={proof} onChange={setProof} />}
          </div>
        )}
      </Modal>
    </div>
  );
}

function CustomerProofReview({ eventId, proof, onChange }) {
  const [index, setIndex] = useState(0);
  const [comment, setComment] = useState('');
  const [note, setNote] = useState('');
  const { busy, run, messages } = useAction();
  const open = proof.status === 'SENT' || proof.status === 'CHANGES_REQUESTED';
  const comments = proof.comments.filter((c) => c.spreadIndex === index);
  const commentCount = (i) => proof.comments.filter((c) => c.spreadIndex === i && c.author === 'CUSTOMER').length;
  return (
    <section className="growth-review">
      <div className="customer-list-heading"><div><h3 className="studio-h3">Album design · version {proof.version}</h3>
        <p className="event-form-note">{proof.status === 'APPROVED' ? 'You approved this design. 🎉' : proof.status === 'CHANGES_REQUESTED' ? 'You asked for changes; your photographer is on it.' : proof.message || 'Leave a comment on any spread, then approve or ask for changes.'}</p></div></div>
      {messages}
      <div className="growth-review-stage">
        <button className="button button-quiet button-small" type="button" disabled={index === 0} onClick={() => setIndex(index - 1)} aria-label="Previous spread">‹</button>
        <img src={api.customerSpreadUrl(eventId, proof.id, index)} alt={`Spread ${index + 1} of ${proof.spreadCount}`} />
        <button className="button button-quiet button-small" type="button" disabled={index >= proof.spreadCount - 1} onClick={() => setIndex(index + 1)} aria-label="Next spread">›</button>
      </div>
      <div className="growth-thumbs">{Array.from({ length: proof.spreadCount }, (_, i) => (
        <button key={i} type="button" className={i === index ? 'active' : ''} onClick={() => setIndex(i)}>{i + 1}{commentCount(i) > 0 && <sup>{commentCount(i)}</sup>}</button>
      ))}</div>
      <div className="growth-review-comments">
        <b>Spread {index + 1}</b>
        {comments.length === 0 && <p className="customer-empty">No comments on this spread.</p>}
        {comments.map((c) => <p key={c.id} className={`growth-comment growth-comment-${c.author.toLowerCase()}${c.resolved ? ' is-resolved' : ''}`}><b>{c.author === 'CUSTOMER' ? 'You' : 'Studio'}:</b> {c.body}{c.resolved ? ' ✓' : ''}</p>)}
        {open && (
          <form className="growth-inline-form" onSubmit={async (e) => {
            e.preventDefault();
            const r = await run(() => api.commentProof(eventId, proof.id, { spreadIndex: index, body: comment }), 'Comment added.');
            if (r) { onChange(r); setComment(''); }
          }}>
            <input required maxLength="2000" value={comment} onChange={(e) => setComment(e.target.value)} placeholder="e.g. Swap the left photo for one with grandparents" />
            <button className="button button-quiet button-small" type="submit" disabled={busy}>Comment</button>
          </form>
        )}
      </div>
      {open && proof.status === 'SENT' && (
        <div className="growth-decision">
          <input maxLength="2000" value={note} onChange={(e) => setNote(e.target.value)} placeholder="Overall note (optional)" />
          <button className="button button-quiet" type="button" disabled={busy} onClick={async () => { const r = await run(() => api.requestProofChanges(eventId, proof.id, note), 'Sent to your photographer.'); if (r) onChange(r); }}>Request changes</button>
          <button className="button button-dark" type="button" disabled={busy} onClick={async () => { if (!window.confirm('Approve this album design for printing?')) return; const r = await run(() => api.approveProof(eventId, proof.id, note), 'Approved! Your photographer will send it for printing.'); if (r) onChange(r); }}>Approve design</button>
        </div>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------------------------
// Settings: branding, watermark, custom domain
// ---------------------------------------------------------------------------------------------

export function BrandingSettings() {
  const brand = useLoad(() => api.brand(), []);
  const [data, setData] = useState(null);
  const [form, setForm] = useState(null);
  const [domain, setDomain] = useState('');
  const { busy, run, messages } = useAction();
  useEffect(() => { if (brand.data) setData(brand.data); }, [brand.data]);
  useEffect(() => {
    if (!data) return;
    setForm({ brandColor: data.brandColor || '', watermarkMode: data.watermarkMode, watermarkText: data.watermarkText || '', watermarkUseLogo: data.watermarkUseLogo, watermarkPosition: data.watermarkPosition, watermarkOpacity: data.watermarkOpacity });
    setDomain(data.customDomain || '');
  }, [data]);
  if (!data || !form) return <p className="customer-empty">{brand.error || 'Loading…'}</p>;
  const save = async (e) => { e.preventDefault(); const r = await run(() => api.saveBrand({ ...form, watermarkOpacity: Number(form.watermarkOpacity) }), 'Branding saved.'); if (r) setData(r); };
  return (
    <div className="growth-stack">
      {messages}
      <form className="customer-list-section studio-settings" onSubmit={save}>
        <fieldset><legend>Your brand</legend>
          <div className="growth-logo-field studio-span-2">
            {data.logoUrl ? <img src={api.assetUrl(data.logoUrl)} alt="Studio logo" /> : <span className="growth-logo-empty">No logo</span>}
            <label className="button button-quiet button-small">Upload logo<input hidden type="file" accept="image/png,image/jpeg" disabled={busy || (!data.canBrand && !data.canWatermark)} onChange={async (e) => { const file = e.target.files?.[0]; if (file) { const r = await run(() => api.uploadLogo(file), 'Logo uploaded.'); if (r) setData(r); } }} /></label>
            {data.logoUrl && <button className="customer-id-link studio-danger-link" type="button" onClick={async () => { const r = await run(() => api.deleteLogo(), 'Logo removed.'); if (r) setData(r); }}>Remove</button>}
            <span className="field-hint">PNG with a transparent background works best (max 1 MB).</span>
          </div>
          <label className="event-form-label">Brand colour<span className="growth-color"><input type="color" value={form.brandColor || '#9a7b3f'} onChange={(e) => setForm({ ...form, brandColor: e.target.value })} disabled={!data.canBrand} /><input value={form.brandColor} placeholder="#9A7B3F" maxLength="7" onChange={(e) => setForm({ ...form, brandColor: e.target.value })} disabled={!data.canBrand} /></span></label>
          <p className="event-form-note">Your logo and colour appear on the quotation page, enquiry form and client portal{data.canBrand ? '' : ' (Studio plan and above)'}.</p>
        </fieldset>
        <fieldset><legend>Watermark on previews</legend>
          <label className="event-form-label">When<select value={form.watermarkMode} onChange={(e) => setForm({ ...form, watermarkMode: e.target.value })} disabled={!data.canWatermark}>
            <option value="OFF">Off</option><option value="UNTIL_PAID">Until the client has paid in full</option><option value="ALWAYS">Always</option>
          </select></label>
          <label className="event-form-label">Position<select value={form.watermarkPosition} onChange={(e) => setForm({ ...form, watermarkPosition: e.target.value })} disabled={!data.canWatermark}>
            <option value="TILED">Tiled across the photo</option><option value="CENTER">Centre</option><option value="BOTTOM_RIGHT">Bottom-right corner</option>
          </select></label>
          <label className="event-form-label">Text<input maxLength="60" value={form.watermarkText} onChange={(e) => setForm({ ...form, watermarkText: e.target.value })} placeholder={`© ${data.studioName}`} disabled={!data.canWatermark || form.watermarkUseLogo} /></label>
          <label className="event-form-label">Opacity {form.watermarkOpacity}%<input type="range" min="5" max="80" value={form.watermarkOpacity} onChange={(e) => setForm({ ...form, watermarkOpacity: e.target.value })} disabled={!data.canWatermark} /></label>
          <label className="studio-check studio-span-2"><input type="checkbox" checked={form.watermarkUseLogo} disabled={!data.canWatermark || !data.logoUrl} onChange={(e) => setForm({ ...form, watermarkUseLogo: e.target.checked })} />Use my logo instead of text</label>
          <p className="event-form-note studio-span-2">Applies to client gallery previews and album proofs. Downloads and originals are never watermarked.{data.canWatermark ? '' : ' Available on Solo and above.'}</p>
          <div className="event-form-actions studio-span-2"><button className="button button-dark" type="submit" disabled={busy}>Save branding</button></div>
        </fieldset>
      </form>
      <section className="customer-list-section studio-settings">
        <fieldset><legend>Custom domain</legend>
          {!data.canUseCustomDomain ? <p className="event-form-note studio-span-2">Use your own domain (e.g. photos.yourstudio.com) for your portfolio and enquiry form on the Business plan.</p> : (
            <>
              <label className="event-form-label">Domain<input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="photos.yourstudio.com" /></label>
              <div className="growth-inline-actions">
                <button className="button button-quiet button-small" type="button" disabled={busy || !domain} onClick={async () => { const r = await run(() => api.setDomain(domain), 'Domain saved. Add the DNS record below, then verify.'); if (r) setData(r); }}>Save</button>
                {data.customDomain && <button className="button button-dark button-small" type="button" disabled={busy} onClick={async () => { const r = await run(() => api.verifyDomain(), 'Verified! Your domain is live (the HTTPS certificate is issued on first visit).'); if (r) setData(r); }}>Verify</button>}
                {data.customDomain && <button className="customer-id-link studio-danger-link" type="button" disabled={busy} onClick={async () => { const r = await run(() => api.removeDomain(), 'Domain removed.'); if (r) setData(r); }}>Remove</button>}
              </div>
              {data.customDomain && (
                <div className="studio-span-2 growth-dns">
                  <p>{data.customDomainVerifiedAt ? <span className="studio-paid">✓ {data.customDomain} is connected.</span> : <>Add this record at your domain provider (GoDaddy, Hostinger, etc.):</>}</p>
                  {!data.customDomainVerifiedAt && <table className="customer-table"><thead><tr><th>Type</th><th>Name</th><th>Value</th></tr></thead><tbody><tr><td>CNAME</td><td>{data.customDomain.split('.')[0]}</td><td>{data.customDomainTarget}</td></tr></tbody></table>}
                </div>
              )}
            </>
          )}
        </fieldset>
      </section>
    </div>
  );
}

// ---- Editing style: learn the studio's look from its own finished photos; stamp the logo away from faces
export function EditingStyleSettings() {
  const loaded = useLoad(() => api.editingStyle(), []);
  const [data, setData] = useState(null);
  const [form, setForm] = useState(null);
  const [sample, setSample] = useState(null);
  const [preview, setPreview] = useState({ before: '', after: '' });
  const [previewBusy, setPreviewBusy] = useState(false);
  const { busy, run, messages, setError } = useAction();
  useEffect(() => { if (loaded.data) setData(loaded.data); }, [loaded.data]);
  useEffect(() => { if (data) setForm({ strength: data.strength, applyStyle: data.apply_style, stampLogo: data.stamp_logo }); }, [data]);
  useEffect(() => () => { if (preview.before) URL.revokeObjectURL(preview.before); if (preview.after) URL.revokeObjectURL(preview.after); }, [preview]);
  if (!data || !form) return <p className="customer-empty">{loaded.error || 'Loading…'}</p>;
  if (!data.planAllows) return <UpgradeNotice feature="CUSTOM_BRANDING" title="Editing style">Teach FrameBucket your editing look from your own finished photos and apply it to every upload, with your logo placed clear of faces.</UpgradeNotice>;

  const learn = async (files) => {
    if (files.length < 5) { setError('Choose at least 5 finished photos (up to 40) that show your look.'); return; }
    const r = await run(() => api.uploadStyleReferences(files.slice(0, 40)), `Learned your look from ${Math.min(files.length, 40)} photos.`);
    if (r) setData(r);
  };
  const save = async (e) => { e.preventDefault(); const r = await run(() => api.saveEditingStyle({ strength: Number(form.strength), applyStyle: form.applyStyle, stampLogo: form.stampLogo }), 'Saved. New uploads will use these settings.'); if (r) setData(r); };
  const tryOn = async (file, strength = form.strength) => {
    if (!file) return;
    setSample(file); setPreviewBusy(true); setError('');
    try {
      const after = await api.previewEditingStyle(file, Number(strength));
      setPreview({ before: URL.createObjectURL(file), after: URL.createObjectURL(after) });
    } catch (requestError) { setError(requestError.message); } finally { setPreviewBusy(false); }
  };
  const summary = data.summary;

  return (
    <div className="growth-stack">
      {messages}
      <section className="customer-list-section studio-settings">
        <fieldset><legend>Your look</legend>
          {data.has_style ? (
            <div className="studio-span-2 growth-style-summary">
              <div className="growth-swatches">{(summary?.swatches || []).map((colour) => <span key={colour} style={{ background: colour }} title={colour} />)}</div>
              <div><strong>Learned from {data.reference_count} photos</strong><p>{(summary?.words || []).join(' · ')}</p></div>
            </div>
          ) : <p className="event-form-note studio-span-2">Upload 15–40 of your own finished photos that show the look you want: a mix of portraits, candids and décor, in different light. FrameBucket learns the tones, colours and contrast and applies the same look to every photo you upload.</p>}
          <div className="growth-inline-actions studio-span-2">
            <label className="button button-dark button-small">{data.has_style ? 'Re-learn from new photos' : 'Choose reference photos'}<input hidden multiple type="file" accept="image/jpeg,image/png,image/webp" disabled={busy} onChange={(e) => { const files = Array.from(e.target.files || []); e.target.value = ''; learn(files); }} /></label>
            {data.has_style && <button className="customer-id-link studio-danger-link" type="button" disabled={busy} onClick={async () => { const r = await run(() => api.clearEditingStyle(), 'Look removed. New uploads get basic corrections only.'); if (r) { setData(r); setPreview({ before: '', after: '' }); } }}>Remove look</button>}
            {busy && <span className="field-hint">Analysing photos…</span>}
          </div>
          <p className="event-form-note studio-span-2">Use photos you edited yourself. FrameBucket also learns how your camera's photos look before editing ({data.baseline_samples} uploads so far); the match improves after about 30 uploads.</p>
        </fieldset>
      </section>

      <form className="customer-list-section studio-settings" onSubmit={save}>
        <fieldset><legend>When photos are processed</legend>
          <label className="studio-check studio-span-2"><input type="checkbox" checked={form.applyStyle} disabled={!data.has_style} onChange={(e) => setForm({ ...form, applyStyle: e.target.checked })} />Apply my look to new uploads</label>
          <label className="event-form-label studio-span-2">Strength {form.strength}%<input type="range" min="0" max="100" step="5" value={form.strength} disabled={!data.has_style} onChange={(e) => setForm({ ...form, strength: e.target.value })} onMouseUp={() => sample && tryOn(sample)} onTouchEnd={() => sample && tryOn(sample)} /></label>
          <label className="studio-check studio-span-2"><input type="checkbox" checked={form.stampLogo} onChange={(e) => setForm({ ...form, stampLogo: e.target.checked })} />Add my logo to delivered photos (placed in a corner, never over a face)</label>
          {!data.has_logo && <p className="event-form-note studio-span-2">Upload your logo under <strong>Branding &amp; watermark</strong> first. A PNG with a transparent background works best.</p>}
          <p className="event-form-note studio-span-2">Applies to photos uploaded from now on. Originals are always kept untouched; photos already in galleries are not changed.</p>
          <div className="event-form-actions studio-span-2"><button className="button button-dark" type="submit" disabled={busy}>Save</button></div>
        </fieldset>
      </form>

      {data.has_style && (
        <section className="customer-list-section studio-settings">
          <fieldset><legend>Try it on a photo</legend>
            <div className="growth-inline-actions studio-span-2">
              <label className="button button-quiet button-small">Choose a sample photo<input hidden type="file" accept="image/jpeg,image/png,image/webp,image/tiff" onChange={(e) => { const file = e.target.files?.[0]; e.target.value = ''; tryOn(file); }} /></label>
              {previewBusy && <span className="field-hint">Rendering preview…</span>}
            </div>
            {preview.after && (
              <div className="growth-before-after studio-span-2">
                <figure><img src={preview.before} alt="Your photo before" /><figcaption>Before</figcaption></figure>
                <figure><img src={preview.after} alt="With your look and logo" /><figcaption>With your look{form.stampLogo && data.has_logo ? ' and logo' : ''} · {form.strength}%</figcaption></figure>
              </div>
            )}
          </fieldset>
        </section>
      )}
    </div>
  );
}

export { inr };
