# 8. Language Support (Tamil, Kannada, Hindi, …)

VoxReader can read books in any language **for which the device has a TTS voice**. This
doc explains what the app does automatically, what depends on the phone, and how the
pieces fit together (added in v1.1).

## What the app does automatically

### 1. Script detection at import (`extract.js → detectLanguage`)

When a book is imported, the extractor samples its text and counts characters per Unicode
script block (Tamil `U+0B80–0BFF`, Kannada `U+0C80–0CFF`, Devanagari `U+0900–097F`,
Telugu, Malayalam, Bengali, Gujarati, Gurmukhi, Odia, Sinhala, Arabic/Urdu, CJK, Hangul).
If one script makes up ≥ 20% of the letters, the book gets a language code:

```js
book.lang = 'ta'   // Tamil book — stored with the book, shown in the Library
```

Devanagari is reported as `hi` — a `hi-IN` voice reads Devanagari text regardless of
whether it is Hindi or Marathi.

> Books imported **before** v1.1 have no `lang` field and simply keep the old behavior.
> Re-import them to get language detection.

### 2. Sentence splitting understands Indic punctuation (`splitSentences`)

Hindi (and Sanskrit/Marathi) end sentences with the danda **।** and double danda **॥**,
not a period. The sentence regex now treats `। ॥` (plus Urdu `۔ ؟` and CJK `。！？`) as
sentence endings, so Hindi paragraphs split into proper sentences — otherwise a whole
paragraph would be one giant utterance and highlighting/seeking would be useless.
Tamil and Kannada mostly use the Latin period, which already worked.

### 3. Automatic voice matching (`player.js → getVoice`)

The voice picked for playback follows this priority:

1. **Your saved choice for the book's language** — the app remembers one voice *per
   language* (`voiceMap` setting: `{ ta: …, hi: …, en: … }`). Pick a Tamil voice once
   while reading a Tamil book and every Tamil book uses it from then on.
2. **Your global voice choice**, but only if it can actually speak the book's language.
   (Your English voice will never be used to mangle a Tamil book.)
3. **Any installed voice matching the book's language** — offline voices preferred.
4. Device language → English → any voice (for undetected/English books).

On Android there is one extra fallback: if no exact voice id matches, the native plugin
calls `tts.setLanguage(book.lang)` so the engine at least switches language
(`NativeTTS.configure`).

### 4. Voice sheet niceties (`app.js → renderVoiceList`)

- The **book's language group is listed first**, then the device language, then A–Z.
- If the book's language has **no installed voice**, the sheet says so and tells you how
  to install one (see below) instead of silently reading with the wrong voice.
- The Library shows each non-English book's detected language next to its type badge.

## What depends on the phone

The app does not ship voices — they come from the device's TTS engine. On Android the
usual engine is **Speech Services by Google**, which has high-quality offline voices for
**Hindi (hi-IN), Tamil (ta-IN), Kannada (kn-IN)**, Telugu, Malayalam, Bengali, Gujarati,
Marathi, Urdu and more.

To install/verify voices on Android:

```
Settings → System → Languages → Text-to-speech output
  → Preferred engine: Speech Services by Google
  → ⚙ → Install voice data → pick Tamil / Kannada / Hindi
```

Once installed, the voices appear in VoxReader's voice sheet (marked *offline*) with a
tap-to-preview. In the browser/PWA the voice list instead comes from the OS/browser
(Chrome on Android exposes the same Google voices).

## About the source documents

- **Word/.docx and pasted text**: Unicode Indic text just works.
- **PDFs**: quality depends on how the PDF was made. Text-based PDFs with proper Unicode
  encoding extract fine. Two known problem classes:
  - *Scanned/image PDFs* have no text at all → nothing to read (an OCR step would be a
    big but valuable future enhancement).
  - *Legacy glyph-encoded PDFs* (old Tamil/Hindi fonts like TSCII or Krutidev that fake
    the script with custom glyph codes) extract as garbage — that's a property of the
    file, not fixable at read time. Re-exporting the document with Unicode fonts fixes it.

## Limitations & future work

- **Time estimates** use one words-per-minute constant (170) for all languages; Indic
  languages tend to have longer words, so "minutes remaining" runs slightly optimistic.
  A per-language WPM table in `player.js` would refine this.
- **One language per book**: detection picks the dominant script. Mixed-language books
  are read entirely with the dominant language's voice.
- **The app UI itself is English.** Localizing the interface (Tamil/Hindi menus) is a
  separate task: extract the strings in `index.html`/`app.js` into a dictionary and
  swap by `navigator.language`. The reading experience doesn't depend on it.

## How it's tested

`test/e2e.js` section 7b pastes a Hindi passage and asserts: danda splitting produces
multiple sentences, `book.lang === 'hi'`, the stubbed `hi-IN` voice is auto-picked *over*
the user's explicitly chosen English voice, and the voice sheet lists Hindi first.
