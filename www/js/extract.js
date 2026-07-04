/* Text extraction: PDF via pdf.js, DOCX via mammoth, plain text/paste.
   Output shape shared by all formats:
   {
     title, author, type, pages,           // pages = 1 for docx/text
     blocks:    [{ tag, text, page }],     // paragraphs/headings in reading order
     sentences: [{ t, b, p, w }],          // t=text, b=block index, p=page (1-based), w=word count
     cover,                                // dataURL thumbnail
     wordCount
   }
*/

import * as pdfjsLib from '../vendor/pdf.min.mjs';

pdfjsLib.GlobalWorkerOptions.workerSrc = new URL('../vendor/pdf.worker.min.mjs', import.meta.url).toString();

/* CMap + standard-font data let pdf.js decode fonts with predefined/composite
   encodings (common in Indic and CJK PDFs). Without these, such PDFs extract
   as junk characters even though they display fine in other viewers. */
const PDF_OPEN_OPTS = {
  cMapUrl: new URL('../vendor/cmaps/', import.meta.url).toString(),
  cMapPacked: true,
  standardFontDataUrl: new URL('../vendor/standard_fonts/', import.meta.url).toString(),
};

const MAX_SENTENCE_CHARS = 280; // keep utterances short so TTS engines never truncate

export function countWords(text) {
  const m = text.match(/\S+/g);
  return m ? m.length : 0;
}

/** Split a block of prose into speakable sentences, hard-capping very long ones.
    Sentence-ending punctuation covers Latin (.!?…), Indic danda/double-danda (।॥),
    Arabic/Urdu (۔؟) and CJK (。！？) full stops. */
export function splitSentences(text) {
  const clean = text.replace(/\s+/g, ' ').trim();
  if (!clean) return [];
  const raw = clean.match(/[^.!?…।॥۔؟。！？]+[.!?…।॥۔؟。！？]+["')\]]*\s*|[^.!?…।॥۔؟。！？]+$/g) || [clean];
  const out = [];
  for (let s of raw) {
    s = s.trim();
    if (!s) continue;
    while (s.length > MAX_SENTENCE_CHARS) {
      // prefer breaking at a comma/semicolon, then at a space
      let cut = -1;
      for (const re of [/[,;:]\s/g, /\s/g]) {
        let best = -1, m;
        while ((m = re.exec(s)) !== null) {
          if (m.index + 1 <= MAX_SENTENCE_CHARS) best = m.index + m[0].length; else break;
        }
        if (best > 40) { cut = best; break; }
      }
      if (cut === -1) cut = MAX_SENTENCE_CHARS;
      out.push(s.slice(0, cut).trim());
      s = s.slice(cut).trim();
    }
    if (s) out.push(s);
  }
  return out;
}

function buildSentences(blocks) {
  const sentences = [];
  blocks.forEach((blk, bi) => {
    for (const t of splitSentences(blk.text)) {
      sentences.push({ t, b: bi, p: blk.page, w: countWords(t) });
    }
  });
  return sentences;
}

/* ---------------- language detection ----------------
   Detects the dominant writing script of a book so the player can automatically
   pick a matching TTS voice (e.g. a Tamil book gets a Tamil voice even when the
   phone's UI language is English). Returns a BCP-47 base code. Note: scripts map
   to their most common language — Devanagari is reported as 'hi' (also covers
   Marathi/Sanskrit text; a hi-IN voice reads Devanagari either way). */

const SCRIPT_RANGES = [
  ['ta', /[஀-௿]/g], // Tamil
  ['kn', /[ಀ-೿]/g], // Kannada
  ['hi', /[ऀ-ॿ]/g], // Devanagari (Hindi, Marathi)
  ['te', /[ఀ-౿]/g], // Telugu
  ['ml', /[ഀ-ൿ]/g], // Malayalam
  ['bn', /[ঀ-৿]/g], // Bengali
  ['gu', /[઀-૿]/g], // Gujarati
  ['pa', /[਀-੿]/g], // Gurmukhi (Punjabi)
  ['or', /[଀-୿]/g], // Odia
  ['si', /[඀-෿]/g], // Sinhala
  ['ur', /[؀-ۿ]/g], // Arabic script (Urdu, Arabic)
  ['zh', /[一-鿿]/g], // CJK ideographs
  ['ja', /[぀-ヿ]/g], // Hiragana/Katakana
  ['ko', /[가-힯]/g], // Hangul
];

export function detectLanguage(text) {
  const sample = text.slice(0, 4000);
  let best = null, bestCount = 0;
  for (const [lang, re] of SCRIPT_RANGES) {
    const count = (sample.match(re) || []).length;
    if (count > bestCount) { best = lang; bestCount = count; }
  }
  const letters = (sample.match(/\p{L}/gu) || []).length;
  if (best && letters && bestCount / letters >= 0.2) return best;
  return 'en';
}

function blocksLanguage(blocks) {
  return detectLanguage(blocks.map((b) => b.text).join(' '));
}

/* ---------------- extraction quality check ----------------
   Many older Tamil/Hindi PDFs use legacy glyph-encoded fonts (TSCII, Bamini,
   Krutidev, …): the page LOOKS right because the embedded font paints the right
   shapes, but the underlying character codes are not Unicode, so extraction
   yields garbage like "ொகாZP|<க pPயா^!". Two reliable symptoms:
     1. Latin letters mixed INSIDE a word that contains Indic characters.
     2. Indic combining marks (vowel signs etc.) with no Indic base letter
        before them — impossible in real text.
   If a large share of Indic words show these symptoms, the source is flagged
   as glyph-encoded so the UI can fall back to the original page view. */

const INDIC_CHAR = /[ऀ-෿]/;

export function assessTextQuality(text) {
  const tokens = (text.match(/\S+/g) || []).slice(0, 3000);
  let indicTokens = 0;
  let suspicious = 0;
  for (const tok of tokens) {
    if (!INDIC_CHAR.test(tok)) continue;
    indicTokens++;
    let bad = /[A-Za-z]/.test(tok); // Latin glyph codes inside an Indic word
    if (!bad) {
      const chars = [...tok];
      for (let i = 0; i < chars.length; i++) {
        const c = chars[i];
        if (INDIC_CHAR.test(c) && /\p{M}/u.test(c)) {
          const prev = i > 0 ? chars[i - 1] : null;
          const validBase = prev && INDIC_CHAR.test(prev) && /[\p{L}\p{M}]/u.test(prev);
          if (!validBase) { bad = true; break; }
        }
      }
    }
    if (bad) suspicious++;
  }
  const suspiciousRatio = indicTokens ? suspicious / indicTokens : 0;
  return {
    indicTokens,
    suspiciousRatio,
    corrupted: indicTokens >= 20 && suspiciousRatio > 0.3,
  };
}

function blocksCorrupted(blocks) {
  return assessTextQuality(blocks.map((b) => b.text).join(' ')).corrupted;
}

/* ---------------- visual-order repair for Indic PDFs ----------------
   Many PDF producers (including Chromium's print-to-PDF) write Indic text in
   VISUAL order — the order glyphs are painted — rather than logical Unicode
   order. Pre-base vowel signs (Tamil ெ ே ை, Devanagari ி) are drawn to the
   LEFT of their consonant, so extraction yields e.g. "ெபான்" for "பொன்" and
   the two-part vowel ொ comes out as ெ…ா around the consonant. Readers like
   Google reorder this internally; we do the same:
     - a document is "visual order" if pre-base marks appear at word starts
       (impossible in logical text),
     - in that mode every pre-base mark is re-attached AFTER its following
       consonant, and split two-part vowels are recombined (ெ+ா→ொ, ே+ா→ோ,
       ெ+ௗ→ௌ). */

const PRE_BASE_MARK = /[\u0BC6-\u0BC8\u093F]/; // Tamil e/E/ai signs, Devanagari i sign
const TWO_PART_VOWEL = {
  '\u0BC6\u0BBE': '\u0BCA', // Tamil o
  '\u0BC7\u0BBE': '\u0BCB', // Tamil oo
  '\u0BC6\u0BD7': '\u0BCC', // Tamil au
};

export function needsVisualOrderFix(text) {
  const wordInitialMarks = text.match(/(?:^|\s)[\u0BC6-\u0BC8\u093F]/g);
  return !!wordInitialMarks && wordInitialMarks.length >= 2;
}

export function fixVisualOrder(text) {
  const chars = [...text];
  const out = [];
  for (let i = 0; i < chars.length; i++) {
    const c = chars[i];
    if (PRE_BASE_MARK.test(c)) {
      let j = i + 1;
      while (j < chars.length && chars[j] === ' ') j++;
      const cons = chars[j];
      if (cons && INDIC_CHAR.test(cons) && /\p{L}/u.test(cons)) {
        let k = j + 1;
        while (k < chars.length && chars[k] === ' ') k++;
        const combined = TWO_PART_VOWEL[c + (chars[k] || '')];
        if (combined) { out.push(cons, combined); i = k; }
        else { out.push(cons, c); i = j; }
        continue;
      }
    }
    out.push(c);
  }
  return out.join('');
}

/** Clean one extracted PDF text run: drop control chars from unmapped glyphs
    and spaces wrongly inserted before combining marks. */
function cleanExtractedRun(text) {
  return text
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\uFFFD]/g, '')
    .replace(/[ \t]+(?=\p{M})/gu, '');
}

/* ---------------- PDF ---------------- */

export async function extractPdf(file, onProgress) {
  const data = await file.arrayBuffer();
  const loadingTask = pdfjsLib.getDocument({ data, ...PDF_OPEN_OPTS });
  const doc = await loadingTask.promise;
  const blocks = [];
  let meta = { title: '', author: '' };
  try {
    const info = (await doc.getMetadata()).info || {};
    meta.title = (info.Title || '').trim();
    meta.author = (info.Author || '').trim();
  } catch { /* metadata optional */ }

  for (let p = 1; p <= doc.numPages; p++) {
    const page = await doc.getPage(p);
    const content = await page.getTextContent();
    let para = '';
    let lastY = null;
    let lastHeight = 0;
    const flush = () => {
      const text = cleanExtractedRun(para.replace(/\s+/g, ' ')).trim();
      if (text) blocks.push({ tag: 'p', text, page: p });
      para = '';
    };
    for (const item of content.items) {
      if (!('str' in item)) continue;
      const y = item.transform ? item.transform[5] : null;
      const h = item.height || lastHeight;
      // A vertical jump much bigger than the line height ⇒ new paragraph.
      if (lastY !== null && y !== null && Math.abs(lastY - y) > Math.max(h, lastHeight, 8) * 1.8) flush();
      if (item.str) {
        // De-hyphenate words broken across lines: "invest-" + "ment" → "investment"
        if (para.endsWith('-') && item.hasEOL === undefined) para = para.slice(0, -1);
        para += item.str;
      }
      if (item.hasEOL) {
        if (para.endsWith('-')) para = para.slice(0, -1); else para += ' ';
      }
      if (y !== null) lastY = y;
      if (item.height) lastHeight = item.height;
    }
    flush();
    if (onProgress) onProgress(p, doc.numPages);
  }

  // Decide once per document whether text came out in visual order, then repair.
  if (needsVisualOrderFix(blocks.map((b) => b.text).join(' '))) {
    for (const b of blocks) b.text = fixVisualOrder(b.text);
  }

  const cover = await renderPdfCover(doc);
  const title = meta.title || titleFromFilename(file.name);
  const sentences = buildSentences(blocks);
  const out = {
    title,
    author: meta.author || 'PDF document',
    type: 'pdf',
    lang: blocksLanguage(blocks),
    textCorrupted: blocksCorrupted(blocks),
    pages: doc.numPages,
    blocks,
    sentences,
    cover,
    wordCount: sentences.reduce((a, s) => a + s.w, 0),
  };
  await loadingTask.destroy();
  return out;
}

async function renderPdfCover(doc) {
  try {
    const page = await doc.getPage(1);
    const viewport = page.getViewport({ scale: 1 });
    const scale = 220 / viewport.width;
    const vp = page.getViewport({ scale });
    const canvas = document.createElement('canvas');
    canvas.width = Math.ceil(vp.width);
    canvas.height = Math.ceil(vp.height);
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    await page.render({ canvasContext: ctx, viewport: vp }).promise;
    return canvas.toDataURL('image/jpeg', 0.82);
  } catch {
    return null;
  }
}

/** Render one full-size page to a canvas for the "Original" reading view. */
export async function renderPdfPage(doc, pageNum, targetWidth) {
  const page = await doc.getPage(pageNum);
  const viewport = page.getViewport({ scale: 1 });
  const scale = (targetWidth / viewport.width) * (window.devicePixelRatio > 1 ? 1.5 : 1);
  const vp = page.getViewport({ scale });
  const canvas = document.createElement('canvas');
  canvas.width = Math.ceil(vp.width);
  canvas.height = Math.ceil(vp.height);
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  await page.render({ canvasContext: ctx, viewport: vp }).promise;
  return canvas;
}

/** High-resolution render for OCR. Unlike the reading view, this must NOT
    downsample: scanned books embed page images whose small marks (Tamil pulli,
    matras) vanish if shrunk. ~2200px width ≈ 260 DPI on A4 — what OCR wants. */
export async function renderPdfPageForOcr(doc, pageNum, targetWidth = 2200) {
  const page = await doc.getPage(pageNum);
  const viewport = page.getViewport({ scale: 1 });
  const vp = page.getViewport({ scale: targetWidth / viewport.width });
  const canvas = document.createElement('canvas');
  canvas.width = Math.ceil(vp.width);
  canvas.height = Math.ceil(vp.height);
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  await page.render({ canvasContext: ctx, viewport: vp }).promise;
  return canvas;
}

export async function openPdf(fileBlob) {
  const data = await fileBlob.arrayBuffer();
  return pdfjsLib.getDocument({ data, ...PDF_OPEN_OPTS }).promise;
}

/* ---------------- DOCX ---------------- */

export async function extractDocx(file) {
  if (typeof window.mammoth === 'undefined') throw new Error('Word converter not loaded');
  const arrayBuffer = await file.arrayBuffer();
  const result = await window.mammoth.convertToHtml({ arrayBuffer });
  const dom = new DOMParser().parseFromString(result.value, 'text/html');
  const blocks = [];
  const walk = (root) => {
    for (const el of root.children) {
      const tag = el.tagName.toLowerCase();
      if (/^h[1-6]$/.test(tag) || tag === 'p' || tag === 'blockquote') {
        const text = el.textContent.replace(/\s+/g, ' ').trim();
        if (text) blocks.push({ tag: /^h[1-6]$/.test(tag) ? tag : 'p', text, page: 1 });
      } else if (tag === 'ul' || tag === 'ol') {
        for (const li of el.querySelectorAll(':scope > li')) {
          const text = li.textContent.replace(/\s+/g, ' ').trim();
          if (text) blocks.push({ tag: 'li', text, page: 1 });
        }
      } else if (tag === 'table') {
        const text = el.textContent.replace(/\s+/g, ' ').trim();
        if (text) blocks.push({ tag: 'p', text, page: 1 });
      } else {
        walk(el);
      }
    }
  };
  walk(dom.body);
  if (!blocks.length) throw new Error('No readable text found in this document');

  const firstHeading = blocks.find((b) => b.tag.startsWith('h'));
  const title = (firstHeading && firstHeading.text.length <= 120 ? firstHeading.text : '') || titleFromFilename(file.name);
  const sentences = buildSentences(blocks);
  return {
    title,
    author: 'Word document',
    type: 'docx',
    lang: blocksLanguage(blocks),
    textCorrupted: blocksCorrupted(blocks),
    pages: 1,
    blocks,
    sentences,
    cover: makeGeneratedCover(title),
    wordCount: sentences.reduce((a, s) => a + s.w, 0),
  };
}

/* ---------------- Plain text / paste ---------------- */

export function extractPlainText(title, text, type = 'text') {
  const blocks = text
    .split(/\n{2,}|\r\n{2,}/)
    .map((p) => p.replace(/\s+/g, ' ').trim())
    .filter(Boolean)
    .map((t) => ({ tag: 'p', text: t, page: 1 }));
  if (!blocks.length) throw new Error('No text to read');
  const sentences = buildSentences(blocks);
  return {
    title: title || 'Pasted text',
    author: type === 'txt' ? 'Text file' : 'Pasted text',
    type,
    lang: blocksLanguage(blocks),
    textCorrupted: blocksCorrupted(blocks),
    pages: 1,
    blocks,
    sentences,
    cover: makeGeneratedCover(title || 'Pasted text'),
    wordCount: sentences.reduce((a, s) => a + s.w, 0),
  };
}

/* ---------------- Generated cover ---------------- */

const COVER_GRADIENTS = [
  ['#4f46e5', '#9333ea'],
  ['#0ea5e9', '#6366f1'],
  ['#a21caf', '#f43f5e'],
  ['#059669', '#0ea5e9'],
  ['#d97706', '#dc2626'],
];

export function makeGeneratedCover(title) {
  try {
    const canvas = document.createElement('canvas');
    canvas.width = 220;
    canvas.height = 300;
    const ctx = canvas.getContext('2d');
    let hash = 0;
    for (const ch of title) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
    const [c1, c2] = COVER_GRADIENTS[hash % COVER_GRADIENTS.length];
    const g = ctx.createLinearGradient(0, 0, 220, 300);
    g.addColorStop(0, c1);
    g.addColorStop(1, c2);
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 220, 300);
    ctx.fillStyle = 'rgba(255,255,255,0.92)';
    ctx.font = '600 26px system-ui, sans-serif';
    ctx.textBaseline = 'top';
    const words = title.split(/\s+/).slice(0, 12);
    let line = '', y = 28;
    const lines = [];
    for (const w of words) {
      const test = line ? line + ' ' + w : w;
      if (ctx.measureText(test).width > 180 && line) { lines.push(line); line = w; } else line = test;
      if (lines.length === 6) break;
    }
    if (line && lines.length < 7) lines.push(line);
    for (const l of lines) { ctx.fillText(l, 20, y); y += 34; }
    return canvas.toDataURL('image/jpeg', 0.85);
  } catch {
    return null;
  }
}

/* ---------------- Router ---------------- */

export async function extractFile(file, onProgress) {
  const name = file.name.toLowerCase();
  if (name.endsWith('.pdf') || file.type === 'application/pdf') return extractPdf(file, onProgress);
  if (name.endsWith('.docx') || file.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document') {
    return extractDocx(file);
  }
  if (name.endsWith('.doc')) {
    throw new Error('Legacy .doc files aren’t supported — please save the file as .docx and import again.');
  }
  if (name.endsWith('.txt') || file.type === 'text/plain') {
    const text = await file.text();
    return extractPlainText(titleFromFilename(file.name), text, 'txt');
  }
  throw new Error('Unsupported file type. Import a PDF, Word (.docx) or text (.txt) file.');
}
