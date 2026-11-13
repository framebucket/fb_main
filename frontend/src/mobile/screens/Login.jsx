import { useState } from 'react';
import { api } from '../../api.js';
import { Button, Field, Segmented, useToast } from '../ui.jsx';

export default function LoginScreen({ onLogin }) {
  const toast = useToast();
  const [mode, setMode] = useState('photographer');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [needsMobileCode, setNeedsMobileCode] = useState(false);
  const [mobileCode, setMobileCode] = useState('');
  const [customerMobile, setCustomerMobile] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [customerCode, setCustomerCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

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

  async function signIn(event) {
    event.preventDefault();
    const profile = await attempt(() => api.login({ username: username.trim(), password }));
    if (profile) onLogin(profile);
  }

  async function verifyMobile(event) {
    event.preventDefault();
    const result = await attempt(() => api.verifyMobile(username.trim(), mobileCode));
    if (result) {
      toast(result.message || 'Mobile number verified. Sign in again.');
      setNeedsMobileCode(false);
      setMobileCode('');
    }
  }

  async function sendCustomerCode(event) {
    event?.preventDefault();
    const result = await attempt(() => api.requestCustomerOtp(customerMobile.trim()));
    if (result) {
      setCodeSent(true);
      toast(result.message || 'Code sent.');
    }
  }

  async function verifyCustomer(event) {
    event.preventDefault();
    const profile = await attempt(() => api.verifyCustomerOtp({ mobileNumber: customerMobile.trim(), code: customerCode }));
    if (profile) window.location.replace('/customer-portal');
  }

  return (
    <div className="m-login">
      <div className="m-login-hero">
        <img src="/framebucket-mark.png" alt="" className="m-login-mark" />
        <h1>FrameBucket</h1>
        <p>{mode === 'photographer' ? 'Your studio, in your pocket.' : 'Your memories, close by.'}</p>
      </div>
      <div className="m-login-card">
        <Segmented value={mode} onChange={(value) => { setMode(value); setError(''); }} options={[['photographer', 'Photographer'], ['customer', 'Customer']]} />
        {mode === 'photographer' ? (
          needsMobileCode ? (
            <form className="m-form" onSubmit={verifyMobile}>
              <p className="m-muted">Enter the 6-digit code sent to your mobile number to finish verifying your account.</p>
              <Field label="Verification code">
                <input inputMode="numeric" autoComplete="one-time-code" maxLength="6" value={mobileCode} onChange={(event) => setMobileCode(event.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="000000" required />
              </Field>
              {error && <p className="m-form-error" role="alert">{error}</p>}
              <Button type="submit" block busy={busy} disabled={mobileCode.length !== 6}>Verify mobile number</Button>
              <div className="m-login-links">
                <button type="button" onClick={() => attempt(() => api.resendMobileCode(username.trim())).then((result) => result && toast(result.message))}>Send a new code</button>
                <button type="button" onClick={() => setNeedsMobileCode(false)}>Back to sign in</button>
              </div>
            </form>
          ) : (
            <form className="m-form" onSubmit={signIn}>
              <Field label="Email or mobile number">
                <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" inputMode="email" autoCapitalize="none" required maxLength="320" placeholder="you@yourstudio.com" />
              </Field>
              <Field label="Password">
                <span className="m-input-with-action">
                  <input type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required placeholder="Your password" />
                  <button type="button" onClick={() => setShowPassword((value) => !value)}>{showPassword ? 'Hide' : 'Show'}</button>
                </span>
              </Field>
              {error && <p className="m-form-error" role="alert">{error}</p>}
              {/verify your mobile/i.test(error) && <button type="button" className="m-link" onClick={() => setNeedsMobileCode(true)}>Enter mobile verification code</button>}
              <Button type="submit" block busy={busy}>Sign in</Button>
              <div className="m-login-links">
                <button type="button" onClick={() => {
                  if (!username.includes('@')) { setError('Enter your email address above to resend the verification link.'); return; }
                  attempt(() => api.resendVerification(username.trim())).then((result) => result && toast(result.message));
                }}>Resend verification email</button>
                <a href="/register">Create an account</a>
              </div>
            </form>
          )
        ) : (
          <form className="m-form" onSubmit={codeSent ? verifyCustomer : sendCustomerCode}>
            <p className="m-muted">{codeSent ? 'Enter the code sent to your registered email and mobile.' : 'Sign in with the mobile number your photographer registered.'}</p>
            <Field label="Mobile number">
              <input type="tel" inputMode="tel" value={customerMobile} onChange={(event) => setCustomerMobile(event.target.value)} readOnly={codeSent} autoComplete="tel" required maxLength="13" placeholder="9876543210" />
            </Field>
            {codeSent && (
              <Field label="One-time code">
                <input inputMode="numeric" autoComplete="one-time-code" maxLength="6" value={customerCode} onChange={(event) => setCustomerCode(event.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="000000" required />
              </Field>
            )}
            {error && <p className="m-form-error" role="alert">{error}</p>}
            <Button type="submit" block busy={busy} disabled={codeSent && customerCode.length !== 6}>{codeSent ? 'Verify and continue' : 'Send sign-in code'}</Button>
            {codeSent && <div className="m-login-links"><button type="button" onClick={sendCustomerCode}>Resend code</button><button type="button" onClick={() => { setCodeSent(false); setCustomerCode(''); }}>Use another number</button></div>}
          </form>
        )}
      </div>
    </div>
  );
}
