import { isNativeApp, saveBlob } from '../nativeSave.js';

const inrFormatter = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });

export function inr(amount) {
  return inrFormatter.format(Number(amount || 0));
}

/** ₹1.2L / ₹3.4Cr style for small stat tiles. */
export function inrShort(amount) {
  const value = Number(amount || 0);
  const abs = Math.abs(value);
  if (abs >= 1e7) return `₹${(value / 1e7).toFixed(abs >= 1e8 ? 0 : 1)}Cr`;
  if (abs >= 1e5) return `₹${(value / 1e5).toFixed(abs >= 1e6 ? 0 : 1)}L`;
  if (abs >= 1e3) return `₹${(value / 1e3).toFixed(abs >= 1e4 ? 0 : 1)}K`;
  return inr(value);
}

/** Event prices are free text (₹1,50,000 or "On request"). */
export function eventPrice(price) {
  if (price == null || String(price).trim() === '') return '—';
  const numeric = Number(price);
  return Number.isFinite(numeric) ? inr(numeric) : String(price);
}

export function todayIso() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

export function isoDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function dayDate(value, options = { day: 'numeric', month: 'short', year: 'numeric' }) {
  if (!value) return '—';
  return new Date(`${String(value).slice(0, 10)}T00:00:00`).toLocaleDateString('en-IN', options);
}

export function relativeDay(value) {
  if (!value) return '';
  const target = new Date(`${String(value).slice(0, 10)}T00:00:00`);
  const today = new Date(`${todayIso()}T00:00:00`);
  const days = Math.round((target - today) / 86400000);
  if (days === 0) return 'Today';
  if (days === 1) return 'Tomorrow';
  if (days === -1) return 'Yesterday';
  if (days > 1 && days < 7) return target.toLocaleDateString('en-IN', { weekday: 'long' });
  if (days < 0 && days > -7) return `${-days} days ago`;
  return dayDate(value, { day: 'numeric', month: 'short' });
}

export function timeLabel(value) {
  const match = /^(\d{2}):(\d{2})/.exec(value || '');
  return match ? new Date(2000, 0, 1, Number(match[1]), Number(match[2])).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' }) : '';
}

export function dateParts(value) {
  const date = new Date(`${String(value).slice(0, 10)}T00:00:00`);
  return {
    day: date.getDate(),
    month: date.toLocaleDateString('en-IN', { month: 'short' }).toUpperCase(),
    weekday: date.toLocaleDateString('en-IN', { weekday: 'short' }),
  };
}

export function digits(mobile) {
  const clean = String(mobile || '').replace(/\D/g, '');
  return clean.length === 10 ? `91${clean}` : clean;
}

export function telLink(mobile) {
  return `tel:+${digits(mobile)}`;
}

export function whatsappLink(mobile, text = '') {
  return `https://wa.me/${digits(mobile)}${text ? `?text=${encodeURIComponent(text)}` : ''}`;
}

export function mapsLink(event) {
  if (event.locationMapUrl && /^https:\/\/(www\.)?(google\.[a-z.]+\/maps|maps\.google\.|maps\.app\.goo\.gl|goo\.gl\/maps)/i.test(event.locationMapUrl)) {
    return event.locationMapUrl;
  }
  if (!event.locationName) return '';
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(event.locationName)}`;
}

export function initials(name) {
  return String(name || '?').trim().split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase() || '').join('') || '?';
}

/** PDFs, receipts and other files: share sheet inside the app, new tab in a browser. */
export async function openFile(url, filename) {
  if (!isNativeApp()) {
    window.open(url, '_blank', 'noopener');
    return;
  }
  const response = await fetch(url, { credentials: 'include' });
  if (!response.ok) throw new Error(`The file could not be opened (HTTP ${response.status}).`);
  await saveBlob(await response.blob(), filename);
}

export function plural(count, one, many = `${one}s`) {
  return `${count} ${count === 1 ? one : many}`;
}
