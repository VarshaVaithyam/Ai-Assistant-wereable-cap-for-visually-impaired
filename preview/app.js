import { normalizeHost, readSettings } from './settings.mjs';
const screen = document.querySelector('#screen');
const title = document.querySelector('#title');
const back = document.querySelector('#back');
let storage;
try { storage = window.localStorage; } catch { storage = { getItem: () => null, setItem: () => { throw new Error('Storage unavailable'); } }; }
let settings = readSettings(storage);
let toastTimer;
const icon = name => `<svg aria-hidden="true"><use href="#${name}"/></svg>`;
const action = (name, text, id, primary = false, disabled = false) => `<button class="action ${primary ? 'primary' : ''}" id="${id}" ${disabled ? 'disabled' : ''}>${icon(name)}<span>${text}</span></button>`;
function toast(message) {
  const element = document.querySelector('#toast');
  clearTimeout(toastTimer);
  element.textContent = message;
  element.hidden = false;
  toastTimer = setTimeout(() => { element.hidden = true; }, 6500);
}
const note = `<div class="preview-note"><p><strong>Visual preview only.</strong> Camera controls require the Android app connected to the cap’s Wi-Fi. This browser preview makes no camera requests.</p></div>`;
const home = () => `
  <p class="section-label" style="color:var(--amber);font-size:11px;letter-spacing:1.8px">YOUR EVERYDAY COMPANION</p>
  <h3 style="font-size:32px;line-height:1.15;font-weight:600;margin:12px 0 24px">Less on screen.<br>More within reach.</h3>
  <div class="preview-note" style="margin:0 0 28px;padding:20px">
  <h3 class="status-title">Camera not checked</h3>
  <p>Join the cap’s Wi-Fi to use its camera. A phone camera is also available in the Android app for reading signs.</p>
  ${action('refresh', 'Reconnect camera', 'reconnect')}
  </div>
  <h3 style="font-size:22px;font-weight:600">What would you like to do?</h3>
  ${action('scan', 'Read a sign', 'open-read', true)}
  ${action('camera', 'Camera preview', 'open-camera')}
  ${action('walk', 'Your steps', 'open-steps')}
  ${action('sound', 'Guardian & help', 'open-help')}
  ${action('settings', 'Settings and voice test', 'open-settings')}
  <h3 class="section-label">Not connected yet</h3>
  <p>Walking guidance and scene understanding need a verified detection model. Voice destination search and navigation need GPS and pedestrian routing. Fall detection and automatic SOS are not implemented.</p>
  <p class="safety">Experimental assistive prototype. Keep using your white cane or guide dog. No announcement does not mean a clear path. Prefer open-ear audio.</p>`;
const modulePreview = (heading, description, label) => `
  <h3 class="status-title">${heading}</h3><p>${description}</p>
  <div class="preview-note"><p>This is a browser visual preview. This feature uses Android device APIs in the Flutter source; it cannot run here. No simulated results are supplied.</p></div>
  <button class="action primary" disabled>${label} — Android app only</button>`;
const camera = () => `
  <h3 class="status-title">Stream stopped</h3>
  <div class="preview-box">${icon('camera')}<span>No camera frame</span></div>
  ${action('refresh', 'Start stream', 'start', true, true)}
  ${action('stop', 'Stop stream', 'stop', false, true)}
  ${action('photo', 'Capture snapshot', 'capture', false, true)}
  <p>Preview only: this release does not detect objects. Snapshots are shown locally, not uploaded or saved. Phone camera support is planned.</p>${note}`;
const preferences = () => `
  <form id="settings-form" novalidate>
  <label class="field-label" for="host">Camera base address</label>
  <input type="url" id="host" autocomplete="off" autocapitalize="none" spellcheck="false" aria-describedby="host-helper host-error" required>
  <p id="host-helper" class="helper">Default: http://192.168.4.1</p>
  <p id="host-error" class="error" role="alert" hidden></p>
  <p>Android permits cleartext HTTP only for 192.168.4.1 by default. For another local HTTP hostname or IP, add that exact host to the network security configuration and rebuild. HTTPS requires a trusted certificate.</p>
  <label class="switch-row"><span><strong>Spoken status updates</strong><small>Save to apply changes</small></span><input type="checkbox" id="audio" role="switch" aria-label="Spoken status updates"></label>
  <button type="submit" class="action primary">Save browser-preview settings</button>
  </form>
  ${action('sound', 'Test voice in browser', 'voice', false, !settings.audio)}
  <p>In the Android app, audio uses Android’s current media output, including paired Bluetooth earphones. Pair and select the output in Android settings.</p>
  <p>Model: not installed<br>Step sensor: Android only, permission on Start<br>GPS, microphone, and phone camera: not requested in this increment.</p>
  <div class="preview-note"><p>Settings here are saved only in this browser, not in the APK. Voice test uses your browser’s speech engine, not Flutter TTS. No connection check is performed.</p></div>`;
let currentScreen = 'home';
function navigate(page, focus = true, record = true) {
  if ('speechSynthesis' in window) window.speechSynthesis.cancel();
  const current = ['home', 'camera', 'settings', 'read', 'steps', 'help'].includes(page) ? page : 'home';
  if (record && current !== currentScreen) {
    history.pushState({ visionAssistScreen: current, depth: (history.state?.depth ?? 0) + 1 }, '', '#' + current);
  }
  currentScreen = current;
  const modules = {
    read: () => modulePreview('Point. Capture. Listen.', 'The new Flutter source reads Latin-script signs with Google ML Kit, using a cap snapshot or the phone camera. Recognized text can be read aloud. Tamil-script OCR is not supported.', 'Capture and read'),
    steps: () => modulePreview('Your steps', 'The new Flutter source requests activity permission on Start, uses a hardware step counter and counts session deltas. No hardware reading is available in this browser preview.', 'Start step counter'),
    help: () => modulePreview('A direct way to call.', 'The new Flutter source saves a guardian number locally and opens the phone dialer only after confirmation. It does not automatically place a call, send messages, share location or detect falls.', 'Open phone dialer'),
  };
  screen.innerHTML = current === 'home' ? home() : current === 'camera' ? camera() : current === 'settings' ? preferences() : modules[current]();
  title.textContent = {home: 'VisionAssist', camera: 'Live camera', settings: 'Settings', read: 'Read a sign', steps: 'Your steps', help: 'Guardian & help'}[current];
  back.hidden = current === 'home';
  document.querySelector('#brand-logo').hidden = current !== 'home';
  document.querySelectorAll('[data-screen]').forEach(button => {
    const selected = button.dataset.screen === current;
    button.classList.toggle('selected', selected);
    if (selected) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
  });
  screen.scrollTop = 0;
  if (focus) screen.focus();
  if (current === 'home') {
    document.querySelector('#open-read').onclick = () => navigate('read');
    document.querySelector('#open-steps').onclick = () => navigate('steps');
    document.querySelector('#open-help').onclick = () => navigate('help');
    document.querySelector('#open-camera').onclick = () => navigate('camera');
    document.querySelector('#open-settings').onclick = () => navigate('settings');
    document.querySelector('#reconnect').onclick = () => toast('Camera connection is available in the Android APK, not this visual preview.');
  }
  if (current === 'settings') {
    const host = document.querySelector('#host');
    host.value = settings.host;
    document.querySelector('#audio').checked = settings.audio;
    document.querySelector('#settings-form').onsubmit = event => {
      event.preventDefault();
      const error = document.querySelector('#host-error');
      try {
        const next = { host: normalizeHost(host.value), audio: document.querySelector('#audio').checked };
        storage.setItem('visionassist-visual-preview', JSON.stringify(next));
        settings = next;
        error.hidden = true;
        host.removeAttribute('aria-invalid');
        document.querySelector('#voice').disabled = !settings.audio;
        toast('Saved in this browser only. No camera connection was attempted.');
      } catch (problem) {
        error.textContent = problem instanceof TypeError ? 'Enter a base HTTP or HTTPS address, such as http://192.168.4.1.' : problem.message;
        error.hidden = false;
        host.setAttribute('aria-invalid', 'true');
        host.focus();
      }
    };
    document.querySelector('#voice').onclick = () => {
      if (!('speechSynthesis' in window)) { toast('Speech synthesis is unavailable in this browser.'); return; }
      const speech = new SpeechSynthesisUtterance('VisionAssist voice test. Keep environmental sounds audible.');
      speech.rate = 0.9;
      speech.onerror = () => toast('Browser voice unavailable. Try the voice test in the Android APK.');
      window.speechSynthesis.cancel();
      window.speechSynthesis.speak(speech);
      toast('Using browser speech, not the Android TTS engine.');
    };
  }
}
document.querySelectorAll('[data-screen]').forEach(button => button.onclick = () => navigate(button.dataset.screen));
back.onclick = () => {
  if ((history.state?.depth ?? 0) > 0) history.back();
  else navigate('home');
};
window.addEventListener('popstate', () => {
  navigate(history.state?.visionAssistScreen ?? location.hash.slice(1), true, false);
});
document.addEventListener('visibilitychange', () => {
  if (document.hidden && 'speechSynthesis' in window) window.speechSynthesis.cancel();
});
const initialScreen = ['home', 'camera', 'settings', 'read', 'steps', 'help'].includes(location.hash.slice(1)) ? location.hash.slice(1) : 'home';
history.replaceState({ visionAssistScreen: initialScreen, depth: 0 }, '', '#' + initialScreen);
navigate(initialScreen, false, false);
