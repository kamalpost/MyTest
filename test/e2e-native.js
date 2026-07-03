/* Native-backend smoke test: emulates the Capacitor NativeTTS plugin in the page
   and verifies the NativePlayer drives it correctly (voice list, configure,
   batch queueing, utterance progress -> UI highlight, now-playing payloads,
   pause/stop, queueDone -> next batch/finish, media actions -> player). */
const http = require('http'); const fs = require('fs'); const path = require('path');
const { chromium } = require('playwright');
const APP_DIR = require('path').join(__dirname, '..', 'www'); const PORT = 8745;
const MIME = { '.html':'text/html','.js':'text/javascript','.mjs':'text/javascript','.css':'text/css','.webmanifest':'application/manifest+json','.png':'image/png' };
http.createServer((req,res)=>{ let p=req.url.split('?')[0]; if(p==='/')p='/index.html'; const f=path.join(APP_DIR,p);
 if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){res.writeHead(404);res.end();return;}
 res.writeHead(200,{'Content-Type':MIME[path.extname(f)]||'application/octet-stream'}); fs.createReadStream(f).pipe(res); }).listen(PORT);

const NATIVE_STUB = `
(() => {
  const listeners = {};
  const calls = [];
  let queue = [], playingTimer = null, rate = 1;
  window.__native = { calls, get queue(){return queue;} };
  function emit(ev, data) { (listeners[ev]||[]).forEach(fn => fn(data)); }
  window.__emitNative = emit;
  function runQueue() {
    clearTimeout(playingTimer);
    if (!queue.length) return;
    const item = queue.shift();
    emit('utterance', { id: item.id });
    const words = (item.text.match(/\\S+/g)||[]).length;
    playingTimer = setTimeout(() => {
      if (!queue.length) emit('queueDone', {});
      else runQueue();
    }, Math.max(8, words/((170*rate/60))*1000/15));
  }
  const plugin = {
    addListener(ev, fn) { (listeners[ev]=listeners[ev]||[]).push(fn); return Promise.resolve({remove(){}}); },
    getVoices() { calls.push(['getVoices']); return Promise.resolve({ voices: [
      { id: 'en-us-x-a#female-local', name: 'English (US) · female', lang: 'en-US', networkRequired: false },
      { id: 'en-gb-x-b#male-local', name: 'English (UK) · male', lang: 'en-GB', networkRequired: false },
      { id: 'ta-in-x-c#female-local', name: 'Tamil (India) · female', lang: 'ta-IN', networkRequired: true },
    ]}); },
    configure(o) { calls.push(['configure', o]); if (o.rate) rate = o.rate; return Promise.resolve(); },
    speakBatch(o) { calls.push(['speakBatch', {n: o.sentences.length, first: o.sentences[0].id}]);
      queue = o.sentences.slice(); runQueue(); return Promise.resolve(); },
    preview(o) { calls.push(['preview', o]); return Promise.resolve(); },
    stop() { calls.push(['stop']); queue = []; clearTimeout(playingTimer); return Promise.resolve(); },
    setNowPlaying(o) { calls.push(['setNowPlaying', {title:o.title, playing:o.playing, hasCover: !!o.cover}]); return Promise.resolve(); },
    clearNowPlaying() { calls.push(['clearNowPlaying']); return Promise.resolve(); },
    requestNotifications() { calls.push(['requestNotifications']); return Promise.resolve({granted:true}); },
  };
  window.Capacitor = { isNativePlatform: () => true, Plugins: { NativeTTS: plugin } };
})();
`;

let passed = 0, failed = 0; const failures = [];
function check(name, cond, extra) {
  if (cond) { passed++; console.log('  ✓', name); }
  else { failed++; failures.push(name + (extra?` — ${extra}`:'')); console.log('  ✗', name, extra||''); }
}

(async()=>{
 const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/opt/pw-browsers/chromium' });
 const ctx = await browser.newContext({viewport:{width:412,height:915}, isMobile:true, hasTouch:true});
 await ctx.addInitScript(NATIVE_STUB);
 const page = await ctx.newPage();
 const errors = [];
 page.on('pageerror', e => errors.push(String(e)));
 page.on('console', m => { if (m.type()==='error') errors.push(m.text()); });
 await page.goto(`http://localhost:${PORT}/index.html`);
 await page.waitForTimeout(700);

 console.log('\n— native boot —');
 check('app boots in native mode', await page.isVisible('#view-home'));
 check('SW skipped on native', await page.evaluate(() => !navigator.serviceWorker || !navigator.serviceWorker.controller));
 const offline = await page.evaluate(() => document.querySelector('#offline-status').textContent);
 check('offline status says native', offline.includes('native'), offline);

 console.log('\n— native voices —');
 await page.click('#tile-sample');
 await page.waitForSelector('#view-reader:not(.hidden)');
 await page.click('#ctl-voice');
 await page.waitForSelector('#sheet-voice:not(.hidden)');
 check('native voices listed', (await page.locator('.voice-item').count()) === 3);
 check('offline/online voice tags', (await page.textContent('#voice-list')).includes('offline'));
 await page.locator('.voice-item', { hasText: 'English (UK)' }).click();
 await page.waitForTimeout(150);
 const calls1 = await page.evaluate(() => window.__native.calls.filter(c => c[0]==='configure' || c[0]==='preview'));
 check('voice select configures + previews natively', calls1.some(c => c[0]==='configure' && c[1].voice === 'en-gb-x-b#male-local') && calls1.some(c => c[0]==='preview'));
 await page.click('#sheet-backdrop', { position: { x: 10, y: 10 } });

 console.log('\n— native playback —');
 await page.click('#ctl-play');
 await page.waitForTimeout(900);
 const calls2 = await page.evaluate(() => window.__native.calls);
 check('notification permission requested', calls2.some(c => c[0]==='requestNotifications'));
 check('sentences batched to native engine', calls2.some(c => c[0]==='speakBatch' && c[1].n > 5 && c[1].first === 0));
 check('now-playing set to playing', calls2.some(c => c[0]==='setNowPlaying' && c[1].playing));
 check('cover artwork sent once for book', calls2.some(c => c[0]==='setNowPlaying' && c[1].hasCover));
 check('play button shows pause', (await page.textContent('#ctl-play')).includes('❚'));
 const idx = await page.evaluate(() => Number(document.querySelector('#reader-text span.active')?.dataset.si));
 check('utterance events advance highlight', idx >= 1, `idx=${idx}`);

 await page.click('#ctl-play'); // pause
 await page.waitForTimeout(200);
 const calls3 = await page.evaluate(() => window.__native.calls.slice(-4));
 check('pause stops native queue', calls3.some(c => c[0]==='stop'));
 check('pause updates notification', calls3.some(c => c[0]==='setNowPlaying' && c[1].playing === false));

 console.log('\n— media actions from notification —');
 await page.evaluate(() => window.__emitNative('mediaAction', { action: 'play' }));
 await page.waitForTimeout(300);
 check('notification Play resumes', await page.evaluate(() => window.__vox.player.playing));
 const before = await page.evaluate(() => window.__vox.player.cursor);
 await page.evaluate(() => window.__emitNative('mediaAction', { action: 'next' }));
 await page.waitForTimeout(300);
 const after = await page.evaluate(() => window.__vox.player.cursor);
 check('notification Forward skips ahead', after > before, `${before} → ${after}`);
 await page.evaluate(() => window.__emitNative('mediaAction', { action: 'pause' }));
 await page.waitForTimeout(200);
 check('notification Pause pauses', await page.evaluate(() => !window.__vox.player.playing));

 console.log('\n— finish behaviour —');
 await page.evaluate(() => window.__vox.player.seekToSentence(window.__vox.player.book.sentences.length - 1));
 await page.evaluate(() => window.__vox.player.play());
 await page.waitForTimeout(800);
 const done = await page.evaluate(() => ({ playing: window.__vox.player.playing,
   cleared: window.__native.calls.some(c => c[0]==='clearNowPlaying') }));
 check('queueDone at end finishes playback', done.playing === false);
 check('notification cleared at finish', done.cleared);

 const realErrors = errors.filter(e => !e.includes('favicon'));
 check('no page errors', realErrors.length === 0, realErrors.slice(0,3).join(' | '));

 console.log(`\n========== ${passed} passed, ${failed} failed ==========`);
 if (failures.length) { failures.forEach(f => console.log(' -', f)); process.exit(1); }
 process.exit(0);
})().catch(e => { console.error('FATAL', e); process.exit(1); });
