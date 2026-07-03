/* VoxReader end-to-end test.
   Serves the app dir over HTTP and drives it in headless Chromium.
   speechSynthesis is stubbed (headless Linux ships no TTS voices) with a fake
   engine that fires start/end events on a timer, so playback logic, highlighting,
   progress, persistence and media-session integration are all exercised for real. */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const APP_DIR = require('path').join(__dirname, '..', 'www');
const FIX_DIR = __dirname;
const SHOTS = path.join(__dirname, 'shots');
const PORT = 8734;

const MIME = {
  '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript',
  '.css': 'text/css', '.json': 'application/json', '.webmanifest': 'application/manifest+json',
  '.png': 'image/png', '.svg': 'image/svg+xml', '.pdf': 'application/pdf',
};

function serve() {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      let p = decodeURIComponent(req.url.split('?')[0]);
      if (p === '/') p = '/index.html';
      const file = path.join(APP_DIR, p);
      if (!file.startsWith(APP_DIR) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
        res.writeHead(404); res.end('nope'); return;
      }
      res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
      fs.createReadStream(file).pipe(res);
    });
    server.listen(PORT, () => resolve(server));
  });
}

// Deterministic speechSynthesis stub: each utterance "speaks" for
// (words / (170*rate/60)) seconds but accelerated 15x so tests run fast.
const SPEECH_STUB = `
(() => {
  const voices = [
    { voiceURI: 'stub-en-US-1', name: 'Ava (Stub)', lang: 'en-US', localService: true, default: true },
    { voiceURI: 'stub-en-US-2', name: 'Noah (Stub)', lang: 'en-US', localService: true, default: false },
    { voiceURI: 'stub-en-GB-1', name: 'Oliver (Stub)', lang: 'en-GB', localService: true, default: false },
    { voiceURI: 'stub-hi-IN-1', name: 'Priya (Stub)', lang: 'hi-IN', localService: true, default: false },
  ];
  let current = null;
  window.__spoken = [];
  const synth = {
    speaking: false, paused: false, pending: false,
    getVoices: () => voices,
    speak(u) {
      synth.speaking = true;
      current = u;
      window.__spoken.push({ text: u.text, voice: u.voice && u.voice.name, rate: u.rate });
      const words = (u.text.match(/\\S+/g) || []).length;
      const ms = Math.max(12, (words / ((170 * (u.rate || 1)) / 60)) * 1000 / 15);
      u.__timer = setTimeout(() => {
        if (current !== u) return;
        synth.speaking = false;
        u.onend && u.onend({ type: 'end' });
      }, ms);
      u.onstart && setTimeout(() => u.onstart && u.onstart({ type: 'start' }), 1);
    },
    cancel() {
      if (current) {
        clearTimeout(current.__timer);
        const u = current; current = null;
        synth.speaking = false;
        u.onerror && setTimeout(() => u.onerror({ type: 'error', error: 'interrupted' }), 0);
      }
    },
    pause() { synth.paused = true; },
    resume() { synth.paused = false; },
    addEventListener() {},
  };
  Object.defineProperty(window, 'speechSynthesis', { value: synth });
  window.SpeechSynthesisUtterance = class {
    constructor(text) { this.text = text || ''; this.rate = 1; this.voice = null; this.lang = ''; }
  };
})();
`;

let passed = 0, failed = 0;
const failures = [];
function check(name, cond, extra) {
  if (cond) { passed++; console.log('  ✓', name); }
  else { failed++; failures.push(name + (extra ? ` — ${extra}` : '')); console.log('  ✗', name, extra || ''); }
}

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const server = await serve();
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/opt/pw-browsers/chromium' });
  const ctx = await browser.newContext({
    viewport: { width: 412, height: 915 },  // Pixel-ish phone
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    permissions: ['notifications'],
  });
  await ctx.addInitScript(SPEECH_STUB);
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  const url = `http://localhost:${PORT}/index.html`;

  console.log('\n— 1. App boots, home renders —');
  await page.goto(url);
  await page.waitForTimeout(800);
  check('home view visible', await page.isVisible('#view-home'));
  check('promo card rendered', await page.isVisible('#promo-card'));
  check('import tiles present', (await page.locator('.import-tile').count()) === 4);
  check('empty state shown before any import', await page.isVisible('#continue-empty'));
  await page.screenshot({ path: path.join(SHOTS, '01-home-empty.png') });

  console.log('\n— 2. Sample book + playback engine —');
  await page.click('#tile-sample');
  await page.waitForSelector('#view-reader:not(.hidden)', { timeout: 5000 });
  check('reader opens for sample', true);
  check('sentences rendered as spans', (await page.locator('#reader-text span[data-si]').count()) > 10);
  const totalBefore = await page.textContent('#time-total');
  check('total time computed', totalBefore !== '00:00', totalBefore);

  await page.click('#ctl-play');
  await page.waitForTimeout(1200);
  check('play button shows pause state', (await page.textContent('#ctl-play')).includes('❚'));
  const spoken1 = await page.evaluate(() => window.__spoken.length);
  check('utterances flowing to TTS engine', spoken1 >= 2, `spoken=${spoken1}`);
  const activeIdx = await page.evaluate(() => Number(document.querySelector('#reader-text span.active')?.dataset.si));
  check('current sentence highlighted & advancing', activeIdx >= 1, `idx=${activeIdx}`);
  const msState = await page.evaluate(() => navigator.mediaSession.playbackState);
  const msTitle = await page.evaluate(() => navigator.mediaSession.metadata && navigator.mediaSession.metadata.title);
  check('media session playing (notification controls)', msState === 'playing', msState);
  check('media session metadata title set', msTitle === 'Welcome to VoxReader', msTitle);
  await page.screenshot({ path: path.join(SHOTS, '02-reader-playing.png') });

  // pause persists position
  await page.click('#ctl-play');
  await page.waitForTimeout(300);
  check('pause works', (await page.textContent('#ctl-play')).trim() === '▶');
  check('media session paused', (await page.evaluate(() => navigator.mediaSession.playbackState)) === 'paused');

  console.log('\n— 3. Speed control —');
  await page.click('#ctl-speed');
  await page.waitForSelector('#sheet-speed:not(.hidden)');
  await page.click('.speed-chip[data-rate="2"]');
  check('speed chip 2× selected', await page.locator('.speed-chip[data-rate="2"].selected').isVisible());
  check('speed button label updated', (await page.textContent('#ctl-speed')).trim() === '2.0');
  const totalAfter = await page.textContent('#time-total');
  check('total time halves at 2×', totalAfter !== totalBefore, `${totalBefore} → ${totalAfter}`);
  await page.click('#sheet-backdrop', { position: { x: 10, y: 10 } });

  console.log('\n— 4. Voice picker —');
  await page.click('#ctl-voice');
  await page.waitForSelector('#sheet-voice:not(.hidden)');
  const voiceCount = await page.locator('.voice-item').count();
  check('voices listed', voiceCount === 4, `count=${voiceCount}`);
  await page.locator('.voice-item', { hasText: 'Noah' }).click();
  await page.waitForTimeout(200);
  check('voice selectable', await page.locator('.voice-item.selected', { hasText: 'Noah' }).isVisible());
  await page.click('#sheet-backdrop', { position: { x: 10, y: 10 } });
  await page.click('#ctl-play'); // resume with the new voice
  await page.waitForTimeout(600);
  const lastVoice = await page.evaluate(() => window.__spoken[window.__spoken.length - 1].voice);
  const lastRate = await page.evaluate(() => window.__spoken[window.__spoken.length - 1].rate);
  check('new voice used for playback', lastVoice === 'Noah (Stub)', lastVoice);
  check('2× rate applied to utterances', lastRate === 2, `rate=${lastRate}`);
  await page.click('#ctl-play'); // pause again

  console.log('\n— 5. Skip & scrub —');
  const idxBefore = await page.evaluate(() => Number(document.querySelector('#reader-text span.active')?.dataset.si));
  await page.click('#ctl-fwd');
  await page.waitForTimeout(200);
  const idxAfter = await page.evaluate(() => Number(document.querySelector('#reader-text span.active')?.dataset.si));
  check('skip +10s moves forward', idxAfter > idxBefore, `${idxBefore} → ${idxAfter}`);
  await page.click('#ctl-back');
  await page.waitForTimeout(200);
  const idxBack = await page.evaluate(() => Number(document.querySelector('#reader-text span.active')?.dataset.si));
  check('skip −10s moves back', idxBack < idxAfter, `${idxAfter} → ${idxBack}`);
  // tap a sentence to jump
  await page.locator('#reader-text span[data-si="3"]').click();
  await page.waitForTimeout(200);
  check('tap-to-jump on sentence', (await page.evaluate(() => Number(document.querySelector('#reader-text span.active')?.dataset.si))) === 3);

  console.log('\n— 6. PDF import —');
  await page.click('#reader-close');
  await page.setInputFiles('#file-input', path.join(FIX_DIR, 'fixture.pdf'));
  await page.waitForSelector('#view-reader:not(.hidden)', { timeout: 15000 });
  await page.waitForTimeout(500);
  const chip = await page.textContent('#reader-title-chip');
  check('PDF title from metadata', chip.includes('The Art of Testing'), chip);
  check('page indicator "1 of 3"', (await page.textContent('#page-indicator')).includes('of 3'));
  const pdfText = await page.textContent('#reader-text');
  check('PDF text extracted', pdfText.includes('proofreading a book'), '');
  check('page breaks rendered', (await page.locator('#reader-text .page-break').count()) === 3);
  // original view
  await page.click('#reader-view-toggle');
  await page.waitForTimeout(1500);
  check('original PDF pages rendered', (await page.locator('#reader-original canvas').count()) >= 1);
  await page.screenshot({ path: path.join(SHOTS, '03-pdf-original.png') });
  await page.click('#reader-view-toggle'); // back to text
  await page.click('#ctl-play');
  await page.waitForTimeout(900);
  check('PDF plays', (await page.evaluate(() => window.__spoken[window.__spoken.length - 1].text)).length > 0);
  await page.click('#ctl-play');
  await page.screenshot({ path: path.join(SHOTS, '04-pdf-reader.png') });

  console.log('\n— 7. DOCX import —');
  await page.click('#reader-close');
  await page.setInputFiles('#file-input', path.join(FIX_DIR, 'fixture.docx'));
  await page.waitForSelector('#view-reader:not(.hidden)', { timeout: 15000 });
  await page.waitForTimeout(400);
  const dchip = await page.textContent('#reader-title-chip');
  check('DOCX title from Heading1', dchip.includes('Meditations on Word Files'), dchip);
  const dtext = await page.textContent('#reader-text');
  check('DOCX body extracted', dtext.includes('Mammoth converts it to clean text'));
  check('DOCX headings preserved', (await page.locator('#reader-text h1, #reader-text h2').count()) >= 2);
  check('view toggle hidden for DOCX', await page.locator('#reader-view-toggle').isHidden());
  await page.click('#ctl-play');
  await page.waitForTimeout(700);
  await page.click('#ctl-play');
  await page.screenshot({ path: path.join(SHOTS, '05-docx-reader.png') });

  console.log('\n— 7b. Indic language support (Hindi paste) —');
  await page.click('#reader-close');
  await page.click('#tile-text');
  await page.waitForSelector('#sheet-text:not(.hidden)');
  await page.fill('#paste-title', 'हिंदी परीक्षण');
  await page.fill('#paste-text',
    'यह पहला वाक्य है। यह दूसरा वाक्य है। क्या यह तीसरा वाक्य है? हाँ, यह तीसरा वाक्य है।\n\n' +
    'यह दूसरा अनुच्छेद है। इसमें भी कुछ वाक्य हैं। पढ़ने का आनंद लीजिये॥');
  await page.click('#paste-save');
  await page.waitForSelector('#view-reader:not(.hidden)', { timeout: 5000 });
  await page.waitForTimeout(300);
  const hindiSpans = await page.locator('#reader-text span[data-si]').count();
  check('danda (।/॥) splits Hindi sentences', hindiSpans >= 6, `spans=${hindiSpans}`);
  const detectedLang = await page.evaluate(() => window.__vox.player.book.lang);
  check('book language detected as Hindi', detectedLang === 'hi', detectedLang);
  await page.click('#ctl-play');
  await page.waitForTimeout(900);
  const hindiVoice = await page.evaluate(() => window.__spoken[window.__spoken.length - 1].voice);
  check('Hindi voice auto-picked over global English choice', hindiVoice === 'Priya (Stub)', hindiVoice);
  await page.click('#ctl-play'); // pause
  // voice sheet puts the book's language group first
  await page.click('#ctl-voice');
  await page.waitForSelector('#sheet-voice:not(.hidden)');
  const firstGroup = await page.locator('.voice-lang').first().textContent();
  check('voice sheet lists Hindi group first for a Hindi book', /hindi|हिन्दी|hi/i.test(firstGroup), firstGroup);
  await page.click('#sheet-backdrop', { position: { x: 10, y: 10 } });
  await page.screenshot({ path: path.join(SHOTS, '10-hindi-reader.png') });

  console.log('\n— 7c. Unicode Tamil PDF (CMap decode) + glyph-corruption handling —');
  await page.click('#reader-close');
  await page.setInputFiles('#file-input', path.join(FIX_DIR, 'fixture-tamil.pdf'));
  await page.waitForSelector('#view-reader:not(.hidden)', { timeout: 15000 });
  await page.waitForTimeout(500);
  const ttext = await page.textContent('#reader-text');
  // visual-order repair must yield logical Tamil: பொன் (two-part ொ) and கரை (reordered ை)
  check('Tamil PDF text extracted in logical order',
    ttext.includes('பொன்') && ttext.includes('கரை') && ttext.includes('தெளிவாக') && ttext.includes('வானம்'),
    ttext.slice(0, 80));
  check('reader title from Tamil PDF metadata', (await page.textContent('#reader-title-chip')).includes('பொன்னி'));
  const tbook = await page.evaluate(() => ({
    id: window.__vox.player.book.id,
    lang: window.__vox.player.book.lang,
    corrupted: window.__vox.player.book.textCorrupted,
  }));
  check('Tamil language detected from PDF', tbook.lang === 'ta', tbook.lang);
  check('clean Unicode PDF not flagged as corrupted', tbook.corrupted === false);
  check('clean PDF opens in text view', await page.isVisible('#reader-text'));

  const q = await page.evaluate(async () => {
    const { assessTextQuality } = await import('./js/extract.js');
    const garbled = ('ொகாZP|<க pPயா^! எ¢றா¢. கzகால¢ அவைன உ ேநா<8 பா~பா நா¢ தா¢ ொசா¢ேனன? எ¢ அ|ைம ேதாழ~களா8ய cBக q¢ ேப|r ஒ|வைரயா| வ~ கP ¢ Rr bைல<: ').repeat(4);
    const clean = ('பொன்னியின் செல்வன் ஒரு சிறந்த வரலாற்று நாவல். கல்கி எழுதிய இந்த நூல் தமிழ் இலக்கியத்தில் முக்கியமான படைப்பு ஆகும். வந்தியத்தேவன் காவிரி ஆற்றின் கரையில் பயணம் செய்தான். ').repeat(4);
    return { g: assessTextQuality(garbled), c: assessTextQuality(clean) };
  });
  check('glyph-encoded junk detected as corrupted', q.g.corrupted === true, `ratio=${q.g.suspiciousRatio.toFixed(2)}`);
  check('clean Unicode Tamil not flagged', q.c.corrupted === false, `ratio=${q.c.suspiciousRatio.toFixed(2)}`);

  // a corrupted PDF must open in the original-pages view with a warning banner
  const cloneId = await page.evaluate(async (id) => {
    const db = await import('./js/db.js');
    const full = await db.getBook(id);
    const clone = { ...full, id: 'bk_corrupt_test', title: 'Corrupt Fixture', textCorrupted: true, cursor: 0 };
    await db.putBook(clone);
    return clone.id;
  }, tbook.id);
  await page.evaluate((id) => window.__vox.openReader(id), cloneId);
  await page.waitForTimeout(900);
  check('corrupted PDF opens in original page view', await page.isVisible('#reader-original'));
  check('original pages render for corrupted PDF', (await page.locator('#reader-original .pdf-page').count()) >= 1);
  await page.click('#reader-view-toggle');
  await page.waitForTimeout(200);
  check('corruption warning banner shown in text view', await page.isVisible('.corrupt-note'));
  await page.screenshot({ path: path.join(SHOTS, '11-corrupt-fallback.png') });
  await page.evaluate(async (id) => { const db = await import('./js/db.js'); await db.deleteBook(id); }, cloneId);
  await page.evaluate((id) => window.__vox.openReader(id), tbook.id);
  await page.waitForTimeout(400);

  console.log('\n— 8. Library, mini player, home —');
  await page.click('#reader-close');
  check('mini player appears after closing reader', await page.isVisible('#mini-player'));
  const miniTitle = await page.textContent('#mini-title');
  check('mini player shows current book', miniTitle.includes('பொன்னி'), miniTitle);
  await page.click('[data-nav="library"]');
  await page.waitForTimeout(300);
  check('library lists 5 books', (await page.locator('.library-item').count()) === 5);
  check('library shows book language', (await page.textContent('#library-list')).toLowerCase().includes('hindi'));
  check('remaining-time estimates shown', (await page.textContent('#library-list')).includes('remaining') || (await page.textContent('#library-list')).includes('less than a minute'));
  await page.fill('#library-search', 'meditations');
  await page.waitForTimeout(200);
  check('search filters library', (await page.locator('.library-item').count()) === 1);
  await page.fill('#library-search', '');
  await page.screenshot({ path: path.join(SHOTS, '06-library.png') });
  await page.click('[data-nav="home"]');
  await page.waitForTimeout(300);
  check('continue-listening row populated', (await page.locator('#continue-row .book-card').count()) === 5);
  await page.screenshot({ path: path.join(SHOTS, '07-home-books.png') });

  console.log('\n— 9. Persistence across reload —');
  await page.reload();
  await page.waitForTimeout(900);
  check('books survive reload', (await page.locator('#continue-row .book-card').count()) === 5);
  const speedVal = await page.evaluate(async () => {
    const mod = await import('./js/db.js');
    return mod.getSetting('rate', 1);
  });
  check('speed setting persisted (2×)', speedVal === 2, `rate=${speedVal}`);
  const voiceVal = await page.evaluate(async () => (await import('./js/db.js')).getSetting('voiceURI'));
  check('voice choice persisted', voiceVal === 'stub-en-US-2', voiceVal);
  const voiceMapVal = await page.evaluate(async () => (await import('./js/db.js')).getSetting('voiceMap', {}));
  check('per-language voice map persisted', voiceMapVal && voiceMapVal.en === 'stub-en-US-2', JSON.stringify(voiceMapVal));
  // resume position: open the PDF book again — cursor should be > 0
  // (order by lastOpenedAt: tamil, hindi, docx, pdf, sample → pdf is 4th)
  await page.click('#continue-row .book-card:nth-child(4)');
  await page.waitForSelector('#view-reader:not(.hidden)');
  await page.waitForTimeout(400);
  const resumedIdx = await page.evaluate(() => Number(document.querySelector('#reader-text span.active')?.dataset.si));
  check('reading position restored after reload', resumedIdx > 0, `idx=${resumedIdx}`);
  await page.click('#reader-close');

  console.log('\n— 10. Profile / settings —');
  await page.click('[data-nav="profile"]');
  await page.waitForTimeout(400);
  check('book count shown', (await page.textContent('#setting-book-count')).trim() === '5');
  check('storage usage shown', /(KB|MB|GB)/.test(await page.textContent('#setting-storage')));
  check('speed shown in settings', (await page.textContent('#setting-speed-value')).includes('2'));
  // reminders toggle triggers Notification permission (granted in this context)
  await page.check('#setting-reminders');
  await page.waitForTimeout(300);
  check('reading reminders enabled', await page.isChecked('#setting-reminders'));
  await page.screenshot({ path: path.join(SHOTS, '08-profile.png') });

  console.log('\n— 11. Offline (service worker) —');
  await page.waitForFunction(() => navigator.serviceWorker && navigator.serviceWorker.controller, null, { timeout: 10000 }).catch(() => {});
  const swActive = await page.evaluate(() => !!(navigator.serviceWorker && navigator.serviceWorker.controller));
  check('service worker controls the page', swActive);
  const cachedCount = await page.evaluate(async () => {
    const keys = await caches.keys();
    if (!keys.length) return 0;
    const c = await caches.open(keys[0]);
    return (await c.keys()).length;
  });
  check('app assets precached', cachedCount >= 12, `cached=${cachedCount}`);
  await ctx.setOffline(true);
  await page.reload();
  await page.waitForTimeout(1200);
  check('app loads with network OFF (restores last view)', await page.isVisible('#view-profile'));
  await page.click('[data-nav="home"]');
  await page.waitForTimeout(300);
  check('home renders offline', await page.isVisible('#view-home'));
  check('books available offline', (await page.locator('#continue-row .book-card').count()) === 5);
  await page.click('#continue-row .book-card:nth-child(1)');
  await page.waitForSelector('#view-reader:not(.hidden)', { timeout: 5000 });
  await page.click('#ctl-play');
  await page.waitForTimeout(700);
  const offlineSpoken = await page.evaluate(() => window.__spoken.length);
  check('playback works offline', offlineSpoken > 0, `spoken=${offlineSpoken}`);
  await page.screenshot({ path: path.join(SHOTS, '09-offline-reader.png') });
  await ctx.setOffline(false);

  console.log('\n— 12. Delete book —');
  await page.click('#reader-close');
  page.on('dialog', (d) => d.accept());
  await page.click('[data-nav="library"]');
  await page.waitForTimeout(300);
  await page.locator('.library-item .lib-more').first().click();
  await page.waitForSelector('#sheet-book:not(.hidden)');
  await page.click('#book-sheet-delete');
  await page.waitForTimeout(500);
  check('book deleted from library', (await page.locator('.library-item').count()) === 4);

  console.log('\n— console errors —');
  const realErrors = errors.filter((e) => !e.includes('favicon') && !e.includes('net::ERR_INTERNET_DISCONNECTED') && !e.includes('Failed to load resource'));
  check('no page errors', realErrors.length === 0, realErrors.slice(0, 3).join(' | '));

  await browser.close();
  server.close();

  console.log(`\n========== ${passed} passed, ${failed} failed ==========`);
  if (failures.length) { console.log('FAILURES:'); failures.forEach((f) => console.log(' -', f)); process.exit(1); }
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
