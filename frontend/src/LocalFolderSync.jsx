import { useEffect, useRef, useState } from 'react';
import { api } from './api.js';

const POLL_INTERVAL_MS = 5000;
const FILE_STABLE_MS = 10000;
const SYNC_DB_NAME = 'framebucket-gallery-sync';
const SYNC_STORE_NAME = 'uploaded-files';
const SUPPORTED_EXTENSIONS = new Set([
  '.arw', '.cr2', '.cr3', '.dng', '.nef', '.orf', '.raf', '.rw2',
  '.bmp', '.jpeg', '.jpg', '.png', '.tif', '.tiff', '.webp',
  '.mp4', '.mov', '.m4v', '.webm', '.avi',
]);

let databasePromise;
const uploadedInMemory = new Set();

function openSyncDatabase() {
  if (!globalThis.indexedDB) return Promise.reject(new Error('IndexedDB is unavailable.'));
  if (!databasePromise) {
    databasePromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(SYNC_DB_NAME, 1);
      request.onupgradeneeded = () => {
        const database = request.result;
        if (!database.objectStoreNames.contains(SYNC_STORE_NAME)) {
          database.createObjectStore(SYNC_STORE_NAME);
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('Could not open local sync history.'));
      request.onblocked = () => reject(new Error('Close another FrameBucket tab to initialize local sync history.'));
    });
  }
  return databasePromise;
}

function databaseRequest(mode, run) {
  return openSyncDatabase().then((database) => new Promise((resolve, reject) => {
    const transaction = database.transaction(SYNC_STORE_NAME, mode);
    const request = run(transaction.objectStore(SYNC_STORE_NAME));
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error('Could not update local sync history.'));
    transaction.onabort = () => reject(transaction.error || new Error('Local sync history transaction was aborted.'));
  }));
}

async function hasUploaded(key) {
  if (uploadedInMemory.has(key)) return true;
  try {
    const result = await databaseRequest('readonly', (store) => store.get(key));
    if (result) uploadedInMemory.add(key);
    return Boolean(result);
  } catch {
    return false;
  }
}

async function rememberUploaded(key) {
  uploadedInMemory.add(key);
  try {
    await databaseRequest('readwrite', (store) => store.put(Date.now(), key));
  } catch {
    // Keep this run working if the browser blocks persistent local storage.
  }
}

function extensionOf(filename) {
  const dot = filename.lastIndexOf('.');
  return dot < 0 ? '' : filename.slice(dot).toLowerCase();
}

function hashText(value) {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash = Math.imul(hash ^ value.charCodeAt(index), 16777619);
  }
  return (hash >>> 0).toString(36).slice(0, 7);
}

function galleryFolderName(rootName, relativeDirectory) {
  const suffix = relativeDirectory.split('/').filter(Boolean).join(' - ');
  const fullName = `${rootName} — ${suffix}`;
  if (fullName.length <= 80) return fullName;
  return `${fullName.slice(0, 70).trim()}-${hashText(fullName)}`;
}

function fileIdentity(eventId, baseFolderId, rootName, relativePath, file) {
  return JSON.stringify([
    eventId,
    baseFolderId,
    rootName,
    relativePath,
    file.size,
    file.lastModified,
  ]);
}

async function scanDirectory(directoryHandle, includeSubfolders) {
  const files = [];
  const directories = [];

  async function visit(handle, relativeDirectory) {
    if (relativeDirectory) directories.push(relativeDirectory);
    for await (const [name, entry] of handle.entries()) {
      if (entry.kind === 'directory') {
        if (includeSubfolders) {
          await visit(entry, relativeDirectory ? `${relativeDirectory}/${name}` : name);
        }
        continue;
      }

      if (!SUPPORTED_EXTENSIONS.has(extensionOf(name))) continue;
      const file = await entry.getFile();
      files.push({
        file,
        relativePath: relativeDirectory ? `${relativeDirectory}/${name}` : name,
        relativeDirectory,
      });
    }
  }

  await visit(directoryHandle, '');
  directories.sort((left, right) => left.length - right.length || left.localeCompare(right));
  files.sort((left, right) => left.relativePath.localeCompare(right.relativePath));
  return { files, directories };
}

function waitForNextScan() {
  return new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS));
}

export default function LocalFolderSync({
  eventId,
  baseFolderId,
  baseFolderName,
  folders,
  disabled = false,
  onRunningChange,
  onFoldersChange,
  onUploaded,
}) {
  const [directoryHandle, setDirectoryHandle] = useState(null);
  const [includeSubfolders, setIncludeSubfolders] = useState(true);
  const [starting, setStarting] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [uploadedCount, setUploadedCount] = useState(0);
  const [pendingCount, setPendingCount] = useState(0);
  const [currentFile, setCurrentFile] = useState('');
  const [status, setStatus] = useState('Choose the folder where your camera saves photos.');
  const [error, setError] = useState('');

  const stopRef = useRef(false);
  const runningRef = useRef(false);
  const mountedRef = useRef(true);
  const observedFilesRef = useRef(new Map());
  const retryAfterRef = useRef(new Map());
  const remoteFoldersRef = useRef(new Map());
  const foldersRef = useRef(folders);
  const callbacksRef = useRef({ onRunningChange, onFoldersChange, onUploaded });
  const uploadedCountRef = useRef(0);
  foldersRef.current = folders;
  callbacksRef.current = { onRunningChange, onFoldersChange, onUploaded };

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      stopRef.current = true;
      callbacksRef.current.onRunningChange?.(false);
    };
  }, []);

  function setRunState(value) {
    runningRef.current = value;
    if (mountedRef.current) {
      setSyncing(value);
      callbacksRef.current.onRunningChange?.(value);
    }
  }

  async function chooseFolder() {
    setError('');
    if (!window.isSecureContext || typeof window.showDirectoryPicker !== 'function') {
      setError('Automatic folder sync needs Chrome or Edge on this HTTPS site. Use Upload photos / videos in another browser.');
      return;
    }
    try {
      const handle = await window.showDirectoryPicker({ mode: 'read' });
      setDirectoryHandle(handle);
      observedFilesRef.current.clear();
      retryAfterRef.current.clear();
      remoteFoldersRef.current.clear();
      setStatus(`Selected local folder “${handle.name}”. Start sync when ready.`);
    } catch (pickerError) {
      if (pickerError?.name !== 'AbortError') setError(pickerError.message || 'Could not open the selected folder.');
    }
  }

  async function getRemoteFolder(relativeDirectory, context) {
    if (!relativeDirectory) return context.baseFolderId;
    const mapKey = relativeDirectory.toLocaleLowerCase();
    if (remoteFoldersRef.current.has(mapKey)) return remoteFoldersRef.current.get(mapKey);

    const folderName = galleryFolderName(context.rootName, relativeDirectory);
    const existing = foldersRef.current.find((folder) => folder.name?.toLocaleLowerCase() === folderName.toLocaleLowerCase());
    if (existing) {
      remoteFoldersRef.current.set(mapKey, existing.folder_id);
      return existing.folder_id;
    }

    try {
      const created = await api.createDataFolder(context.eventId, folderName);
      remoteFoldersRef.current.set(mapKey, created.folder_id);
      const updated = [...foldersRef.current.filter((folder) => folder.folder_id !== created.folder_id), created];
      foldersRef.current = updated;
      callbacksRef.current.onFoldersChange?.(updated);
      return created.folder_id;
    } catch (createError) {
      // Another tab may have created this matching folder while the sync was running.
      const fresh = await api.dataFolders(context.eventId);
      const refreshed = fresh.folders || [];
      foldersRef.current = refreshed;
      callbacksRef.current.onFoldersChange?.(refreshed);
      const racedFolder = refreshed.find((folder) => folder.name?.toLocaleLowerCase() === folderName.toLocaleLowerCase());
      if (racedFolder) {
        remoteFoldersRef.current.set(mapKey, racedFolder.folder_id);
        return racedFolder.folder_id;
      }
      throw createError;
    }
  }

  async function startSync() {
    if (runningRef.current || !directoryHandle || !eventId || !baseFolderId) return;
    setError('');
    setStarting(true);
    setRunState(true);
    setStatus('Requesting access to the selected folder…');
    try {
      // Call requestPermission directly from the Start button gesture. Browsers
      // may reject a permission prompt if another async step happens first.
      const permission = await directoryHandle.requestPermission({ mode: 'read' });
      if (permission !== 'granted') throw new Error('Folder access was not granted. Choose the folder and allow read access to start syncing.');

      stopRef.current = false;
      uploadedCountRef.current = 0;
      setUploadedCount(0);
      setPendingCount(0);
      remoteFoldersRef.current.clear();
      remoteFoldersRef.current.set('', baseFolderId);
      setStatus(`Watching “${directoryHandle.name}”. New files upload after their copy has settled.`);

      const context = {
        eventId,
        baseFolderId,
        rootName: directoryHandle.name,
      };

      while (!stopRef.current) {
        const { files, directories } = await scanDirectory(directoryHandle, includeSubfolders);
        const now = Date.now();

        for (const relativeDirectory of directories) {
          if (stopRef.current) break;
          const folderRetryKey = `folder:${relativeDirectory}`;
          if ((retryAfterRef.current.get(folderRetryKey) || 0) > now) continue;
          try {
            await getRemoteFolder(relativeDirectory, context);
            retryAfterRef.current.delete(folderRetryKey);
          } catch (folderError) {
            retryAfterRef.current.set(folderRetryKey, now + 15000);
            if (mountedRef.current) {
              setError(`Could not create the gallery folder for ${relativeDirectory}: ${folderError.message}. Sync will retry.`);
              setStatus('Waiting to retry folder creation…');
            }
          }
        }

        const readyFiles = [];
        for (const item of files) {
          if (item.file.size <= 0) continue;
          const previous = observedFilesRef.current.get(item.relativePath);
          const signature = `${item.file.size}:${item.file.lastModified}`;
          if (!previous || previous.signature !== signature) {
            observedFilesRef.current.set(item.relativePath, { signature, firstSeenAt: now, stableScans: 0 });
            continue;
          }
          previous.stableScans += 1;
          if (previous.stableScans >= 2 && now - previous.firstSeenAt >= FILE_STABLE_MS) readyFiles.push(item);
        }
        setPendingCount(files.length - readyFiles.length);

        for (const item of readyFiles) {
          if (stopRef.current) break;
          const folderRetryKey = `folder:${item.relativeDirectory}`;
          if ((retryAfterRef.current.get(folderRetryKey) || 0) > Date.now()) continue;
          let destinationFolderId;
          try {
            destinationFolderId = await getRemoteFolder(item.relativeDirectory, context);
            retryAfterRef.current.delete(folderRetryKey);
          } catch (folderError) {
            retryAfterRef.current.set(folderRetryKey, Date.now() + 15000);
            if (mountedRef.current) setError(`Could not create the gallery folder for ${item.relativeDirectory}: ${folderError.message}. Sync will retry.`);
            continue;
          }
          const key = fileIdentity(context.eventId, context.baseFolderId, context.rootName, item.relativePath, item.file);
          if (await hasUploaded(key)) continue;
          if ((retryAfterRef.current.get(key) || 0) > Date.now()) continue;

          if (mountedRef.current) {
            setCurrentFile(item.relativePath);
            setStatus(`Uploading “${item.relativePath}”…`);
          }
          try {
            await api.uploadDataFile(context.eventId, destinationFolderId, item.file);
            await rememberUploaded(key);
            retryAfterRef.current.delete(key);
            uploadedCountRef.current += 1;
            if (mountedRef.current) {
              setUploadedCount(uploadedCountRef.current);
              setError('');
            }
            callbacksRef.current.onUploaded?.(destinationFolderId);
          } catch (uploadError) {
            retryAfterRef.current.set(key, Date.now() + 15000);
            if (mountedRef.current) {
              setError(`Could not upload ${item.relativePath}: ${uploadError.message}. Sync will retry.`);
              setStatus('Waiting to retry the failed upload…');
            }
          } finally {
            if (mountedRef.current) setCurrentFile('');
          }
        }

        if (!stopRef.current && mountedRef.current) {
          setStatus(`Watching “${directoryHandle.name}” · ${uploadedCountRef.current} uploaded this session.`);
        }
        if (!stopRef.current) await waitForNextScan();
      }
      if (mountedRef.current) setStatus('Folder sync stopped. Files already uploaded remain in the gallery.');
    } catch (syncError) {
      if (mountedRef.current) {
        setError(syncError.message || 'Folder sync stopped because the local folder could not be read.');
        setStatus('Folder sync stopped.');
      }
    } finally {
      if (mountedRef.current) setStarting(false);
      setRunState(false);
      if (mountedRef.current) setCurrentFile('');
    }
  }

  function stopSync() {
    stopRef.current = true;
    setStatus('Stopping after the current upload…');
  }

  const supported = Boolean(window.isSecureContext && typeof window.showDirectoryPicker === 'function');

  return (
    <section className="local-sync-panel" aria-labelledby="local-sync-title">
      <div className="local-sync-heading">
        <div>
          <p className="eyebrow">Camera to gallery</p>
          <h3 id="local-sync-title">Live folder sync</h3>
          <p>Choose the laptop folder where your camera saves photos. New photos and videos upload to this event automatically.</p>
        </div>
        <span className={`local-sync-indicator ${syncing ? 'is-running' : ''}`}>{syncing ? 'Syncing' : 'Manual start'}</span>
      </div>

      {!supported && <p className="local-sync-warning">Automatic folder sync needs Chrome or Edge on this HTTPS site. The regular upload button still works in other browsers.</p>}
      {error && <p className="local-sync-error" role="alert">{error}</p>}

      <div className="local-sync-controls">
        <button className="button button-quiet" type="button" onClick={chooseFolder} disabled={!supported || disabled || syncing || starting}>
          {directoryHandle ? 'Change local folder' : 'Choose local folder'}
        </button>
        {directoryHandle && <span className="local-sync-folder-name">{directoryHandle.name}</span>}
        {!syncing ? (
          <button className="button button-dark" type="button" onClick={startSync} disabled={!directoryHandle || disabled || starting}>
            {starting ? 'Requesting access…' : 'Start auto-sync'}
          </button>
        ) : (
          <button className="button button-quiet" type="button" onClick={stopSync}>Stop sync</button>
        )}
      </div>

      <label className="local-sync-option">
        <input type="checkbox" checked={includeSubfolders} onChange={(changeEvent) => setIncludeSubfolders(changeEvent.target.checked)} disabled={syncing || starting} />
        Include local subfolders and create matching gallery folders
      </label>

      <div className="local-sync-status" role="status" aria-live="polite">
        <span>{status}</span>
        {currentFile && <span className="local-sync-current">Current file: {currentFile}</span>}
        {syncing && <span>{pendingCount} file{pendingCount === 1 ? '' : 's'} waiting to finish copying · {uploadedCount} uploaded this session</span>}
      </div>

      <p className="local-sync-note">
        Files in the selected folder go to “{baseFolderName}”. Local subfolders are mirrored as separate event folders. Keep this page open and the laptop awake during the shoot; files are checked every 5 seconds and uploaded after remaining unchanged for about 10 seconds.
      </p>
    </section>
  );
}
