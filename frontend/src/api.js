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
    throw new Error(message);
  }

  return payload;
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
  nextEventId: () => request('/api/events/next-id'),
  createEvent: (form) => request('/api/events', { method: 'POST', body: form }),
  blockedEventDates: () => request('/api/events/blocked-dates'),
  eventTypes: () => request('/api/events/types'),
  createEventType: (name) => request('/api/events/types', { method: 'POST', body: { name } }),
  enquiries: () => request('/api/enquiries'),
  dashboardSummary: () => request('/api/dashboard/summary'),
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
  requestCustomerOtp: (mobileNumber) => request('/api/customer-auth/request-code', { method: 'POST', body: { mobileNumber } }),
  verifyCustomerOtp: async (form) => {
    const profile = await request('/api/customer-auth/verify-code', { method: 'POST', body: form });
    csrfToken = null;
    await loadCsrfToken();
    return profile;
  },
  customerMe: () => request('/api/customer-auth/me'),
  customerEvents: () => request('/api/customer-portal/events'),
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
