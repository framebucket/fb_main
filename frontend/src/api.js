import { saveBlob } from './nativeSave.js';

const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
let csrfToken = null;

async function loadCsrfToken() {
  const response = await fetch(apiBase + '/api/auth/csrf', {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('Could not start a secure session. Check that the backend is running.');
  }

  const csrfCookie = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith('XSRF-TOKEN='));
  if (!csrfCookie) {
    throw new Error('The secure request token was not set. Check the backend cookie settings.');
  }
  csrfToken = decodeURIComponent(csrfCookie.slice('XSRF-TOKEN='.length));
}

async function request(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const isStateChanging = !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method);
  const headers = { ...(options.headers || {}) };

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  if (isStateChanging) {
    if (!csrfToken) {
      await loadCsrfToken();
    }
    headers['X-XSRF-TOKEN'] = csrfToken;
  }

  const response = await fetch(apiBase + path, {
    ...options,
    method,
    headers,
    credentials: 'include',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  const responseText = await response.text();
  let payload = null;
  if (responseText) {
    try {
      payload = JSON.parse(responseText);
    } catch {
      payload = { detail: responseText };
    }
  }

  if (!response.ok) {
    const message = payload?.detail || payload?.message || 'The request could not be completed.';
    const error = new Error(message);
    // PLAN_UPGRADE_REQUIRED errors carry the missing feature or the limit that was reached.
    error.status = response.status;
    error.code = payload?.code;
    error.feature = payload?.feature;
    error.limit = payload?.limit;
    throw error;
  }

  return payload;
}

function query(params) {
  const search = new URLSearchParams();
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value);
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

async function requestMultipart(path, body) {
  if (!csrfToken) {
    await loadCsrfToken();
  }
  const response = await fetch(apiBase + path, {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': csrfToken },
    credentials: 'include',
    body,
  });
  const responseText = await response.text();
  let payload = null;
  if (responseText) {
    try { payload = JSON.parse(responseText); } catch { payload = { detail: responseText }; }
  }
  if (!response.ok) {
    throw new Error(payload?.detail || payload?.message || `The upload failed (HTTP ${response.status}).`);
  }
  return payload;
}

async function requestImage(path, body) {
  if (!csrfToken) {
    await loadCsrfToken();
  }
  const response = await fetch(apiBase + path, {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': csrfToken },
    credentials: 'include',
    body,
  });
  if (!response.ok) {
    const text = await response.text();
    let payload = null;
    try { payload = JSON.parse(text); } catch { payload = { detail: text }; }
    throw new Error(payload?.detail || payload?.message || `The preview failed (HTTP ${response.status}).`);
  }
  return response.blob();
}

/** Anonymous POST without CSRF/cookies, for the embeddable enquiry form (works inside third-party iframes). */
async function publicPost(path, body) {
  const response = await fetch(apiBase + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'omit',
    body: JSON.stringify(body),
  });
  const text = await response.text();
  let payload = null;
  if (text) { try { payload = JSON.parse(text); } catch { payload = { detail: text }; } }
  if (!response.ok) throw new Error(payload?.detail || payload?.message || 'The request could not be completed.');
  return payload;
}

async function putUploadBody(grant, file, onProgress) {
  const sameOrigin = Boolean(grant.same_origin);
  const headers = { ...(grant.upload_headers || {}) };
  if (sameOrigin) {
    if (!csrfToken) await loadCsrfToken();
    headers['X-XSRF-TOKEN'] = csrfToken;
  }
  await new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(grant.upload_method || 'PUT', sameOrigin ? apiBase + grant.upload_url : grant.upload_url);
    // Direct storage URLs are presigned; cookies are only needed for the same-origin fallback.
    xhr.withCredentials = sameOrigin;
    Object.entries(headers).forEach(([name, value]) => xhr.setRequestHeader(name, value));
    xhr.upload.onprogress = (progressEvent) => {
      if (onProgress && progressEvent.lengthComputable) onProgress(progressEvent.loaded / progressEvent.total);
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
        return;
      }
      let message = `The upload failed (HTTP ${xhr.status}).`;
      try { message = JSON.parse(xhr.responseText)?.detail || message; } catch { /* storage returns XML errors */ }
      reject(new Error(message));
    };
    xhr.onerror = () => reject(new Error('The upload was interrupted. Check your connection and try again.'));
    xhr.send(file);
  });
}

async function uploadGalleryFile(eventId, folderId, file, onProgress) {
  const base = `/api/events/${encodeURIComponent(eventId)}/gallery/uploads`;
  const grant = await request(base, {
    method: 'POST',
    body: {
      folderId: folderId || 'root',
      filename: file.name,
      sizeBytes: file.size,
      contentType: file.type || '',
    },
  });
  await putUploadBody(grant, file, onProgress);
  return request(`${base}/${encodeURIComponent(grant.photo_id)}/complete`, { method: 'POST' });
}

async function downloadBlob(path, body, filename) {
  if (!csrfToken) {
    await loadCsrfToken();
  }
  const response = await fetch(apiBase + path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const responseText = await response.text();
    let payload = null;
    try { payload = responseText ? JSON.parse(responseText) : null; } catch { payload = { detail: responseText }; }
    const detail = payload?.detail;
    const message = Array.isArray(detail)
      ? detail.map((item) => item?.msg).filter(Boolean).join(', ')
      : detail || payload?.message || `The download failed (HTTP ${response.status}).`;
    throw new Error(message);
  }

  let downloadFilename = filename;
  const contentDisposition = response.headers.get('Content-Disposition') || '';
  const encodedFilename = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(contentDisposition)?.[1];
  const regularFilename = /filename\s*=\s*"?([^";]+)"?/i.exec(contentDisposition)?.[1];
  if (encodedFilename) {
    try { downloadFilename = decodeURIComponent(encodedFilename); } catch { downloadFilename = encodedFilename; }
  } else if (regularFilename) {
    downloadFilename = regularFilename.trim();
  }

  await saveBlob(await response.blob(), String(downloadFilename || filename).replace(/[\\/]/g, '_'));
}

async function downloadAuthenticatedFile(path, filename) {
  const response = await fetch(apiBase + path, { credentials: 'include' });
  if (!response.ok) {
    const responseText = await response.text();
    let payload = null;
    try { payload = responseText ? JSON.parse(responseText) : null; } catch { payload = { detail: responseText }; }
    throw new Error(payload?.detail || payload?.message || `The download failed (HTTP ${response.status}).`);
  }
  await saveBlob(await response.blob(), filename);
}

export const api = {
  register: (form) => request('/api/auth/register', { method: 'POST', body: form }),
  plans: () => request('/api/subscriptions'),
  verifyEmail: (token) => request('/api/auth/verify-email', { method: 'POST', body: { token } }),
  resendVerification: (email) => request('/api/auth/resend-verification', { method: 'POST', body: { email } }),
  verifyMobile: (identifier, code) => request('/api/auth/verify-mobile', { method: 'POST', body: { identifier, code } }),
  resendMobileCode: (identifier) => request('/api/auth/resend-mobile-code', { method: 'POST', body: { identifier } }),
  customers: () => request('/api/customers'),
  customer: (customerId) => request(`/api/customers/${encodeURIComponent(customerId)}`),
  registerCustomer: (form) => request('/api/customers', { method: 'POST', body: form }),
  events: () => request('/api/events'),
  downloadEventQrCode: (eventId, eventCode) => downloadAuthenticatedFile(
    `/api/events/${encodeURIComponent(eventId)}/qr-code.png`,
    `FrameBucket-${String(eventCode || 'event').replace(/[^a-z0-9._-]+/gi, '-')}-QR.png`,
  ),
  nextEventId: () => request('/api/events/next-id'),
  createEvent: (form) => request('/api/events', { method: 'POST', body: form }),
  assignEvent: (eventId, assigneeType, assigneeId) => request(`/api/events/${encodeURIComponent(eventId)}/assignee`, { method: 'PUT', body: { assigneeType, assigneeId } }),
  updateCustomerEventLocation: (eventId, form) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/location`, { method: 'PUT', body: form }),
  blockedEventDates: () => request('/api/events/blocked-dates'),
  eventTypes: () => request('/api/events/types'),
  createEventType: (name) => request('/api/events/types', { method: 'POST', body: { name } }),
  teamMembers: () => request('/api/team'),
  createTeamMember: (form) => request('/api/team', { method: 'POST', body: form }),
  inviteTeamMember: (form) => request('/api/team/invitations', { method: 'POST', body: form }),
  resendTeamInvite: (memberType, memberId) => request(`/api/team/${encodeURIComponent(memberType)}/${encodeURIComponent(memberId)}/invitation`, { method: 'POST' }),
  publicTeamInvite: (token) => request(`/api/public/team-invites/${encodeURIComponent(token)}`),
  completeTeamInvite: (token, form) => request(`/api/public/team-invites/${encodeURIComponent(token)}`, { method: 'PUT', body: form }),
  enquiries: () => request('/api/enquiries'),
  dashboardSummary: () => request('/api/dashboard/summary'),
  publicStats: () => request('/api/public/stats'),
  createEnquiry: (form) => request('/api/enquiries', { method: 'POST', body: form }),
  convertEnquiry: (enquiryId, form) => request(`/api/enquiries/${encodeURIComponent(enquiryId)}/convert`, { method: 'POST', body: form }),
  saveEnquiryFinalizedQuote: (enquiryId, finalizedQuote) => request(`/api/enquiries/${encodeURIComponent(enquiryId)}/finalized-quote`, { method: 'PUT', body: { finalizedQuote } }),
  enquiryEventSetup: (enquiryId) => request(`/api/enquiries/${encodeURIComponent(enquiryId)}/event-setup`),
  createEventFromEnquiry: (enquiryId, form) => request(`/api/enquiries/${encodeURIComponent(enquiryId)}/create-event`, { method: 'POST', body: form }),
  enquiryQuotationUrl: (enquiryId) => `${apiBase}/api/enquiries/${encodeURIComponent(enquiryId)}/quotation.pdf`,
  enquiryFinalizedQuotationUrl: (enquiryId) => `${apiBase}/api/enquiries/${encodeURIComponent(enquiryId)}/finalized-quotation.pdf`,
  publicQuoteContext: (token) => request(`/api/public/enquiries/quote/${encodeURIComponent(token)}`),
  calculateQuote: (token, form) => request(`/api/public/enquiries/quote/${encodeURIComponent(token)}/calculate`, { method: 'POST', body: form }),
  submitQuote: (token, form) => request(`/api/public/enquiries/quote/${encodeURIComponent(token)}/submit`, { method: 'POST', body: form }),
  publicQuotePdfUrl: (token) => `${apiBase}/api/public/enquiries/quote/${encodeURIComponent(token)}/pdf`,
  publicEventGallery: (token) => request(`/api/public/event-galleries/${encodeURIComponent(token)}`),
  registerEventGuest: (token, form) => requestMultipart(
    `/api/public/event-galleries/${encodeURIComponent(token)}/guests`,
    form,
  ),
  deleteEventGuest: (token, guestId, deletionToken) => request(
    `/api/public/event-galleries/${encodeURIComponent(token)}/guests`,
    { method: 'DELETE', body: { guestId, deletionToken } },
  ),
  requestCustomerOtp: (mobileNumber) => request('/api/customer-auth/request-code', { method: 'POST', body: { mobileNumber } }),
  verifyCustomerOtp: async (form) => {
    const profile = await request('/api/customer-auth/verify-code', { method: 'POST', body: form });
    csrfToken = null;
    await loadCsrfToken();
    return profile;
  },
  customerMe: () => request('/api/customer-auth/me'),
  customerEvents: () => request('/api/customer-portal/events'),
  customerAlbumReviews: () => request('/api/customer-portal/album-reviews'),
  customerFolders: (eventId) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/gallery/folders`),
  dataFolders: (eventId) => request(`/api/events/${encodeURIComponent(eventId)}/gallery/folders`),
  createDataFolder: (eventId, name) => request(`/api/events/${encodeURIComponent(eventId)}/gallery/folders`, { method: 'POST', body: { name } }),
  photographerMedia: (eventId, folderId) => request(`/api/events/${encodeURIComponent(eventId)}/gallery/media${folderId ? `?folderId=${encodeURIComponent(folderId)}` : ''}`),
  uploadDataFile: (eventId, folderId, file, onProgress) => uploadGalleryFile(eventId, folderId, file, onProgress),
  customerMedia: (eventId) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/gallery/media`),
  customerSelection: (eventId) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/gallery/selection`),
  saveCustomerSelection: (eventId, photoIds) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/gallery/selection`, { method: 'PUT', body: { photoIds } }),
  downloadCustomerGallery: (eventId, photoIds, filename) => downloadBlob(
    `/api/customer-portal/events/${encodeURIComponent(eventId)}/gallery/download`,
    { photoIds: photoIds || [] },
    filename,
  ),
  submitAlbum: (eventId, photoIds) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/gallery/album-submissions`, { method: 'POST', body: { photoIds } }),
  photographerPreviewUrl: (eventId, mediaId) => `${apiBase}/api/events/${encodeURIComponent(eventId)}/gallery/media/${encodeURIComponent(mediaId)}/preview`,
  photographerOriginalUrl: (eventId, mediaId) => `${apiBase}/api/events/${encodeURIComponent(eventId)}/gallery/media/${encodeURIComponent(mediaId)}/original`,
  customerPreviewUrl: (eventId, mediaId) => `${apiBase}/api/customer-portal/events/${encodeURIComponent(eventId)}/gallery/media/${encodeURIComponent(mediaId)}/preview`,
  // Studio settings, billing, expenses, portfolio and e-album flipbooks
  studioProfile: () => request('/api/studio/profile'),
  saveStudioProfile: (form) => request('/api/studio/profile', { method: 'PUT', body: form }),
  financeSummary: () => request('/api/studio/finance/summary'),
  eventBilling: (eventId) => request(`/api/events/${encodeURIComponent(eventId)}/billing`),
  recordPayment: (eventId, form) => request(`/api/events/${encodeURIComponent(eventId)}/payments`, { method: 'POST', body: form }),
  deletePayment: (eventId, paymentId) => request(`/api/events/${encodeURIComponent(eventId)}/payments/${encodeURIComponent(paymentId)}`, { method: 'DELETE' }),
  receiptUrl: (eventId, paymentId) => `${apiBase}/api/events/${encodeURIComponent(eventId)}/payments/${encodeURIComponent(paymentId)}/receipt.pdf`,
  paymentQrUrl: (eventId, amount) => `${apiBase}/api/events/${encodeURIComponent(eventId)}/payment-qr.png${amount ? `?amount=${encodeURIComponent(amount)}` : ''}`,
  saveDownloadPolicy: (eventId, form) => request(`/api/events/${encodeURIComponent(eventId)}/download-policy`, { method: 'PUT', body: form }),
  expenses: () => request('/api/studio/expenses'),
  createExpense: (form) => request('/api/studio/expenses', { method: 'POST', body: form }),
  updateExpense: (expenseId, form) => request(`/api/studio/expenses/${encodeURIComponent(expenseId)}`, { method: 'PUT', body: form }),
  deleteExpense: (expenseId) => request(`/api/studio/expenses/${encodeURIComponent(expenseId)}`, { method: 'DELETE' }),
  portfolioItems: () => request('/api/studio/portfolio'),
  addPortfolioItems: (form) => request('/api/studio/portfolio', { method: 'POST', body: form }),
  updatePortfolioItem: (itemId, form) => request(`/api/studio/portfolio/${encodeURIComponent(itemId)}`, { method: 'PUT', body: form }),
  removePortfolioItem: (itemId) => request(`/api/studio/portfolio/${encodeURIComponent(itemId)}`, { method: 'DELETE' }),
  flipbooks: (eventId) => request(`/api/events/${encodeURIComponent(eventId)}/flipbooks`),
  flipbookCustomerSelection: (eventId) => request(`/api/events/${encodeURIComponent(eventId)}/flipbooks/customer-selection`),
  createFlipbook: (eventId, form) => request(`/api/events/${encodeURIComponent(eventId)}/flipbooks`, { method: 'POST', body: form }),
  deleteFlipbook: (eventId, flipbookId) => request(`/api/events/${encodeURIComponent(eventId)}/flipbooks/${encodeURIComponent(flipbookId)}`, { method: 'DELETE' }),
  customerBilling: (eventId) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/billing`),
  customerReceiptUrl: (eventId, paymentId) => `${apiBase}/api/customer-portal/events/${encodeURIComponent(eventId)}/payments/${encodeURIComponent(paymentId)}/receipt.pdf`,
  customerPaymentQrUrl: (eventId, purpose) => `${apiBase}/api/customer-portal/events/${encodeURIComponent(eventId)}/payment-qr.png?purpose=${encodeURIComponent(purpose || 'BALANCE')}`,
  publicPortfolio: (slug) => request(`/api/public/portfolio/${encodeURIComponent(slug)}`),
  publicFlipbook: (token) => request(`/api/public/flipbooks/${encodeURIComponent(token)}`),
  publicFlipbookPageUrl: (token, index) => `${apiBase}/api/public/flipbooks/${encodeURIComponent(token)}/pages/${index}`,
  apiUrl: (path) => `${apiBase}${path}`,
  // Subscription plans
  mySubscription: () => request('/api/subscriptions/me'),
  requestPlan: (planCode, billingCycle, message) => request('/api/subscriptions/requests', { method: 'POST', body: { planCode, billingCycle, message } }),
  cancelPlanRequest: () => request('/api/subscriptions/requests/pending', { method: 'DELETE' }),
  // Admin portal (ROLE_ADMIN)
  adminOverview: () => request('/api/admin/overview'),
  adminPhotographers: (params) => request(`/api/admin/photographers${query(params)}`),
  adminPhotographer: (id) => request(`/api/admin/photographers/${encodeURIComponent(id)}`),
  adminChangePlan: (id, body) => request(`/api/admin/photographers/${encodeURIComponent(id)}/plan`, { method: 'PUT', body }),
  adminExtendTrial: (id, days, note) => request(`/api/admin/photographers/${encodeURIComponent(id)}/trial`, { method: 'POST', body: { days, note } }),
  adminSuspend: (id, reason) => request(`/api/admin/photographers/${encodeURIComponent(id)}/suspend`, { method: 'POST', body: { reason } }),
  adminReactivate: (id) => request(`/api/admin/photographers/${encodeURIComponent(id)}/reactivate`, { method: 'POST' }),
  adminSaveNotes: (id, notes) => request(`/api/admin/photographers/${encodeURIComponent(id)}/notes`, { method: 'PUT', body: { notes } }),
  adminPlans: () => request('/api/admin/plans'),
  adminFeatures: () => request('/api/admin/features'),
  adminUpdatePlan: (code, body) => request(`/api/admin/plans/${encodeURIComponent(code)}`, { method: 'PUT', body }),
  adminPlanRequests: (status) => request(`/api/admin/plan-requests${query({ status })}`),
  adminApproveRequest: (id, body) => request(`/api/admin/plan-requests/${encodeURIComponent(id)}/approve`, { method: 'POST', body }),
  adminRejectRequest: (id, note) => request(`/api/admin/plan-requests/${encodeURIComponent(id)}/reject`, { method: 'POST', body: { note } }),
  adminAudit: (limit) => request(`/api/admin/audit${query({ limit })}`),

  // ---- Bookings: quote catalogue, lead capture, follow-ups, contracts
  quoteCatalog: () => request('/api/studio/quote-catalog'),
  saveQuoteSettings: (body) => request('/api/studio/quote-catalog/settings', { method: 'PUT', body }),
  addQuoteItem: (body) => request('/api/studio/quote-catalog/items', { method: 'POST', body }),
  updateQuoteItem: (id, body) => request(`/api/studio/quote-catalog/items/${encodeURIComponent(id)}`, { method: 'PUT', body }),
  removeQuoteItem: (id) => request(`/api/studio/quote-catalog/items/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  addQuotePackage: (body) => request('/api/studio/quote-catalog/packages', { method: 'POST', body }),
  updateQuotePackage: (id, body) => request(`/api/studio/quote-catalog/packages/${encodeURIComponent(id)}`, { method: 'PUT', body }),
  deleteQuotePackage: (id) => request(`/api/studio/quote-catalog/packages/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  leadCapture: () => request('/api/studio/lead-capture'),
  enquiryTracking: () => request('/api/studio/enquiries/tracking'),
  remindEnquiry: (id) => request(`/api/studio/enquiries/${encodeURIComponent(id)}/remind`, { method: 'POST' }),
  contractDraft: (id) => request(`/api/studio/enquiries/${encodeURIComponent(id)}/contract`),
  sendContract: (id, body) => request(`/api/studio/enquiries/${encodeURIComponent(id)}/contract`, { method: 'POST', body }),
  cancelContract: (id) => request(`/api/studio/enquiries/${encodeURIComponent(id)}/contract`, { method: 'DELETE' }),
  confirmAdvance: (id, body) => request(`/api/studio/enquiries/${encodeURIComponent(id)}/contract/advance`, { method: 'POST', body }),
  contractPdfUrl: (id) => `${apiBase}/api/studio/enquiries/${encodeURIComponent(id)}/contract.pdf`,
  publicEnquiryForm: (slug) => request(`/api/public/studios/${encodeURIComponent(slug)}/enquiry-form`),
  submitPublicEnquiry: (slug, body) => publicPost(`/api/public/studios/${encodeURIComponent(slug)}/enquiries`, body),
  signContract: (token, body) => request(`/api/public/enquiries/quote/${encodeURIComponent(token)}/contract/sign`, { method: 'POST', body }),
  reportAdvance: (token, reference) => request(`/api/public/enquiries/quote/${encodeURIComponent(token)}/contract/advance`, { method: 'POST', body: { reference } }),
  advanceQrUrl: (token) => `${apiBase}/api/public/enquiries/quote/${encodeURIComponent(token)}/contract/advance-qr.png`,
  publicContractPdfUrl: (token) => `${apiBase}/api/public/enquiries/quote/${encodeURIComponent(token)}/contract.pdf`,
  resolveDomain: () => request('/api/public/domains/resolve'),
  // ---- Delivery: checklist, films, proofs, branding
  deliverySteps: (eventId) => request(`/api/events/${encodeURIComponent(eventId)}/delivery/steps`),
  addDeliveryStep: (eventId, body) => request(`/api/events/${encodeURIComponent(eventId)}/delivery/steps`, { method: 'POST', body }),
  updateDeliveryStep: (eventId, id, body) => request(`/api/events/${encodeURIComponent(eventId)}/delivery/steps/${encodeURIComponent(id)}`, { method: 'PUT', body }),
  deleteDeliveryStep: (eventId, id) => request(`/api/events/${encodeURIComponent(eventId)}/delivery/steps/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  films: (eventId) => request(`/api/events/${encodeURIComponent(eventId)}/films`),
  filmVideos: (eventId) => request(`/api/events/${encodeURIComponent(eventId)}/films/videos`),
  addFilm: (eventId, body) => request(`/api/events/${encodeURIComponent(eventId)}/films`, { method: 'POST', body }),
  deleteFilm: (eventId, id) => request(`/api/events/${encodeURIComponent(eventId)}/films/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  proofs: (eventId) => request(`/api/events/${encodeURIComponent(eventId)}/proofs`),
  albumDesigns: (eventId) => request(`/api/events/${encodeURIComponent(eventId)}/album-designs`),
  createAlbumDesign: (eventId, body) => request(`/api/events/${encodeURIComponent(eventId)}/album-designs`, { method: 'POST', body }),
  albumDesign: (eventId, designId) => request(`/api/events/${encodeURIComponent(eventId)}/album-designs/${encodeURIComponent(designId)}`),
  albumDesignSpreadUrl: (eventId, designId, index) => `${apiBase}/api/events/${encodeURIComponent(eventId)}/album-designs/${encodeURIComponent(designId)}/spreads/${index}`,
  albumDesignDownloadUrl: (eventId, designId, kind) => `${apiBase}/api/events/${encodeURIComponent(eventId)}/album-designs/${encodeURIComponent(designId)}/download/${kind}`,
  sendAlbumDesign: (eventId, designId, message) => request(`/api/events/${encodeURIComponent(eventId)}/album-designs/${encodeURIComponent(designId)}/send`, { method: 'POST', body: { message } }),
  sendProof: (eventId, body) => request(`/api/events/${encodeURIComponent(eventId)}/proofs`, { method: 'POST', body }),
  replyProof: (eventId, proofId, body) => request(`/api/events/${encodeURIComponent(eventId)}/proofs/${encodeURIComponent(proofId)}/comments`, { method: 'POST', body }),
  resolveProofComment: (eventId, proofId, commentId, resolved) => request(`/api/events/${encodeURIComponent(eventId)}/proofs/${encodeURIComponent(proofId)}/comments/${encodeURIComponent(commentId)}`, { method: 'PUT', body: { resolved } }),
  brand: () => request('/api/studio/brand'),
  saveBrand: (body) => request('/api/studio/brand', { method: 'PUT', body }),
  uploadLogo: (file) => { const form = new FormData(); form.append('file', file); return requestMultipart('/api/studio/brand/logo', form); },
  deleteLogo: () => request('/api/studio/brand/logo', { method: 'DELETE' }),
  setDomain: (domain) => request('/api/studio/brand/domain', { method: 'PUT', body: { domain } }),
  verifyDomain: () => request('/api/studio/brand/domain/verify', { method: 'POST' }),
  removeDomain: () => request('/api/studio/brand/domain', { method: 'DELETE' }),
  editingStyle: () => request('/api/studio/editing-style'),
  saveEditingStyle: (body) => request('/api/studio/editing-style', { method: 'PUT', body }),
  clearEditingStyle: () => request('/api/studio/editing-style', { method: 'DELETE' }),
  uploadStyleReferences: (files) => { const form = new FormData(); files.forEach((file) => form.append('files', file)); return requestMultipart('/api/studio/editing-style/references', form); },
  previewEditingStyle: (file, strength) => { const form = new FormData(); form.append('file', file); return requestImage(`/api/studio/editing-style/preview?strength=${encodeURIComponent(strength)}`, form); },
  invitation: (eventId) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/invitation`),
  saveInvitation: (eventId, details) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/invitation`, { method: 'PUT', body: details }),
  uploadInvitationPhoto: (eventId, file) => { const form = new FormData(); form.append('file', file); return requestMultipart(`/api/customer-portal/events/${encodeURIComponent(eventId)}/invitation/photo`, form); },
  deleteInvitationPhoto: (eventId) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/invitation/photo`, { method: 'DELETE' }),
  generateInvitationDesigns: (eventId) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/invitation/designs`, { method: 'POST', body: {} }),
  invitationDesignUrl: (eventId, index, version) => `${apiBase}/api/customer-portal/events/${encodeURIComponent(eventId)}/invitation/designs/${index}?v=${encodeURIComponent(version || '')}`,
  finalizeInvitation: (eventId, index) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/invitation/finalize`, { method: 'POST', body: { index } }),
  invitationFileUrl: (eventId, kind, version) => `${apiBase}/api/customer-portal/events/${encodeURIComponent(eventId)}/invitation/${kind}?v=${encodeURIComponent(version || '')}`,
  customerDelivery: (eventId) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/delivery`),
  customerFilmUrl: (eventId, filmId) => `${apiBase}/api/customer-portal/events/${encodeURIComponent(eventId)}/films/${encodeURIComponent(filmId)}/stream`,
  customerSpreadUrl: (eventId, proofId, index) => `${apiBase}/api/customer-portal/events/${encodeURIComponent(eventId)}/proofs/${encodeURIComponent(proofId)}/spreads/${index}`,
  commentProof: (eventId, proofId, body) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/proofs/${encodeURIComponent(proofId)}/comments`, { method: 'POST', body }),
  approveProof: (eventId, proofId, note) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/proofs/${encodeURIComponent(proofId)}/approve`, { method: 'POST', body: { note } }),
  requestProofChanges: (eventId, proofId, note) => request(`/api/customer-portal/events/${encodeURIComponent(eventId)}/proofs/${encodeURIComponent(proofId)}/request-changes`, { method: 'POST', body: { note } }),
  assetUrl: (path) => (path ? `${apiBase}${path}` : null),
  // ---- Crew, gear, costs, insights
  crewMembers: () => request('/api/studio/crew/members'),
  eventCrew: (eventId) => request(`/api/events/${encodeURIComponent(eventId)}/crew`),
  crewCheck: (eventId, memberType, memberId) => request(`/api/events/${encodeURIComponent(eventId)}/crew/check${query({ memberType, memberId })}`),
  addCrew: (eventId, body) => request(`/api/events/${encodeURIComponent(eventId)}/crew`, { method: 'POST', body }),
  updateCrew: (eventId, id, body) => request(`/api/events/${encodeURIComponent(eventId)}/crew/${encodeURIComponent(id)}`, { method: 'PUT', body }),
  removeCrew: (eventId, id) => request(`/api/events/${encodeURIComponent(eventId)}/crew/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  crewBalances: () => request('/api/studio/crew/balances'),
  crewPayouts: () => request('/api/studio/crew/payouts'),
  addCrewPayout: (body) => request('/api/studio/crew/payouts', { method: 'POST', body }),
  deleteCrewPayout: (id) => request(`/api/studio/crew/payouts/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  crewUnavailable: () => request('/api/studio/crew/unavailable'),
  addCrewUnavailable: (body) => request('/api/studio/crew/unavailable', { method: 'POST', body }),
  deleteCrewUnavailable: (id) => request(`/api/studio/crew/unavailable/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  gear: () => request('/api/studio/gear'),
  addGear: (body) => request('/api/studio/gear', { method: 'POST', body }),
  updateGear: (id, body) => request(`/api/studio/gear/${encodeURIComponent(id)}`, { method: 'PUT', body }),
  deleteGear: (id) => request(`/api/studio/gear/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  eventGear: (eventId) => request(`/api/events/${encodeURIComponent(eventId)}/gear`),
  addEventGear: (eventId, body) => request(`/api/events/${encodeURIComponent(eventId)}/gear`, { method: 'POST', body }),
  updateEventGear: (eventId, gearId, body) => request(`/api/events/${encodeURIComponent(eventId)}/gear/${encodeURIComponent(gearId)}`, { method: 'PUT', body }),
  removeEventGear: (eventId, gearId) => request(`/api/events/${encodeURIComponent(eventId)}/gear/${encodeURIComponent(gearId)}`, { method: 'DELETE' }),
  eventCosts: (eventId) => request(`/api/events/${encodeURIComponent(eventId)}/costs`),
  addEventCost: (eventId, body) => request(`/api/events/${encodeURIComponent(eventId)}/costs`, { method: 'POST', body }),
  deleteEventCost: (eventId, id) => request(`/api/events/${encodeURIComponent(eventId)}/costs/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  insights: () => request('/api/studio/insights'),
  login: async (form) => {
    const profile = await request('/api/auth/login', { method: 'POST', body: form });
    csrfToken = null;
    await loadCsrfToken();
    return profile;
  },
  me: () => request('/api/auth/me'),
  logout: async () => {
    await request('/api/auth/logout', { method: 'POST' });
    csrfToken = null;
  },
};
