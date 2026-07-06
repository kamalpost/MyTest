/* YourHour replica — data, charts, navigation, usage tracking */
(function () {
'use strict';

var $ = function (s) { return document.querySelector(s); };
var $$ = function (s) { return Array.prototype.slice.call(document.querySelectorAll(s)); };
var SVGNS = 'http://www.w3.org/2000/svg';

/* ---------------- demo data (matches screenshots, 06 Jul 2026) ---------------- */

var DATA = {
  goalMinutes: 225,
  todayMinutes: 150,
  todayUnlocks: 37,
  unlockGoal: 50,
  categories: [
    { name: 'Social',       mins: 6,   label: '6m',     color: '#f15f79' },
    { name: 'Games',        mins: 0,   label: '0m',     color: '#3a7bd5' },
    { name: 'Media',        mins: 0,   label: '0m',     color: '#e73827' },
    { name: 'Productivity', mins: 136, label: '2h 16m', color: '#71c075' },
    { name: 'Custom',       mins: 9,   label: '9m',     color: '#bdbdbd' }
  ],
  // minutes of usage per 30-min slot, 48 slots from 12:00 AM
  hourly: [0,0,0,0,0,0,0,0,0,0,0,7,1.5,28,32,17,4,1,2,7,0,0,7,7.5,
           1,0.7,2,5,7,8,7.5,3,2,14.5,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
  week: [
    { label: '30-06', mins: 376 },
    { label: '01-07', mins: 327 },
    { label: '02-07', mins: 436 },
    { label: '03-07', mins: 422 },
    { label: '04-07', mins: 405 },
    { label: '05-07', mins: 414 },
    { label: 'TODAY', mins: 154, today: true }
  ],
  days: [
    { num: '06', date: '06 Jul, 2026', usage: '2h 31m', ud: -64, unlock: 37, kd: 23 },
    { num: '05', date: '05 Jul, 2026', usage: '6h 54m', ud: 2,   unlock: 30, kd: -51 },
    { num: '04', date: '04 Jul, 2026', usage: '6h 45m', ud: -4,  unlock: 61, kd: -10 },
    { num: '03', date: '03 Jul, 2026', usage: '7h 02m', ud: 12,  unlock: 68, kd: 6 },
    { num: '02', date: '02 Jul, 2026', usage: '6h 17m', ud: 15,  unlock: 64, kd: -8 },
    { num: '01', date: '01 Jul, 2026', usage: '5h 27m', ud: -13, unlock: 70, kd: 27 }
  ],
  weeks: [
    { num: 'W28', date: '30 Jun - 06 Jul', usage: '38h 42m', ud: -9,  unlock: 307, kd: -4 },
    { num: 'W27', date: '23 Jun - 29 Jun', usage: '42h 18m', ud: 6,   unlock: 321, kd: 11 },
    { num: 'W26', date: '16 Jun - 22 Jun', usage: '39h 55m', ud: -2,  unlock: 289, kd: -7 }
  ],
  months: [
    { num: 'Jul', date: 'July, 2026',  usage: '32h 51m', ud: -11, unlock: 267, kd: -9 },
    { num: 'Jun', date: 'June, 2026',  usage: '171h 36m', ud: 4,  unlock: 1290, kd: 2 },
    { num: 'May', date: 'May, 2026',   usage: '164h 02m', ud: -6, unlock: 1265, kd: -5 }
  ]
};

/* ---------------- native bridge (Android APK) ----------------
   When running inside the Android app, window.YourHourNative provides
   real UsageStatsManager data which replaces the sample dataset. */

function fmtHM(mins) {
  mins = Math.max(0, Math.round(mins));
  return Math.floor(mins / 60) + 'H ' + (mins % 60) + 'M';
}
function fmtHm(mins) {
  mins = Math.max(0, Math.round(mins));
  return mins >= 60 ? Math.floor(mins / 60) + 'h ' + String(mins % 60).padStart(2, '0') + 'm' : mins + 'm';
}
function pctDelta(cur, prev) {
  if (prev > 0) return Math.round((cur - prev) / prev * 100);
  return cur > 0 ? 100 : 0;
}

var NATIVE = window.YourHourNative || null;
var nativeGranted = false;

function applyNativeData() {
  if (!NATIVE) return false;
  try { nativeGranted = NATIVE.hasPermission(); } catch (e) { return false; }
  window.__nativeGranted = nativeGranted;
  if (!nativeGranted) return false;
  var snap;
  try { snap = JSON.parse(NATIVE.getSnapshot()); } catch (e) { return false; }
  if (!snap || !snap.granted) return false;

  DATA.todayMinutes = Math.round(snap.todayMinutes);
  DATA.todayUnlocks = snap.unlocks;
  DATA.hourly = snap.hourly;
  DATA.week = snap.week.map(function (w) {
    return { label: w.label, mins: w.mins, today: w.label === 'TODAY' };
  });

  var c = snap.categories;
  DATA.categories = [
    { name: 'Social',       mins: c.social,       label: fmtHm(c.social),       color: '#f15f79' },
    { name: 'Games',        mins: c.games,        label: fmtHm(c.games),        color: '#3a7bd5' },
    { name: 'Media',        mins: c.media,        label: fmtHm(c.media),        color: '#e73827' },
    { name: 'Productivity', mins: c.productivity, label: fmtHm(c.productivity), color: '#71c075' },
    { name: 'Custom',       mins: c.custom,       label: fmtHm(c.custom),       color: '#bdbdbd' }
  ];

  var days = snap.days; // oldest first
  var list = [];
  for (var i = days.length - 1; i > 0; i--) {
    list.push({
      num: days[i].num, date: days[i].date,
      usage: fmtHm(days[i].mins),
      ud: pctDelta(days[i].mins, days[i - 1].mins),
      unlock: days[i].unlocks,
      kd: pctDelta(days[i].unlocks, days[i - 1].unlocks)
    });
  }
  DATA.days = list;
  DATA.nativeTimeline = snap.timeline;

  // reflect live values in the static parts of the dashboard
  document.querySelector('.rl-left .v-green').textContent = fmtHM(DATA.todayMinutes);
  document.querySelector('.rl-left .v-dim').textContent = '/' + fmtHM(DATA.goalMinutes);
  document.querySelector('.rl-right .v-cyan').textContent = DATA.todayUnlocks;
  document.querySelector('.rl-right .v-dim').textContent = '/' + DATA.unlockGoal;
  return true;
}

/* small app-icon builders (approximations of real launcher icons) */
function appIcon(kind) {
  var d = document.createElement('span');
  d.className = 'appdot';
  if (kind && kind.img) {
    var img = document.createElement('img');
    img.src = kind.img;
    img.alt = kind.name || '';
    d.appendChild(img);
    return d;
  }
  var map = {
    whatsapp: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#25d366"/><path d="M12 5.4a6.5 6.5 0 0 0-5.6 9.8L5.5 18l2.9-.86A6.5 6.5 0 1 0 12 5.4zm3.3 8.9c-.15.4-.85.77-1.2.8-.32.03-.72.04-2.3-.63-1.94-.83-3.16-2.85-3.26-2.98-.1-.13-.78-1.06-.78-2.03 0-.96.5-1.43.67-1.62.18-.2.38-.24.5-.24l.37.01c.12 0 .28-.05.44.33l.6 1.5c.05.12.09.25.01.4l-.24.38-.35.38c-.1.1-.22.22-.1.44.13.22.57.95 1.22 1.53.84.76 1.55 1 1.77 1.1.22.1.35.1.48-.05l.72-.85c.17-.22.31-.16.52-.1l1.36.65c.22.1.36.16.42.26.05.1.05.55-.1.94z" fill="#fff"/></svg>',
    chrome: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#fff"/><circle cx="12" cy="12" r="4.2" fill="#4285f4"/><circle cx="12" cy="12" r="5.3" fill="none" stroke="#fff" stroke-width="1"/><path d="M12 2a10 10 0 0 1 8.66 5H12a5 5 0 0 0-4.55 2.93z" fill="#ea4335"/><path d="M3.34 7A10 10 0 0 0 10 21.8l4.32-7.5A5 5 0 0 1 7.45 9.93z" fill="#34a853"/><path d="M20.66 7A10 10 0 0 1 10 21.8l4.33-7.48A5 5 0 0 0 17 7z" fill="#fbbc05"/></svg>',
    phone: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#fff"/><path d="M16.9 14.4l-2-0.9a1 1 0 0 0-1.1.3l-.8 1a9.4 9.4 0 0 1-3.8-3.8l1-.8a1 1 0 0 0 .3-1.1l-.9-2A1 1 0 0 0 8.5 6.4l-1.3.3c-.5.13-.9.6-.85 1.15A11.6 11.6 0 0 0 16.1 17.6c.55.06 1.02-.35 1.15-.85l.3-1.3a1 1 0 0 0-.65-1.05z" fill="#1a73e8"/></svg>',
    gmail: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#fff"/><path d="M5 8.2v8h2.6v-5l4.4 3.3 4.4-3.3v5H19v-8L12 13z" fill="#ea4335"/></svg>',
    google: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#fff"/><text x="12" y="16.6" font-size="13" font-weight="700" text-anchor="middle" font-family="sans-serif" fill="#4285f4">G</text></svg>',
    zoom: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#2d8cff"/><rect x="5" y="8.6" width="9.4" height="6.8" rx="1.6" fill="#fff"/><path d="M15.2 11l3.8-2.2v6.4L15.2 13z" fill="#fff"/><text x="12" y="20.9" font-size="3.4" text-anchor="middle" font-family="sans-serif" fill="#fff">zoom</text></svg>',
    clock: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#3f51b5"/><circle cx="12" cy="12" r="8.4" fill="#e8eaf6"/><path d="M12 7v5l3.4 2" stroke="#1a237e" stroke-width="1.6" fill="none" stroke-linecap="round"/></svg>',
    maps: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#fff"/><path d="M12 4.5a5.3 5.3 0 0 1 5.3 5.3c0 3.9-5.3 9.7-5.3 9.7s-5.3-5.8-5.3-9.7A5.3 5.3 0 0 1 12 4.5z" fill="#4285f4"/><circle cx="12" cy="9.8" r="2.2" fill="#fff"/></svg>',
    teams: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#fff"/><rect x="4.5" y="7.5" width="9.5" height="9.5" rx="1.6" fill="#5059c9"/><text x="9.3" y="14.9" font-size="7.5" font-weight="700" text-anchor="middle" font-family="sans-serif" fill="#fff">T</text><circle cx="17.4" cy="9.6" r="2.3" fill="#7b83eb"/><path d="M14.6 12.6h5.6v3.1a2.8 2.8 0 0 1-5.6 0z" fill="#7b83eb"/></svg>',
    launcher: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#b71c1c"/><circle cx="12" cy="12" r="10" fill="none" stroke="#4dd0e1" stroke-width="1.4"/><path d="M12 5l2 5h5l-4 3.2 1.6 5L12 15l-4.6 3.2 1.6-5L5 10h5z" fill="#ff8a65"/></svg>',
    burst: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#fff"/><g stroke="#e05a4e" stroke-width="1.8" stroke-linecap="round"><path d="M12 4v16M4 12h16M6.4 6.4l11.2 11.2M17.6 6.4L6.4 17.6"/></g><circle cx="12" cy="12" r="2.6" fill="#e05a4e"/></svg>',
    wallet: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#fff"/><path d="M5 10.5c3-3.6 7-4.6 10.6-2.6l3 1.7c.9.5 1.2 1.7.6 2.6-2.9 4.4-8.9 5.6-13 2.6z" fill="#4285f4"/><path d="M5 13.8c3.4-2.7 7.6-2.9 11.2-.5l2.4 1.6c-3.2 3.9-8.9 4.7-13.1 1.9z" fill="#34a853" opacity=".85"/></svg>'
  };
  d.innerHTML = map[kind] || map.google;
  return d;
}

var TIMELINE = [
  { t: '12:00 AM', happy: true }, { t: '01:00 AM', happy: true },
  { t: '02:00 AM', happy: true }, { t: '03:00 AM', happy: true },
  { t: '04:00 AM', happy: true }, { t: '05:00 AM', happy: true },
  { t: '06:00 AM', apps: ['whatsapp','launcher','chrome','burst','phone'], d: '11m 28s' },
  { t: '07:00 AM', apps: ['phone','launcher','google','gmail','chrome'], d: '55m 41s' },
  { t: '08:00 AM', apps: ['zoom','clock','launcher','maps','whatsapp'], d: '18m 34s' },
  { t: '09:00 AM', apps: ['teams','zoom','google','launcher','wallet'], d: '5m 17s' },
  { t: '10:00 AM', apps: ['chrome','gmail','launcher'], d: '9m 03s' },
  { t: '11:00 AM', apps: ['whatsapp','chrome','launcher','phone'], d: '14m 22s' },
  { t: '12:00 PM', apps: ['launcher','chrome'], d: '8m 41s' },
  { t: '01:00 PM', apps: ['whatsapp','gmail','launcher'], d: '7m 55s' },
  { t: '02:00 PM', apps: ['chrome','whatsapp','launcher','maps'], d: '22m 36s' },
  { t: '03:00 PM', apps: ['whatsapp','launcher'], d: '2m 48s' },
  { t: '04:00 PM', happy: true }, { t: '05:00 PM', happy: true },
  { t: '06:00 PM', happy: true }, { t: '07:00 PM', happy: true },
  { t: '08:00 PM', happy: true }, { t: '09:00 PM', happy: true },
  { t: '10:00 PM', happy: true }, { t: '11:00 PM', happy: true }
];

var FOCUS = [
  { key: 'youtube', name: 'YouTube Shorts',
    icon: '<svg viewBox="0 0 24 24"><rect x="1.5" y="4.5" width="21" height="15" rx="4" fill="#f00"/><path d="M10 9l6 3-6 3z" fill="#fff"/></svg>' },
  { key: 'instagram', name: 'Instagram Reels',
    icon: '<svg viewBox="0 0 24 24"><defs><linearGradient id="ig" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#fd5"/><stop offset=".4" stop-color="#ff543e"/><stop offset=".7" stop-color="#c837ab"/><stop offset="1" stop-color="#5b51d8"/></linearGradient></defs><rect x="2" y="2" width="20" height="20" rx="6" fill="url(#ig)"/><rect x="6.4" y="6.4" width="11.2" height="11.2" rx="3.4" fill="none" stroke="#fff" stroke-width="1.7"/><circle cx="12" cy="12" r="2.7" fill="none" stroke="#fff" stroke-width="1.7"/><circle cx="16.1" cy="7.9" r="1.05" fill="#fff"/></svg>' },
  { key: 'snapchat', name: 'Snapchat Spotlight',
    icon: '<svg viewBox="0 0 24 24"><rect x="2" y="2" width="20" height="20" rx="5" fill="#fffc00"/><path d="M12 5.2c2.3 0 3.9 1.7 3.9 4l-.02 1.9c.5.25 1-.45 1.6-.15.5.24.35.9-.2 1.16-.5.24-1.3.4-1.1 1.15.35 1.3 1.8 2.2 2.7 2.4-.06.5-1.2.86-1.9.94-.1.3-.16.72-.4.8-.55.2-1.3-.2-2 .06-.6.23-1.1 1.14-2.58 1.14S10 17.7 9.42 17.46c-.7-.27-1.45.14-2-.06-.24-.08-.3-.5-.4-.8-.7-.08-1.84-.44-1.9-.94.9-.2 2.35-1.1 2.7-2.4.2-.75-.6-.9-1.1-1.15-.55-.27-.7-.92-.2-1.16.6-.3 1.1.4 1.6.15L8.1 9.2c0-2.3 1.6-4 3.9-4z" fill="#fff" stroke="#000" stroke-width=".4"/></svg>' },
  { key: 'facebook', name: 'Facebook Reels',
    icon: '<svg viewBox="0 0 24 24"><rect x="2" y="2" width="20" height="20" rx="6" fill="#1877f2"/><path d="M15.5 12.5H13.3V19h-2.7v-6.5H8.9V10h1.7V8.6c0-1.9 1-3 3-3 .8 0 1.5.06 1.8.1v2.1h-1.2c-.9 0-1 .4-1 1V10h2.4z" fill="#fff"/></svg>' }
];

/* ---------------- status bar clock / demo freeze ---------------- */

var params = new URLSearchParams(location.search);
function tick() {
  var el = $('#sb-time');
  if (params.get('t')) { el.textContent = params.get('t'); return; }
  var d = new Date(), h = d.getHours() % 12 || 12;
  el.textContent = h + ':' + String(d.getMinutes()).padStart(2, '0');
}
tick(); setInterval(tick, 5000);
if (params.get('b')) $('#sb-batt-num').textContent = params.get('b');

/* ---------------- SVG helpers ---------------- */

function el(name, attrs, parent) {
  var e = document.createElementNS(SVGNS, name);
  for (var k in attrs) e.setAttribute(k, attrs[k]);
  if (parent) parent.appendChild(e);
  return e;
}
function polar(cx, cy, r, deg) {
  var a = (deg - 90) * Math.PI / 180;
  return [cx + r * Math.cos(a), cy + r * Math.sin(a)];
}
function arcPath(cx, cy, r, startDeg, endDeg) {
  var s = polar(cx, cy, r, startDeg), e = polar(cx, cy, r, endDeg);
  var large = (endDeg - startDeg) % 360 > 180 ? 1 : 0;
  return 'M' + s[0] + ' ' + s[1] + ' A' + r + ' ' + r + ' 0 ' + large + ' 1 ' + e[0] + ' ' + e[1];
}

/* ---------------- dashboard rings ---------------- */

function drawRings() {
  var size = 222, c = size / 2;
  var svg = el('svg', { viewBox: '0 0 ' + size + ' ' + size, width: size, height: size });
  var rOuter = 100, rInner = 83, w = 10;

  el('circle', { cx: c, cy: c, r: rOuter, fill: 'none', stroke: '#2c2c2c', 'stroke-width': w }, svg);
  el('circle', { cx: c, cy: c, r: rInner, fill: 'none', stroke: '#2c2c2c', 'stroke-width': w }, svg);
  el('circle', { cx: c, cy: c, r: 70, fill: '#232323' }, svg);

  var unlockDeg = Math.min(359.9, 360 * DATA.todayUnlocks / DATA.unlockGoal);
  el('path', { d: arcPath(c, c, rOuter, 0, unlockDeg), fill: 'none', stroke: '#0097a7',
               'stroke-width': w, 'stroke-linecap': 'round' }, svg);
  var pe = polar(c, c, rOuter, unlockDeg);
  el('circle', { cx: pe[0], cy: pe[1], r: 7, fill: '#0097a7' }, svg);

  var useDeg = Math.min(359.9, 360 * DATA.todayMinutes / DATA.goalMinutes);
  el('path', { d: arcPath(c, c, rInner, 0, useDeg), fill: 'none', stroke: '#81c784',
               'stroke-width': w, 'stroke-linecap': 'round' }, svg);
  var pg = polar(c, c, rInner, useDeg);
  el('circle', { cx: pg[0], cy: pg[1], r: 7, fill: '#81c784' }, svg);

  var left = Math.max(0, DATA.goalMinutes - DATA.todayMinutes);
  var t1 = el('text', { x: c, y: c - 5, 'text-anchor': 'middle', fill: '#81c784',
    'font-size': '31', 'font-weight': '700', 'font-family': "'Open Sans',sans-serif" }, svg);
  t1.textContent = Math.floor(left / 60) + 'H ' + (left % 60) + 'M';
  var t2 = el('text', { x: c, y: c + 31, 'text-anchor': 'middle', fill: '#81c784',
    'font-size': '27', 'font-weight': '700', 'font-family': "'Open Sans',sans-serif" }, svg);
  t2.textContent = 'LEFT';

  $('#rings').appendChild(svg);
}

/* partially visible app row at bottom of ring card */
function drawRingApps() {
  ['zoom','clock','launcher','maps','whatsapp'].forEach(function (k) {
    $('#ring-apps').appendChild(appIcon(k));
  });
}

/* ---------------- category donut + legend ---------------- */

function drawDonut() {
  var size = 158, c = size / 2, r = 65, w = 14;
  var svg = el('svg', { viewBox: '0 0 ' + size + ' ' + size, width: size, height: size });
  var total = 0;
  DATA.categories.forEach(function (x) { total += Math.max(x.mins, 0.8); });
  // draw starting at top going clockwise: custom(gray), then social(pink), media sliver, games sliver, productivity(green)
  var order = [4, 0, 2, 1, 3];
  var a = -14; // slight offset so gray straddles 12 o'clock like the screenshot
  order.forEach(function (i) {
    var cat = DATA.categories[i];
    var deg = 360 * Math.max(cat.mins, 0.8) / total;
    el('path', { d: arcPath(c, c, r, a, a + deg - 1.6), fill: 'none',
                 stroke: cat.color, 'stroke-width': w }, svg);
    a += deg;
  });
  var t1 = el('text', { x: c, y: c - 7, 'text-anchor': 'middle', fill: '#bdbdbd',
    'font-size': '16', 'font-family': "'Open Sans',sans-serif" }, svg);
  t1.textContent = 'USAGE';
  var t2 = el('text', { x: c, y: c + 19, 'text-anchor': 'middle', fill: '#fff',
    'font-size': '22', 'font-weight': '800', 'font-family': "'Open Sans',sans-serif" }, svg);
  var catTotal = 0;
  DATA.categories.forEach(function (x) { catTotal += x.mins; });
  t2.textContent = DATA.nativeTimeline ? fmtHM(catTotal) : '2H 33M';
  $('#donut').appendChild(svg);

  var legend = $('#cat-legend');
  DATA.categories.forEach(function (cat) {
    var row = document.createElement('div');
    row.className = 'leg-row';
    row.innerHTML = '<span class="leg-dot" style="background:' + cat.color + '"></span>' +
      '<span class="leg-name">' + cat.name + '</span>' +
      '<span class="leg-val">' + cat.label + '</span>';
    legend.appendChild(row);
  });
}

/* ---------------- hourly bar chart ---------------- */

function drawHourly() {
  var W = 360, H = 170, padL = 30, padB = 26, padT = 8;
  var plotW = W - padL - 6, plotH = H - padB - padT;
  var svg = el('svg', { viewBox: '0 0 ' + W + ' ' + H });
  var maxV = Math.max.apply(null, DATA.hourly.concat([1]));
  var step = Math.max(10, Math.ceil(maxV / 33) * 10);
  var maxY = step * 3.3;

  [0, step, step * 2, step * 3].forEach(function (v) {
    var y = padT + plotH * (1 - v / maxY);
    el('line', { x1: padL, y1: y, x2: W - 4, y2: y, stroke: '#2e2e2e', 'stroke-width': 1 }, svg);
    var t = el('text', { x: padL - 8, y: y + 5, 'text-anchor': 'end', fill: '#e0e0e0',
      'font-size': '14', 'font-family': "'Open Sans',sans-serif" }, svg);
    t.textContent = v;
  });

  var n = DATA.hourly.length, bw = plotW / n;
  DATA.hourly.forEach(function (v, i) {
    if (v <= 0) return;
    var h = plotH * v / maxY;
    el('rect', { x: padL + i * bw + 1, y: padT + plotH - h,
                 width: bw - 2.2, height: h, fill: '#71c075' }, svg);
  });

  ['12 AM','04 AM','08 AM','12 PM','04 PM','08 PM','12 AM'].forEach(function (lab, i) {
    var x = padL + plotW * i / 6;
    var t = el('text', { x: x, y: H - 7, 'text-anchor': i === 0 ? 'start' : (i === 6 ? 'end' : 'middle'),
      fill: '#e0e0e0', 'font-size': '12', 'font-family': "'Open Sans',sans-serif" }, svg);
    t.textContent = lab;
  });
  $('#hourly-chart').appendChild(svg);
}

/* ---------------- weekly bar chart ---------------- */

function drawWeekly() {
  var W = 360, H = 190, padL = 38, padB = 24, padT = 20;
  var plotW = W - padL - 8, plotH = H - padB - padT;
  var svg = el('svg', { viewBox: '0 0 ' + W + ' ' + H });
  var maxV = DATA.goalMinutes;
  DATA.week.forEach(function (d) { maxV = Math.max(maxV, d.mins); });
  var maxY = Math.round(maxV * 1.012);

  var grad = el('linearGradient', { id: 'wg', x1: 0, y1: 0, x2: 0, y2: 1 }, svg);
  el('stop', { offset: '0', 'stop-color': '#81c784' }, grad);
  el('stop', { offset: '1', 'stop-color': '#55aa59' }, grad);

  el('line', { x1: padL, y1: padT, x2: padL, y2: padT + plotH, stroke: '#3a3a3a', 'stroke-width': 1 }, svg);
  [0, Math.floor(maxY / 4), Math.floor(maxY / 2), Math.floor(maxY * 3 / 4), maxY].forEach(function (v) {
    var y = padT + plotH * (1 - v / maxY);
    var t = el('text', { x: padL - 7, y: y + 5, 'text-anchor': 'end', fill: '#e0e0e0',
      'font-size': '14', 'font-family': "'Open Sans',sans-serif" }, svg);
    t.textContent = v;
  });

  var n = DATA.week.length, slot = plotW / n, bw = 15;
  DATA.week.forEach(function (d, i) {
    var x = padL + slot * i + (slot - bw) / 2;
    var h = plotH * d.mins / maxY;
    var y = padT + plotH - h;
    el('rect', { x: x, y: y, width: bw, height: h, rx: 2, fill: 'url(#wg)' }, svg);
    var t = el('text', { x: x + bw / 2, y: y - 6, 'text-anchor': 'middle', fill: '#81c784',
      'font-size': '16.5', 'font-weight': '600', 'font-family': "'Open Sans',sans-serif" }, svg);
    t.textContent = d.mins;
    if (d.today) {
      var m = el('text', { x: x + bw / 2, y: y + 14, 'text-anchor': 'middle', fill: '#fff',
        'font-size': '10', 'font-weight': '700', 'font-family': "'Open Sans',sans-serif" }, svg);
      m.textContent = '✓';
    }
    var lab = el('text', { x: x + bw / 2, y: H - 5, 'text-anchor': 'middle', fill: '#e0e0e0',
      'font-size': '13.5', 'font-family': "'Open Sans',sans-serif" }, svg);
    lab.textContent = d.label === 'TODAY' ? 'TODA' : d.label;
  });

  var gy = padT + plotH * (1 - DATA.goalMinutes / maxY);
  el('line', { x1: padL, y1: gy, x2: W - 4, y2: gy, stroke: '#81c784',
               'stroke-width': 1.6, 'stroke-dasharray': '7 6' }, svg);
  $('#weekly-chart').appendChild(svg);
}

/* ---------------- reports list ---------------- */

var CHEV = '<svg class="rc-chev" viewBox="0 0 24 24" fill="none" stroke="#e0e0e0" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6l6 6-6 6"/></svg>';

function deltaHTML(pct, invertGood) {
  // negative pct = decrease (good for usage), arrow up/down + colored %
  var down = pct < 0;
  var good = invertGood ? !down : down;
  return '<span class="rc-delta ' + (down ? 'delta-down' : 'delta-up') + ' ' +
         (good ? 'delta-good' : 'delta-bad') + '">' + Math.abs(pct) + '%</span>';
}

function renderReports(tab) {
  var list = $('#report-list');
  list.innerHTML = '';
  var rows = tab === 'weekly' ? DATA.weeks : tab === 'monthly' ? DATA.months : DATA.days;
  rows.forEach(function (d) {
    var card = document.createElement('div');
    card.className = 'report-card';
    card.innerHTML =
      '<span class="rc-badge"><span class="rc-badge-num">' + d.num + '</span><span class="rc-badge-sub">..</span></span>' +
      '<div class="rc-body">' +
        '<div class="rc-date">' + d.date + '</div>' +
        '<div class="rc-row"><span class="rc-label">Usage:</span><span class="rc-value">' + d.usage + '</span>' + deltaHTML(d.ud, false) + CHEV + '</div>' +
        '<div class="rc-row"><span class="rc-label">Unlock:</span><span class="rc-value">' + d.unlock + '</span>' + deltaHTML(d.kd, false) + '<span style="width:20px;flex:0 0 20px"></span></div>' +
      '</div>';
    card.addEventListener('click', function () {
      $('#daydetail-title').textContent = d.date;
      $('#dd-usage').textContent = d.usage;
      $('#dd-unlock').textContent = d.unlock + ' times';
      showScreen('daydetail');
    });
    list.appendChild(card);
  });
}

$$('#report-tabs .tab').forEach(function (t) {
  t.addEventListener('click', function () {
    $$('#report-tabs .tab').forEach(function (x) { x.classList.remove('tab-active'); });
    t.classList.add('tab-active');
    renderReports(t.dataset.tab);
  });
});

/* ---------------- timeline ---------------- */

function fmtSecsShort(secs) {
  secs = Math.round(secs);
  if (secs >= 3600) {
    return Math.floor(secs / 3600) + 'h ' + String(Math.floor(secs % 3600 / 60)).padStart(2, '0') + 'm';
  }
  return Math.floor(secs / 60) + 'm ' + String(secs % 60).padStart(2, '0') + 's';
}
function hourLabel(h) {
  var ampm = h < 12 ? 'AM' : 'PM';
  var hr = h % 12 === 0 ? 12 : h % 12;
  return String(hr).padStart(2, '0') + ':00 ' + ampm;
}

function renderTimeline() {
  var wrap = $('#timeline');
  wrap.innerHTML = '';
  var rows = TIMELINE;
  if (DATA.nativeTimeline) {
    rows = DATA.nativeTimeline.map(function (r, h) {
      if (!r.apps.length || r.secs < 30) return { t: hourLabel(h), happy: true };
      return {
        t: hourLabel(h),
        apps: r.apps.map(function (a) { return { img: a.icon, name: a.name }; }),
        d: fmtSecsShort(r.secs)
      };
    });
  }
  rows.forEach(function (row) {
    var r = document.createElement('div');
    r.className = 'tl-row';
    var mid, right;
    if (row.happy) {
      mid = '<span class="tl-emoji">😊</span>';
      right = '<span class="tl-right dim">Happy …</span>';
    } else {
      mid = '<span class="tl-icons"></span>';
      right = '<span class="tl-right">' + row.d + '</span>';
    }
    r.innerHTML = '<span class="tl-time">' + row.t + '</span><span class="tl-mid">' + mid + '</span>' + right;
    if (row.apps) {
      var icons = r.querySelector('.tl-icons');
      row.apps.forEach(function (k) { icons.appendChild(appIcon(k)); });
      var chev = document.createElementNS(SVGNS, 'svg');
      chev.setAttribute('class', 'tl-expand');
      chev.setAttribute('viewBox', '0 0 24 24');
      chev.innerHTML = '<path d="M6 9l6 6 6-6" fill="none" stroke="#bdbdbd" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>';
      icons.appendChild(chev);
    }
    wrap.appendChild(r);
  });
}

/* ---------------- focus list ---------------- */

function renderFocus() {
  var wrap = $('#focus-list');
  wrap.innerHTML = '';
  var states = {}, serviceOn = false;
  if (NATIVE && NATIVE.getBlocks) {
    try {
      var b = JSON.parse(NATIVE.getBlocks());
      states = b.blocks || {};
      serviceOn = !!b.serviceEnabled;
    } catch (e) {}
  }
  FOCUS.forEach(function (f, i) {
    var card = document.createElement('div');
    card.className = 'focus-card';
    card.innerHTML =
      '<div class="focus-main">' +
        '<span class="focus-appicon">' + f.icon.replace(/id="ig"/g, 'id="ig' + i + '"').replace(/url\(#ig\)/g, 'url(#ig' + i + ')') + '</span>' +
        '<span class="focus-name">' + f.name + '</span>' +
        '<label class="toggle"><input type="checkbox"><span class="track"></span><span class="knob"></span></label>' +
      '</div>' +
      '<div class="focus-sub">Turned off</div>';
    var input = card.querySelector('input');
    var sub = card.querySelector('.focus-sub');
    input.checked = !!states[f.key];
    function refresh() {
      if (!input.checked) {
        sub.textContent = 'Turned off';
        sub.style.color = '#757575';
      } else if (NATIVE && !serviceOn) {
        sub.textContent = 'Tap to enable YourHour in Accessibility settings';
        sub.style.color = '#ffab00';
      } else {
        sub.textContent = 'Blocking is active';
        sub.style.color = '#4dd0e1';
      }
    }
    refresh();
    input.addEventListener('change', function () {
      if (NATIVE && NATIVE.setBlock) {
        NATIVE.setBlock(f.key, input.checked);
        serviceOn = NATIVE.isAccessibilityEnabled();
        if (input.checked && !serviceOn) NATIVE.openAccessibilitySettings();
      }
      refresh();
    });
    sub.addEventListener('click', function () {
      if (NATIVE && input.checked && !serviceOn) NATIVE.openAccessibilitySettings();
    });
    wrap.appendChild(card);
  });
}

/* ---------------- navigation ---------------- */

var APPBARS = {
  dashboard: 'appbar-main', reports: 'appbar-reports', focus: 'appbar-focus',
  challenges: 'appbar-challenges', stories: 'appbar-stories',
  daydetail: 'appbar-daydetail', timeline: 'appbar-timeline'
};
var NAV_FOR = { daydetail: 'reports', timeline: 'reports' };
var currentScreen = 'dashboard';

function showScreen(name) {
  currentScreen = name;
  $$('.screen').forEach(function (s) { s.classList.add('hidden'); });
  $$('.appbar').forEach(function (a) { a.classList.add('hidden'); });
  $('#screen-' + name).classList.remove('hidden');
  $('#' + APPBARS[name]).classList.remove('hidden');
  $('#phone').classList.toggle('no-bottomnav', name === 'daydetail' || name === 'timeline');
  var navName = NAV_FOR[name] || name;
  $$('.nav-item').forEach(function (n) {
    n.classList.toggle('nav-active', n.dataset.screen === navName);
  });
  $('#screen-' + name).scrollTop = 0;
}

$$('.nav-item').forEach(function (n) {
  n.addEventListener('click', function () { showScreen(n.dataset.screen); });
});
$('#appbar-daydetail .ic-back').addEventListener('click', function () { showScreen('reports'); });
$('#appbar-timeline .ic-back').addEventListener('click', function () { showScreen('daydetail'); });
$('#open-timeline').addEventListener('click', function () { showScreen('timeline'); });

/* Android hardware/gesture back */
window.onNativeBack = function () {
  if (currentScreen === 'timeline') { showScreen('daydetail'); return true; }
  if (currentScreen === 'daydetail') { showScreen('reports'); return true; }
  if (currentScreen !== 'dashboard') { showScreen('dashboard'); return true; }
  return false;
};

/* Called by MainActivity.onResume — pick up freshly granted permissions */
window.onNativeResume = function () {
  if (!NATIVE) return;
  if (!nativeGranted) {
    try { if (NATIVE.hasPermission()) { location.reload(); return; } } catch (e) {}
  }
  renderFocus(); // accessibility service may have been toggled in Settings
};

/* ---------------- real usage tracking (web session) ----------------
   Measures how long this app is actually on screen and how many times
   it is brought back to the foreground ("unlocks"), stored per day.   */

var STORE_KEY = 'yourhour-usage';
function loadUsage() {
  try { return JSON.parse(localStorage.getItem(STORE_KEY)) || {}; } catch (e) { return {}; }
}
function saveUsage(u) { try { localStorage.setItem(STORE_KEY, JSON.stringify(u)); } catch (e) {} }
function dayKey() { return new Date().toISOString().slice(0, 10); }

var usage = loadUsage();
var today = usage[dayKey()] || (usage[dayKey()] = { seconds: 0, opens: 0 });
today.opens += 1;
saveUsage(usage);

var lastTick = Date.now();
setInterval(function () {
  var now = Date.now();
  if (!document.hidden) {
    today = usage[dayKey()] || (usage[dayKey()] = { seconds: 0, opens: 0 });
    today.seconds += Math.min(5, (now - lastTick) / 1000);
    saveUsage(usage);
  }
  lastTick = now;
}, 5000);
document.addEventListener('visibilitychange', function () {
  if (!document.hidden) {
    today = usage[dayKey()] || (usage[dayKey()] = { seconds: 0, opens: 0 });
    today.opens += 1;
    saveUsage(usage);
  }
  lastTick = Date.now();
});

function fmtSecs(s) {
  var m = Math.floor(s / 60);
  return Math.floor(m / 60) + 'h ' + (m % 60) + 'm ' + Math.floor(s % 60) + 's';
}
$('#btn-screentime').addEventListener('click', function () {
  if (NATIVE && !nativeGranted) { NATIVE.openUsageAccess(); return; }
  if (nativeGranted) { alert('Screen time today: ' + fmtHm(DATA.todayMinutes)); return; }
  var t = usage[dayKey()] || { seconds: 0, opens: 0 };
  alert('Measured time in this app today: ' + fmtSecs(t.seconds));
});
$('#btn-unlockcount').addEventListener('click', function () {
  if (NATIVE && !nativeGranted) { NATIVE.openUsageAccess(); return; }
  if (nativeGranted) { alert('Unlocks today: ' + DATA.todayUnlocks); return; }
  var t = usage[dayKey()] || { seconds: 0, opens: 0 };
  alert('Times this app came to the foreground today: ' + t.opens);
});

/* ---------------- boot ---------------- */

if (NATIVE) document.body.classList.add('native'); // real status/nav bars exist
applyNativeData();

/* On-device without usage access yet: turn the promo banner into a
   permission prompt so real data is one tap away. */
if (NATIVE && !nativeGranted) {
  var bt = document.querySelector('#banner-badge .banner-title');
  var bb = document.querySelector('#banner-badge .banner-bottom');
  bt.textContent = 'Grant Usage Access!!';
  bb.innerHTML = 'Open Settings&nbsp;&nbsp;<span class="arr">&#8594;</span>';
  document.querySelector('#banner-badge').addEventListener('click', function () {
    NATIVE.openUsageAccess();
  });
}

drawRings();
drawRingApps();
drawDonut();
drawHourly();
drawWeekly();
renderReports('daily');
renderTimeline();
renderFocus();
showScreen(params.get('screen') || 'dashboard');

if ('serviceWorker' in navigator && location.protocol !== 'file:') {
  navigator.serviceWorker.register('sw.js').catch(function () {});
}
})();
