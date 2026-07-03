// Creates test fixtures: a 3-page PDF (pdf-lib) and a minimal valid DOCX (zip built by hand via Python is avoided; we use the zip format through pdf-lib? no — we write the docx with a tiny store-only zip writer below).
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const { PDFDocument, StandardFonts, rgb } = require('pdf-lib');

const OUT = process.argv[2] || __dirname;

/* ---------- PDF ---------- */
async function makePdf() {
  const doc = await PDFDocument.create();
  doc.setTitle('The Art of Testing');
  doc.setAuthor('Vox Fixture');
  const font = await doc.embedFont(StandardFonts.Helvetica);
  const pages = [
    [
      'Chapter One. The beginning of every journey is a single step.',
      'Testing software is much like proofreading a book. Every sentence must be heard aloud to know that it truly works.',
      'This page exists to verify that PDF text extraction handles multiple paragraphs correctly.',
    ],
    [
      'Chapter Two. The middle of the story matters most.',
      'A good reader keeps their place. When you leave and come back, the book should open exactly where you stopped listening.',
    ],
    [
      'Chapter Three. All endings are new beginnings.',
      'If you can hear this final page, the whole pipeline works: import, extraction, playback and progress tracking.',
    ],
  ];
  for (const paras of pages) {
    const page = doc.addPage([595, 842]);
    let y = 780;
    for (const p of paras) {
      // naive wrap at ~80 chars
      const words = p.split(' ');
      let line = '';
      for (const w of words) {
        if ((line + ' ' + w).length > 78) {
          page.drawText(line, { x: 60, y, size: 13, font, color: rgb(0.1, 0.1, 0.12) });
          y -= 20;
          line = w;
        } else line = line ? line + ' ' + w : w;
      }
      if (line) { page.drawText(line, { x: 60, y, size: 13, font, color: rgb(0.1, 0.1, 0.12) }); y -= 20; }
      y -= 14;
    }
  }
  fs.writeFileSync(path.join(OUT, 'fixture.pdf'), await doc.save());
  console.log('wrote fixture.pdf');
}

/* ---------- DOCX (minimal zip, stored entries) ---------- */
function crc32(buf) {
  let table = crc32.table;
  if (!table) {
    table = crc32.table = new Int32Array(256);
    for (let i = 0; i < 256; i++) {
      let c = i;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      table[i] = c;
    }
  }
  let crc = -1;
  for (let i = 0; i < buf.length; i++) crc = (crc >>> 8) ^ table[(crc ^ buf[i]) & 0xff];
  return (crc ^ -1) >>> 0;
}

function makeZip(entries) {
  const chunks = [];
  const central = [];
  let offset = 0;
  for (const [name, content] of entries) {
    const data = Buffer.from(content, 'utf8');
    const nameBuf = Buffer.from(name, 'utf8');
    const crc = crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26);
    chunks.push(local, nameBuf, data);
    const cd = Buffer.alloc(46);
    cd.writeUInt32LE(0x02014b50, 0);
    cd.writeUInt16LE(20, 6);
    cd.writeUInt32LE(crc, 16);
    cd.writeUInt32LE(data.length, 20);
    cd.writeUInt32LE(data.length, 24);
    cd.writeUInt16LE(nameBuf.length, 28);
    cd.writeUInt32LE(offset, 42);
    central.push(cd, nameBuf);
    offset += local.length + nameBuf.length + data.length;
  }
  const cdBuf = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(cdBuf.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...chunks, cdBuf, end]);
}

function makeDocx() {
  const contentTypes = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>`;
  const rels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>`;
  const p = (t, style) => `<w:p>${style ? `<w:pPr><w:pStyle w:val="${style}"/></w:pPr>` : ''}<w:r><w:t xml:space="preserve">${t}</w:t></w:r></w:p>`;
  const document = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
${p('Meditations on Word Files', 'Heading1')}
${p('This document proves that Word import works. Mammoth converts it to clean text on the device, with no server involved.')}
${p('Second paragraph here. It contains two sentences. Both should become separate audio segments.')}
${p('A Second Heading', 'Heading2')}
${p('The final paragraph brings the word count up and lets the reader estimate a listening time.')}
</w:body></w:document>`;
  const zip = makeZip([
    ['[Content_Types].xml', contentTypes],
    ['_rels/.rels', rels],
    ['word/document.xml', document],
  ]);
  fs.writeFileSync(path.join(OUT, 'fixture.docx'), zip);
  console.log('wrote fixture.docx');
}

makePdf().then(makeDocx);
