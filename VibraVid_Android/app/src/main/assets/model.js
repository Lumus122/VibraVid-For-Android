'use strict';
/* Pure library rules, shared by the phone UI and its regression tests. */
(function (global) {
  const kinds = ['movie', 'series', 'anime', 'song', 'album', 'book', 'live'];
  const layouts = [
    {id:'classic', name:'Classico', note:'La disposizione che conosci.'},
    {id:'compact', name:'Compatto', note:'Più titoli, meno spazio tra gli elementi.'},
    {id:'posters', name:'Panoramico', note:'Titoli centrati e sezioni ampie.'},
    {id:'list', name:'Elenco', note:'Titoli in righe, informazioni a fianco.'},
    {id:'panels', name:'Pannelli', note:'Sezioni e schede ben separate.'},
    {id:'focus', name:'Focus', note:'Navigazione in alto, contenuti raccolti.'},
    {id:'editorial', name:'Editoriale', note:'Titoli grandi, righe e colonne ordinate.'},
    {id:'studio', name:'Studio', note:'Navigazione laterale e aree operative.'},
    {id:'soft', name:'Morbido', note:'Sezioni arrotondate e controlli ariosi.'},
    {id:'minimal', name:'Essenziale', note:'Superfici pulite, pochi bordi e spazi misurati.'}
  ];
  const palettes = [
    {id:'graphite', name:'Grafite', note:'Carbone e perla', colors:{paper:'#111215',surface:'#1e2025',soft:'#2b2e35',raised:'#30333b',recess:'#16181d',ink:'#f3f2ef',muted:'#bec0c6',line:'#454850',accent:'#e4e2dc',cinemaPaper:'#08090b',cinemaSurface:'#17191d',cinemaSoft:'#252830'}},
    {id:'ocean', name:'Oceano', note:'Ardesia e azzurro', colors:{paper:'#10151c',surface:'#1b2531',soft:'#283647',raised:'#2d3e50',recess:'#141c26',ink:'#edf3f8',muted:'#b8c8d8',line:'#52667b',accent:'#b2d1ef',cinemaPaper:'#070b10',cinemaSurface:'#141c26',cinemaSoft:'#233141'}},
    {id:'sage', name:'Salvia', note:'Antracite e verde tenue', colors:{paper:'#111714',surface:'#1e2923',soft:'#2b3a31',raised:'#304238',recess:'#161f1a',ink:'#f0f5ef',muted:'#bdcdbf',line:'#536d5d',accent:'#bed5bf',cinemaPaper:'#080d0a',cinemaSurface:'#17211b',cinemaSoft:'#25352b'}},
    {id:'amber', name:'Ambra', note:'Fumo e sabbia', colors:{paper:'#181410',surface:'#2b241d',soft:'#3c3228',raised:'#453a2f',recess:'#211b15',ink:'#f7f1e8',muted:'#d0c2b2',line:'#76634f',accent:'#e2c8a6',cinemaPaper:'#0e0a07',cinemaSurface:'#211a14',cinemaSoft:'#34291f'}},
    {id:'mauve', name:'Malva', note:'Grafite e lilla tenue', colors:{paper:'#171219',surface:'#281f2d',soft:'#392c40',raised:'#413449',recess:'#201924',ink:'#f5eff8',muted:'#cbbfd2',line:'#705b7c',accent:'#d6bfdf',cinemaPaper:'#0c080e',cinemaSurface:'#1f1724',cinemaSoft:'#312538'}},
    {id:'paper', name:'Carta', note:'Bianco e blu deciso', light:true, nightAccent:'#b2d1ef', colors:{paper:'#f5f7fb',surface:'#ffffff',soft:'#e5ebf4',raised:'#dae3f0',recess:'#eef2f8',ink:'#172337',muted:'#44546c',line:'#a0aec2',accent:'#194d91'}},
    {id:'cream', name:'Crema', note:'Avorio e terracotta', light:true, nightAccent:'#e2c8a6', colors:{paper:'#faf4e9',surface:'#fffaf2',soft:'#f0e4d3',raised:'#eadcc6',recess:'#f5ecdf',ink:'#35251b',muted:'#62503e',line:'#b5a18a',accent:'#863c26'}},
    {id:'lagoon', name:'Laguna', note:'Menta e turchese', light:true, nightAccent:'#b3ddd2', colors:{paper:'#eef8f5',surface:'#ffffff',soft:'#dcefe8',raised:'#d1e8e0',recess:'#e6f3ee',ink:'#123b35',muted:'#3e6058',line:'#8caea2',accent:'#14635b'}},
    {id:'rose', name:'Rosa', note:'Rosa chiaro e lampone', light:true, nightAccent:'#edc0cd', colors:{paper:'#fff2f5',surface:'#fffafb',soft:'#f6e2e9',raised:'#efd7e1',recess:'#fbeaf0',ink:'#442234',muted:'#6b4659',line:'#bd9baa',accent:'#913454'}},
    {id:'lavender', name:'Lavanda', note:'Lilla chiaro e indaco', light:true, nightAccent:'#d0c4f1', colors:{paper:'#f4f1fc',surface:'#fdfbff',soft:'#e8e1f6',raised:'#dfd6ef',recess:'#efebf8',ink:'#302344',muted:'#584b6b',line:'#a99dbf',accent:'#5b3a91'}}
  ];
  const appearance = prefs => ({
    layout: layouts.some(option => option.id === (prefs || {}).layout) ? prefs.layout : 'classic',
    palette: palettes.some(option => option.id === (prefs || {}).palette) ? prefs.palette : 'graphite'
  });
  const paletteColors = prefs => {
    const palette=palettes.find(option => option.id === appearance(prefs).palette);
    let colors = {...palette.colors};
    if (prefs && prefs.theme === 'cinema') {
      if(palette.light){colors={...palettes[0].colors};colors.accent=palette.nightAccent;}
      colors.paper=colors.cinemaPaper; colors.surface=colors.cinemaSurface; colors.soft=colors.cinemaSoft;
    }
    delete colors.cinemaPaper; delete colors.cinemaSurface; delete colors.cinemaSoft;
    const light=palette.light && (!prefs || prefs.theme!=='cinema');
    colors['on-accent']=light?'#ffffff':colors.paper;
    colors['button-start']=light?colors.accent:colors.ink;
    return colors;
  };
  const lightPalette = prefs => palettes.find(option=>option.id===appearance(prefs).palette).light===true && (!prefs || prefs.theme!=='cinema');
  const clone = value => JSON.parse(JSON.stringify(value));
  let marks=/[\u0300-\u036f]/g, titleSeparators=/[\s\u0021-\u002f\u003a-\u0040\u005b-\u0060\u007b-\u007e\u2000-\u206f]/g;
  try { marks=new RegExp('\\p{M}','gu');titleSeparators=new RegExp('[^\\p{L}\\p{N}]','gu'); } catch {}
  const normalizedTitle = name => String(name || '').toLocaleLowerCase('it').normalize('NFD').replace(marks,'').replace(titleSeparators,'');
  const text = (value, limit, required = false) => {
    if (typeof value !== 'string' || value.length > limit || (required && !value.trim())) throw new Error('Controlla i campi: un testo è vuoto o troppo lungo.');
    return value.trim();
  };
  const integer = (value, min, max, nullable = false) => {
    if (nullable && (value === null || value === undefined || value === '')) return null;
    if (!Number.isInteger(value) || value < min || value > max) throw new Error('Controlla anno, durata e valutazione.');
    return value;
  };
  const url = value => {
    if (!value) return '';
    value = text(value, 2000);
    let parsed;
    try { parsed = new URL(value); } catch { throw new Error('Usa un collegamento HTTPS completo.'); }
    if (parsed.protocol !== 'https:' || parsed.username || parsed.password || !parsed.hostname) throw new Error('Usa un collegamento HTTPS completo.');
    return parsed.href;
  };
  const identity = row => JSON.stringify([row.name.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('it').trim().replace(/\s+/g, ' '), row.year || null, row.kind]);
  const nextIdentifier = items => {
    const ids = new Set(items.map(i => i.id));
    const next = Math.max(0, ...ids) + 1;
    if (next <= 2147483647) return next;
    for (let id = 1; id <= items.length + 1; id++) if (!ids.has(id)) return id;
  };
  function mergeMetadata(existing, incoming) {
    existing.sources = [...new Set(existing.sources.concat(incoming.sources))].slice(0, 30);
    if (!existing.poster && incoming.poster) {
      existing.poster = incoming.poster;
      existing.image_source = incoming.image_source;
      existing.image_credit = incoming.image_credit;
    }
    if (!existing.url) existing.url = incoming.url;
  }
  function clean(row) {
    if (!row || typeof row !== 'object' || Array.isArray(row)) throw new Error('Un titolo del file non è valido.');
    const kind = row.kind || 'movie';
    if (!kinds.includes(kind)) throw new Error('Tipo di titolo non valido.');
    const sources = row.sources || [], tags = row.tags || [];
    if (!Array.isArray(sources) || sources.length > 30 || !Array.isArray(tags) || tags.length > 8) throw new Error('Troppe fonti o atmosfere.');
    const state = row.state || 'planned';
    if (!['planned', 'watching', 'watched'].includes(state)) throw new Error('Stato del titolo non valido.');
    let poster = text(row.poster || '', 2000);
    if (/via\.placeholder/.test(poster)) poster = '';
    if (poster.startsWith('http://')) poster = 'https://' + poster.slice(7);
    return {
      name: text(row.name, 300, true), kind, year: integer(row.year, 1800, 2200, true), poster: url(poster),
      sources: [...new Set(sources.map(s => text(s, 100, true)))], state, rating: integer(row.rating === undefined ? 0 : row.rating, 0, 5),
      duration: integer(row.duration, 1, 600, true), notes: text(row.notes || '', 2000),
      tags: [...new Set(tags.map(t => text(t, 32, true)))], collection_name: text(row.collection_name || '', 80),
      url: url(row.url || ''), preview: url(row.preview || ''), description: text(row.description || '', 6000),
      image_source: url(row.image_source || ''), image_credit: text(row.image_credit || '', 1000)
    };
  }
  function empty() { return {version: 1, items: [], collections: [], preferences: {theme: 'light', layout:'classic', palette:'graphite', reducedMotion: false, wifiOnly: false}, recent: [], files: []}; }
  function hydrate(value) {
    if (!value || value.version !== 1 || !Array.isArray(value.items) || value.items.length > 2000) throw new Error('La libreria salvata non è leggibile. I dati non sono stati modificati.');
    const data = empty();
    const seen = new Set(), identifiers = new Set();
    for (let index = 0; index < value.items.length; index++) {
      const row = value.items[index], item = {...clean(row), id: integer(row.id, 1, 2147483647), updated_at: typeof row.updated_at === 'string' ? row.updated_at : new Date().toISOString()};
      const key = identity(item);
      if (seen.has(key) || identifiers.has(item.id)) throw new Error('La libreria contiene titoli duplicati. I dati non sono stati modificati.');
      seen.add(key); identifiers.add(item.id); data.items.push(item);
    }
    if (value.collections != null && !Array.isArray(value.collections)) throw new Error('Le raccolte salvate non sono valide. I dati non sono stati modificati.');
    data.collections = [...new Set((value.collections || []).map(s => text(s, 80, true)).concat(data.items.map(i => i.collection_name).filter(Boolean)))];
    if (data.collections.length > 40) throw new Error('Puoi avere al massimo 40 raccolte.');
    const prefs = value.preferences || {};
    data.preferences = {theme: prefs.theme === 'cinema' ? 'cinema' : 'light', ...appearance(prefs), reducedMotion: prefs.reducedMotion === true, wifiOnly: prefs.wifiOnly === true};
    data.recent = (Array.isArray(value.recent) ? value.recent : []).filter(s => typeof s === 'string' && s.length <= 200).slice(0, 8);
    const tokens = new Set();
    data.files = (Array.isArray(value.files) ? value.files : []).filter(f => {
      if (!f || typeof f !== 'object' || typeof f.token !== 'string' || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(f.token) || typeof f.name !== 'string' || tokens.has(f.token)) return false;
      tokens.add(f.token); return true;
    }).slice(0, 80).map(f => ({token:f.token, name:f.name.slice(0, 300) || 'Video del dispositivo'}));
    return data;
  }
  function add(data, row) {
    const item = clean(row), key = identity(item), next = clone(data), existing = next.items.find(i => identity(i) === key);
    if (existing) {
      mergeMetadata(existing, item);
      return {data: next, id: existing.id, added: false};
    }
    if (next.items.length >= 2000) throw new Error('La libreria contiene già 2.000 titoli. Esporta o rimuovi qualche titolo.');
    const id = nextIdentifier(next.items);
    next.items.push({...item, id, updated_at: new Date().toISOString()});
    if (item.collection_name && !next.collections.includes(item.collection_name)) {
      if (next.collections.length >= 40) throw new Error('Puoi avere al massimo 40 raccolte.');
      next.collections.push(item.collection_name);
    }
    return {data: next, id, added: true};
  }
  function edit(data, id, values) {
    const next = clone(data), index = next.items.findIndex(i => i.id === id);
    if (index < 0) throw new Error('Il titolo non è più nella libreria.');
    const previous = next.items[index], item = clean({...previous, ...values});
    if (next.items.some(i => i.id !== id && identity(i) === identity(item))) throw new Error('Questo titolo esiste già nella libreria.');
    next.items[index] = {...item, id, updated_at: new Date().toISOString()};
    if (item.collection_name && !next.collections.includes(item.collection_name)) {
      if (next.collections.length >= 40) throw new Error('Puoi avere al massimo 40 raccolte.');
      next.collections.push(item.collection_name);
    }
    return next;
  }
  function importLibrary(data, file) {
    if (!file || file.format !== 'vibravid-library' || file.version !== 1 || !Array.isArray(file.items) || file.items.length > 2000) throw new Error('Scegli un file JSON esportato da VibraVid.');
    const validated = file.items.map(clean); // Validate the entire file before any change.
    const next = clone(data), identities = new Map(next.items.map(i => [identity(i), i]));
    const collections = new Set(next.collections);
    let added = 0, nextId = nextIdentifier(next.items);
    const usedIds = new Set(next.items.map(i => i.id));
    for (const row of validated) {
      const key = identity(row), existing = identities.get(key);
      if (existing) {
        mergeMetadata(existing, row);
      } else {
        if (next.items.length >= 2000) throw new Error('La libreria contiene già 2.000 titoli.');
        if (nextId > 2147483647) nextId = 1;
        while (usedIds.has(nextId)) nextId++;
        const item = {...row, id: nextId++, updated_at: new Date().toISOString()};usedIds.add(item.id);
        next.items.push(item); identities.set(key, item); added++;
        if (row.collection_name) collections.add(row.collection_name);
        if (collections.size > 40) throw new Error('Puoi avere al massimo 40 raccolte.');
      }
    }
    next.collections = [...collections];
    return {data: next, added, merged: file.items.length - added};
  }
  function exportLibrary(data) {
    return {format: 'vibravid-library', version: 1, items: data.items.map(i => ({...clone(i), collection: null}))};
  }
  function plan(data, minutes, count, mood = '', exclude = []) {
    integer(minutes, 15, 600); integer(count, 1, 2);
    const score = item => item.duration / minutes * 60 + item.rating * 3 + (item.state === 'planned' ? 5 : 0);
    const candidates = data.items.filter(i => i.state !== 'watched' && i.duration && i.duration <= minutes && (!mood || i.tags.some(t => t.toLocaleLowerCase('it') === mood.toLocaleLowerCase('it'))))
      .sort((a, b) => score(b) - score(a));
    const plans = [], previous = exclude.slice().sort((a, b) => a - b).join(',');
    let fallback = null;
    const consider = items => {
      const value = items.reduce((sum, item) => sum + score(item), 0);
      if (items.map(i => i.id).sort((a, b) => a - b).join(',') === previous) { fallback = items; return; }
      if (plans.length === 12 && value <= plans[11].score) return;
      plans.push({items, score:value}); plans.sort((a,b) => b.score - a.score);
      if (plans.length > 12) plans.pop();
    };
    for (let i = 0; i < candidates.length; i++) {
      if (count === 1) consider([candidates[i]]);
      else for (let j = i + 1; j < candidates.length; j++) {
        if (plans.length === 12 && score(candidates[i]) + score(candidates[j]) <= plans[11].score) break;
        if (candidates[i].duration + candidates[j].duration <= minutes) consider([candidates[i], candidates[j]]);
      }
    }
    const chosen = plans.length ? plans[Math.floor(Math.random() * plans.length)].items : fallback || [];
    return {items: chosen, minutes: chosen.reduce((s, i) => s + i.duration, 0), missing: data.items.filter(i => i.state !== 'watched' && !i.duration).length};
  }
  const model = {empty, hydrate, clean, identity, normalizedTitle, add, edit, importLibrary, exportLibrary, plan, url, clone, layouts, palettes, appearance, paletteColors, lightPalette};
  if (typeof module !== 'undefined' && module.exports) module.exports = model;
  else global.VibraModel = model;
})(typeof window !== 'undefined' ? window : globalThis);
