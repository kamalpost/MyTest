/* IndexedDB wrapper — stores books (with original file blob + extracted text) and settings. */

const DB_NAME = 'voxreader';
const DB_VERSION = 1;

let dbPromise = null;

function open() {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('books')) {
        const books = db.createObjectStore('books', { keyPath: 'id' });
        books.createIndex('lastOpenedAt', 'lastOpenedAt');
        books.createIndex('addedAt', 'addedAt');
      }
      if (!db.objectStoreNames.contains('settings')) {
        db.createObjectStore('settings', { keyPath: 'key' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbPromise;
}

function tx(db, store, mode, fn) {
  return new Promise((resolve, reject) => {
    const t = db.transaction(store, mode);
    const s = t.objectStore(store);
    const out = fn(s);
    t.oncomplete = () => resolve(out && out.result !== undefined ? out.result : undefined);
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error || new Error('transaction aborted'));
  });
}

export async function putBook(book) {
  const db = await open();
  await tx(db, 'books', 'readwrite', (s) => s.put(book));
  return book;
}

export async function getBook(id) {
  const db = await open();
  return new Promise((resolve, reject) => {
    const req = db.transaction('books').objectStore('books').get(id);
    req.onsuccess = () => resolve(req.result || null);
    req.onerror = () => reject(req.error);
  });
}

/** Returns all books WITHOUT the heavy fields (file blob, sentences) — for lists. */
export async function listBooks() {
  const db = await open();
  return new Promise((resolve, reject) => {
    const req = db.transaction('books').objectStore('books').getAll();
    req.onsuccess = () => {
      const light = (req.result || []).map((b) => {
        const { file, sentences, blocks, ...rest } = b;
        return rest;
      });
      resolve(light);
    };
    req.onerror = () => reject(req.error);
  });
}

export async function deleteBook(id) {
  const db = await open();
  await tx(db, 'books', 'readwrite', (s) => s.delete(id));
}

/** Patch a few fields on a stored book without rewriting blobs from memory. */
export async function updateBook(id, patch) {
  const db = await open();
  return new Promise((resolve, reject) => {
    const t = db.transaction('books', 'readwrite');
    const s = t.objectStore('books');
    const req = s.get(id);
    req.onsuccess = () => {
      const book = req.result;
      if (!book) { resolve(null); return; }
      Object.assign(book, patch);
      s.put(book);
      t.oncomplete = () => resolve(book);
    };
    req.onerror = () => reject(req.error);
    t.onerror = () => reject(t.error);
  });
}

export async function getSetting(key, fallback = null) {
  const db = await open();
  return new Promise((resolve, reject) => {
    const req = db.transaction('settings').objectStore('settings').get(key);
    req.onsuccess = () => resolve(req.result ? req.result.value : fallback);
    req.onerror = () => reject(req.error);
  });
}

export async function setSetting(key, value) {
  const db = await open();
  await tx(db, 'settings', 'readwrite', (s) => s.put({ key, value }));
}

export async function storageEstimate() {
  if (navigator.storage && navigator.storage.estimate) {
    try { return await navigator.storage.estimate(); } catch { /* ignore */ }
  }
  return null;
}

export async function requestPersistence() {
  if (navigator.storage && navigator.storage.persist) {
    try { return await navigator.storage.persist(); } catch { return false; }
  }
  return false;
}
