'use strict';
(() => {
  const M = window.VibraModel, $ = selector => document.querySelector(selector);
  const main = $('#main'), sheet = $('#sheet'), sheetContent = $('#sheet-content');
  const native = window.VibraAndroid, callbacks = new Map();
  const sessionId='v'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,12)+'-';
  let sequence = 0, data, page = '', loadFailed = false, saveChain = Promise.resolve(), toastTimer;
  let results = [], searchKind = 'movie', searchQuery = '', searchGeneration = 0, searchRequest = '', searching = false;
  let filters = {query: '', state: '', collection: '', sort: 'recent'}, currentPlan = null, downloadTimer, libraryLimit = 100;
  let sheetDirty = null, lastFocus = null, sheetClosing = false, sheetRevision = 0, toastRevision = 0, sheetMotion = null;
  let recoveredPicker = null;
  let appearanceRevision = Date.now()*1000;
  const removalConfirmations = new Set();
  let sources = [], sourceQuery = '', sourceFilter = '', selectedSource = '__all__', lastWifi;
  let providerResults=[],providerSearching=false,providerRequest='',providerGeneration=0,providerMessage='',providerWarnings=[];
  let providerKind='movie',providerQuality=720,providerBusy=false,resolveRequest='',resolveGeneration=0,resolveStage='',providerEpisodeRows=[],providerEpisodeIndex=-1;
  const icons = name => `<svg aria-hidden="true"><use href="#${name}"/></svg>`;
  const esc = text => String(text === undefined || text === null ? '' : text).replace(/[&<>"']/g, s => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[s]));
  const kindName = kind => ({movie:'Film',series:'Serie',anime:'Anime',song:'Brano',album:'Album',book:'Libro',live:'Live'}[kind] || 'Titolo');
  const stateName = state => ({planned:'Da vedere',watching:'In visione',watched:'Visto'}[state]);
  const formValues = form => { const result = {}; for (const pair of new FormData(form)) result[pair[0]] = pair[1]; return result; };
  const finishMotion = motion => new Promise(resolve => {
    const timer=setTimeout(resolve,600);
    const finished=motion.finished||new Promise(done=>{motion.onfinish=done;motion.oncancel=done;});
    finished.then(()=>{clearTimeout(timer);resolve();},()=>{clearTimeout(timer);resolve();});
  });
  const reduced = () => data && (data.preferences.reducedMotion || matchMedia('(prefers-reduced-motion: reduce)').matches);
  function animate(element, frames, options) {
    if (!element || reduced() || !element.animate) return null;
    try { return element.animate(frames, {duration:350, easing:'cubic-bezier(.22,1,.36,1)', ...options}); } catch { return null; }
  }
  function api(action, payload = {}, options = {}) {
    const id = sessionId + (++sequence);
    if(action==='download'||action==='mediaRequirements')payload={...payload,requirementsRevision:appearanceRevision};
    if (!native) {
      return browserPreview(action, payload);
    }
    const promise = new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        callbacks.delete(id);
        if (action === 'search' || action.startsWith('provider')) native.request('cancel-' + id, 'cancel', JSON.stringify({id}));
        reject(new Error('L’operazione sta impiegando troppo tempo. Riprova.'));
      }, options.picker ? 15 * 60 * 1000 : options.timeout || 35000);
      callbacks.set(id, {resolve, reject, timeout});
      try { native.request(id, action, JSON.stringify(payload)); }
      catch { clearTimeout(timeout); callbacks.delete(id); reject(new Error('Il collegamento con Android non è disponibile. Riapri l’app.')); }
    });
    promise.requestId = id;
    return promise;
  }
  window.VibraNativeReceive = message => {
    if (message.event === 'pickerRecovered') {
      if (!data) recoveredPicker = message.value;
      else recoverPicker(message.value).catch(error => toast(error.message));
      return;
    }
    if (message.event === 'downloadsChanged' || message.event === 'resume') {
      if (page === 'files') refreshDownloads();
      return;
    }
    const request = callbacks.get(message.id);
    if (!request) return;
    clearTimeout(request.timeout); callbacks.delete(message.id);
    if (message.ok) request.resolve(message.value);
    else request.reject(new Error(message.value || 'Operazione non riuscita.'));
  };
  async function browserPreview(action, payload) {
    if (action === 'load') return JSON.parse(localStorage.getItem('vv.android.preview') || JSON.stringify(M.empty()));
    if (action === 'save') { localStorage.setItem('vv.android.preview', JSON.stringify(payload)); return {saved:true}; }
    if (action === 'theme' || action === 'mediaRequirements') return {};
    if (action === 'sources') { const response=await fetch('providers.json'); if(!response.ok)throw new Error('Le fonti non si caricano.');return (await response.json()).map(s=>({...s,automatic:['streamingcommunity','streamingcommunity_2','animeunity','animeworld','eurostreaming','raiplay'].includes(s.id)})); }
    if (action === 'downloads') return [];
    throw new Error('Questa funzione usa Android ed è disponibile nell’APK.');
  }
  function mutate(fn) {
    const task = saveChain.catch(() => {}).then(async () => {
      if (loadFailed) throw new Error('La libreria non è stata caricata. Riapri l’app prima di modificarla.');
      const next = M.hydrate(fn(M.clone(data)));
      await api('save', next);
      data = next; applyPreferences();
      return next;
    });
    saveChain = task; return task;
  }
  function toast(message) {
    const revision = ++toastRevision;
    clearTimeout(toastTimer);
    const element = $('#toast'); element.textContent = message; element.hidden = false;
    animate(element, [{opacity:0,transform:'translateY(10px)'},{opacity:1,transform:'translateY(0)'}], {duration:300});
    toastTimer = setTimeout(() => {
      const out = animate(element, [{opacity:1},{opacity:0,transform:'translateY(6px)'}], {duration:180});
      if (out) finishMotion(out).then(() => { if (revision === toastRevision) element.hidden = true; }).catch(() => {}); else element.hidden = true;
    }, 4200);
  }
  function applyPreferences() {
    document.documentElement.dataset.theme = data.preferences.theme;
    document.documentElement.dataset.layout = data.preferences.layout;
    document.documentElement.dataset.palette = data.preferences.palette;
    document.documentElement.dataset.scheme = M.lightPalette(data.preferences)?'light':'dark';
    document.documentElement.style.colorScheme = M.lightPalette(data.preferences)?'light':'dark';
    const colors=M.paletteColors(data.preferences);
    Object.keys(colors).forEach(key=>document.documentElement.style.setProperty('--'+key,colors[key]));
    document.querySelector('meta[name="theme-color"]').content=colors.paper;
    document.documentElement.dataset.reduced = String(data.preferences.reducedMotion);
    api('theme', {dark:data.preferences.theme === 'cinema',light:M.lightPalette(data.preferences),background:colors.paper,revision:++appearanceRevision}).catch(() => {});
    if(lastWifi!==data.preferences.wifiOnly){lastWifi=data.preferences.wifiOnly;api('mediaRequirements',{wifiOnly:lastWifi}).catch(e=>toast(e.message));}
  }
  function sheetHeading(title) { return `<div class="sheet-heading"><h2 id="sheet-title">${esc(title)}</h2><button type="button" class="icon-button" data-action="close-sheet" aria-label="Chiudi">${icons('close')}</button></div>`; }
  function openSheet(title, body, dirty = null) {
    ++sheetRevision;
    delete sheet.dataset.result;
    if(sheetMotion)sheetMotion.cancel();sheetMotion=null;
    if (sheet.getAnimations) sheet.getAnimations().forEach(motion => motion.cancel());
    sheetDirty = dirty; sheetClosing = false; lastFocus = document.activeElement;
    sheetContent.innerHTML = sheetHeading(title) + `<div class="sheet-body">${body}</div>`;
    if (!sheet.open) sheet.showModal();
    sheet.scrollTop = 0;
    sheetMotion=animate(sheet, [{opacity:0,transform:'translateY(35px) scale(.99)'},{opacity:1,transform:'translateY(0) scale(1)'}], {duration:400});
    requestAnimationFrame(() => { if(!sheet.open)return;const focus = sheet.querySelector('[autofocus]') || sheet.querySelector('[data-action="close-sheet"]'); if (focus) focus.focus({preventScroll:true}); });
  }
  async function closeSheet(force = false) {
    if (!sheet.open || sheetClosing) return;
    if (!force && sheetDirty && sheetDirty()) {
      let warning = sheet.querySelector('.unsaved');
      if (!warning) {
        warning = document.createElement('div'); warning.className = 'unsaved quiet-card';
        warning.innerHTML = '<h3>Hai modifiche da salvare</h3><p>Puoi continuare a modificarle oppure chiudere senza salvarle.</p><div class="row wrap"><button class="secondary" data-action="keep-editing">Continua</button><button class="danger" data-action="discard-edit">Chiudi senza salvare</button></div>';
        sheet.querySelector('.sheet-body').append(warning);
      }
      warning.scrollIntoView({block:'nearest',behavior:reduced()?'auto':'smooth'}); warning.querySelector('button').focus(); return;
    }
    sheetClosing = true;
    const revision = ++sheetRevision;
    if(sheetMotion)sheetMotion.cancel();
    const motion = sheetMotion=animate(sheet, [{opacity:1,transform:'translateY(0)'},{opacity:0,transform:'translateY(20px)'}], {duration:180});
    if (motion) await finishMotion(motion).catch(() => {});
    if (revision !== sheetRevision) return;
    if(motion)motion.cancel();sheetMotion=null;
    sheet.close(); sheetDirty = null; sheetClosing = false;
    if (lastFocus && lastFocus.isConnected) lastFocus.focus({preventScroll:true});
  }
  sheet.addEventListener('cancel', event => { event.preventDefault(); closeSheet(); });
  sheet.addEventListener('click', event => { if (event.target === sheet) { const r = sheet.getBoundingClientRect(); if (event.clientY < r.top || event.clientX < r.left || event.clientX > r.right) closeSheet(); } });
  function saved(row) { return data.items.find(i => M.identity(i) === M.identity(row)); }
  function poster(row, badge = false) {
    return `<div class="poster">${row.poster ? `<img src="${esc(row.poster)}" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer">` : icons('library')}${badge ? `<span class="kind-badge">${esc(kindName(row.kind))}</span>` : ''}</div>`;
  }
  function card(row, index, library = false) {
    const stored = library ? row : saved(row), label = [row.year, row.duration ? row.duration + ' min' : ''].filter(Boolean).join(' · ');
    return `<article class="title-card${library?' library-card':''}"><button type="button" class="card-open" data-action="${library?'edit':'detail'}" data-id="${library?row.id:index}" aria-label="${library?'Modifica':'Dettagli di'} ${esc(row.name)}">${poster(row,true)}<div class="card-copy"><div class="card-title">${esc(row.name)}</div><div class="card-meta">${esc(label || (row.sources || []).join(', '))}</div>${library?`<div class="reading-state">${esc(stateName(row.state))}${row.rating?' · '+'★'.repeat(row.rating):''}</div>`:''}</div></button>${library?'':`<button class="card-save" data-action="save-result" data-id="${index}" data-saved="${!!stored}" aria-label="${stored?'Salvato in libreria:':'Salva in libreria:'} ${esc(row.name)}">${icons(stored?'check':'plus')}</button>`}</article>`;
  }
  function preparePosters(container = main) {
    container.querySelectorAll('.poster').forEach(p => {
      const image = p.querySelector('img'); if (image && !image.dataset.fallbackBound) { image.dataset.fallbackBound='true'; image.addEventListener('error', () => { image.remove(); p.insertAdjacentHTML('afterbegin', icons('library')); }, {once:true}); }
    });
  }
  function navigate(next) {
    if (!data || loadFailed) return;
    if (!['search','catalog','library','tonight','files','settings'].includes(next)) next = 'search';
    clearInterval(downloadTimer); page = next; main.dataset.page=next;
    document.querySelectorAll('.bottom-nav a').forEach(a => { if (a.dataset.page === (page==='catalog'?'search':page)) a.setAttribute('aria-current','page'); else a.removeAttribute('aria-current'); });
    if (next === 'search') renderSources();
    if (next === 'catalog') renderCatalog();
    if (next === 'library') renderLibrary();
    if (next === 'tonight') renderTonight();
    if (next === 'files') { renderFiles(); refreshDownloads(); downloadTimer = setInterval(() => { if (!document.hidden) refreshDownloads(); }, 4000); }
    if (next === 'settings') renderSettings();
    window.scrollTo({top:0,behavior:'auto'});
    animate(main, [{opacity:.4,transform:'translateY(8px)'},{opacity:1,transform:'translateY(0)'}], {duration:360});
    preparePosters();
  }
  function renderSources() {
    const automatic=sources.filter(s=>s.automatic);
    main.innerHTML=`<section class="page-intro source-intro"><h1>Cosa<br>scarichiamo?</h1><p>Scrivi il titolo. VibraVid cerca il video e lo porta sul telefono.</p></section>
      <section class="source-hero direct-search"><form id="source-form"><label class="field"><span>Il titolo del film o della serie</span><input id="source-query" type="search" minlength="2" maxlength="200" required placeholder="Es. il tuo film preferito" value="${esc(sourceQuery)}" enterkeyhint="search" autocomplete="off"></label><button class="primary" type="submit" ${providerSearching||providerBusy?'disabled':''}>${icons('download')}Cerca e scarica</button>
      <details class="search-options"><summary>Fonti e qualità</summary><div class="search-option-fields"><label class="field"><span>Cerca su</span><select id="source-choice"><option value="__all__">Tutte le fonti automatiche</option>${automatic.map(s=>`<option value="${esc(s.id)}" ${s.id===selectedSource?'selected':''}>${esc(s.name)}</option>`).join('')}</select></label><label class="field"><span>Tipo</span><select id="provider-kind">${[['movie','Film'],['series','Serie'],['anime','Anime'],['all','Tutto']].map(([key,label])=>`<option value="${key}" ${providerKind===key?'selected':''}>${label}</option>`).join('')}</select></label><label class="field"><span>Qualità massima</span><select id="provider-quality">${[480,720,1080].map(q=>`<option value="${q}" ${providerQuality===q?'selected':''}>${q}p</option>`).join('')}</select></label></div></details></form>
      <p class="hint">Se il film corrisponde a un solo titolo, il download parte direttamente. Per più versioni, scegli quella giusta qui.</p></section>
      <div id="provider-progress" class="provider-progress" role="status" ${providerBusy?'':'hidden'}><span class="progress-orbit" aria-hidden="true">${icons('download')}</span><div><strong>Preparo il tuo video</strong><p id="provider-progress-text">${esc(resolveStage)}</p></div><button class="link-button" data-action="cancel-resolve" ${resolveRequest?'':'hidden'}>Annulla</button></div>
      <div id="provider-status" class="search-status" role="status">${esc(providerMessage)}</div><div id="provider-results"></div>
      <section class="search-footnote"><p>La tua libreria, le raccolte e Serata sono sempre qui.</p><button class="link-button" data-action="navigate" data-page="catalog">Cerca solo informazioni</button><button class="link-button" data-action="manage-sources">Gestisci le fonti</button></section>`;
    updateProviderResults();
  }
  function updateProviderResults(){
    if(page!=='search')return;
    const target=$('#provider-results');if(!target)return;
    const submit=$('#source-form button[type=submit]');if(submit)submit.disabled=providerSearching||providerBusy;
    $('#provider-status').textContent=providerMessage;
    if(providerSearching){target.innerHTML=`<button class="secondary" data-action="cancel-provider-search">Interrompi ricerca</button><div class="poster-grid search-skeleton" aria-hidden="true">${Array.from({length:4},()=>'<div class="skeleton"><div class="poster"></div><div class="card-title"></div></div>').join('')}</div>`;return;}
    target.innerHTML=providerResults.length?`<div class="provider-grid">${providerResults.map((row,index)=>`<article class="provider-result">${poster(row,true)}<div class="provider-copy"><h2>${esc(row.name)}</h2><p>${esc([row.year,kindName(row.kind)].filter(Boolean).join(' · '))}</p><span class="provider-source">${esc(row.source)}</span><button class="primary" data-action="${row.kind==='movie'?'provider-download':'provider-episodes'}" data-id="${index}" ${providerBusy?'disabled':''}>${icons(row.kind==='movie'?'download':'play')}${row.kind==='movie'?'Scarica':'Scegli episodio'}</button></div></article>`).join('')}</div>`:sourceQuery&&!providerBusy?'<p class="note">Cerca con il titolo completo, oppure cambia fonte o tipo di contenuto.</p>':'';
    if(providerWarnings.length)target.insertAdjacentHTML('beforeend',`<details class="provider-errors"><summary>Alcune fonti non hanno risposto</summary>${providerWarnings.map(w=>`<p class="note">${esc(w)}</p>`).join('')}</details>`);
    preparePosters(target);
  }
  function abortRequest(id){
    if(!id)return;const callback=callbacks.get(id);if(callback){clearTimeout(callback.timeout);callbacks.delete(id);callback.reject(new Error('Operazione interrotta.'));}
    api('cancel',{id}).catch(()=>{});
  }
  const normalizedTitle=M.normalizedTitle;
  function groupProviderResults(items){
    const groups=new Map();
    items.forEach(row=>{const key=String(row.name).toLocaleLowerCase('it').trim()+'|'+row.kind+'|'+(row.year||'');const group=groups.get(key);if(group){if(!group.variants.some(v=>v.provider===row.provider))group.variants.push(row);}else groups.set(key,{...row,variants:[row]});});
    return Array.from(groups.values());
  }
  async function runProviderSearch(query){
    if(providerBusy)return;
    query=query.trim();if(query.length<2){toast('Scrivi almeno due caratteri.');return;}
    const generation=++providerGeneration;abortRequest(providerRequest);providerRequest='';providerSearching=true;providerResults=[];providerWarnings=[];sourceQuery=query;
    providerMessage='Cerco nelle fonti…';updateProviderResults();$('#source-query').blur();
    try{
      const request=api('providerSearch',{query,source:selectedSource,kind:providerKind},{timeout:65000});providerRequest=request.requestId||'';
      const response=await request;if(generation!==providerGeneration)return;
      providerSearching=false;providerRequest='';providerResults=groupProviderResults(response.items);providerWarnings=response.warnings||[];
      providerMessage=providerResults.length?providerResults.length+' risultati. Scegli il titolo per scaricarlo.':'Nessun titolo trovato nelle fonti che hanno risposto.';
      updateProviderResults();
      const normalizedQuery=normalizedTitle(query);
      const exact=providerResults.map((row,index)=>({row,index})).filter(x=>normalizedQuery&&x.row.kind==='movie'&&normalizedTitle(x.row.name)===normalizedQuery);
      const identities=new Set(exact.map(x=>normalizedTitle(x.row.name)+'|'+(x.row.year||'')));
      if(exact.length&&identities.size===1)await downloadProvider(exact[0].index);
    }catch(error){if(generation!==providerGeneration)return;providerSearching=false;providerRequest='';providerMessage=error.message;updateProviderResults();}
  }
  function cancelProviderSearch(){++providerGeneration;abortRequest(providerRequest);providerRequest='';providerSearching=false;providerMessage='Ricerca interrotta.';updateProviderResults();}
  async function downloadProvider(index,episode=''){
    const row=providerResults[index];if(!row||providerBusy)return;
    const generation=++resolveGeneration,quality=providerQuality;providerBusy=true;resolveStage='Trovo il collegamento per '+row.name+'…';if(page==='search')renderSources();
    try{
      let queued=false,lastError;const variants=episode?[row]:(row.variants||[row]);
      for(let attempt=0;attempt<variants.length;attempt++){
        try{
          const variant=variants[attempt];resolveStage=(attempt?'Provo un’altra fonte: ':'Trovo il video su ')+variant.source+'…';
          const request=api('providerResolve',{key:variant.key,episode},{timeout:65000});resolveRequest=request.requestId||'';if(page==='search')renderSources();
          const payload=await request;if(generation!==resolveGeneration)return;resolveRequest='';resolveStage='Preparo video e audio per il download…';if(page==='search')renderSources();
          await api('download',{...payload,quality,wifiOnly:data.preferences.wifiOnly},{timeout:40000});queued=true;break;
        }catch(error){if(generation!==resolveGeneration)return;lastError=error;}
      }
      if(!queued)throw lastError||new Error('Nessuna fonte disponibile per questo titolo.');
      if(generation!==resolveGeneration)return;providerBusy=false;toast('Il video è in Download.');if(page==='search'){await closeSheet(true);location.hash='files';}else if(page==='files')refreshDownloads();
    }catch(error){if(generation!==resolveGeneration)return;providerMessage=error.message;toast(error.message);}
    finally{if(generation===resolveGeneration){providerBusy=false;resolveRequest='';if(page==='search')renderSources();}}
  }
  function cancelProviderResolve(){if(!resolveRequest)return;++resolveGeneration;abortRequest(resolveRequest);resolveRequest='';providerBusy=false;providerMessage='Preparazione interrotta.';if(page==='search')renderSources();}
  async function showProviderEpisodes(index,season=-1){
    const row=providerResults[index];if(!row)return;providerEpisodeIndex=index;
    openSheet(row.name,'<p class="note" role="status">Carico le stagioni e gli episodi…</p>');
    const revision=sheetRevision;
    try{
      const response=await api('providerEpisodes',{key:row.key,season},{timeout:65000});if(revision!==sheetRevision||providerEpisodeIndex!==index||!sheet.open)return;
      providerEpisodeRows=response.episodes;
      openSheet(row.name,`<label class="field"><span>Stagione</span><select id="provider-season" data-id="${index}">${response.seasons.map(s=>`<option value="${s.number}" ${s.number===response.season?'selected':''}>${esc(s.name)}</option>`).join('')}</select></label><div class="episode-list">${providerEpisodeRows.map((ep,i)=>`<button class="episode-download" data-action="provider-episode-download" data-id="${i}"><span><strong>Episodio ${esc(ep.number)}</strong><small>${esc(ep.name)}</small></span>${icons('download')}</button>`).join('')||'<p class="note">Nessun episodio disponibile per questa stagione.</p>'}</div>`);
    }catch(error){if(revision===sheetRevision&&sheet.open)openSheet(row.name,`<p class="error-note">${esc(error.message)}</p><button class="secondary" data-action="provider-episodes" data-id="${index}">Riprova</button>`);}
  }
  function manageSources(){
    openSheet('Le fonti',`<p class="note">${sources.filter(s=>s.automatic).length} fonti hanno ricerca e download diretti. Puoi aggiornare gli indirizzi del PC qui. Il browser con AD resta disponibile come strumento aggiuntivo.</p><label class="source-filter"><input id="source-filter" type="search" placeholder="Filtra le fonti" value="${esc(sourceFilter)}" maxlength="80" aria-label="Filtra le fonti"></label><div class="source-grid" id="source-list"></div><button class="secondary" data-action="import-sources">Importa indirizzi PC</button>`);updateSourceCards();
  }
  function updateSourceCards(){
    const target=$('#source-list');if(!target)return;
    const query=sourceFilter.trim().toLocaleLowerCase('it');
    const rows=sources.filter(s=>(s.name+' '+s.category).toLocaleLowerCase('it').includes(query));
    target.innerHTML=rows.length?rows.map((s,i)=>`<article class="source-card"><span class="source-mark">${esc(s.name.slice(0,2).toUpperCase())}</span><div class="source-copy"><strong>${esc(s.name)}</strong><small>${esc(s.category)}</small></div><button class="icon-button" data-action="open-source" data-source="${esc(s.id)}" aria-label="Apri ${esc(s.name)}">${icons('arrow')}</button><button class="source-address" data-action="edit-source" data-source="${esc(s.id)}" aria-label="Modifica indirizzo di ${esc(s.name)}">${esc(new URL(s.url).host)} · modifica</button></article>`).join(''):'<p class="note">Nessuna fonte corrisponde. Prova un altro nome.</p>';
  }
  async function openSource(id,query=''){
    await api('openSource',{id,query,wifiOnly:data.preferences.wifiOnly});
  }
  function editSource(id){
    const source=sources.find(s=>s.id===id);if(!source)return;
    openSheet('Indirizzo di '+source.name,`<p class="note">Se il sito ha cambiato indirizzo, puoi aggiornarlo qui. Usa la pagina principale HTTPS.</p><form id="source-address-form" data-source="${esc(id)}"><label class="field"><span>Indirizzo della fonte</span><input name="url" type="url" value="${esc(source.url)}" maxlength="2500" required></label><p id="source-error" class="error-note" role="alert" hidden></p><div class="form-actions"><button type="submit" class="primary">Salva indirizzo</button></div></form>`);
  }
  async function importSources(){
    const response=await api('import',{purpose:'sources'}, {picker:true});if(response.cancelled)return;
    const report=await api('sourceImport',{data:response.data});sources=await api('sources');
    toast(report.updated+' indirizzi aggiornati.');if(page==='search')renderSources();
  }
  function renderCatalog() {
    main.innerHTML = `<section class="page-intro"><h1>La prossima bella storia.</h1><p>Informazioni, poster e idee per la libreria. Per scaricare il film, usa Cerca.</p><button class="link-button" data-action="navigate" data-page="search">‹ Torna ai download diretti</button></section>
      <form id="search-form"><div class="search-box"><input type="search" id="search-query" aria-label="Titolo da cercare" placeholder="Un film, una serie…" minlength="2" maxlength="200" value="${esc(searchQuery)}" enterkeyhint="search" required autocomplete="off"><button type="submit" class="primary" aria-label="Cerca">${icons('search')}</button></div></form>
      <div class="segments" aria-label="Tipo di ricerca"><button type="button" data-action="search-kind" data-kind="movie" aria-pressed="${searchKind==='movie'}">Film</button><button type="button" data-action="search-kind" data-kind="series" aria-pressed="${searchKind==='series'}">Serie</button></div>
      <div id="search-status" class="search-status" role="status">${searching?'Ricerca in corso…':results.length?results.length+' titoli trovati':''}</div><div id="search-results"></div>`;
    updateResults();
  }
  function updateResults() {
    if (page !== 'catalog') return;
    const container = $('#search-results');
    if (searching) {
      container.innerHTML = '<div class="row"><button class="secondary" data-action="cancel-search">Interrompi ricerca</button></div><div class="poster-grid search-skeleton" aria-hidden="true">' + Array.from({length:4},()=>'<div class="skeleton"><div class="poster"></div><div class="card-title"></div><div class="card-meta"></div></div>').join('') + '</div>';
    } else if (results.length) {
      container.innerHTML = `<div class="poster-grid">${results.map((r,i)=>card(r,i)).join('')}</div><p class="attribution">${searchKind==='series'?'Metadati e immagini: <a href="https://www.tvmaze.com" data-external>TVmaze</a>, <a href="https://creativecommons.org/licenses/by-sa/4.0/" data-external>CC BY-SA</a>.':'Metadati da <a href="https://www.wikidata.org" data-external>Wikidata</a>, CC0. Immagini Wikimedia Commons, con crediti e licenza nei dettagli.'}<br>La ricerca mostra informazioni sui titoli, non la disponibilità del film completo.</p>`;
    } else {
      container.innerHTML = `<section class="quiet-card"><h2>Il tuo spazio, ovunque.</h2><p>La libreria, le note e le tue serate restano sul telefono. Hai già una libreria sul PC?</p><button class="secondary" data-action="import">${icons('download')}Importa la libreria</button></section>${data.recent.length?`<div class="section-head"><h2>Ricerche recenti</h2></div><div class="chip-list">${data.recent.map(q=>`<button class="chip" data-action="recent" data-query="${esc(q)}">${esc(q)}</button>`).join('')}</div>`:''}`;
    }
    preparePosters(container);
  }
  async function runSearch(query) {
    query = query.trim(); if (query.length < 2) { toast('Scrivi almeno due caratteri.'); return; }
    const generation = ++searchGeneration;
    abortRequest(searchRequest);
    searchQuery = query; searching = true; results = []; updateResults();
    if ($('#search-status')) $('#search-status').textContent = 'Cerco in ' + (searchKind==='movie'?'Wikidata':'TVmaze') + '…';
    const input = $('#search-query'); if (input) input.blur();
    try {
      const request = api('search',{query,kind:searchKind}); searchRequest = request.requestId || '';
      const response = await request;
      if (generation !== searchGeneration) return;
      results = response.items.map(r => M.clean(r)); searching = false; searchRequest = '';
      await mutate(next => { next.recent = [query,...next.recent.filter(q=>q!==query)].slice(0,8); return next; });
      if (generation !== searchGeneration) return;
      if (page === 'catalog') { $('#search-status').textContent = results.length ? `${results.length} titoli da ${response.source}${response.cached?' · Ricerca recente':''}` : 'Nessun titolo trovato. Prova il titolo originale o passa a Film/Serie.'; updateResults(); }
    } catch (error) {
      if (generation !== searchGeneration) return;
      searching = false; searchRequest = ''; if (page==='catalog') { updateResults(); $('#search-status').textContent = error.message; } else toast(error.message);
    }
  }
  function cancelSearch() {
    ++searchGeneration; searching = false;
    abortRequest(searchRequest);
    searchRequest = ''; updateResults(); if ($('#search-status')) $('#search-status').textContent = 'Ricerca interrotta.';
  }
  async function saveResult(index) {
    const row = results[index]; if (!row) return;
    const revision=sheetRevision;
    let added;
    await mutate(next => { const result=M.add(next,row); added=result.added; return result.data; });
    toast(added?'Titolo aggiunto alla tua libreria.':'Il titolo è già nella tua libreria.');
    updateResults();
    if (sheet.open && sheetRevision === revision && results[index] === row && sheet.dataset.result === String(index)) detail(index);
  }
  function detail(index) {
    const row = results[index]; if (!row) return;
    const existing = saved(row);
    openSheet('Da guardare più da vicino', `<div class="detail-intro">${poster(row)}<div><h3>${esc(row.name)}</h3><p>${esc([kindName(row.kind),row.year,row.duration?row.duration+' min'+(row.kind==='series'?' / episodio':''):''].filter(Boolean).join(' · '))}</p><p>${esc(row.sources.join(', '))}</p></div></div>${row.description?`<p class="description">${esc(row.description)}</p>`:''}<div class="detail-actions"><button class="primary" data-action="${existing?'edit':'save-result'}" data-id="${existing?existing.id:index}">${icons(existing?'check':'plus')}${existing?'In libreria':'Salva in libreria'}</button>${row.preview?`<button class="secondary" data-action="preview" data-id="${index}">${icons('play')}Anteprima</button>`:''}${row.url?`<button class="secondary" data-action="source" data-id="${index}">${icons('arrow')}Apri ${row.kind==='movie'?'Wikidata':'TVmaze'}</button>`:''}<button class="secondary" data-action="share-result" data-id="${index}">${icons('share')}Condividi</button></div><p class="attribution">${row.kind==='movie'?'Metadati Wikidata, CC0. La scheda originale riporta fonti e riferimenti.':'Informazioni TVmaze, CC BY-SA. La durata indica un episodio.'}</p>${row.image_source?`<p class="attribution">Immagine: <a href="${esc(row.image_source)}" data-external>${esc(row.image_credit||'Wikimedia Commons — crediti e licenza')}</a></p>`:''}`);
    sheet.dataset.result = String(index); preparePosters(sheet);
  }
  function renderLibrary() {
    main.innerHTML = `<section class="page-intro"><div class="page-title-row"><h1>La tua libreria.</h1><button class="icon-button" data-action="add-manual" aria-label="Aggiungi un titolo">${icons('plus')}</button></div><p>${data.items.length?`${data.items.length} ${data.items.length===1?'titolo':'titoli'}, le tue note, il tuo ritmo.`:'Tieni qui le storie che vuoi ritrovare.'}</p></section>
      <div class="library-filters"><input id="library-query" type="search" placeholder="Filtra i tuoi titoli…" aria-label="Filtra la libreria" value="${esc(filters.query)}"><select id="library-state" aria-label="Stato"><option value="">Tutti gli stati</option><option value="planned">Da vedere</option><option value="watching">In visione</option><option value="watched">Visti</option></select><select id="library-collection" aria-label="Raccolta"><option value="">Tutte le raccolte</option>${data.collections.map(c=>`<option value="${esc(c)}">${esc(c)}</option>`).join('')}</select><select id="library-sort" aria-label="Ordinamento"><option value="recent">Più recenti</option><option value="name">Titolo A–Z</option><option value="rating">Valutazione</option><option value="duration">Durata</option></select></div><div id="library-grid"></div>`;
    $('#library-state').value=filters.state; $('#library-collection').value=filters.collection; $('#library-sort').value=filters.sort;
    updateLibraryGrid();
  }
  function updateLibraryGrid() {
    if (page!=='library') return;
    const rows = data.items.filter(i=>(!filters.query||i.name.toLocaleLowerCase('it').includes(filters.query.toLocaleLowerCase('it')))&&(!filters.state||i.state===filters.state)&&(!filters.collection||i.collection_name===filters.collection));
    rows.sort((a,b)=>filters.sort==='name'?a.name.localeCompare(b.name,'it'):filters.sort==='rating'?b.rating-a.rating:filters.sort==='duration'?(a.duration||999)-(b.duration||999):b.updated_at.localeCompare(a.updated_at));
    const container=$('#library-grid');
    const positions=new Map([...container.querySelectorAll('.title-card')].map(e=>[e.querySelector('button').dataset.id,e.getBoundingClientRect()]));
    container.innerHTML = rows.length?`<div class="poster-grid">${rows.slice(0,libraryLimit).map((r,i)=>card(r,i,true)).join('')}</div>${rows.length>libraryLimit?`<button class="secondary wide load-more" data-action="more-library">Mostra altri titoli (${rows.length-libraryLimit} rimasti)</button>`:''}`:`<div class="empty">${icons('library')}<h2>${data.items.length?'Nessun titolo con questi filtri.':'La prima storia la scegli tu.'}</h2><p>${data.items.length?'Cambia il testo, lo stato o la raccolta per ritrovare gli altri titoli.':'Cerca un film o una serie, aggiungi un titolo a mano oppure importa la libreria del PC.'}</p>${data.items.length?'':`<div class="row wrap"><button class="primary" data-action="navigate" data-page="search">Cerca un titolo</button><button class="secondary" data-action="import">Importa</button></div>`}</div>`;
    preparePosters(container);
    container.querySelectorAll('.title-card').forEach(e=>{
      const old=positions.get(e.querySelector('button').dataset.id), now=e.getBoundingClientRect();
      if(old) animate(e,[{transform:`translate(${old.left-now.left}px,${old.top-now.top}px)`},{transform:'translate(0,0)'}],{duration:420});
    });
  }
  function editTitle(id = null) {
    const row=id?data.items.find(i=>i.id===id):{name:'',kind:'movie',year:null,state:'planned',rating:0,duration:null,tags:[],notes:'',collection_name:''};
    if(!row)return;
    openSheet(id?'La tua scheda':'Aggiungi una storia',`<form id="title-form" data-id="${id||''}"><div class="form-grid">
      <label class="field full"><span>Titolo</span><input name="name" maxlength="300" value="${esc(row.name)}" required ${id?'':'autofocus'}></label>
      <label class="field"><span>Tipo</span><select name="kind">${['movie','series','anime','song','album','book','live'].map(k=>`<option value="${k}" ${row.kind===k?'selected':''}>${esc(kindName(k))}</option>`).join('')}</select></label>
      <label class="field"><span>Anno</span><input name="year" type="number" min="1800" max="2200" inputmode="numeric" value="${row.year||''}"></label>
      <label class="field"><span>Stato</span><select name="state">${['planned','watching','watched'].map(k=>`<option value="${k}" ${row.state===k?'selected':''}>${stateName(k)}</option>`).join('')}</select></label>
      <label class="field"><span>Valutazione</span><select name="rating">${[0,1,2,3,4,5].map(r=>`<option value="${r}" ${row.rating===r?'selected':''}>${r?'★'.repeat(r):'Da valutare'}</option>`).join('')}</select></label>
      <label class="field"><span>Durata, in minuti</span><input name="duration" type="number" min="1" max="600" inputmode="numeric" value="${row.duration||''}" placeholder="Per Serata"></label>
      <label class="field"><span>Raccolta</span><input name="collection_name" maxlength="80" value="${esc(row.collection_name)}" placeholder="Es. Weekend" list="collections"></label>
      <datalist id="collections">${data.collections.map(c=>`<option value="${esc(c)}"></option>`).join('')}</datalist>
      <label class="field full"><span>Atmosfere, separate da virgole</span><input name="tags" maxlength="260" value="${esc(row.tags.join(', '))}" placeholder="Es. Avventura, Relax"><small class="hint">Fino a 8. Per le serie, la durata è quella di un episodio.</small></label>
      <label class="field full"><span>Le tue note</span><textarea name="notes" maxlength="2000" placeholder="Cosa ti incuriosisce? Con chi lo vuoi vedere?">${esc(row.notes)}</textarea></label>
      </div><div id="form-error" class="error-note" role="alert" hidden></div><div class="form-actions">${id?`<button type="button" class="link-button" data-action="delete-title" data-id="${id}">Rimuovi</button>`:''}<button class="primary" type="submit">${icons('check')}Salva la scheda</button></div></form>${row.image_source?`<p class="attribution">Immagine: <a href="${esc(row.image_source)}" data-external>${esc(row.image_credit||'Wikimedia Commons — crediti e licenza')}</a></p>`:''}`);
    delete sheet.dataset.result;
    const form=$('#title-form'), initial=JSON.stringify([...new FormData(form)]);
    sheetDirty=()=>JSON.stringify([...new FormData(form)])!==initial;
  }
  async function saveTitle(form) {
    const fields=formValues(form),id=Number(form.dataset.id)||null;
    const values={...fields,year:fields.year?Number(fields.year):null,duration:fields.duration?Number(fields.duration):null,rating:Number(fields.rating),tags:fields.tags.split(',').map(s=>s.trim()).filter(Boolean)};
    const button=form.querySelector('button[type=submit]');button.disabled=true;
    try {
      await mutate(next=>id?M.edit(next,id,values):M.add(next,values).data);
      currentPlan=null;
      if(sheet.contains(form)&&sheet.open)await closeSheet(true); toast('Scheda salvata.');
      if(page==='library')renderLibrary(); if(page==='catalog')updateResults(); if(page==='tonight')renderTonight();
    }catch(error){const note=form.querySelector('#form-error');if(form.isConnected&&note){note.hidden=false;note.textContent=error.message;note.scrollIntoView({block:'nearest'});}else toast(error.message);}
    finally{button.disabled=false;}
  }
  function renderTonight() {
    const moods=[...new Set([].concat(...data.items.map(i=>i.tags)))].sort((a,b)=>a.localeCompare(b,'it'));
    main.innerHTML=`<section class="tonight-hero"><svg aria-hidden="true"><use href="#moon"/></svg><h1>Stasera, il tempo è tuo.</h1><p>Una o due storie dalla tua libreria, nel tempo che hai.</p></section>
      <form id="tonight-form" class="tonight-form"><label class="field"><span>Minuti disponibili</span><input name="minutes" type="number" inputmode="numeric" min="15" max="600" value="${currentPlan?currentPlan.budget:120}" required></label><label class="field"><span>Quante visioni?</span><select name="count"><option value="1">Una storia</option><option value="2" ${currentPlan&&currentPlan.count===2?'selected':''}>Due storie</option></select></label><label class="field full"><span>Atmosfera</span><select name="mood"><option value="">Tutte le atmosfere</option>${moods.map(m=>`<option value="${esc(m)}" ${currentPlan&&currentPlan.mood===m?'selected':''}>${esc(m)}</option>`).join('')}</select></label><button class="primary full" type="submit">${icons('moon')}Componi la serata</button></form><div id="tonight-plan"></div><p class="note">Serata usa le durate e le atmosfere dei tuoi titoli. Per una serie conta un episodio; le visioni già segnate come viste vengono escluse.</p>`;
    if(currentPlan)updatePlan();
  }
  function createPlan(form, again=false) {
    const fields=formValues(form),budget=Number(fields.minutes),count=Number(fields.count),mood=fields.mood;
    currentPlan={...M.plan(data,budget,count,mood,again&&currentPlan?currentPlan.items.map(i=>i.id):[]),budget,count,mood};
    updatePlan();
  }
  function updatePlan() {
    const container=$('#tonight-plan');if(!container||!currentPlan)return;
    if(!currentPlan.items.length){container.innerHTML=`<section class="quiet-card"><h2>Facciamo spazio a una storia.</h2><p>Nessuna combinazione entra in questo tempo. Aumenta i minuti o cambia atmosfera.${currentPlan.missing?' '+currentPlan.missing+' titoli non hanno ancora una durata.':''}</p><button class="secondary" data-action="navigate" data-page="library">Completa le durate</button></section>`;return;}
    let elapsed=0;
    container.innerHTML=`<section class="plan"><div class="plan-head"><h2>${currentPlan.minutes} minuti per te.</h2><p>${currentPlan.budget-currentPlan.minutes} minuti liberi per una pausa.</p></div>${currentPlan.items.map(row=>{const start=elapsed;elapsed+=row.duration;return `<div class="plan-item">${poster(row)}<div><span class="time-marker">${start===0?'Si comincia':'Dopo '+start+' minuti'}</span><h3>${esc(row.name)}</h3><p>${row.duration} min${row.kind==='series'?' / episodio':''}${row.tags.length?' · '+esc(row.tags.slice(0,2).join(', ')):''}</p></div></div>`;}).join('')}<div class="plan-actions"><button class="secondary" data-action="another-plan">Un’altra proposta</button><button class="primary" data-action="share-plan">${icons('share')}Condividi</button></div></section>`;
    preparePosters(container);animate(container,[{opacity:0,transform:'translateY(14px)'},{opacity:1,transform:'translateY(0)'}],{duration:450});
  }
  function renderFiles() {
    main.innerHTML=`<section class="page-intro"><h1>Le storie, con te.</h1><p>I film scaricati dalle fonti e i video del telefono, nello stesso posto.</p></section><div class="file-actions"><button class="primary" data-action="pick-video">${icons('files')}Scegli video</button><button class="secondary" data-action="new-download">${icons('download')}Da un link</button></div><section class="files-section device-section"><div class="section-head"><h2>Dal dispositivo</h2><span>${data.files.length} video</span></div><div id="local-files">${data.files.length?data.files.map(file=>`<article class="local-file"><strong>${esc(file.name)}</strong><small>Collegato dal telefono</small><div class="row"><button class="secondary" data-action="play-file" data-token="${esc(file.token)}">${icons('play')}Riprendi</button><button class="link-button" data-action="forget-file" data-token="${esc(file.token)}">Scollega</button></div></article>`).join(''):'<p class="note">Scegli un video dal selettore Android. Il file rimane dove si trova e puoi riprenderlo dal punto lasciato.</p>'}</div></section><section class="files-section downloads-section"><div class="section-head"><h2>Download</h2><button class="icon-button" data-action="refresh-downloads" aria-label="Aggiorna download">${icons('download')}</button></div><div id="downloads"><p class="note">Leggo lo stato dei download…</p></div></section><p class="note">I download continuano con una notifica anche quando lasci l’app. HLS/DASH restano nello spazio offline di VibraVid. I file scaricati vengono rimossi se disinstalli VibraVid. I video scelti dal dispositivo restano al loro posto.</p>`;
  }
  let refreshing=false;
  async function refreshDownloads() {
    if(page!=='files'||refreshing)return;refreshing=true;
    try{
      const rows=await api('downloads');if(page!=='files')return;
      const bytes=n=>n<0?'':n>1048576?(n/1048576).toFixed(1)+' MB':Math.round(n/1024)+' KB';
      const ids=new Set(rows.map(row=>String(row.id)));removalConfirmations.forEach(id=>{if(!ids.has(id))removalConfirmations.delete(id);});
      $('#downloads').innerHTML=rows.length?rows.slice().reverse().map(row=>{
        const offline=row.engine==='offline', complete=row.status===8, failed=row.status===16;
        const status=complete?'Disponibile offline':failed?'Download non riuscito':row.paused?'In pausa':row.status===4?'In attesa della rete':row.status===2?'Download in corso':data.preferences.wifiOnly?'In coda · Attendo Wi-Fi':'In coda';
        const progress=complete?100:row.percent>=0?Math.min(100,Math.round(row.percent)):row.total>0?Math.min(100,Math.round(row.bytes/row.total*100)):null;
        return `<article class="download-row"><strong>${esc(row.name)}</strong><small>${esc(row.source||'Video')} · ${status}</small>${failed?`<p class="error-note">${esc(row.error||'Il collegamento potrebbe essere scaduto. Riapri la fonte e rileva nuovamente il video.')}</p>`:''}<progress aria-label="Avanzamento di ${esc(row.name)}" max="100" ${progress===null?'':`value="${progress}"`}></progress><small>${complete&&row.bytes===0?'Già salvato sul telefono':bytes(row.bytes)}${row.total>0?' / '+bytes(row.total):''}${progress!==null?' · '+progress+'%':''}${complete&&offline?' · Salvato nell’app':''}</small><div class="row wrap">${complete?`<button class="secondary" data-action="${offline?'media-play':'open-download'}" data-id="${esc(row.id)}" data-name="${esc(row.name)}">${icons('play')}Guarda offline</button>${offline?`<button class="link-button" data-action="media-repair" data-id="${esc(row.id)}">Verifica e ripara</button>`:''}`:offline?`<button class="secondary" data-action="media-pause" data-id="${esc(row.id)}" data-paused="${!row.paused&&!failed}">${row.paused||failed?'Riprendi':'Pausa'}</button>`:''}<button class="link-button" data-action="${offline?'media-remove':'remove-download'}" data-id="${esc(row.id)}" data-complete="${complete}" data-confirm="${removalConfirmations.has(String(row.id))?'yes':''}">${removalConfirmations.has(String(row.id))?'Conferma rimozione':complete?'Elimina video':failed?'Rimuovi':'Annulla'}</button></div></article>`;
      }).join(''):'<p class="note">Nessun download. Cerca un titolo e premi Cerca e scarica.</p>';
    }catch(error){if(page==='files')$('#downloads').innerHTML=`<p class="error-note">${esc(error.message)}</p>`;}
    finally{refreshing=false;}
  }
  function newDownload(){
    openSheet('Un video da portare con te',`<p class="note">Incolla l’URL HTTPS di un video MP4/WebM o di una playlist HLS/DASH. Per un sito, apri la sua scheda in Fonti e avvia il player. I contenuti DRM richiedono la licenza offline del servizio.</p><form id="download-form"><div class="form-grid"><label class="field full"><span>Collegamento del file</span><input name="url" type="url" placeholder="https://…/video.mp4" maxlength="2000" required></label><label class="field full"><span>Nome del video</span><input name="name" maxlength="180" placeholder="Il mio video" required></label><label class="field full"><span>Qualità massima</span><select name="quality"><option value="720">720p</option><option value="1080">1080p</option><option value="480">480p</option></select></label></div><p class="hint">${data.preferences.wifiOnly?'Il download partirà con una rete Wi-Fi.':'Può usare anche i dati mobili. Puoi cambiarlo nelle Impostazioni.'}</p><div id="download-error" class="error-note" role="alert" hidden></div><div class="form-actions"><button type="submit" class="primary">${icons('download')}Avvia download</button></div></form>`);
  }
  function renderSettings(){
    const p=data.preferences;
    main.innerHTML=`<section class="page-intro"><h1>A modo tuo.</h1><p>Il tuo spazio, il tuo ritmo.</p></section>
      <section class="appearance-panel" aria-labelledby="appearance-title"><h2 id="appearance-title">Aspetto</h2><p class="appearance-note">Combina layout e colori. La scelta si applica subito e resta salvata sul telefono.</p>
      <div class="appearance-pickers"><section class="appearance-picker"><h3 id="layout-label">Layout</h3><div class="layout-choices" role="group" aria-labelledby="layout-label">${M.layouts.map(option=>`<button type="button" class="appearance-choice layout-choice" data-action="appearance" data-key="layout" data-value="${option.id}" aria-pressed="${p.layout===option.id}"><span class="layout-mini" data-mini="${option.id}" aria-hidden="true"><i></i><i></i><i></i><i></i></span><span class="choice-copy"><strong>${option.name}</strong><small>${option.note}</small></span><span class="choice-check" aria-hidden="true">${icons('check')}</span></button>`).join('')}</div>
      </section><section class="appearance-picker"><h3 id="palette-label">Colori</h3><div class="palette-choices" role="group" aria-labelledby="palette-label">${M.palettes.map(option=>`<button type="button" class="appearance-choice palette-choice" data-action="appearance" data-key="palette" data-value="${option.id}" aria-pressed="${p.palette===option.id}"><span class="palette-swatch" data-swatch="${option.id}" aria-hidden="true"><i></i><i></i><i></i></span><span class="choice-copy"><strong>${option.name}</strong><small>${option.note}</small></span><span class="choice-check" aria-hidden="true">${icons('check')}</span></button>`).join('')}</div></section></div><p class="appearance-summary" role="status" aria-live="polite" id="appearance-summary">${M.layouts.find(o=>o.id===p.layout).name} · ${M.palettes.find(o=>o.id===p.palette).name}. Locandine sempre a colori.</p></section><div class="settings-list">
      <div class="setting"><div><strong>Modalità cinema</strong><small>Fondi più profondi. Scurisce anche le palette chiare.</small></div><button class="switch" role="switch" aria-checked="${p.theme==='cinema'}" aria-label="Modalità cinema" data-action="preference" data-key="theme"></button></div>
      <div class="setting"><div><strong>Riduci movimento</strong><small>Transizioni immediate. Rispettiamo anche la scelta del telefono.</small></div><button class="switch" role="switch" aria-checked="${p.reducedMotion}" aria-label="Riduci movimento" data-action="preference" data-key="reducedMotion"></button></div>
      <div class="setting"><div><strong>Download solo con Wi-Fi</strong><small>Si applica anche ai download in coda.</small></div><button class="switch" role="switch" aria-checked="${p.wifiOnly}" aria-label="Download solo con Wi-Fi" data-action="preference" data-key="wifiOnly"></button></div>
      </div><section class="quiet-card"><h2>Le tue fonti viaggiano.</h2><p>Importa il file domains.json della configurazione PC per aggiornare gli indirizzi. Puoi anche modificarli dalla scheda di ogni fonte.</p><button class="secondary" data-action="import-sources">Importa indirizzi PC</button></section><section class="quiet-card"><h2>Le tue storie viaggiano.</h2><p>Importa il JSON di VibraVid sul PC o esporta la libreria del telefono. Le note esistenti vengono conservate.</p><div class="row wrap"><button class="secondary" data-action="import">Importa JSON</button><button class="primary" data-action="export">Esporta JSON</button></div></section><section class="quiet-card"><h3>VibraVid Studio per Android</h3><p>Versione 2.5.1. La ricerca interroga direttamente i cataloghi delle fonti supportate. Un titolo univoco avvia il download senza aprire il sito; per titoli omonimi o episodi puoi scegliere dentro l’app. Il browser delle fonti include il blocco pubblicità, disattivabile con il pulsante AD per ogni fonte. Il download autonomo salva video, audio e sottotitoli compatibili sul telefono. HLS/DASH vengono riprodotti offline nell’app. La ricerca delle informazioni usa Wikidata e TVmaze; Gli adattatori automatici Android disponibili sono StreamingCommunity, Streamingcommunity 2, AnimeUnity, AnimeWorld, Eurostreaming e RaiPlay. Le altre fonti restano negli strumenti aggiuntivi.</p><p>Nessuna telemetria e nessun account incorporato. I dati della libreria restano nello spazio privato dell’app. Disinstallando l’app li rimuovi: esporta prima se vuoi conservarli.</p><button class="link-button" data-action="licenses">Licenze e fonti</button></section>${native?'':'<p class="error-note">Anteprima nel browser. Le funzioni del telefono sono disponibili nell’APK.</p>'}`;
    main.querySelectorAll('[data-swatch]').forEach(swatch=>{const option=M.palettes.find(palette=>palette.id===swatch.dataset.swatch);[option.colors.paper,option.colors.raised,option.colors.accent].forEach((color,index)=>{swatch.children[index].style.backgroundColor=color;});});
  }
  async function importLibrary(){
    const response=await api('import',{purpose:'library'}, {picker:true});if(response.cancelled)return;
    let report;await mutate(next=>{report=M.importLibrary(next,response.data);return report.data;});
    currentPlan=null;
    toast(`${report.added} titoli importati, ${report.merged} già presenti. Le note precedenti sono conservate.`);
    if(page==='library')renderLibrary();if(page==='catalog')updateResults();if(page==='tonight')renderTonight();
  }
  async function recoverPicker(event){
    const result=event.result;
    if(event.type===102){toast('Libreria esportata.');return;}
    if(event.type===103){await mutate(next=>{next.files=[result,...next.files].slice(0,80);return next;});location.hash='files';toast('Video collegato.');return;}
    if(event.purpose==='sources'){const report=await api('sourceImport',{data:result.data});sources=await api('sources');toast(report.updated+' indirizzi aggiornati.');}
    else{let report;await mutate(next=>{report=M.importLibrary(next,result.data);return report.data;});currentPlan=null;toast(report.added+' titoli importati.');}
    navigate(page);
  }
  async function removeTitle(id,button){
    const row=data.items.find(i=>i.id===id);if(!row)return;
    if(button.dataset.confirm!=='yes'){button.dataset.confirm='yes';button.textContent='Conferma rimozione';button.className='danger';return;}
    await mutate(next=>{next.items=next.items.filter(i=>i.id!==id);return next;});
    currentPlan=null;if(sheet.contains(button)&&sheet.open)await closeSheet(true);toast('Titolo rimosso dalla libreria.');
    if(page==='library')renderLibrary();if(page==='catalog')updateResults();if(page==='tonight')renderTonight();
  }
  document.addEventListener('submit',event=>{
    const form=event.target;
    if(form.id==='source-form'){event.preventDefault();sourceQuery=$('#source-query').value.trim();selectedSource=$('#source-choice').value;providerKind=$('#provider-kind').value;providerQuality=Number($('#provider-quality').value);runProviderSearch(sourceQuery);}
    if(form.id==='source-address-form'){event.preventDefault();const button=form.querySelector('button');button.disabled=true;api('sourceUpdate',{id:form.dataset.source,url:form.elements.url.value}).then(async()=>{sources=await api('sources');if(sheet.contains(form)&&sheet.open){await closeSheet(true);if(page==='search')renderSources();}else if($('#source-list'))updateSourceCards();toast('Indirizzo aggiornato.');}).catch(e=>{const note=form.querySelector('#source-error');if(form.isConnected&&note){note.textContent=e.message;note.hidden=false;}else toast(e.message);}).finally(()=>{button.disabled=false;});}
    if(form.id==='search-form'){event.preventDefault();runSearch($('#search-query').value);}
    if(form.id==='title-form'){event.preventDefault();saveTitle(form);}
    if(form.id==='tonight-form'){event.preventDefault();try{createPlan(form);}catch(e){toast(e.message);}}
    if(form.id==='download-form'){
      event.preventDefault();const button=form.querySelector('button[type=submit]');button.disabled=true;
      const payload=formValues(form);payload.wifiOnly=data.preferences.wifiOnly;
      api('download',payload).then(async()=>{if(sheet.contains(form)&&sheet.open)await closeSheet(true);toast('Video preparato. Il download è in coda.');if(page==='files')refreshDownloads();}).catch(e=>{const note=form.querySelector('#download-error');if(form.isConnected&&note){note.textContent=e.message;note.hidden=false;}else toast(e.message);}).finally(()=>{button.disabled=false;});
    }
  });
  document.addEventListener('input',event=>{if(event.target.id==='source-query')sourceQuery=event.target.value;if(event.target.id==='search-query')searchQuery=event.target.value;if(event.target.id==='source-filter'){sourceFilter=event.target.value;updateSourceCards();}if(event.target.id==='library-query'){filters.query=event.target.value;libraryLimit=100;updateLibraryGrid();}});
  document.addEventListener('change',event=>{
    if(event.target.id==='source-choice')selectedSource=event.target.value;
    if(event.target.id==='provider-kind')providerKind=event.target.value;
    if(event.target.id==='provider-quality')providerQuality=Number(event.target.value);
    if(event.target.id==='provider-season')showProviderEpisodes(Number(event.target.dataset.id),Number(event.target.value));
    if(event.target.id==='library-state')filters.state=event.target.value;
    if(event.target.id==='library-collection')filters.collection=event.target.value;
    if(event.target.id==='library-sort')filters.sort=event.target.value;
    if(event.target.id.startsWith('library-')){libraryLimit=100;updateLibraryGrid();}
  });
  document.addEventListener('pointerdown',event=>{
    const button=event.target.closest('button');if(!button||button.disabled||reduced())return;
    const ripple=document.createElement('span'),rect=button.getBoundingClientRect();ripple.className='ripple';ripple.style.left=(event.clientX-rect.left)+'px';ripple.style.top=(event.clientY-rect.top)+'px';button.append(ripple);setTimeout(()=>ripple.remove(),550);
  },{passive:true});
  document.addEventListener('click',async event=>{
    if(event.target.closest('a.skip')){event.preventDefault();main.focus({preventScroll:true});main.scrollIntoView({block:'start'});return;}
    const external=event.target.closest('[data-external]');if(external){event.preventDefault();api('openLink',{url:external.href}).catch(e=>toast(e.message));return;}
    const button=event.target.closest('[data-action]');if(!button||button.disabled)return;
    const action=button.dataset.action,id=Number(button.dataset.id);
    try{
      if(action==='close-sheet')await closeSheet();
      if(action==='keep-editing'){const warning=sheet.querySelector('.unsaved');if(warning)warning.remove();}
      if(action==='discard-edit')await closeSheet(true);
      if(action==='navigate')location.hash=button.dataset.page;
      if(action==='search-kind'){
        if(searching)cancelSearch();searchKind=button.dataset.kind;results=[];renderCatalog();
      }
      if(action==='recent'){searchQuery=button.dataset.query;$('#search-query').value=searchQuery;runSearch(searchQuery);}
      if(action==='cancel-search')cancelSearch();
      if(action==='save-result'){button.disabled=true;try{await saveResult(id);}finally{button.disabled=false;}}
      if(action==='detail')detail(id);
      if(action==='edit')editTitle(id);
      if(action==='add-manual')editTitle();
      if(action==='more-library'){libraryLimit+=100;updateLibraryGrid();}
      if(action==='delete-title')await removeTitle(id,button);
      if(action==='source'&&results[id])await api('openLink',{url:results[id].url});
      if(action==='preview'&&results[id])await api('play',{url:results[id].preview,name:results[id].name+' — Anteprima'});
      if(action==='share-result'&&results[id])await api('share',{text:results[id].name+(results[id].year?' ('+results[id].year+')':'')+(results[id].url?'\n'+results[id].url:'')});
      if(action==='another-plan')createPlan($('#tonight-form'),true);
      if(action==='share-plan'&&currentPlan)await api('share',{text:'La nostra serata con VibraVid\n'+currentPlan.items.map(i=>i.name+' — '+i.duration+' min').join('\n')+'\nTotale: '+currentPlan.minutes+' minuti.'});
      if(action==='import'){button.disabled=true;try{await importLibrary();}finally{button.disabled=false;}}
      if(action==='export'){
        button.disabled=true;try{await saveChain;const response=await api('export',{data:M.exportLibrary(data)},{picker:true});if(!response.cancelled)toast('Libreria esportata.');}finally{button.disabled=false;}
      }
      if(action==='preference'){
        const key=button.dataset.key;button.disabled=true;
        try{await mutate(next=>{next.preferences[key]=key==='theme'?(next.preferences.theme==='light'?'cinema':'light'):!next.preferences[key];return next;});if(page==='settings')renderSettings();}finally{button.disabled=false;}
      }
      if(action==='appearance'){
        const key=button.dataset.key,value=button.dataset.value;
        if(!['layout','palette'].includes(key)||!(key==='layout'?M.layouts:M.palettes).some(option=>option.id===value))return;
        const group=button.closest('[role="group"]');
        group.querySelectorAll('button').forEach(control=>{control.disabled=true;});
        try{
          await mutate(next=>{next.preferences[key]=value;if(key==='palette'&&M.palettes.find(option=>option.id===value).light)next.preferences.theme='light';return next;});
          if(page==='settings'&&main.contains(group)){
            group.querySelectorAll('button').forEach(control=>control.setAttribute('aria-pressed',String(control.dataset.value===data.preferences[key])));
            $('#appearance-summary').textContent=`${M.layouts.find(o=>o.id===data.preferences.layout).name} · ${M.palettes.find(o=>o.id===data.preferences.palette).name}. Locandine sempre a colori.`;
            const cinema=main.querySelector('[data-key="theme"]');if(cinema)cinema.setAttribute('aria-checked',String(data.preferences.theme==='cinema'));
          }
        }finally{group.querySelectorAll('button').forEach(control=>{control.disabled=false;});}
      }
      if(action==='open-source')await openSource(button.dataset.source);
      if(action==='manage-sources')manageSources();
      if(action==='cancel-provider-search')cancelProviderSearch();
      if(action==='cancel-resolve')cancelProviderResolve();
      if(action==='provider-download')await downloadProvider(id);
      if(action==='provider-episodes')await showProviderEpisodes(id);
      if(action==='provider-episode-download'){const episode=providerEpisodeRows[id];if(episode){await closeSheet(true);await downloadProvider(providerEpisodeIndex,episode.key);}}
      if(action==='edit-source')editSource(button.dataset.source);
      if(action==='import-sources')await importSources();
      if(action==='media-repair'){await api('mediaRepair',{id:button.dataset.id});toast('Controllo le parti salvate e recupero quelle mancanti.');setTimeout(refreshDownloads,500);}
      if(action==='media-play')await api('mediaPlay',{id:button.dataset.id});
      if(action==='media-pause'){await api('mediaPause',{id:button.dataset.id,paused:button.dataset.paused==='true'});setTimeout(refreshDownloads,500);}
      if(action==='media-remove'){if(!removalConfirmations.has(button.dataset.id)){removalConfirmations.add(button.dataset.id);button.dataset.confirm='yes';button.textContent='Conferma rimozione';return;}await api('mediaRemove',{id:button.dataset.id});removalConfirmations.delete(button.dataset.id);setTimeout(refreshDownloads,500);}
      if(action==='new-download')newDownload();
      if(action==='refresh-downloads')refreshDownloads();
      if(action==='pick-video'){
        button.disabled=true;try{const file=await api('pickVideo',{}, {picker:true});if(!file.cancelled){await mutate(next=>{next.files=[file,...next.files].slice(0,80);return next;});if(page==='files'){renderFiles();refreshDownloads();}toast('Video collegato. Il file originale resta al suo posto.');}}finally{button.disabled=false;}
      }
      if(action==='play-file')await api('play',{token:button.dataset.token,name:(data.files.find(f=>f.token===button.dataset.token)||{}).name});
      if(action==='forget-file'){await mutate(next=>{next.files=next.files.filter(f=>f.token!==button.dataset.token);return next;});if(page==='files'){renderFiles();refreshDownloads();}toast('Collegamento rimosso. Il video resta sul telefono.');}
      if(action==='open-download')await api('openDownload',{id,name:button.dataset.name});
      if(action==='remove-download'){
        if(!removalConfirmations.has(String(id))){removalConfirmations.add(String(id));button.dataset.confirm='yes';button.textContent=button.dataset.complete==='true'?'Conferma eliminazione':'Conferma annullamento';button.className='danger';return;}
        await api('cancelDownload',{id});removalConfirmations.delete(String(id));refreshDownloads();
      }
      if(action==='licenses'){
        openSheet('Licenze e fonti',`<p class="note">VibraVid Studio Android: GNU GPL v3. Il codice completo accompagna l’APK.</p><p class="note">AndroidX Media3 e dipendenze AndroidX: Apache License 2.0. Nunito Sans e Bricolage Grotesque: SIL Open Font License. Font e licenze sono inclusi localmente.</p><p class="note">TVmaze: metadati CC BY-SA, con attribuzione nelle ricerche. Wikidata: metadati CC0. Wikimedia Commons: crediti degli autori e licenze delle immagini nei dettagli.</p><div class="row wrap"><button class="secondary" data-action="license-text" data-file="GPL.txt">GPL v3</button><button class="secondary" data-action="license-text" data-file="APACHE-2.0.txt">Apache 2.0</button><button class="secondary" data-action="license-text" data-file="THIRD-PARTY-NOTICES.txt">Componenti</button><button class="secondary" data-action="license-text" data-file="fonts/nunito-sans-OFL.txt">Nunito Sans</button><button class="secondary" data-action="license-text" data-file="fonts/bricolage-grotesque-OFL.txt">Bricolage</button></div>`);
      }
      if(action==='license-text'){
        const revision=sheetRevision;
        const response=await fetch(button.dataset.file);if(!response.ok)throw new Error('Licenza non disponibile.');
        const text=await response.text();if(revision===sheetRevision&&sheet.open&&button.isConnected)openSheet('Testo della licenza',`<p class="note license-text">${esc(text)}</p>`);
      }
      if(action==='reload')location.reload();
    }catch(error){toast(error.message);}
  });
  $('#open-settings').addEventListener('click',()=>{location.hash='settings';});
  window.addEventListener('hashchange',()=>{if(sheet.open)closeSheet(true);navigate(location.hash.slice(1));});
  document.addEventListener('visibilitychange',()=>{if(!document.hidden&&page==='files')refreshDownloads();});
  const viewport=window.visualViewport;
  if(viewport)viewport.addEventListener('resize',()=>{document.body.classList.toggle('keyboard',viewport.height<window.innerHeight*.72);});
  window.VibraApp={
    back(){if(sheet.open){closeSheet();return true;}if(page!=='search'){location.hash='search';return true;}if(providerSearching){cancelProviderSearch();return true;}if(resolveRequest){cancelProviderResolve();return true;}if(searching){cancelSearch();return true;}return false;},
    // A diagnostic snapshot contains counts and UI state, never notes or credentials.
    diagnostics(){return {page,library:data?data.items.length:0,collections:data?data.collections.length:0,results:results.length,providerResults:providerResults.length,providerSearching,providerBusy,searching,theme:data?data.preferences.theme:null,native:!!native,ready:!!data&&!loadFailed};}
  };
  (async()=>{
    try{data=M.hydrate(await api('load'));sources=await api('sources');applyPreferences();navigate(location.hash.slice(1)||'search');if(recoveredPicker){const result=recoveredPicker;recoveredPicker=null;recoverPicker(result).catch(error=>toast(error.message));}}
    catch(error){loadFailed=true;main.innerHTML=`<section class="empty"><h1>La libreria non si apre.</h1><p>${esc(error.message)}</p><p>I dati salvati non sono stati sostituiti.</p><button class="primary" data-action="reload">Riprova</button></section>`;}
  })();
})();
