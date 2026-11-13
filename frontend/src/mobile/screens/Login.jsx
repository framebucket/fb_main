import { useEffect, useState } from 'react';
import { api } from '../../api.js';
import { Button, Field, Segmented, useToast } from '../ui.jsx';
import { useLayout } from '../layout.js';

const MOBILE_PATTERN = /^(?:[0-9]{10}|\+91[0-9]{10})$/;

function useCountdown() {
  const [left, setLeft] = useState(0);
  useEffect(() => {
    if (left <= 0) return undefined;
    const timer = window.setTimeout(() => setLeft((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [left]);
  return [left, () => setLeft(60)];
}

function WhatsAppNote({ sentTo }) {
  return (
    <div className="m-otp-note" role="status">
      <span className="m-otp-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M12 2a10 10 0 0 0-8.66 15l-1.3 4.76 4.88-1.28A10 10 0 1 0 12 2Zm4.5 12.06c-.25-.12-1.46-.72-1.69-.8-.23-.08-.39-.12-.56.12-.16.25-.64.8-.78.97-.15.16-.29.18-.54.06a6.7 6.7 0 0 1-3.32-2.9c-.25-.43.25-.4.72-1.33.08-.16.04-.3-.02-.43l-.76-1.83c-.2-.48-.4-.41-.56-.42h-.47a.9.9 0 0 0-.66.31 2.77 2.77 0 0 0-.86 2.06 4.8 4.8 0 0 0 1 2.56 11 11 0 0 0 4.2 3.7c1.56.68 2.18.73 2.96.62.48-.07 1.46-.6 1.67-1.18.2-.58.2-1.07.15-1.18-.06-.1-.22-.16-.47-.28Z" /></svg>
      </span>
      <span><strong>Check WhatsApp{sentTo ? ` on ${sentTo}` : ''}</strong><small>Enter the 6-digit code. A copy is in your email too.</small></span>
    </div>
  );
}

function CodeField({ value, onChange }) {
  return (
    <Field label="6-digit code">
      <input className="m-otp-input" inputMode="numeric" autoComplete="one-time-code" maxLength="6" value={value}
        onChange={(event) => onChange(event.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="••••••" required autoFocus />
    </Field>
  );
}

export default function LoginScreen({ onLogin }) {
  const toast = useToast();
  const layout = useLayout();
  const [mode, setMode] = useState('photographer');
  const [identifier, setIdentifier] = useState('');
  const [sentTo, setSentTo] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [code, setCode] = useState('');
  const [customerMobile, setCustomerMobile] = useState('');
  const [customerSent, setCustomerSent] = useState(false);
  const [customerCode, setCustomerCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [wait, restartWait] = useCountdown();

  async function attempt(task) {
    setBusy(true);
    setError('');
    try {
      return await task();
    } catch (requestError) {
      setError(requestError.message);
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function sendCode(event) {
    event?.preventDefault();
    const value = identifier.trim();
    if (!value.includes('@') && !MOBILE_PATTERN.test(value)) {
      setError('Enter your registered mobile number (10 digits) or email address.');
      return;
    }
    const result = await attempt(() => api.requestLoginCode(value));
    if (result) {
      setSentTo(result.sentTo || '');
      setCodeSent(true);
      setCode('');
      restartWait();
      if (event == null) toast(result.message || 'New code sent.');
    }
  }

  async function verify(event) {
    event.preventDefault();
    const profile = await attempt(() => api.verifyLoginCode({ identifier: identifier.trim(), code }));
    if (profile) onLogin(profile);
  }

  async function sendCustomerCode(event) {
    event?.preventDefault();
    const result = await attempt(() => api.requestCustomerOtp(customerMobile.trim()));
    if (result) {
      setCustomerSent(true);
      restartWait();
      if (event == null) toast(result.message || 'Code sent.');
    }
  }

  async function verifyCustomer(event) {
    event.preventDefault();
    const profile = await attempt(() => api.verifyCustomerOtp({ mobileNumber: customerMobile.trim(), code: customerCode }));
    if (profile) window.location.replace('/customer-portal');
  }

  const resendLabel = wait > 0 ? `Resend in ${wait}s` : 'Resend code';

  return (
    <div className="m-login">
      <div className="m-login-hero">
        <img src="/framebucket-mark.png" alt="" className="m-login-mark" />
        <h1>FrameBucket</h1>
        <p>{mode === 'photographer' ? (layout === 'desktop' ? 'Your whole studio, in one place.' : 'Your studio, in your pocket.') : 'Your memories, close by.'}</p>
      </div>
      <div className="m-login-card">
        <Segmented value={mode} onChange={(value) => { setMode(value); setError(''); }} options={[['photographer', 'Photographer'], ['customer', 'Customer']]} />
        {mode === 'photographer' ? (
          codeSent ? (
            <form className="m-form" onSubmit={verify}>
              <WhatsAppNote sentTo={sentTo} />
              <CodeField value={code} onChange={setCode} />
              {error && <p className="m-form-error" role="alert">{error}</p>}
              <Button type="submit" block busy={busy} disabled={code.length !== 6}>Sign in</Button>
              <div className="m-login-links">
                <button type="button" disabled={busy || wait > 0} onClick={() => sendCode()}>{resendLabel}</button>
                <button type="button" onClick={() => { setCodeSent(false); setCode(''); setError(''); }}>Use another number</button>
              </div>
            </form>
          ) : (
            <form className="m-form" onSubmit={sendCode}>
              <p className="m-muted">No password needed. We’ll send a sign-in code to your WhatsApp.</p>
              <Field label="WhatsApp number or email">
                <input value={identifier} onChange={(event) => setIdentifier(event.target.value)} autoComplete="username" inputMode="email" autoCapitalize="none" required maxLength="320" placeholder="9876543210" />
              </Field>
              {error && <p className="m-form-error" role="alert">{error}</p>}
              <Button type="submit" block busy={busy}>Send code on WhatsApp</Button>
              <div className="m-login-links">
                <span className="m-muted">You stay signed in on this device.</span>
                <a href="/register">Create an account</a>
              </div>
            </form>
          )
        ) : (
          <form className="m-form" onSubmit={customerSent ? verifyCustomer : sendCustomerCode}>
            {customerSent ? <WhatsAppNote sentTo={customerMobile.trim()} /> : <p className="m-muted">Sign in with the mobile number your photographer registered.</p>}
            <Field label="Mobile number">
              <input type="tel" inputMode="tel" value={customerMobile} onChange={(event) => setCustomerMobile(event.target.value)} readOnly={customerSent} autoComplete="tel" required maxLength="13" placeholder="9876543210" />
            </Field>
            {customerSent && <CodeField value={customerCode} onChange={setCustomerCode} />}
            {error && <p className="m-form-error" role="alert">{error}</p>}
            <Button type="submit" block busy={busy} disabled={customerSent && customerCode.length !== 6}>{customerSent ? 'Verify and continue' : 'Send code on WhatsApp'}</Button>
            {customerSent && <div className="m-login-links"><button type="button" disabled={busy || wait > 0} onClick={() => sendCustomerCode()}>{resendLabel}</button><button type="button" onClick={() => { setCustomerSent(false); setCustomerCode(''); }}>Use another number</button></div>}
          </form>
        )}
      </div>
    </div>
  );
}
