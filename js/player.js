/* TTS playback engine.
   Speaks a book sentence-by-sentence with speechSynthesis, exposes play/pause/seek,
   estimates listening time from word counts, and integrates with the Media Session API
   so the OS shows lock-screen / notification playback controls.

   Note on the silent <audio> loop: speechSynthesis alone does not take "audio focus",
   so Android/Chrome won't surface a media notification for it. Playing a silent looping
   WAV alongside speech makes the browser treat the app as an active media session,
   which is what puts the play/pause/skip controls into the system notification shade. */

const BASE_WPM = 170; // average narration words-per-minute at 1.0x

export const SPEED_PRESETS = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3, 3.5];

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

export class Player {
  constructor() {
    this.synth = window.speechSynthesis || null;
    this.voices = [];
    this.voiceURI = null;
    this.rate = 1;
    this.book = null;         // { id, title, author, cover, sentences: [{t,b,p,w}] }
    this.cursor = 0;          // index of current sentence
    this.playing = false;
    this.cumWords = [];       // cumWords[i] = words before sentence i
    this.totalWords = 0;
    this.listeners = { sentence: [], state: [], voices: [] };
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

  on(event, fn) { this.listeners[event].push(fn); }
  _emit(event, ...args) { for (const fn of this.listeners[event]) fn(...args); }

  _loadVoices() {
    const v = this.synth.getVoices();
    if (v.length) {
      this.voices = v;
      this._emit('voices', v);
    }
  }

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
    this._setupMediaSession();
    this._emit('sentence', this.cursor);
    this._emit('state');
  }

  /* ---- time math (at current rate) ---- */
  _wordsPerSec() { return (BASE_WPM * this.rate) / 60; }
  elapsedSec() { return this.cumWords.length ? this.cumWords[this.cursor] / this._wordsPerSec() : 0; }
  totalSec() { return this.totalWords / this._wordsPerSec(); }
  remainingSec() { return Math.max(0, this.totalSec() - this.elapsedSec()); }
  progress() { return this.totalWords ? this.cumWords[this.cursor] / this.totalWords : 0; }

  play() {
    if (!this.book || !this.supported) return false;
    if (!this.book.sentences.length) return false;
    if (this.cursor >= this.book.sentences.length) this.cursor = 0;
    this.playing = true;
    this._startAudioFocus();
    this._speakCurrent();
    this._startKeepAlive();
    this._updateMediaSession();
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

  toggle() { this.playing ? this.pause() : this.play(); }

  stop() {
    this.playing = false;
    this._cancelSpeech();
    this._stopKeepAlive();
    this._pauseAudioFocus();
    this._emit('state');
  }

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
    // binary search over cumWords
    let lo = 0, hi = this.book.sentences.length - 1;
    while (lo < hi) {
      const mid = (lo + hi + 1) >> 1;
      if (this.cumWords[mid] <= target) lo = mid; else hi = mid - 1;
    }
    this.seekToSentence(lo);
  }

  /* ---- internals ---- */

  _cancelSpeech() {
    this._stopping = true;
    try { this.synth && this.synth.cancel(); } catch { /* ignore */ }
    this._utterance = null;
    // allow the cancel's async 'end/error' events to flush before we clear the flag
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
    if (voice) { u.voice = voice; u.lang = voice.lang; }
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
      // Genuine engine failure (e.g. no voices) — stop rather than spin.
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

  /* Chrome (desktop) silently stops long speech sessions; a periodic pause/resume keeps it alive. */
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

  /* ---- media session / notification controls ---- */

  _startAudioFocus() {
    if (!this._audio) {
      this._audio = new Audio(makeSilentWavUrl());
      this._audio.loop = true;
      this._audio.volume = 0.001;
    }
    this._audio.play().catch(() => { /* autoplay may need the user gesture that triggered play() */ });
  }

  _pauseAudioFocus() {
    if (this._audio) this._audio.pause();
  }

  _setupMediaSession() {
    if (!('mediaSession' in navigator) || !this.book) return;
    const ms = navigator.mediaSession;
    const artwork = this.book.cover
      ? [{ src: this.book.cover, sizes: '220x300', type: 'image/jpeg' }]
      : [];
    ms.metadata = new MediaMetadata({
      title: this.book.title,
      artist: this.book.author || 'VoxReader',
      album: 'VoxReader',
      artwork,
    });
    const safe = (action, fn) => { try { ms.setActionHandler(action, fn); } catch { /* unsupported action */ } };
    safe('play', () => this.play());
    safe('pause', () => this.pause());
    safe('stop', () => this.stop());
    safe('seekbackward', (d) => this.skip(-(d.seekOffset || 10)));
    safe('seekforward', (d) => this.skip(d.seekOffset || 10));
    safe('previoustrack', () => this.skip(-30));
    safe('nexttrack', () => this.skip(30));
  }

  _updateMediaSession() {
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
