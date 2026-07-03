/* TTS playback engine with two interchangeable backends:
   - WebPlayer:    speechSynthesis + Media Session API (browser / installed PWA)
   - NativePlayer: Capacitor "NativeTTS" plugin (Android APK) — the WebView has no
     speechSynthesis, so speech runs on the native Android TTS engine. Sentences are
     queued into the engine in batches, so playback keeps going even when the WebView
     is throttled in the background, and a native MediaSession notification provides
     lock-screen / notification-shade controls.

   Both expose the same API: load/play/pause/toggle/stop/seek/skip, voice & rate,
   time estimates, and 'sentence' / 'state' / 'voices' events. */

const BASE_WPM = 170; // average narration words-per-minute at 1.0x

export const SPEED_PRESETS = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3, 3.5];

function isNative() {
  return !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform());
}

export function createPlayer() {
  if (isNative() && window.Capacitor.Plugins && window.Capacitor.Plugins.NativeTTS) {
    return new NativePlayer(window.Capacitor.Plugins.NativeTTS);
  }
  return new WebPlayer();
}

/* ================= shared core ================= */

class BasePlayer {
  constructor() {
    this.voices = [];        // [{voiceURI, name, lang, localService}]
    this.voiceURI = null;
    this.rate = 1;
    this.book = null;
    this.cursor = 0;
    this.playing = false;
    this.cumWords = [];
    this.totalWords = 0;
    this.listeners = { sentence: [], state: [], voices: [] };
  }

  get supported() { return true; }

  on(event, fn) { this.listeners[event].push(fn); }
  _emit(event, ...args) { for (const fn of this.listeners[event]) fn(...args); }

  getVoice() {
    if (!this.voices.length) return null;
    if (this.voiceURI) {
      const v = this.voices.find((x) => x.voiceURI === this.voiceURI);
      if (v) return v;
    }
    const lang = navigator.language || 'en-US';
    return (
      this.voices.find((x) => x.lang === lang && x.localService) ||
      this.voices.find((x) => x.lang.startsWith(lang.split('-')[0]) && x.localService) ||
      this.voices.find((x) => x.lang.startsWith('en') && x.localService) ||
      this.voices.find((x) => x.localService) ||
      this.voices[0]
    );
  }

  setVoice(voiceURI) {
    this.voiceURI = voiceURI;
    if (this.playing) this._restartCurrent();
  }

  setRate(rate) {
    this.rate = Math.min(4, Math.max(0.5, rate));
    if (this.playing) this._restartCurrent();
    this._emit('state');
  }

  load(book, cursor = 0) {
    this.stop();
    this.book = book;
    this.cursor = Math.min(Math.max(0, cursor), Math.max(0, book.sentences.length - 1));
    this.cumWords = new Array(book.sentences.length + 1);
    let acc = 0;
    book.sentences.forEach((s, i) => { this.cumWords[i] = acc; acc += s.w; });
    this.cumWords[book.sentences.length] = acc;
    this.totalWords = acc;
    this._bookLoaded();
    this._emit('sentence', this.cursor);
    this._emit('state');
  }

  /* time math (at current rate) */
  _wordsPerSec() { return (BASE_WPM * this.rate) / 60; }
  elapsedSec() { return this.cumWords.length ? this.cumWords[this.cursor] / this._wordsPerSec() : 0; }
  totalSec() { return this.totalWords / this._wordsPerSec(); }
  remainingSec() { return Math.max(0, this.totalSec() - this.elapsedSec()); }
  progress() { return this.totalWords ? this.cumWords[this.cursor] / this.totalWords : 0; }

  toggle() { this.playing ? this.pause() : this.play(); }

  seekToSentence(i) {
    if (!this.book) return;
    this.cursor = Math.min(Math.max(0, i), this.book.sentences.length - 1);
    this._emit('sentence', this.cursor);
    if (this.playing) this._restartCurrent();
    else this._emit('state');
  }

  /** Skip forward/back by ~`seconds` of speech at the current rate. */
  skip(seconds) {
    if (!this.book) return;
    const deltaWords = Math.max(1, Math.abs(seconds) * this._wordsPerSec());
    const dir = seconds < 0 ? -1 : 1;
    let i = this.cursor;
    let moved = 0;
    while (moved < deltaWords) {
      const next = i + dir;
      if (next < 0 || next >= this.book.sentences.length) break;
      moved += this.book.sentences[dir < 0 ? next : i].w || 1;
      i = next;
    }
    this.seekToSentence(i);
  }

  seekToProgress(frac) {
    if (!this.book || !this.totalWords) return;
    const target = frac * this.totalWords;
    let lo = 0, hi = this.book.sentences.length - 1;
    while (lo < hi) {
      const mid = (lo + hi + 1) >> 1;
      if (this.cumWords[mid] <= target) lo = mid; else hi = mid - 1;
    }
    this.seekToSentence(lo);
  }

  /* subclass hooks */
  _bookLoaded() {}
  _restartCurrent() {}
  play() { return false; }
  pause() {}
  stop() {}
  preview() {}
  updateMediaSession() {}
}

/* ================= Web Speech backend ================= */

class WebPlayer extends BasePlayer {
  constructor() {
    super();
    this.synth = window.speechSynthesis || null;
    this._utterance = null;
    this._keepAlive = null;
    this._audio = null;
    this._stopping = false;
    if (this.synth) {
      this._loadVoices();
      this.synth.addEventListener?.('voiceschanged', () => this._loadVoices());
    }
  }

  get supported() { return !!this.synth; }

  _loadVoices() {
    const v = this.synth.getVoices();
    if (v.length) {
      this.voices = v.map((x) => ({ voiceURI: x.voiceURI, name: x.name, lang: x.lang, localService: x.localService, _v: x }));
      this._emit('voices', this.voices);
    }
  }

  _bookLoaded() { this._setupMediaSession(); }

  play() {
    if (!this.book || !this.supported) return false;
    if (!this.book.sentences.length) return false;
    if (this.cursor >= this.book.sentences.length) this.cursor = 0;
    this.playing = true;
    this._startAudioFocus();
    this._speakCurrent();
    this._startKeepAlive();
    this.updateMediaSession();
    this._emit('state');
    return true;
  }

  pause() {
    this.playing = false;
    this._cancelSpeech();
    this._stopKeepAlive();
    this._pauseAudioFocus();
    if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'paused';
    this._emit('state');
  }

  stop() {
    this.playing = false;
    this._cancelSpeech();
    this._stopKeepAlive();
    this._pauseAudioFocus();
    this._emit('state');
  }

  preview(voice) {
    try {
      this.synth.cancel();
      const u = new SpeechSynthesisUtterance('This is how I sound.');
      if (voice && voice._v) { u.voice = voice._v; u.lang = voice.lang; }
      u.rate = this.rate;
      this.synth.speak(u);
    } catch { /* preview is best-effort */ }
  }

  _cancelSpeech() {
    this._stopping = true;
    try { this.synth && this.synth.cancel(); } catch { /* ignore */ }
    this._utterance = null;
    setTimeout(() => { this._stopping = false; }, 0);
  }

  _restartCurrent() {
    this._cancelSpeech();
    // small delay: Chrome misbehaves if speak() is called synchronously after cancel()
    setTimeout(() => { if (this.playing) this._speakCurrent(); }, 60);
  }

  _speakCurrent() {
    if (!this.playing || !this.book) return;
    if (this.cursor >= this.book.sentences.length) { this._finished(); return; }
    const s = this.book.sentences[this.cursor];
    const u = new SpeechSynthesisUtterance(s.t);
    const voice = this.getVoice();
    if (voice && voice._v) { u.voice = voice._v; u.lang = voice.lang; }
    u.rate = this.rate;
    u.onend = () => {
      if (this._stopping || this._utterance !== u) return;
      this.cursor += 1;
      if (this.cursor >= this.book.sentences.length) { this._finished(); return; }
      this._emit('sentence', this.cursor);
      this._speakCurrent();
    };
    u.onerror = (e) => {
      if (this._stopping || this._utterance !== u) return;
      if (e.error === 'interrupted' || e.error === 'canceled') return;
      this.playing = false;
      this._stopKeepAlive();
      this._pauseAudioFocus();
      this._emit('state', { error: e.error || 'speech failed' });
    };
    this._utterance = u;
    this._emit('sentence', this.cursor);
    try { this.synth.speak(u); } catch {
      this.playing = false;
      this._emit('state', { error: 'speech failed' });
    }
  }

  _finished() {
    this.playing = false;
    this.cursor = this.book ? this.book.sentences.length : 0;
    this._stopKeepAlive();
    this._pauseAudioFocus();
    this._emit('state', { finished: true });
  }

  /* Chrome (desktop) silently stops long speech sessions; periodic pause/resume keeps it alive. */
  _startKeepAlive() {
    this._stopKeepAlive();
    const isChromeDesktop = /Chrome/.test(navigator.userAgent) && !/Android|Mobile/.test(navigator.userAgent);
    if (!isChromeDesktop) return;
    this._keepAlive = setInterval(() => {
      if (this.playing && this.synth.speaking && !this.synth.paused) {
        this.synth.pause();
        this.synth.resume();
      }
    }, 12000);
  }

  _stopKeepAlive() {
    if (this._keepAlive) { clearInterval(this._keepAlive); this._keepAlive = null; }
  }

  /* speechSynthesis alone doesn't take audio focus, so browsers won't show a media
     notification for it; a silent looping <audio> makes the session "real". */
  _startAudioFocus() {
    if (!this._audio) {
      this._audio = new Audio(makeSilentWavUrl());
      this._audio.loop = true;
      this._audio.volume = 0.001;
    }
    this._audio.play().catch(() => { /* autoplay may need the user gesture that triggered play() */ });
  }

  _pauseAudioFocus() { if (this._audio) this._audio.pause(); }

  _setupMediaSession() {
    if (!('mediaSession' in navigator) || !this.book) return;
    const ms = navigator.mediaSession;
    const artwork = this.book.cover ? [{ src: this.book.cover, sizes: '220x300', type: 'image/jpeg' }] : [];
    ms.metadata = new MediaMetadata({
      title: this.book.title,
      artist: this.book.author || 'VoxReader',
      album: 'VoxReader',
      artwork,
    });
    const safe = (action, fn) => { try { ms.setActionHandler(action, fn); } catch { /* unsupported */ } };
    safe('play', () => this.play());
    safe('pause', () => this.pause());
    safe('stop', () => this.stop());
    safe('seekbackward', (d) => this.skip(-(d.seekOffset || 10)));
    safe('seekforward', (d) => this.skip(d.seekOffset || 10));
    safe('previoustrack', () => this.skip(-30));
    safe('nexttrack', () => this.skip(30));
  }

  updateMediaSession() {
    if (!('mediaSession' in navigator)) return;
    navigator.mediaSession.playbackState = this.playing ? 'playing' : 'paused';
    try {
      navigator.mediaSession.setPositionState({
        duration: Math.max(1, this.totalSec()),
        playbackRate: 1,
        position: Math.min(this.elapsedSec(), this.totalSec()),
      });
    } catch { /* older browsers */ }
  }
}

/* ================= Native (Capacitor) backend ================= */

const NATIVE_BATCH = 200; // sentences queued into the engine at a time

class NativePlayer extends BasePlayer {
  constructor(plugin) {
    super();
    this.plugin = plugin;
    this._batchEnd = 0;
    this._coverSent = null;
    this._notifAsked = false;
    this._lastNowPlaying = 0;

    plugin.addListener('utterance', ({ id }) => {
      if (!this.playing || typeof id !== 'number') return;
      this.cursor = id;
      this._emit('sentence', id);
      // refresh the notification's position occasionally, not per sentence
      if (Date.now() - this._lastNowPlaying > 5000) this._sendNowPlaying();
    });
    plugin.addListener('queueDone', () => {
      if (!this.playing || !this.book) return;
      if (this._batchEnd < this.book.sentences.length) this._queueFrom(this._batchEnd);
      else this._finished();
    });
    plugin.addListener('mediaAction', (data) => {
      const a = data && data.action;
      if (a === 'play') this.play();
      else if (a === 'pause' || a === 'stop') this.pause();
      else if (a === 'next') this.skip(30);
      else if (a === 'previous') this.skip(-30);
      else if (a === 'seekTo' && this.totalSec() > 0) this.seekToProgress((data.positionSec || 0) / this.totalSec());
    });
    plugin.addListener('ttsReady', () => this._loadVoices());
    this._loadVoices();
  }

  async _loadVoices() {
    try {
      const { voices } = await this.plugin.getVoices();
      this.voices = (voices || []).map((v) => ({
        voiceURI: v.id, name: v.name, lang: v.lang || 'en-US', localService: !v.networkRequired,
      }));
      this._emit('voices', this.voices);
    } catch { /* engine not ready yet; ttsReady will retry */ }
  }

  _bookLoaded() { this._coverSent = null; }

  async play() {
    if (!this.book || !this.book.sentences.length) return false;
    if (this.cursor >= this.book.sentences.length) this.cursor = 0;
    this.playing = true;
    if (!this._notifAsked) {
      this._notifAsked = true;
      this.plugin.requestNotifications().catch(() => {});
    }
    try {
      await this.plugin.configure({ voice: this.voiceURI || undefined, rate: this.rate });
      await this._queueFrom(this.cursor);
    } catch (e) {
      this.playing = false;
      this._emit('state', { error: (e && e.message) || 'speech failed' });
      return false;
    }
    this._sendNowPlaying();
    this._emit('state');
    return true;
  }

  pause() {
    this.playing = false;
    this.plugin.stop().catch(() => {});
    this._sendNowPlaying();
    this._emit('state');
  }

  stop() {
    this.playing = false;
    this.plugin.stop().catch(() => {});
    this.plugin.clearNowPlaying().catch(() => {});
    this._emit('state');
  }

  preview(voice) {
    const cfg = voice ? { voice: voice.voiceURI, rate: this.rate } : { rate: this.rate };
    this.plugin.configure(cfg)
      .then(() => this.plugin.preview({ text: 'This is how I sound.' }))
      .catch(() => {});
  }

  async _restartCurrent() {
    if (!this.playing) return;
    try {
      await this.plugin.configure({ voice: this.voiceURI || undefined, rate: this.rate });
      await this._queueFrom(this.cursor);
    } catch { /* keep state; next play() retries */ }
  }

  _queueFrom(i) {
    const end = Math.min(i + NATIVE_BATCH, this.book.sentences.length);
    this._batchEnd = end;
    const sentences = [];
    for (let k = i; k < end; k++) sentences.push({ id: k, text: this.book.sentences[k].t });
    return this.plugin.speakBatch({ sentences });
  }

  _finished() {
    this.playing = false;
    this.cursor = this.book ? this.book.sentences.length : 0;
    this.plugin.clearNowPlaying().catch(() => {});
    this._emit('state', { finished: true });
  }

  _sendNowPlaying() {
    if (!this.book) return;
    this._lastNowPlaying = Date.now();
    const payload = {
      title: this.book.title,
      author: this.book.author || 'VoxReader',
      playing: this.playing,
      positionSec: this.elapsedSec(),
      durationSec: this.totalSec(),
    };
    if (this.book.cover && this._coverSent !== this.book.id) {
      payload.cover = this.book.cover;
      this._coverSent = this.book.id;
    }
    this.plugin.setNowPlaying(payload).catch(() => {});
  }

  updateMediaSession() {
    if (Date.now() - this._lastNowPlaying > 2000) this._sendNowPlaying();
  }
}

/* ================= helpers ================= */

function makeSilentWavUrl() {
  // 0.5 s of 8 kHz 8-bit mono silence
  const samples = 4000;
  const buf = new Uint8Array(44 + samples);
  const dv = new DataView(buf.buffer);
  const writeStr = (off, s) => { for (let i = 0; i < s.length; i++) buf[off + i] = s.charCodeAt(i); };
  writeStr(0, 'RIFF'); dv.setUint32(4, 36 + samples, true); writeStr(8, 'WAVE');
  writeStr(12, 'fmt '); dv.setUint32(16, 16, true); dv.setUint16(20, 1, true); dv.setUint16(22, 1, true);
  dv.setUint32(24, 8000, true); dv.setUint32(28, 8000, true); dv.setUint16(32, 1, true); dv.setUint16(34, 8, true);
  writeStr(36, 'data'); dv.setUint32(40, samples, true);
  buf.fill(0x80, 44);
  return URL.createObjectURL(new Blob([buf], { type: 'audio/wav' }));
}

export function formatTime(sec) {
  sec = Math.max(0, Math.round(sec));
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  if (h) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

export function formatRemaining(sec) {
  sec = Math.max(0, sec);
  if (sec < 60) return 'less than a minute left';
  const min = Math.round(sec / 60);
  if (min < 60) return `${min} minute${min === 1 ? '' : 's'} remaining`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  return `${h}h ${m}m remaining`;
}
