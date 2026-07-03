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

const MAX_SENTENCE_CHARS = 280; // keep utterances short so TTS engines never truncate

export function countWords(text) {
  const m = text.match(/\S+/g);
  return m ? m.length : 0;
}

/** Split a block of prose into speakable sentences, hard-capping very long ones. */
export function splitSentences(text) {
  const clean = text.replace(/\s+/g, ' ').trim();
  if (!clean) return [];
  const raw = clean.match(/[^.!?…]+[.!?…]+["')\]]*\s*|[^.!?…]+$/g) || [clean];
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

function titleFromFilename(name) {
  return name.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' ').trim() || name;
}

/* ---------------- PDF ---------------- */

export async function extractPdf(file, onProgress) {
  const data = await file.arrayBuffer();
  const loadingTask = pdfjsLib.getDocument({ data });
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
      const text = para.replace(/\s+/g, ' ').trim();
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

  const cover = await renderPdfCover(doc);
  const title = meta.title || titleFromFilename(file.name);
  const sentences = buildSentences(blocks);
  const out = {
    title,
    author: meta.author || 'PDF document',
    type: 'pdf',
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

export async function openPdf(fileBlob) {
  const data = await fileBlob.arrayBuffer();
  return pdfjsLib.getDocument({ data }).promise;
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
