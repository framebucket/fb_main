import { useEffect, useRef, useState } from 'react';
import { api } from '../../api.js';
import { navigate } from '../router.js';
import { useUser } from '../session.js';
import { Button, Empty, ErrorNote, Fab, Field, Icon, Layer, Screen, Sheet, Skeleton, useAction, useBackToClose, useResource, useToast } from '../ui.jsx';
import { openFile, plural } from '../format.js';
import { enqueueUploads, useUploadFinished } from '../uploads.js';
import { useEvent } from './EventDetail.jsx';

const RAW_ACCEPT = 'image/*,.arw,.cr2,.cr3,.dng,.nef,.orf,.raf,.rw2,video/mp4,video/quicktime,video/webm,.avi,.m4v';

export default function GalleryScreen({ eventId, folder, upload }) {
  const { user } = useUser();
  const event = useEvent(eventId);
  const [folderId, setFolderId] = useState(folder || 'root');
  const folders = useResource(() => api.dataFolders(eventId).then((result) => result.folders || []), [eventId]);
  const media = useResource(() => api.photographerMedia(eventId, folderId).then((result) => result.photos || []), [eventId, folderId]);
  const [sheet, setSheet] = useState(upload ? 'upload' : '');
  const [viewer, setViewer] = useState(-1);

  const items = media.data || [];
  const processing = items.filter((item) => item.status === 'QUEUED' || item.status === 'PROCESSING').length;

  // Keep previews fresh while the server is still processing.
  useEffect(() => {
    if (!processing) return undefined;
    const timer = window.setInterval(() => {
      api.photographerMedia(eventId, folderId).then((result) => media.setData(result.photos || [])).catch(() => {});
    }, 4000);
    return () => window.clearInterval(timer);
  }, [processing, eventId, folderId, media.setData]);

  useUploadFinished(eventId, folderId, () => media.reload());

  useEffect(() => {
    // Remember the folder in the URL so back/forward and refresh keep it.
    navigate(`/events/${eventId}/gallery${folderId !== 'root' ? `?folder=${encodeURIComponent(folderId)}` : ''}`, { replace: true });
  }, [eventId, folderId]);

  if (!user.dataModuleEnabled) {
    return (
      <Screen back title="Gallery">
        <Empty icon="image" title="Online galleries" action={<Button onClick={() => navigate('/more/plan')}>See plans</Button>}>Uploading photos and sharing galleries isn't part of your current plan.</Empty>
      </Screen>
    );
  }

  const allFolders = folders.data?.length ? folders.data : [{ folder_id: 'root', name: 'Gallery' }];
  const currentName = allFolders.find((item) => item.folder_id === folderId)?.name || 'Gallery';

  return (
    <Screen back backTo={`/events/${eventId}`} title={currentName} subtitle={event.data ? `${event.data.eventTypeName} · ${event.data.customerName}` : ''}
      onRefresh={() => Promise.all([folders.reload(), media.reload()])} flush
      actions={<button type="button" className="m-icon-button" onClick={() => setSheet('folder')} aria-label="New folder"><Icon name="folder" /></button>}
      fab={<Fab extended icon="upload" label="Upload" onClick={() => setSheet('upload')} />}>
      <div className="m-chip-scroller" role="tablist">
        {allFolders.map((item) => (
          <button key={item.folder_id} type="button" role="tab" aria-selected={item.folder_id === folderId} className={item.folder_id === folderId ? 'is-active' : ''} onClick={() => setFolderId(item.folder_id)}>
            <Icon name="folder" size={15} />{item.name}
          </button>
        ))}
        <button type="button" className="m-chip-add" onClick={() => setSheet('folder')}><Icon name="plus" size={15} />Folder</button>
      </div>
      <ErrorNote message={media.error || folders.error} onRetry={media.reload} />
      {media.data === undefined ? <div className="m-pad"><Skeleton rows={3} /></div> : !items.length ? (
        <Empty icon="image" title="This folder is empty" action={<Button icon="upload" onClick={() => setSheet('upload')}>Upload photos</Button>}>
          Photos you upload are processed into previews for the customer gallery. Originals stay private.
        </Empty>
      ) : (
        <>
          <p className="m-gallery-count">{plural(items.length, 'item')}{processing ? ` · ${processing} processing` : ''}</p>
          <div className="m-photo-grid">
            {items.map((item, index) => <Thumb key={item.photo_id} item={item} eventId={eventId} onOpen={() => setViewer(index)} />)}
          </div>
        </>
      )}
      <UploadSheet open={sheet === 'upload'} onClose={() => setSheet('')} eventId={eventId} folderId={folderId} folderName={currentName} />
      <NewFolderSheet open={sheet === 'folder'} onClose={() => setSheet('')} eventId={eventId} onCreated={(created) => { folders.setData((current) => [...(current || []), created]); setFolderId(created.folder_id); }} />
      {viewer >= 0 && <Viewer items={items} index={viewer} eventId={eventId} onIndex={setViewer} onClose={() => setViewer(-1)} />}
    </Screen>
  );
}

function Thumb({ item, eventId, onOpen }) {
  const pending = item.status === 'QUEUED' || item.status === 'PROCESSING';
  const failed = item.status === 'PROCESSING_FAILED';
  return (
    <button type="button" className="m-thumb" onClick={onOpen} aria-label={item.original_filename}>
      {failed ? <span className="m-thumb-state is-failed"><Icon name="alert" size={20} />Failed</span>
        : pending ? <span className="m-thumb-state"><span className="m-spinner" />{item.status === 'QUEUED' ? 'Queued' : 'Processing'}</span>
          : item.media_type === 'video' ? <><video src={`${api.photographerPreviewUrl(eventId, item.photo_id)}#t=0.5`} preload="metadata" muted playsInline /><span className="m-thumb-badge"><Icon name="video" size={14} /></span></>
            : <img src={api.photographerPreviewUrl(eventId, item.photo_id)} alt="" loading="lazy" />}
    </button>
  );
}

function Viewer({ items, index, eventId, onIndex, onClose }) {
  useBackToClose(true, onClose);
  const [busy, run] = useAction();
  const [dragX, setDragX] = useState(0);
  const start = useRef(null);
  const item = items[index];
  if (!item) return null;
  const ready = item.status !== 'QUEUED' && item.status !== 'PROCESSING' && item.status !== 'PROCESSING_FAILED';
  const go = (next) => { if (next >= 0 && next < items.length) onIndex(next); };
  return (
    <Layer>
    <div className="m-viewer" role="dialog" aria-modal="true"
      onTouchStart={(event) => { start.current = event.touches[0].clientX; }}
      onTouchMove={(event) => { if (start.current !== null) setDragX(event.touches[0].clientX - start.current); }}
      onTouchEnd={() => { if (dragX < -60) go(index + 1); else if (dragX > 60) go(index - 1); setDragX(0); start.current = null; }}>
      <header className="m-viewer-bar">
        <button type="button" className="m-icon-button" onClick={onClose} aria-label="Close"><Icon name="close" /></button>
        <div><b>{item.media_type === 'video' ? 'Video' : `#${item.photo_number}`}</b><span>{index + 1} of {items.length}</span></div>
        <button type="button" className="m-icon-button" disabled={busy} aria-label="Download original"
          onClick={() => run(() => openFile(api.photographerOriginalUrl(eventId, item.photo_id), item.original_filename))}><Icon name="download" /></button>
      </header>
      <div className="m-viewer-stage" style={{ transform: `translateX(${dragX}px)` }}>
        {!ready ? <div className="m-viewer-note">{item.status === 'PROCESSING_FAILED' ? 'Processing failed. Download the original and upload it again.' : 'Still processing…'}</div>
          : item.media_type === 'video' ? <video key={item.photo_id} src={api.photographerPreviewUrl(eventId, item.photo_id)} controls playsInline autoPlay />
            : <img key={item.photo_id} src={api.photographerPreviewUrl(eventId, item.photo_id)} alt={item.original_filename} />}
      </div>
      <footer className="m-viewer-foot">
        <span>{item.original_filename}</span>
        {item.width && <small>{item.width} × {item.height}</small>}
      </footer>
      {index > 0 && <button type="button" className="m-viewer-nav is-prev" onClick={() => go(index - 1)} aria-label="Previous"><Icon name="back" /></button>}
      {index < items.length - 1 && <button type="button" className="m-viewer-nav is-next" onClick={() => go(index + 1)} aria-label="Next"><Icon name="chevron" /></button>}
    </div>
    </Layer>
  );
}

function UploadSheet({ open, onClose, eventId, folderId, folderName }) {
  const toast = useToast();
  const pick = (event) => {
    const files = Array.from(event.target.files || []);
    event.target.value = '';
    if (!files.length) return;
    enqueueUploads(eventId, folderId, files);
    toast(`Uploading ${plural(files.length, 'file')} to ${folderName}. You can keep using the app.`);
    onClose();
  };
  return (
    <Sheet open={open} title={`Upload to ${folderName}`} onClose={onClose}>
      <div className="m-upload-options">
        <label className="m-upload-option">
          <span className="m-upload-icon"><Icon name="image" /></span>
          <span><b>Photos &amp; videos</b><small>From your phone's gallery</small></span>
          <input type="file" multiple accept="image/*,video/*" onChange={pick} />
        </label>
        <label className="m-upload-option">
          <span className="m-upload-icon"><Icon name="camera" /></span>
          <span><b>Take a photo</b><small>Use the camera now</small></span>
          <input type="file" accept="image/*" capture="environment" onChange={pick} />
        </label>
        <label className="m-upload-option">
          <span className="m-upload-icon"><Icon name="file" /></span>
          <span><b>Files</b><small>RAW and other files from storage or a card reader</small></span>
          <input type="file" multiple accept={RAW_ACCEPT} onChange={pick} />
        </label>
      </div>
      <p className="m-muted m-small">Keep the app open until uploads finish. Originals stay private; customers only see processed previews.</p>
    </Sheet>
  );
}

function NewFolderSheet({ open, onClose, eventId, onCreated }) {
  const [name, setName] = useState('');
  const [busy, run] = useAction();
  async function submit(event) {
    event.preventDefault();
    const created = await run(() => api.createDataFolder(eventId, name.trim()), (result) => `Folder “${result.name}” created.`);
    if (created) { setName(''); onClose(); onCreated(created); }
  }
  return (
    <Sheet open={open} title="New folder" onClose={onClose}>
      <form className="m-form" onSubmit={submit}>
        <Field label="Folder name"><input value={name} onChange={(event) => setName(event.target.value)} maxLength="80" placeholder="Haldi, Ceremony, Reception…" required autoFocus /></Field>
        <Button type="submit" block busy={busy} disabled={!name.trim()}>Create folder</Button>
      </form>
    </Sheet>
  );
}
