// Saving files inside the FrameBucket Android/iOS apps.
//
// The phone apps show this same web app in a native web view, where <a download> and blob links do nothing.
// When running inside the app, files are written to the app's cache with the Filesystem plugin and handed
// to the system share sheet, from where the user can save them (Files, Downloads, Photos, Drive, WhatsApp…).
// In a normal browser everything behaves exactly as before.
const CHUNK_BYTES = 3 * 1024 * 1024; // multiple of 3 so each base64 chunk can be appended on its own

export function isNativeApp() {
  const cap = typeof window !== 'undefined' ? window.Capacitor : null;
  return Boolean(cap && typeof cap.isNativePlatform === 'function' && cap.isNativePlatform());
}

function callPlugin(plugin, method, options) {
  const cap = window.Capacitor;
  const proxy = cap.Plugins && cap.Plugins[plugin];
  if (proxy && typeof proxy[method] === 'function') return proxy[method](options);
  return cap.nativePromise(plugin, method, options);
}

function cleanName(filename) {
  return String(filename || 'download').replace(/[\\/:*?"<>|]+/g, '_').trim() || 'download';
}

function bytesToBase64(bytes) {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  }
  return btoa(binary);
}

async function saveInApp(blob, name) {
  const path = `downloads/${Date.now()}-${name}`;
  if (blob.size === 0) {
    await callPlugin('Filesystem', 'writeFile', { path, data: '', directory: 'CACHE', recursive: true });
  }
  for (let offset = 0; offset < blob.size; offset += CHUNK_BYTES) {
    const data = bytesToBase64(new Uint8Array(await blob.slice(offset, offset + CHUNK_BYTES).arrayBuffer()));
    if (offset === 0) {
      await callPlugin('Filesystem', 'writeFile', { path, data, directory: 'CACHE', recursive: true });
    } else {
      await callPlugin('Filesystem', 'appendFile', { path, data, directory: 'CACHE' });
    }
  }
  const { uri } = await callPlugin('Filesystem', 'getUri', { path, directory: 'CACHE' });
  try {
    await callPlugin('Share', 'share', { title: name, files: [uri], dialogTitle: `Save ${name}` });
  } catch (error) {
    // Closing the share sheet without choosing anything is not an error.
    if (!/cancel/i.test(String((error && error.message) || error))) throw error;
  }
}

export async function saveBlob(blob, filename) {
  const name = cleanName(filename);
  if (isNativeApp()) {
    await saveInApp(blob, name);
    return;
  }
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}

// Plain <a href download> links (invitation video, UPI QR…) are routed through saveBlob inside the app.
export function installNativeDownloadLinks(apiBase = '') {
  if (!isNativeApp()) return;
  const origins = new Set([window.location.origin]);
  try { if (apiBase) origins.add(new URL(apiBase).origin); } catch { /* relative base */ }

  document.addEventListener('click', async (event) => {
    const link = event.target && event.target.closest ? event.target.closest('a[download]') : null;
    if (!link || !link.href) return;
    let url;
    try { url = new URL(link.href, window.location.href); } catch { return; }
    if (!origins.has(url.origin)) return;
    event.preventDefault();
    try {
      const response = await fetch(url.href, { credentials: 'include' });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const fallback = decodeURIComponent(url.pathname.split('/').pop() || 'download');
      await saveBlob(await response.blob(), link.getAttribute('download') || fallback);
    } catch (error) {
      window.alert(`The download failed: ${(error && error.message) || error}`);
    }
  }, true);
}
