/* On-device OCR for glyph-encoded / scanned PDFs.
   Uses tesseract.js (WASM, vendored in vendor/ocr — fully offline) to read the
   *rendered pages* of a PDF, the same way Google Docs handles such files.

   Design constraints this module honours:
   - Books can be huge (1000+ pages) and phones are slow (~2–6 s/page), so OCR
     runs page-by-page with progress persisted to the book record every few
     pages. The user can stop anytime and resume later; nothing is lost.
   - The book's real text (blocks/sentences) is only REPLACED when OCR reaches
     the last page, so a half-finished run never leaves a half-broken book.

   Book fields used:
     ocrPages:  { [pageNumber]: "recognized text" }  — accumulated results
     ocrNext:   next page number to process (resume point)
     ocrLang:   tesseract language used (e.g. "tam")
     ocrDone:   true once the text has been swapped in                     */

import { openPdf, renderPdfPage, splitSentences, countWords, detectLanguage, assessTextQuality } from './extract.js';
import * as db from './db.js';

// book.lang (BCP-47 base) → tesseract traineddata name (vendor/ocr/lang/*.traineddata.gz)
const TESS_LANG = { ta: 'tam', kn: 'kan', hi: 'hin', en: 'eng' };

export function ocrSupported() {
  return typeof WebAssembly !== 'undefined';
}

export function tessLangFor(book) {
  return TESS_LANG[(book.lang || 'en').split('-')[0]] || 'eng';
}

let Tesseract = null;
async function loadTesseract() {
  if (!Tesseract) {
    const mod = await import('../vendor/ocr/tesseract.esm.min.js');
    Tesseract = mod.createWorker ? mod : mod.default; // bundle exposes a default export
  }
  return Tesseract;
}

async function makeWorker(lang) {
  const T = await loadTesseract();
  const base = new URL('../vendor/ocr/', import.meta.url).toString();
  return T.createWorker(lang, 1 /* LSTM only */, {
    workerPath: base + 'worker.min.js',
    corePath: base + 'tesseract-core-simd-lstm.wasm.js',
    langPath: base + 'lang/',
    gzip: true,
    workerBlobURL: false,
  });
}

/** Run (or resume) OCR over a book's PDF pages.
    onProgress({ page, total, text }) fires after each recognized page.
    Returns { completed, nextPage }. Stop by calling controller.stop(). */
export function createOcrJob(book) {
  let stopped = false;
  const job = {
    stop() { stopped = true; },
    async run(onProgress) {
      if (!book.file) throw new Error('The original PDF is needed for OCR');
      const lang = book.ocrLang || tessLangFor(book);
      const worker = await makeWorker(lang);
      const doc = await openPdf(book.file);
      try {
        const total = doc.numPages;
        const pages = book.ocrPages || {};
        let page = book.ocrNext || 1;
        for (; page <= total; page++) {
          if (stopped) break;
          // ~1600px wide renders give tesseract enough detail without exhausting memory
          const canvas = await renderPdfPage(doc, page, 1100);
          const { data } = await worker.recognize(canvas);
          canvas.width = canvas.height = 0; // release bitmap memory promptly
          pages[page] = (data.text || '').trim();
          const patch = { ocrPages: pages, ocrNext: page + 1, ocrLang: lang };
          if (page % 3 === 0 || page === total) await db.updateBook(book.id, patch);
          Object.assign(book, patch);
          if (onProgress) onProgress({ page, total, text: pages[page] });
        }
        const completed = page > total;
        if (completed) await finalizeOcr(book);
        else await db.updateBook(book.id, { ocrPages: pages, ocrNext: page, ocrLang: lang });
        return { completed, nextPage: page };
      } finally {
        try { await worker.terminate(); } catch { /* already gone */ }
        try { await doc.destroy?.(); } catch { /* pdf.js version differences */ }
      }
    },
  };
  return job;
}

/** Swap the book's garbled text for the recognized text. */
async function finalizeOcr(book) {
  const blocks = [];
  const pageNums = Object.keys(book.ocrPages).map(Number).sort((a, b) => a - b);
  for (const p of pageNums) {
    for (const para of (book.ocrPages[p] || '').split(/\n{2,}/)) {
      const text = para.replace(/\s+/g, ' ').trim();
      if (text.length > 1) blocks.push({ tag: 'p', text, page: p });
    }
  }
  if (!blocks.length) throw new Error('OCR found no readable text in this book');

  const sentences = [];
  blocks.forEach((blk, bi) => {
    for (const t of splitSentences(blk.text)) {
      sentences.push({ t, b: bi, p: blk.page, w: countWords(t) });
    }
  });
  const joined = blocks.map((b) => b.text).join(' ');
  await db.updateBook(book.id, {
    blocks,
    sentences,
    sentenceCount: sentences.length,
    wordCount: sentences.reduce((a, s) => a + s.w, 0),
    lang: detectLanguage(joined),
    textCorrupted: assessTextQuality(joined).corrupted, // normally false now
    ocrDone: true,
    ocrPages: null, // free the per-page copies; text now lives in blocks/sentences
    cursor: 0,
  });
}

/** Human label for the OCR language actually used. */
export function ocrLangLabel(lang) {
  return { tam: 'Tamil', kan: 'Kannada', hin: 'Hindi', eng: 'English' }[lang] || lang;
}
