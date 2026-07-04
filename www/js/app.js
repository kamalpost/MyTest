/* VoxReader — main UI controller */

import * as db from './db.js';
import { extractFile, extractPlainText, openPdf, renderPdfPage, assessTextQuality } from './extract.js';
import { createPlayer, SPEED_PRESETS, formatTime, formatRemaining } from './player.js';
import { createOcrJob, ocrSupported, tessLangFor, ocrLangLabel } from './ocr.js';

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

const APP_VERSION = '1.2.0';
const state = {
  books: [],            // light book records for lists
  currentBookId: null,  // book loaded in the player
  pdfDoc: null,         // open pdf.js document for "original" view
  readerView: 'text',   // 'text' | 'original'
  fontScale: 1,
  saveTimer: null,
};

const player = createPlayer();
const IS_NATIVE = !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform());

/* ================= boot ================= */

async function boot() {
  registerServiceWorker();
  wireNav();
  wireHome();
  wireLibrary();
  wireReader();
  wireSheets();
  wireProfile();
  wirePlayerEvents();

  const [rate, voiceURI, voiceMap, fontScale] = await Promise.all([
    db.getSetting('rate', 1),
    db.getSetting('voiceURI', null),
    db.getSetting('voiceMap', {}),
    db.getSetting('fontScale', 1),
  ]);
  player.rate = rate;
  player.voiceURI = voiceURI;
  player.voiceMap = voiceMap || {};
  state.fontScale = fontScale;
  document.documentElement.style.setProperty('--reader-scale', fontScale);

  await refreshBooks();
  route(location.hash);
  db.requestPersistence();
}

function registerServiceWorker() {
  if (IS_NATIVE) {
    // In the Android app all assets ship inside the APK — no service worker needed.
    setOfflineStatus('yes — native app');
    return;
  }
  if (!('serviceWorker' in navigator)) {
    setOfflineStatus('not supported');
    return;
  }
  navigator.serviceWorker.register('./sw.js')
    .then((reg) => {
      const set = () => setOfflineStatus(navigator.serviceWorker.controller || reg.active ? 'yes — app cached' : 'installing…');
      set();
      navigator.serviceWorker.ready.then(set);
    })
    .catch(() => setOfflineStatus('failed'));
}

function setOfflineStatus(text) {
  const el = $('#offline-status');
  if (el) el.textContent = text;
}

/* ================= routing ================= */

const VIEWS = ['home', 'library', 'profile'];

function route(hash) {
  const name = (hash || '#home').replace('#', '') || 'home';
  if (name.startsWith('reader')) return; // reader is an overlay managed separately
  showView(VIEWS.includes(name) ? name : 'home');
}

function showView(name) {
  VIEWS.forEach((v) => $(`#view-${v}`).classList.toggle('hidden', v !== name));
  $$('#bottom-nav .nav-btn').forEach((b) => b.classList.toggle('active', b.dataset.nav === name));
  if (location.hash !== `#${name}`) history.replaceState(null, '', `#${name}`);
  if (name === 'library') renderLibrary();
  if (name === 'profile') renderProfile();
  if (name === 'home') renderHome();
}

function wireNav() {
  $$('#bottom-nav .nav-btn').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const target = btn.dataset.nav;
      if (target === 'reader') {
        const id = state.currentBookId || (await lastOpenedId());
        if (id) openReader(id);
        else { toast('Import a book first'); showView('home'); }
        return;
      }
      showView(target);
    });
  });
  $('#nav-add').addEventListener('click', () => $('#file-input').click());
  window.addEventListener('hashchange', () => route(location.hash));
}

async function lastOpenedId() {
  const opened = state.books.filter((b) => b.lastOpenedAt).sort((a, b) => b.lastOpenedAt - a.lastOpenedAt);
  return opened[0]?.id || state.books[0]?.id || null;
}

/* ================= data ================= */

async function refreshBooks() {
  state.books = await db.listBooks();
  renderHome();
  renderLibrary();
  renderMiniPlayer();
}

function bookProgress(b) {
  if (!b.sentenceCount) return 0;
  return Math.min(1, (b.cursor || 0) / b.sentenceCount);
}

function bookRemainingText(b) {
  const wordsLeft = Math.max(0, (b.wordCount || 0) * (1 - bookProgress(b)));
  const sec = wordsLeft / ((170 * (player.rate || 1)) / 60);
  return formatRemaining(sec);
}

/* ================= home ================= */

function wireHome() {
  $('#promo-try').addEventListener('click', () => {
    applyRate(2);
    toast('Playback speed set to 2× — change it anytime from the player');
  });
  $('#tile-files').addEventListener('click', () => $('#file-input').click());
  $('#tile-word').addEventListener('click', () => $('#file-input').click());
  $('#tile-text').addEventListener('click', () => openSheet('sheet-text'));
  $('#tile-sample').addEventListener('click', importSample);
  $('#file-input').addEventListener('change', async (e) => {
    const files = Array.from(e.target.files || []);
    e.target.value = '';
    for (const f of files) await importFile(f);
  });
}

function coverImg(b, cls = '') {
  if (b.cover) return `<img src="${b.cover}" alt="" class="${cls}" />`;
  return `<div class="cover-fallback ${cls}"></div>`;
}

function renderHome() {
  const row = $('#continue-row');
  const recent = state.books
    .slice()
    .sort((a, b) => (b.lastOpenedAt || b.addedAt || 0) - (a.lastOpenedAt || a.addedAt || 0))
    .slice(0, 10);
  row.innerHTML = '';
  for (const b of recent) {
    const el = document.createElement('button');
    el.className = 'book-card';
    el.setAttribute('role', 'listitem');
    el.innerHTML = `
      <div class="cover-wrap">${coverImg(b)}
        <div class="cover-progress" style="width:${Math.round(bookProgress(b) * 100)}%"></div>
      </div>
      <div class="book-name">${escapeHtml(b.title)}</div>
      <div class="book-sub">${escapeHtml(typeLabel(b.type))}</div>`;
    el.addEventListener('click', () => openReader(b.id));
    row.appendChild(el);
  }
  $('#continue-empty').classList.toggle('hidden', recent.length > 0);
}

function typeLabel(t) {
  return { pdf: 'PDF', docx: 'Word', txt: 'Text file', text: 'Pasted text' }[t] || t;
}

/* ================= library ================= */

function wireLibrary() {
  $('#library-search').addEventListener('input', renderLibrary);
}

function renderLibrary() {
  const q = ($('#library-search').value || '').toLowerCase().trim();
  const list = $('#library-list');
  const books = state.books
    .filter((b) => !q || b.title.toLowerCase().includes(q) || (b.author || '').toLowerCase().includes(q))
    .sort((a, b) => (b.lastOpenedAt || b.addedAt || 0) - (a.lastOpenedAt || a.addedAt || 0));
  list.innerHTML = '';
  for (const b of books) {
    const item = document.createElement('div');
    item.className = 'library-item';
    item.setAttribute('role', 'listitem');
    const pct = Math.round(bookProgress(b) * 100);
    item.innerHTML = `
      ${coverImg(b)}
      <div class="lib-meta">
        <div class="lib-title">${escapeHtml(b.title)}</div>
        <div class="lib-sub"><span class="type-badge ${b.type}">${escapeHtml(typeLabel(b.type).toUpperCase())}</span>${b.lang && b.lang !== 'en' ? `${escapeHtml(languageName(b.lang))} · ` : ''}${b.pages > 1 ? `${b.pages} pages · ` : ''}${bookRemainingText(b)}</div>
        <div class="lib-progress"><div style="width:${pct}%"></div></div>
      </div>
      <button class="lib-more" aria-label="Book options">⋮</button>`;
    item.querySelector('.lib-meta').addEventListener('click', () => openReader(b.id));
    item.querySelector('img, .cover-fallback')?.addEventListener('click', () => openReader(b.id));
    item.querySelector('.lib-more').addEventListener('click', (e) => { e.stopPropagation(); openBookSheet(b); });
    list.appendChild(item);
  }
  $('#library-empty').classList.toggle('hidden', books.length > 0);
}

let bookSheetTarget = null;
function openBookSheet(b) {
  bookSheetTarget = b;
  $('#book-sheet-title').textContent = b.title;
  openSheet('sheet-book');
}

/* ================= import ================= */

async function importFile(file) {
  showImport(`Importing “${file.name}”…`);
  try {
    const extracted = await extractFile(file, (p, total) => {
      $('#import-status').textContent = `Reading page ${p} of ${total}…`;
    });
    const book = await saveExtracted(extracted, file);
    hideImport();
    toast(book.textCorrupted
      ? '⚠ This file uses a legacy non-Unicode font — showing original pages; read-aloud may be garbled'
      : `Added “${book.title}”`);
    openReader(book.id);
  } catch (err) {
    hideImport();
    console.error(err);
    toast(err.message || 'Could not import that file');
  }
}

async function saveExtracted(extracted, fileBlob) {
  const id = `bk_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const book = {
    id,
    ...extracted,
    file: fileBlob || null,
    sentenceCount: extracted.sentences.length,
    cursor: 0,
    addedAt: Date.now(),
    lastOpenedAt: null,
    bookmarks: [],
  };
  await db.putBook(book);
  await refreshBooks();
  return book;
}

const SAMPLE_TEXT = `Welcome to VoxReader.

VoxReader turns any PDF or Word document into an audiobook, right on your device. Nothing is uploaded anywhere: your books, your listening position, and your settings all stay in local storage, so everything keeps working with no internet connection at all.

To add a book, tap the plus button or one of the import tiles on the home screen, then pick a PDF or Word file from your device. VoxReader extracts the text, splits it into sentences, and reads it aloud with the voice of your choice.

While listening, you can change the reading speed from half speed all the way up to three and a half times. Skip backwards or forwards ten seconds at a time, or drag the progress bar to jump anywhere in the book. The sentence being read is always highlighted, and tapping any sentence starts reading from there.

VoxReader also shows playback controls in your notification shade and on your lock screen, so you can pause and resume without opening the app. Your position is saved automatically. Whenever you come back, you simply continue where you left off.

Happy listening!`;

async function importSample() {
  try {
    const existing = state.books.find((b) => b.title === 'Welcome to VoxReader');
    if (existing) { openReader(existing.id); return; }
    const extracted = extractPlainText('Welcome to VoxReader', SAMPLE_TEXT, 'text');
    extracted.author = 'VoxReader guide';
    const book = await saveExtracted(extracted, null);
    toast('Sample added — press play!');
    openReader(book.id);
  } catch (err) {
    toast(err.message || 'Could not create sample');
  }
}

function showImport(msg) {
  $('#import-status').textContent = msg;
  $('#import-overlay').classList.remove('hidden');
}
function hideImport() { $('#import-overlay').classList.add('hidden'); }

/* ================= reader ================= */

function wireReader() {
  $('#reader-close').addEventListener('click', closeReader);
  $('#ctl-play').addEventListener('click', () => togglePlay());
  $('#ctl-back').addEventListener('click', () => player.skip(-10));
  $('#ctl-fwd').addEventListener('click', () => player.skip(10));
  $('#ctl-speed').addEventListener('click', () => openSpeedSheet());
  $('#ctl-voice').addEventListener('click', () => openVoiceSheet());
  $('#reader-font').addEventListener('click', () => openSheet('sheet-font'));
  $('#reader-view-toggle').addEventListener('click', toggleReaderView);
  $('#reader-bookmark').addEventListener('click', addBookmark);
  $('#reader-scrub').addEventListener('input', (e) => {
    player.seekToProgress(Number(e.target.value) / 1000);
  });
  $('#reader-text').addEventListener('click', (e) => {
    const span = e.target.closest('span[data-si]');
    if (span) player.seekToSentence(Number(span.dataset.si));
  });
  $('#ocr-stop').addEventListener('click', () => {
    if (ocrJob) { ocrJob.stop(); $('#ocr-status').textContent = 'Finishing current page…'; }
  });
  // save position when leaving the page
  window.addEventListener('visibilitychange', () => { if (document.hidden) savePosition(); });
  window.addEventListener('pagehide', savePosition);
}

async function openReader(id) {
  const book = await db.getBook(id);
  if (!book) { toast('Book not found'); return; }

  $('#view-reader').classList.remove('hidden');
  $('#reader-title-chip').textContent = book.title + (book.author && book.author !== book.title ? ' · ' + book.author : '');

  // Books imported before v1.1.1 have no corruption flag — compute it once now,
  // so legacy glyph-encoded PDFs already in the library get the pages-view fallback.
  if (book.textCorrupted === undefined) {
    book.textCorrupted = assessTextQuality(book.sentences.map((s) => s.t).join(' ')).corrupted;
    db.updateBook(id, { textCorrupted: book.textCorrupted });
  }
  updateReaderWarning(book);

  if (state.currentBookId !== id) {
    state.currentBookId = id;
    player.load(book, book.cursor || 0);
    renderReaderText(book);
    state.pdfDoc = null;
    // Glyph-encoded (non-Unicode) PDFs extract as garbage — open the real pages instead.
    state.readerView = book.textCorrupted && book.type === 'pdf' && book.file ? 'original' : 'text';
    $('#reader-view-toggle').classList.toggle('hidden', book.type !== 'pdf' || !book.file);
    applyReaderView();
  }
  updateReaderChrome();
  highlightSentence(player.cursor, true);

  await db.updateBook(id, { lastOpenedAt: Date.now() });
  const light = state.books.find((b) => b.id === id);
  if (light) light.lastOpenedAt = Date.now();
  renderHome();
  renderMiniPlayer();
}

function closeReader() {
  savePosition();
  $('#view-reader').classList.add('hidden');
  renderMiniPlayer();
  renderHome();
  renderLibrary();
}

/** Persistent warning strip below the title — visible at any reading position. */
function updateReaderWarning(book) {
  const el = $('#reader-warning');
  if (!book.textCorrupted) { el.classList.add('hidden'); return; }
  el.innerHTML = '';
  const msg = document.createElement('span');
  msg.textContent = '⚠ Legacy (non-Unicode) font — text & read-aloud are garbled. '
    + (book.type === 'pdf' && book.file
      ? 'Showing the original pages (📃 toggles views).'
      : 'To listen, import a Unicode version of this book.');
  el.appendChild(msg);
  if (book.type === 'pdf' && book.file && ocrSupported()) {
    const btn = document.createElement('button');
    btn.className = 'ocr-btn';
    btn.textContent = (book.ocrNext || 1) > 1
      ? `▶ Resume text recognition (page ${book.ocrNext} of ${book.pages})`
      : `✨ Recognize text (${ocrLangLabel(tessLangFor(book))} OCR) to enable listening`;
    btn.addEventListener('click', () => runOcr(book.id));
    el.appendChild(btn);
  }
  el.classList.remove('hidden');
}

/* ================= OCR ================= */

let ocrJob = null;

async function runOcr(bookId) {
  if (ocrJob) return;
  const book = await db.getBook(bookId);
  if (!book || !book.file) { toast('The original PDF is not available for this book'); return; }
  player.stop();
  $('#ocr-overlay').classList.remove('hidden');
  $('#ocr-status').textContent = `Loading ${ocrLangLabel(tessLangFor(book))} recognition…`;
  $('#ocr-progress-bar').style.width = `${(((book.ocrNext || 1) - 1) / (book.pages || 1)) * 100}%`;
  ocrJob = createOcrJob(book);
  try {
    const res = await ocrJob.run(({ page, total }) => {
      $('#ocr-status').textContent = `Recognizing page ${page} of ${total}…`;
      $('#ocr-progress-bar').style.width = `${(page / total) * 100}%`;
    });
    $('#ocr-overlay').classList.add('hidden');
    if (res.completed) {
      toast('✅ Text recognized — this book can be listened to now!');
      state.currentBookId = null; // force a full reader reload with the new text
      await refreshBooks();
      await openReader(bookId);
    } else {
      toast(`Recognition paused at page ${res.nextPage} — resume anytime from the banner`);
      updateReaderWarning(await db.getBook(bookId));
    }
  } catch (err) {
    $('#ocr-overlay').classList.add('hidden');
    console.error(err);
    toast(err.message || 'Text recognition failed');
  } finally {
    ocrJob = null;
  }
}

function renderReaderText(book) {
  const art = $('#reader-text');
  art.innerHTML = '';
  let lastPage = 0;
  // group sentences by block so paragraphs stay intact
  const frag = document.createDocumentFragment();
  let blockEl = null;
  let blockIdx = -1;
  book.sentences.forEach((s, i) => {
    if (book.type === 'pdf' && s.p !== lastPage) {
      lastPage = s.p;
      const pb = document.createElement('div');
      pb.className = 'page-break';
      pb.textContent = `Page ${s.p}`;
      frag.appendChild(pb);
      blockIdx = -1;
    }
    if (s.b !== blockIdx) {
      blockIdx = s.b;
      const blk = book.blocks[s.b] || { tag: 'p' };
      const tag = /^h[1-6]$/.test(blk.tag) ? blk.tag : (blk.tag === 'li' ? 'li' : 'p');
      blockEl = document.createElement(tag === 'li' ? 'p' : tag);
      if (blk.tag === 'li') blockEl.textContent = '• ';
      frag.appendChild(blockEl);
    }
    const span = document.createElement('span');
    span.dataset.si = i;
    span.textContent = s.t + ' ';
    blockEl.appendChild(span);
  });
  art.appendChild(frag);
}

function toggleReaderView() {
  state.readerView = state.readerView === 'text' ? 'original' : 'text';
  applyReaderView();
}

async function applyReaderView() {
  const isOriginal = state.readerView === 'original';
  $('#reader-text').classList.toggle('hidden', isOriginal);
  $('#reader-original').classList.toggle('hidden', !isOriginal);
  $('#reader-view-toggle').classList.toggle('on', isOriginal);
  if (isOriginal) await ensurePdfView();
}

async function ensurePdfView() {
  const container = $('#reader-original');
  if (container.dataset.bookId === state.currentBookId) { scrollPdfToCurrent(); return; }
  container.innerHTML = '';
  container.dataset.bookId = state.currentBookId;
  const book = await db.getBook(state.currentBookId);
  if (!book || !book.file) return;
  try {
    state.pdfDoc = await openPdf(book.file);
  } catch {
    toast('Could not render the original PDF');
    return;
  }
  const width = Math.min(container.clientWidth || 520, 520);
  for (let p = 1; p <= state.pdfDoc.numPages; p++) {
    const holder = document.createElement('div');
    holder.className = 'pdf-page';
    holder.dataset.page = p;
    holder.innerHTML = `<div class="page-num">${p} / ${state.pdfDoc.numPages}</div>`;
    container.appendChild(holder);
  }
  const io = new IntersectionObserver(async (entries) => {
    for (const en of entries) {
      if (!en.isIntersecting) continue;
      const holder = en.target;
      if (holder.dataset.rendered) continue;
      holder.dataset.rendered = '1';
      io.unobserve(holder);
      try {
        const canvas = await renderPdfPage(state.pdfDoc, Number(holder.dataset.page), width);
        holder.prepend(canvas);
      } catch { /* page render failed; leave placeholder */ }
    }
  }, { root: $('#reader-content'), rootMargin: '600px' });
  container.querySelectorAll('.pdf-page').forEach((el) => io.observe(el));
  scrollPdfToCurrent();
}

function scrollPdfToCurrent() {
  if (state.readerView !== 'original' || !player.book) return;
  const s = player.book.sentences[Math.min(player.cursor, player.book.sentences.length - 1)];
  if (!s) return;
  const holder = $(`#reader-original .pdf-page[data-page="${s.p}"]`);
  if (holder) {
    $$('#reader-original .pdf-page.current').forEach((el) => el.classList.remove('current'));
    holder.classList.add('current');
    holder.scrollIntoView({ block: 'start', behavior: 'smooth' });
  }
}

async function addBookmark() {
  if (!state.currentBookId || !player.book) return;
  const s = player.book.sentences[player.cursor];
  const bm = { sentence: player.cursor, page: s?.p || 1, at: Date.now() };
  const book = await db.updateBook(state.currentBookId, {});
  if (book) {
    book.bookmarks = book.bookmarks || [];
    book.bookmarks.push(bm);
    await db.putBook(book);
  }
  $('#reader-bookmark').classList.add('on');
  setTimeout(() => $('#reader-bookmark').classList.remove('on'), 900);
  toast(`Bookmarked ${s?.p > 1 ? 'page ' + s.p : 'position'} ✓`);
}

/* font size */
function wireFontSheet() {
  $('#font-smaller').addEventListener('click', () => applyFontScale(state.fontScale - 0.1));
  $('#font-bigger').addEventListener('click', () => applyFontScale(state.fontScale + 0.1));
}
function applyFontScale(v) {
  state.fontScale = Math.min(1.6, Math.max(0.7, Math.round(v * 10) / 10));
  document.documentElement.style.setProperty('--reader-scale', state.fontScale);
  $('#font-size-value').textContent = `${Math.round(state.fontScale * 100)}%`;
  db.setSetting('fontScale', state.fontScale);
}

/* ================= playback UI ================= */

function togglePlay() {
  if (!player.supported) { toast('Text-to-speech is not available in this browser'); return; }
  if (!player.book) return;
  if (!player.playing && player.voices.length === 0) {
    // voices often arrive async; try once more shortly
    setTimeout(() => { if (player.voices.length === 0) toast('No text-to-speech voices found on this device'); }, 700);
  }
  player.toggle();
}

function wirePlayerEvents() {
  player.on('sentence', (i) => {
    highlightSentence(i);
    updateReaderChrome();
    scheduleSavePosition();
    scrollPdfToCurrent();
  });
  player.on('state', (info = {}) => {
    updateReaderChrome();
    renderMiniPlayer();
    if (info.error) toast(`Playback stopped: ${info.error === 'synthesis-failed' || info.error === 'not-allowed' ? 'the speech engine refused — tap play again' : info.error}`);
    if (info.finished) { toast('Finished 🎉'); savePosition(); }
    player.updateMediaSession();
  });
  player.on('voices', () => {
    renderVoiceList();
    renderProfile();
  });
}

function highlightSentence(i, force = false) {
  const prev = $('#reader-text span.active');
  if (prev) prev.classList.remove('active');
  const span = $(`#reader-text span[data-si="${i}"]`);
  if (span) {
    span.classList.add('active');
    if (player.playing || force) span.scrollIntoView({ block: 'center', behavior: force ? 'auto' : 'smooth' });
  }
}

function updateReaderChrome() {
  if (!player.book) return;
  $('#ctl-play').textContent = player.playing ? '❚❚' : '▶';
  $('#ctl-speed').textContent = formatRate(player.rate);
  $('#time-elapsed').textContent = formatTime(player.elapsedSec());
  $('#time-total').textContent = formatTime(player.totalSec());
  const s = player.book.sentences[Math.min(player.cursor, player.book.sentences.length - 1)];
  $('#page-indicator').textContent = player.book.pages > 1 ? `${s?.p || 1} of ${player.book.pages}` : `${Math.round(player.progress() * 100)}%`;
  const scrub = $('#reader-scrub');
  if (document.activeElement !== scrub) scrub.value = Math.round(player.progress() * 1000);
}

function formatRate(r) {
  return (Math.round(r * 100) / 100).toFixed(r % 1 === 0 ? 1 : (r * 100) % 10 === 0 ? 2 : 2);
}

function renderMiniPlayer() {
  const mp = $('#mini-player');
  const readerOpen = !$('#view-reader').classList.contains('hidden');
  if (!player.book || readerOpen) { mp.classList.add('hidden'); return; }
  mp.classList.remove('hidden');
  $('#mini-title').textContent = player.book.title;
  $('#mini-sub').textContent = player.cursor >= player.book.sentences.length ? 'Finished' : formatRemaining(player.remainingSec());
  $('#mini-play').textContent = player.playing ? '❚❚' : '▶';
  $('#mini-cover').src = player.book.cover || '';
  $('#mini-progress-bar').style.width = `${player.progress() * 100}%`;
}

$('#mini-play')?.addEventListener('click', (e) => { e.stopPropagation(); togglePlay(); });
$('#mini-player')?.addEventListener('click', () => { if (state.currentBookId) openReader(state.currentBookId); });

/* position persistence */
function scheduleSavePosition() {
  if (state.saveTimer) return;
  state.saveTimer = setTimeout(() => { state.saveTimer = null; savePosition(); }, 3000);
}

async function savePosition() {
  if (!state.currentBookId || !player.book) return;
  const cursor = Math.min(player.cursor, player.book.sentences.length);
  await db.updateBook(state.currentBookId, { cursor });
  const light = state.books.find((b) => b.id === state.currentBookId);
  if (light) light.cursor = cursor;
}

/* ================= sheets ================= */

function openSheet(id) {
  $('#sheet-backdrop').classList.remove('hidden');
  $(`#${id}`).classList.remove('hidden');
}
function closeSheets() {
  $('#sheet-backdrop').classList.add('hidden');
  $$('.sheet').forEach((s) => s.classList.add('hidden'));
}

function wireSheets() {
  $('#sheet-backdrop').addEventListener('click', closeSheets);
  wireFontSheet();

  // speed sheet
  const slider = $('#speed-slider');
  slider.addEventListener('input', () => applyRate(Number(slider.value), false));
  slider.addEventListener('change', () => applyRate(Number(slider.value)));
  const presets = $('#speed-presets');
  for (const p of SPEED_PRESETS) {
    const chip = document.createElement('button');
    chip.className = 'speed-chip';
    chip.dataset.rate = p;
    chip.textContent = `${p}×`;
    chip.addEventListener('click', () => applyRate(p));
    presets.appendChild(chip);
  }

  // paste-text sheet
  $('#paste-save').addEventListener('click', async () => {
    const text = $('#paste-text').value.trim();
    if (!text) { toast('Paste some text first'); return; }
    try {
      const extracted = extractPlainText($('#paste-title').value.trim(), text, 'text');
      const book = await saveExtracted(extracted, null);
      $('#paste-text').value = '';
      $('#paste-title').value = '';
      closeSheets();
      openReader(book.id);
    } catch (err) { toast(err.message); }
  });

  // book options sheet
  $('#book-sheet-listen').addEventListener('click', () => {
    if (bookSheetTarget) { closeSheets(); openReader(bookSheetTarget.id); }
  });
  $('#book-sheet-restart').addEventListener('click', async () => {
    if (!bookSheetTarget) return;
    await db.updateBook(bookSheetTarget.id, { cursor: 0 });
    if (state.currentBookId === bookSheetTarget.id) player.seekToSentence(0);
    closeSheets();
    await refreshBooks();
    openReader(bookSheetTarget.id);
  });
  $('#book-sheet-delete').addEventListener('click', async () => {
    if (!bookSheetTarget) return;
    if (!confirm(`Delete “${bookSheetTarget.title}” from this device?`)) return;
    if (state.currentBookId === bookSheetTarget.id) {
      player.stop();
      state.currentBookId = null;
      $('#view-reader').classList.add('hidden');
    }
    await db.deleteBook(bookSheetTarget.id);
    bookSheetTarget = null;
    closeSheets();
    await refreshBooks();
    toast('Deleted');
  });
}

function applyRate(rate, persist = true) {
  rate = Math.round(rate * 20) / 20;
  player.setRate(rate);
  $('#speed-value').textContent = `${formatRate(player.rate)}×`;
  $('#speed-slider').value = player.rate;
  $$('#speed-presets .speed-chip').forEach((c) => c.classList.toggle('selected', Number(c.dataset.rate) === player.rate));
  $('#setting-speed-value').textContent = `${formatRate(player.rate)}×`;
  updateReaderChrome();
  renderHome();
  renderLibrary();
  if (persist) db.setSetting('rate', player.rate);
}

function openSpeedSheet() {
  $('#speed-value').textContent = `${formatRate(player.rate)}×`;
  $('#speed-slider').value = player.rate;
  $$('#speed-presets .speed-chip').forEach((c) => c.classList.toggle('selected', Number(c.dataset.rate) === player.rate));
  openSheet('sheet-speed');
}

function openVoiceSheet() {
  renderVoiceList();
  openSheet('sheet-voice');
}

function renderVoiceList() {
  const list = $('#voice-list');
  const voices = player.voices;
  const bookBase = player.bookLang();
  let note = voices.length
    ? 'Voices are provided by your device and work offline.'
    : 'No voices found (yet). On Android, install “Speech Recognition & Synthesis” or enable a TTS engine in system settings.';
  if (voices.length && bookBase && !voices.some((v) => v.lang.toLowerCase().startsWith(bookBase))) {
    note = `This book looks like ${languageName(bookBase)}, but no ${languageName(bookBase)} voice is installed. `
      + 'On Android: Settings → System → Text-to-speech → Google engine → Install voice data.';
  }
  $('#voice-note').textContent = note;
  list.innerHTML = '';
  const selected = player.getVoice();
  const byLang = new Map();
  for (const v of voices) {
    const lang = v.lang || 'other';
    if (!byLang.has(lang)) byLang.set(lang, []);
    byLang.get(lang).push(v);
  }
  // ordering: the current book's language first, then the device language, then A–Z
  const langs = Array.from(byLang.keys()).sort((a, b) => {
    const mine = (navigator.language || 'en').split('-')[0].toLowerCase();
    const rank = (l) => {
      const low = l.toLowerCase();
      if (bookBase && low.startsWith(bookBase)) return 0;
      if (low.startsWith(mine)) return 1;
      return 2;
    };
    return rank(a) - rank(b) || a.localeCompare(b);
  });
  for (const lang of langs) {
    const head = document.createElement('div');
    head.className = 'voice-lang';
    head.textContent = languageName(lang);
    list.appendChild(head);
    for (const v of byLang.get(lang)) {
      const btn = document.createElement('button');
      btn.className = 'voice-item' + (selected && v.voiceURI === selected.voiceURI ? ' selected' : '');
      btn.innerHTML = `<span>${escapeHtml(v.name)}<small>${escapeHtml(v.lang)}${v.localService ? ' · offline' : ' · online'}</small></span><span class="check">✓</span>`;
      btn.addEventListener('click', async () => {
        player.setVoice(v.voiceURI);
        await db.setSetting('voiceURI', v.voiceURI);
        await db.setSetting('voiceMap', player.voiceMap); // per-language memory
        renderVoiceList();
        renderProfile();
        if (!player.playing) previewVoice(v);
      });
      list.appendChild(btn);
    }
  }
}

function previewVoice(v) {
  player.preview(v);
}

function languageName(code) {
  try {
    const dn = new Intl.DisplayNames([navigator.language || 'en'], { type: 'language' });
    return dn.of(code.replace('_', '-')) || code;
  } catch { return code; }
}

/* ================= profile ================= */

function wireProfile() {
  $('#setting-voice').addEventListener('click', openVoiceSheet);
  $('#setting-speed').addEventListener('click', openSpeedSheet);
  $('#setting-clear').addEventListener('click', async () => {
    if (!confirm('Delete ALL books from this device? This cannot be undone.')) return;
    player.stop();
    state.currentBookId = null;
    for (const b of state.books) await db.deleteBook(b.id);
    await refreshBooks();
    toast('All books deleted');
    renderProfile();
  });

  $('#setting-media-notif').addEventListener('change', async (e) => {
    await db.setSetting('mediaNotif', e.target.checked);
    toast(e.target.checked
      ? 'Media controls will appear in your notification shade while listening'
      : 'Media notification disabled');
  });

  $('#setting-reminders').addEventListener('change', async (e) => {
    if (e.target.checked) {
      if (!('Notification' in window)) { toast('Notifications are not supported here'); e.target.checked = false; return; }
      const perm = await Notification.requestPermission();
      if (perm !== 'granted') {
        e.target.checked = false;
        $('#notif-denied-note').classList.toggle('hidden', perm !== 'denied');
        toast('Notification permission not granted');
        return;
      }
      $('#notif-denied-note').classList.add('hidden');
    }
    await db.setSetting('reminders', e.target.checked);
  });
}

async function renderProfile() {
  $('#setting-book-count').textContent = state.books.length;
  $('#setting-speed-value').textContent = `${formatRate(player.rate)}×`;
  const v = player.getVoice();
  $('#setting-voice-value').textContent = v ? v.name : 'Default';
  $('#app-version').textContent = `v${APP_VERSION}`;
  const est = await db.storageEstimate();
  $('#setting-storage').textContent = est && est.usage != null ? humanBytes(est.usage) : '—';
  const [mediaNotif, reminders] = await Promise.all([
    db.getSetting('mediaNotif', true),
    db.getSetting('reminders', false),
  ]);
  $('#setting-media-notif').checked = mediaNotif;
  $('#setting-reminders').checked = reminders && (('Notification' in window) && Notification.permission === 'granted');
}

function humanBytes(n) {
  if (n > 1024 * 1024 * 1024) return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`;
  if (n > 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`;
  return `${Math.round(n / 1024)} KB`;
}

/* reading reminder when app goes to background mid-book */
document.addEventListener('visibilitychange', async () => {
  if (!document.hidden) return;
  const wants = await db.getSetting('reminders', false);
  if (!wants || !('Notification' in window) || Notification.permission !== 'granted') return;
  if (!player.book || player.playing || player.progress() >= 1) return;
  try {
    const reg = await navigator.serviceWorker?.getRegistration();
    const opts = {
      body: `Continue listening — ${formatRemaining(player.remainingSec())}`,
      icon: 'icons/icon-192.png',
      tag: 'voxreader-resume',
      silent: true,
    };
    if (reg && reg.showNotification) reg.showNotification(player.book.title, opts);
    else new Notification(player.book.title, opts);
  } catch { /* best effort */ }
});

/* ================= misc ================= */

let toastTimer = null;
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.add('hidden'), 3200);
}

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

boot();

// test/debug handle (also handy in devtools)
window.__vox = { player, state, openReader };
