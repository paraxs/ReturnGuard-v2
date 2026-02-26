const VERSION = 'fix9.0';
const CACHE = 'returnguard-cache-' + VERSION;
const DB_NAME = 'returnguard_data';
const DB_VER = 5;
const PRECACHE_URLS = ['./'];
let iconDataUrl = '';

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    for (const url of PRECACHE_URLS){
      try{ await cache.add(url); }catch(e){}
    }
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys
      .filter((key) => key.startsWith('returnguard-cache-') && key !== CACHE)
      .map((key) => caches.delete(key)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;
  event.respondWith((async () => {
    const cached = await caches.match(event.request);
    try{
      const resp = await fetch(event.request);
      const cache = await caches.open(CACHE);
      cache.put(event.request, resp.clone());
      return resp;
    }catch(e){
      if (cached) return cached;
      throw e;
    }
  })());
});

function idbOpen(){
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VER);
    req.onupgradeneeded = (e) => {
      const db = e.target.result;
      if (!db.objectStoreNames.contains('kv')){
        db.createObjectStore('kv', { keyPath: 'key' });
      }
      if (!db.objectStoreNames.contains('files')){
        const files = db.createObjectStore('files', { keyPath: 'id' });
        files.createIndex('by_item', 'itemId', { unique: false });
        files.createIndex('by_item_created', ['itemId', 'createdAt'], { unique: false });
      }
    };
    req.onsuccess = (e) => resolve(e.target.result);
    req.onerror = (e) => reject(e.target.error);
  });
}

async function kvGet(key){
  const db = await idbOpen();
  return await new Promise((resolve, reject) => {
    const tx = db.transaction('kv', 'readonly');
    const req = tx.objectStore('kv').get(key);
    req.onsuccess = () => resolve(req.result ? req.result.value : null);
    req.onerror = () => reject(req.error);
  });
}

async function kvPut(key, value){
  const db = await idbOpen();
  return await new Promise((resolve, reject) => {
    const tx = db.transaction('kv', 'readwrite');
    const req = tx.objectStore('kv').put({ key, value, updatedAt: new Date().toISOString() });
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}

function pad(n){ return String(n).padStart(2, '0'); }
function todayISO(){
  const d = new Date();
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
}
function mkDate(iso){ return new Date(iso + 'T12:00:00'); }
function addDays(iso, days){
  const d = mkDate(iso);
  d.setDate(d.getDate() + (days || 0));
  return d.toISOString().slice(0, 10);
}
function daysBetween(aISO, bISO){
  const a = mkDate(aISO);
  const b = mkDate(bISO);
  return Math.round((b - a) / 86400000);
}

function pruneNotified(map){
  const out = {};
  const now = todayISO();
  for (const key in map){
    const due = key.split(':')[1];
    if (!due) continue;
    const age = daysBetween(due, now);
    if (age >= -30 && age <= 14) out[key] = true;
  }
  return out;
}

async function checkDueAndNotify(force){
  const state = await kvGet('state');
  if (!state || !Array.isArray(state.items)) return;
  const now = todayISO();
  let notified = (await kvGet('notified')) || {};
  let changed = false;
  for (const it of state.items){
    if (!it || it.archived || !it.returnDays || !it.date) continue;
    const due = addDays(it.date, it.returnDays);
    const days = daysBetween(now, due);
    if (days < 0) continue;
    if (days > 3 && !force) continue;
    const key = String(it.id || '') + ':' + due;
    if (notified[key] && !force) continue;
    const title = (days === 0 ? 'Heute letzter Rückgabetag' : days === 1 ? 'Morgen letzter Rückgabetag' : 'Rückgabe in ' + days + ' Tagen');
    const body = (it.name || 'Produkt') + (it.store ? ' • ' + it.store : '');
    const options = { body, tag: key, data: { itemId: it.id, due } };
    if (iconDataUrl){
      options.icon = iconDataUrl;
      options.badge = iconDataUrl;
    }
    try{
      await self.registration.showNotification(title, options);
      notified[key] = true;
      changed = true;
    }catch(e){}
  }
  if (changed){
    await kvPut('notified', pruneNotified(notified));
  }
}

self.addEventListener('periodicsync', (event) => {
  if (event.tag === 'returnguard-due-check'){
    event.waitUntil(checkDueAndNotify(false));
  }
});

self.addEventListener('sync', (event) => {
  if (event.tag === 'returnguard-sync'){
    event.waitUntil(checkDueAndNotify(false));
  }
});

self.addEventListener('message', (event) => {
  const data = event.data || {};
  if (data.type === 'SET_ICON'){
    if (typeof data.iconDataUrl === 'string' && data.iconDataUrl.startsWith('data:image/')){
      iconDataUrl = data.iconDataUrl;
    }
    return;
  }
  if (data.type === 'STATE_MIRROR' && data.state){
    event.waitUntil(kvPut('state', data.state));
    return;
  }
  if (data.type === 'CHECK_NOW'){
    event.waitUntil(checkDueAndNotify(true));
  }
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil((async () => {
    const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    if (clients && clients.length){
      const client = clients[0];
      await client.focus();
      client.postMessage({ type: 'FOCUS_ITEM', itemId: event.notification.data && event.notification.data.itemId });
    }
  })());
});
