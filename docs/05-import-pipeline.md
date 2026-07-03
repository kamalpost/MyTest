# 5. The Import Pipeline (`www/js/extract.js`)

Goal: turn *any supported file* into the one Book shape described in doc 3. The entry
point routes by file type:

```js
export async function extractFile(file, onProgress) {
  if (…pdf)   return extractPdf(file, onProgress);
  if (…docx)  return extractDocx(file);
  if (…doc)   throw new Error('Legacy .doc files aren’t supported — save as .docx…');
  if (…txt)   return extractPlainText(title, await file.text(), 'txt');
  throw new Error('Unsupported file type. Import a PDF, Word (.docx) or text (.txt) file.');
}
```

Every branch ends the same way: build `blocks` (paragraphs), split them into `sentences`,
make a `cover`, count words. Errors are thrown with **human-readable messages** because
`app.js` shows them directly in a toast.

## Vendored libraries (in `www/vendor/`)

The two heavy lifters are checked into the repo so the app works offline and builds
reproducibly — no CDN calls at runtime:

| Library | File | Used for |
|---------|------|----------|
| **pdf.js** v5 (legacy build) | `pdf.min.mjs` + `pdf.worker.min.mjs` | Reading PDF text + rendering pages to canvas |
| **mammoth** 1.12 | `mammoth.browser.min.js` | Converting .docx to clean HTML |

> Why the *legacy* pdf.js build? pdf.js v6 uses brand-new JavaScript features
> (`Map.getOrInsertComputed`) that many Android WebViews don't have yet. The v5 legacy
> build targets older engines — this was found the hard way during testing.

pdf.js runs its parsing in a **Web Worker** (a background thread), which is why there are
two files; the worker path is wired at the top of `extract.js`.

## PDF extraction (`extractPdf`) — the tricky one

PDFs don't contain paragraphs — they contain *positioned text fragments* ("items") with
x/y coordinates. `getTextContent()` returns something like:

```
{ str: "The beginning of", transform: [..., x, y], height: 13, hasEOL: false }
{ str: " every journey",   transform: [..., x, y], ... }
```

The code reassembles prose with two heuristics:

1. **Paragraph detection:** if the vertical gap between one item and the next is much
   bigger than a line height (`> lineHeight × 1.8`), flush the current paragraph and start
   a new block.
2. **De-hyphenation:** a line ending in `-` followed by more text is a word broken across
   lines (`invest-` + `ment`), so the hyphen is removed when joining.

Each block remembers its **page number**, which powers the "Page N" dividers and the
original-view auto-scroll. Metadata (`Title`, `Author`) is read via `getMetadata()`,
falling back to a prettified filename.

Two more pdf.js functions live here because the reader needs them later:

- `renderPdfCover(doc)` — draws page 1 at 220 px wide onto a canvas → JPEG data URL.
- `renderPdfPage(doc, n, width)` / `openPdf(blob)` — used by the reader's original view.

## DOCX extraction (`extractDocx`)

Much easier, because mammoth does the format work:

```
.docx file → mammoth.convertToHtml() → "<h1>Title</h1><p>…</p><ul><li>…</li></ul>"
           → DOMParser → walk the elements → blocks
```

The walker keeps `h1–h6` as headings (so the reader can render them bold/large),
flattens list items into `li` blocks, and treats everything else as paragraphs. The
book's title is the **first heading** if there is one, else the filename. Word files get a
**generated cover** (below) since there is no page to render.

> `.doc` (the 1997 binary format) is intentionally rejected with a clear message —
> mammoth only reads `.docx`.

## Plain text & pasted text (`extractPlainText`)

Splits on blank lines into paragraphs. Used for `.txt` files, the paste-text sheet, and
the built-in sample book.

## Sentence splitting (`splitSentences`) — the most reused function

```js
const raw = clean.match(/[^.!?…]+[.!?…]+["')\]]*\s*|[^.!?…]+$/g) || [clean];
```

In words: *grab runs of text up to and including sentence-ending punctuation (plus any
closing quotes/brackets), and whatever trails at the end without punctuation.*

Then a hard safety cap: any sentence longer than **280 characters** is chopped at the
last comma/semicolon (or space) before the limit. This guarantees no utterance is long
enough to hit TTS engine limits (doc 4). Each final sentence gets its word count `w`.

It's deliberately simple — it will split "Dr. Smith" into two pieces. That's an accepted
trade-off; see doc 7 for how you'd improve it.

## Covers (`makeGeneratedCover`)

For books without a visual page (Word/text), a 220×300 canvas is painted with a gradient
picked **deterministically from a hash of the title** (same title → same colors) and the
title text word-wrapped on top. Exported as a JPEG data URL, it's used in book cards, the
mini player, and as the media-notification artwork.

## Error handling philosophy

`extractFile` throws; `importFile` in `app.js` catches, hides the spinner, and shows the
message in a toast. So: **to add a new failure mode, just `throw new Error('message the
user should read')`** anywhere in the pipeline.
