import { useEffect, useState } from 'react';
import { api } from '../api.js';

// One upload queue for the whole app, so uploads keep going (and stay visible) while you move
// between screens. Files go one at a time, straight to storage, like the website.

const listeners = new Set();
const state = { queue: [], current: null, done: 0, failed: [], total: 0, progress: 0, finishedFolders: [] };
let running = false;

function emit() {
  const snapshot = { ...state, queue: [...state.queue], failed: [...state.failed] };
  listeners.forEach((listener) => listener(snapshot));
}

async function pump() {
  if (running) return;
  running = true;
  while (state.queue.length) {
    const job = state.queue.shift();
    state.current = job;
    state.progress = 0;
    emit();
    try {
      await api.uploadDataFile(job.eventId, job.folderId, job.file, (fraction) => {
        state.progress = fraction;
        emit();
      });
      state.done += 1;
      const key = `${job.eventId}:${job.folderId}`;
      if (!state.finishedFolders.includes(key)) state.finishedFolders.push(key);
    } catch (error) {
      state.failed.push({ name: job.file.name, message: error.message, job });
    }
  }
  state.current = null;
  running = false;
  emit();
}

export function enqueueUploads(eventId, folderId, files) {
  if (!state.current && !state.queue.length) {
    state.done = 0;
    state.total = 0;
    state.failed = [];
  }
  for (const file of files) state.queue.push({ eventId, folderId: folderId || 'root', file });
  state.total += files.length;
  emit();
  pump();
}

export function retryFailed() {
  const jobs = state.failed.map((item) => item.job);
  state.failed = [];
  state.total = state.done + jobs.length + state.queue.length + (state.current ? 1 : 0);
  state.queue.push(...jobs);
  emit();
  pump();
}

export function dismissUploads() {
  if (state.current || state.queue.length) return;
  state.done = 0;
  state.total = 0;
  state.failed = [];
  emit();
}

export function useUploads() {
  const [snapshot, setSnapshot] = useState(() => ({ ...state }));
  useEffect(() => {
    listeners.add(setSnapshot);
    return () => listeners.delete(setSnapshot);
  }, []);
  return snapshot;
}

/** Calls onDone whenever an upload into this event/folder finishes. */
export function useUploadFinished(eventId, folderId, onDone) {
  const uploads = useUploads();
  const key = `${eventId}:${folderId || 'root'}`;
  const count = uploads.done;
  useEffect(() => {
    if (count > 0 && uploads.finishedFolders.includes(key)) onDone();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [count, key]);
}
