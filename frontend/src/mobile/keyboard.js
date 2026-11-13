// Keeps forms visible above the on-screen keyboard.
//
// Android only shrinks the app when the keyboard opens if the native side resizes the web view (the
// Keyboard plugin's resizeOnFullScreen); otherwise the keyboard simply covers the bottom of the page.
// This works out how much of the screen the keyboard covers (from the browser's visual viewport, or the
// Keyboard plugin's keyboardWillShow event in the app) and exposes it as --m-kb, so sheets, dialogs and
// the login form sit above it. The focused field is also scrolled into view.

export function installKeyboardInsets() {
  const root = document.documentElement;
  const viewport = window.visualViewport;
  let nativeHeight = 0;
  let fullHeight = window.innerHeight;

  function update() {
    if (!nativeHeight && window.innerHeight > fullHeight) fullHeight = window.innerHeight;
    // Part of the keyboard the window itself has already made room for (when the web view is resized).
    const resizedBy = Math.max(0, fullHeight - window.innerHeight);
    const fromViewport = viewport ? Math.max(0, window.innerHeight - viewport.height - viewport.offsetTop) : 0;
    const fromNative = Math.max(0, nativeHeight - resizedBy);
    const inset = Math.round(Math.max(fromViewport, fromNative));
    root.style.setProperty('--m-kb', `${inset}px`);
    root.classList.toggle('m-keyboard-open', inset > 80 || resizedBy > 120 || nativeHeight > 0);
  }

  viewport?.addEventListener('resize', update);
  viewport?.addEventListener('scroll', update);
  window.addEventListener('resize', update);
  // Fired by the Capacitor Keyboard plugin inside the Android/iOS app.
  window.addEventListener('keyboardWillShow', (event) => { nativeHeight = Number(event.keyboardHeight) || 0; update(); });
  window.addEventListener('keyboardDidShow', (event) => { nativeHeight = Number(event.keyboardHeight) || nativeHeight; update(); });
  window.addEventListener('keyboardWillHide', () => { nativeHeight = 0; update(); });
  window.addEventListener('keyboardDidHide', () => { nativeHeight = 0; update(); });

  document.addEventListener('focusin', (event) => {
    const field = event.target;
    if (!(field instanceof HTMLElement) || !field.matches('input:not([type=checkbox]):not([type=radio]):not([type=file]), textarea, select')) return;
    // Wait for the keyboard to finish opening, then bring the field to the middle of what's visible.
    window.setTimeout(() => { update(); field.scrollIntoView({ block: 'center', behavior: 'smooth' }); }, 320);
  });
  update();
}
