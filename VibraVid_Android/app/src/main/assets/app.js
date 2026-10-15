'use strict';
(() => {
  const L = window.VibraI18n, M = window.VibraModel, $ = selector => document.querySelector(selector);
  const main = $('#main'), sheet = $('#sheet'), sheetContent = $('#sheet-content');
  if('scrollRestoration' in history)history.scrollRestoration='manual';
  const native = window.VibraAndroid, callbacks = new Map();
  const sessionId='v'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,12)+'-';
  let sequence = 0, data, page = '', loadFailed = false, saveChain = Promise.resolve(), toastTimer;
  let results = [], searchKind = 'movie', searchQuery = '', searchGeneration = 0, searchRequest = '', searching = false;
  let filters = {query: '', kind:'', state: '', collection: '', sort: 'recent'}, currentPlan = null, downloadTimer, libraryLimit = 100;
  let sheetDirty = null, lastFocus = null, sheetClosing = false, sheetRevision = 0, toastRevision = 0, sheetMotion = null;
  let recoveredPicker = null;
  const Motion=window.VibraMotion, E=window.VibraExperience;
  let browse={movies:[],series:[]}, browseRequest=null, browseUpdated=0, homeRows=[], activeCatalogRow=null, pendingSourceQuery='', pageMotion=null;
  const positions=new Map(), railPositions=new Map(), browseQueries={movie:'',series:''};
  let appearanceRevision = Date.now()*1000;
  const removalConfirmations = new Set();
  let sources = [], sourceQuery = '', sourceFilter = '', selectedSource = '__all__', lastWifi;
  let providerResults=[],providerSearching=false,providerRequest='',providerGeneration=0,providerMessage='',providerWarnings=[];
  let quickDownload=false, providerFilter='all', settingsTab='general', providerResultQuery='', providerResultScope='__all__';
  let providerKind='all',providerQuality=720,providerBusy=false,resolveRequest='',resolveGeneration=0,resolveStage='',providerEpisodeRows=[],providerEpisodeIndex=-1;
  const icons = name => `<svg aria-hidden="true"><use href="#icon-${name}"/></svg>`;
  const esc = text => Array.from(String(text === undefined || text === null ? '' : text), ch => '&#'+ch.codePointAt(0)+';').join('');
  const kindName = kind => ({movie:L.text('Film'),series:L.text('Serie'),anime:L.text('Anime'),song:L.text('Brano'),album:L.text('Album'),book:L.text('Libro'),live:L.text('Live')}[kind] || L.text('Titolo'));
  const stateName = state => ({planned:L.text('Da vedere'),watching:L.text('In visione'),watched:L.text('Visto')}[state]);
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
        if (action === 'search' || action === 'titleDetails' || action.startsWith('provider')) native.request('cancel-' + id, 'cancel', JSON.stringify({id}));
        reject(new Error(L.text('L’operazione sta impiegando troppo tempo. Riprova.')));
      }, options.picker ? 15 * 60 * 1000 : options.timeout || 35000);
      callbacks.set(id, {resolve, reject, timeout});
      try { native.request(id, action, JSON.stringify(payload)); }
      catch { clearTimeout(timeout); callbacks.delete(id); reject(new Error(L.text('Il collegamento con Android non è disponibile. Riapri l’app.'))); }
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
      if (page === 'files') refreshDownloads();if(page==='library')refreshPlayback();
      return;
    }
    const request = callbacks.get(message.id);
    if (!request) return;
    clearTimeout(request.timeout); callbacks.delete(message.id);
    if (message.ok) request.resolve(message.value);
    else request.reject(new Error(message.value || L.text('Operazione non riuscita.')));
  };
  async function browserPreview(action, payload) {
    if (action === 'load') return JSON.parse(localStorage.getItem('vv.android.preview') || JSON.stringify(M.empty()));
    if (action === 'save') { localStorage.setItem('vv.android.preview', JSON.stringify(payload)); return {saved:true}; }
    if (action === 'theme' || action === 'mediaRequirements') return {};
    if (action === 'sources') { const response=await fetch('providers.json'); if(!response.ok)throw new Error(L.text('Le fonti non si caricano.'));return (await response.json()).map(s=>({...s,automatic:['streamingcommunity','streamingcommunity_2','animeunity','animeworld','eurostreaming','raiplay'].includes(s.id)})); }
    if (action === 'downloads') return [];
    if (action === 'playbackProgress') return {};
    if (action === 'titleDetails') return browserTitleDetails(payload);
    if (action === 'discover') {const response=await fetch('discovery.json');if(!response.ok)throw new Error(L.text('Catalogo non disponibile.'));return response.json();}
    throw new Error(L.text('Questa funzione usa Android ed è disponibile nell’APK.'));
  }
  async function browserTitleDetails(row){
    if(['series','anime'].includes(row.kind)){
      const id=(row.catalog_id||'').match(/^tvmaze:(\d+)$/)?.[1]||(row.url||'').match(/tvmaze.com\/shows\/(\d+)/)?.[1];if(!id)return {};
      const response=await fetch('https://api.tvmaze.com/shows/'+id+'?embed[]=cast&embed[]=episodes');if(!response.ok)throw new Error('Metadata unavailable');const value=await response.json(),groups=new Map();
      (value._embedded?.episodes||[]).forEach(ep=>{if(!groups.has(ep.season))groups.set(ep.season,[]);groups.get(ep.season).push({number:ep.number,name:ep.name,description:stripMarkup(ep.summary||''),duration:ep.runtime,airdate:ep.airdate});});
      return {description:stripMarkup(value.summary||''),catalog_rating:value.rating?.average,cast:(value._embedded?.cast||[]).slice(0,24).map(a=>({name:a.person.name,role:a.character.name,image:a.person.image?.medium||''})),seasons:[...groups].map(([number,episodes])=>({number,episodes})),metadata_source:'TVmaze · CC BY-SA'};
    }return {};
  }
  function stripMarkup(value){return new DOMParser().parseFromString(String(value),'text/html').body.textContent||'';}
  function mutate(fn) {
    const task = saveChain.catch(() => {}).then(async () => {
      if (loadFailed) throw new Error(L.text('La libreria non è stata caricata. Riapri l’app prima di modificarla.'));
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
    const element = $('#toast'); element.textContent = L.text(message); element.hidden = false;
    animate(element, [{opacity:0,transform:'translateY(10px)'},{opacity:1,transform:'translateY(0)'}], {duration:300});
    toastTimer = setTimeout(() => {
      const out = animate(element, [{opacity:1},{opacity:0,transform:'translateY(6px)'}], {duration:180});
      if (out) finishMotion(out).then(() => { if (revision === toastRevision) element.hidden = true; }).catch(() => {}); else element.hidden = true;
    }, 4200);
  }
  function applyPreferences() {
    L.select(data.preferences.language); L.shell();
    document.documentElement.dataset.theme = data.preferences.theme;
    document.documentElement.dataset.layout = data.preferences.layout;
    document.documentElement.dataset.palette = data.preferences.palette;
    document.documentElement.dataset.scheme = M.lightPalette(data.preferences)?'light':'dark';
    document.documentElement.style.colorScheme = M.lightPalette(data.preferences)?'light':'dark';
    const colors=M.paletteColors(data.preferences);
    Object.keys(colors).forEach(key=>document.documentElement.style.setProperty('--'+key,colors[key]));
    document.querySelector('meta[name="theme-color"]').content=colors.paper;
    document.documentElement.dataset.reduced = String(data.preferences.reducedMotion);
    api('theme', {dark:data.preferences.theme === 'cinema',light:M.lightPalette(data.preferences),language:data.preferences.language,reducedMotion:!!data.preferences.reducedMotion,background:colors.paper,revision:++appearanceRevision}).catch(() => {});
    if(lastWifi!==data.preferences.wifiOnly){lastWifi=data.preferences.wifiOnly;api('mediaRequirements',{wifiOnly:lastWifi}).catch(e=>toast(e.message));}
  }
  function sheetHeading(title) { return `<div class="sheet-heading"><h2 id="sheet-title">${esc(title)}</h2><button type="button" class="icon-button" data-action="close-sheet" aria-label="Chiudi">${icons('close')}</button></div>`; }
  function openSheet(title, body, dirty = null) {
    const wasOpen=sheet.open;
    stopTrailers();Motion.stop();
    ++sheetRevision;
    delete sheet.dataset.result; delete sheet.dataset.cinema;
    if(sheetMotion)sheetMotion.cancel();sheetMotion=null;
    if (sheet.getAnimations) sheet.getAnimations().forEach(motion => motion.cancel());
    sheetDirty = dirty; sheetClosing = false; if(!sheet.open)lastFocus = document.activeElement;
    sheetContent.innerHTML = L.html(sheetHeading(title) + `<div class="sheet-body">${body}</div>`);
    if (!sheet.open) sheet.showModal();
    sheet.scrollTop = 0;document.documentElement.dataset.modal='true';
    sheetMotion=animate(sheet, wasOpen?[{opacity:.75},{opacity:1}]:[{opacity:0,transform:'translateY(24px) scale(.98)'},{opacity:1,transform:'translateY(0) scale(1)'}], {duration:wasOpen?160:300});
    requestAnimationFrame(() => { if(!sheet.open)return;const focus = sheet.querySelector('[autofocus]') || sheet.querySelector('[data-action="close-sheet"]'); if (focus) focus.focus({preventScroll:true}); });
  }
  async function closeSheet(force = false) {
    if (!sheet.open || sheetClosing) return;
    if (!force && sheetDirty && sheetDirty()) {
      let warning = sheet.querySelector('.unsaved');
      if (!warning) {
        warning = document.createElement('div'); warning.className = 'unsaved quiet-card';
        warning.innerHTML = L.html('<h3>Hai modifiche da salvare</h3><p>Puoi continuare a modificarle oppure chiudere senza salvarle.</p><div class="row wrap"><button class="secondary" data-action="keep-editing">Continua</button><button class="danger" data-action="discard-edit">Chiudi senza salvare</button></div>');
        sheet.querySelector('.sheet-body').append(warning);
      }
      warning.scrollIntoView({block:'nearest',behavior:reduced()?'auto':'smooth'}); warning.querySelector('button').focus(); return;
    }
    stopTrailers();sheetClosing = true;
    const revision = ++sheetRevision;
    if(sheetMotion)sheetMotion.cancel();const shared=Motion.collapse(lastFocus,sheet.querySelector('.hero-art'));
    const motion = sheetMotion=animate(sheet, [{opacity:1},{opacity:0}], {duration:280,fill:'forwards'});
    await Promise.all([motion?finishMotion(motion).catch(()=>{}):Promise.resolve(),shared]);
    if (revision !== sheetRevision) return;
    if(motion)motion.cancel();sheetMotion=null;
    sheet.close();Motion.stop();delete sheet.dataset.expanded; delete document.documentElement.dataset.modal;sheetDirty = null; sheetClosing = false;
    if (lastFocus && lastFocus.isConnected) lastFocus.focus({preventScroll:true});
  }
  sheet.addEventListener('cancel', event => { event.preventDefault(); closeSheet(); });
  sheet.addEventListener('click', event => { if (event.target === sheet) { const r = sheet.getBoundingClientRect(); if (event.clientY < r.top || event.clientX < r.left || event.clientX > r.right) closeSheet(); } });
  function saved(row) { return data.items.find(i => M.identity(i) === M.identity(row)); }
  function poster(row, badge = false) {
    return `<div class="poster">${row.poster ? `<img src="${esc(row.poster)}" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer">` : icons('library')}${badge ? `<span class="kind-badge">${esc(kindName(row.kind))}</span>${row.year?`<span class="year-badge">${esc(row.year)}</span>`:''}` : ''}</div>`;
  }
  function card(row, index, library = false) {
    const watched=library?E.progress(row,playbackRecords):null;
    const stored = library ? row : saved(row), label = [row.year, row.duration ? row.duration + L.text(' min') : ''].filter(Boolean).join(' · ');
    return `<article class="title-card${library?' library-card':''}"><button type="button" class="card-open" data-action="${library?'library-detail':'detail'}" data-id="${library?row.id:index}" aria-label="${L.text('Dettagli di')} ${esc(row.name)}">${poster(row,true)}<div class="card-copy"><div class="card-title">${esc(row.name)}</div><div class="card-meta">${esc(label || (row.sources || []).join(', '))}</div>${library?`<div class="reading-state">${esc(stateName(row.state))}${row.rating?' · '+'★'.repeat(row.rating):''}</div>${watched?`<progress class="watch-progress" value="${watched.percent}" max="100" aria-label="${L.text('Avanzamento di visione')}"></progress><span class="watch-caption">${watched.percent}% ${L.text(['series','anime'].includes(row.kind)?'dell’ultimo episodio':'visto')}</span>`:''}`:''}</div></button>${library?'':`<button class="card-save" data-action="save-result" data-id="${index}" data-saved="${!!stored}" aria-label="${stored?L.text('Salvato in libreria:'):L.text('Salva in libreria:')} ${esc(row.name)}">${icons(stored?'check':'plus')}</button>`}</article>`;
  }
  function preparePosters(container = main) {
    container.querySelectorAll('.cast-person img').forEach(image=>{if(!image.dataset.bound){image.dataset.bound='true';image.addEventListener('error',()=>image.remove(),{once:true});}});
    container.querySelectorAll('.poster').forEach(p => {
      const image = p.querySelector('img'); if (image && !image.dataset.fallbackBound) { image.dataset.fallbackBound='true'; image.addEventListener('error', () => { image.remove(); p.insertAdjacentHTML('afterbegin', L.html(icons('library'))); }, {once:true}); }
    });
  }
  function rememberPage(){
    if(page){positions.set(page,window.scrollY);main.querySelectorAll('[data-rail]').forEach(rail=>railPositions.set(rail.dataset.rail,rail.scrollLeft));}
  }
  function navigate(next) {
    if (!data || loadFailed) return;
    if (!['home','movies','series','search','catalog','library','tonight','files','settings'].includes(next)) next='home';
    stopTrailers();++downloadsGeneration;rememberPage();const previous=page;clearInterval(downloadTimer);page=next;main.dataset.page=next;
    document.querySelectorAll('.bottom-nav a').forEach(a=>{if(a.dataset.page===(['movies','series','search','catalog','tonight'].includes(page)?'home':page))a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');});
    if(next==='home'){renderHome();loadBrowse();}
    if(next==='movies'||next==='series'){renderBrowse(next==='movies'?'movie':'series');loadBrowse();}
    if(next==='search')renderSources();
    if(next==='catalog')renderCatalog();
    if(next==='library'){renderLibrary();refreshPlayback();}
    if(next==='tonight')renderTonight();
    if(next==='files'){renderFiles();refreshDownloads();downloadTimer=setInterval(()=>{if(!document.hidden)refreshDownloads();},4000);}
    if(next==='settings')renderSettings();
    window.scrollTo({top:positions.get(next)||0,behavior:'auto'});preparePosters();
    if(pageMotion)pageMotion.cancel();
    if(previous&&previous!==next)pageMotion=animate(main,[{opacity:0,transform:'translateY(7px)'},{opacity:1,transform:'translateY(0)'}],{duration:200});
    if(next==='search'&&pendingSourceQuery){const query=pendingSourceQuery;pendingSourceQuery='';runProviderSearch(query);}
  }
  function validBrowse(value){
    const clean=rows=>(Array.isArray(rows)?rows:[]).slice(0,100).flatMap(row=>{try{return [M.clean(row)];}catch{return [];}});
    return {movies:clean(value.movies),series:clean(value.series)};
  }
  async function loadBrowse(){
    if(browseRequest||Date.now()-browseUpdated<6*60*60*1000)return;
    browseRequest=(async()=>{
      try{
        if(!browse.movies.length&&!browse.series.length){const snapshot=await fetch('discovery.json');if(snapshot.ok){browse=validBrowse(await snapshot.json());if(!sheet.open){if(page==='home')refreshHomeShelves();else if(page==='movies'||page==='series')renderBrowse(page==='movies'?'movie':'series');}}}
        const response=await api('discover',{}, {timeout:22000});const next=validBrowse(response);
        if(next.movies.length||next.series.length){browse=next;browseUpdated=Date.now();if(!sheet.open){if(page==='home')refreshHomeShelves();else if(page==='movies'||page==='series')renderBrowse(page==='movies'?'movie':'series');}}
      }catch(error){browseUpdated=Date.now()-6*60*60*1000+60000;}
      finally{browseRequest=null;}
    })();
    return browseRequest;
  }
  function homeShelf(name,rows,key,kind){
    const entries=rows.map(row=>{const index=homeRows.push(row)-1;return {row,index};});
    return `<section class="home-shelf"><div class="shelf-heading"><h2>${L.text(name)}</h2><div class="shelf-tools"><button class="shelf-all" data-action="browse-kind" data-kind="${kind}">${L.text('Tutti')}${icons('chevron')}</button><button class="rail-arrow" data-action="rail-step" data-rail-target="${key}" data-step="-1" aria-label="${L.text('Indietro')}">${icons('chevron')}</button><button class="rail-arrow" data-action="rail-step" data-rail-target="${key}" data-step="1" aria-label="${L.text('Avanti')}">${icons('chevron')}</button></div></div><div class="poster-rail" data-rail="${key}">${entries.length?entries.map(({row,index})=>`<article class="rail-card"><button class="home-open card-open" data-action="home-detail" data-id="${index}" aria-label="${L.text('Dettagli di')} ${esc(row.name)}">${poster(row)}<span class="rail-card-name">${esc(row.name)}</span></button></article>`).join(''):`<div class="rail-empty"><p>${L.text('Cerca un titolo per iniziare.')}</p><button class="secondary" data-action="navigate" data-page="search">${L.text('Cerca')}</button></div>`}</div></section>`;
  }
  function renderHome(){
    main.innerHTML=L.html(`<form id="home-search" class="home-search" role="search"><label>${icons('search')}<span class="sr-only">Cerca un titolo</span><input name="query" type="search" placeholder="Film, serie, anime" minlength="2" maxlength="200" autocomplete="off" required enterkeyhint="search"></label><button type="submit" aria-label="Cerca">${icons('arrow')}</button></form><div class="home-category-links"><a href="#home-movie" data-action="home-section" data-section="home-movie">Film</a><a href="#home-series" data-action="home-section" data-section="home-series">Serie TV</a></div><div id="home-shelves"></div><p class="browse-credit"><a href="https://www.apple.com/itunes/" data-external>Apple Movies</a> · <a href="https://www.tvmaze.com/" data-external>TVmaze</a>. ${L.text('La disponibilità viene verificata nelle fonti.')}</p>`);
    refreshHomeShelves();
  }
  function refreshHomeShelves(){
    if(page!=='home')return;
    main.querySelectorAll('[data-rail]').forEach(rail=>railPositions.set(rail.dataset.rail,rail.scrollLeft));homeRows=[];
    const movies=browse.movies.length?browse.movies:data.items.filter(r=>r.kind==='movie');
    const series=browse.series.length?browse.series:data.items.filter(r=>r.kind==='series'||r.kind==='anime');
    const section=(title,rows,kind)=>`<section class="catalog-section" id="home-${kind}"><div class="section-head"><h1>${L.text(title)}</h1><button class="shelf-all" data-action="browse-kind" data-kind="${kind}">${L.text('Tutti')}${icons('chevron')}</button></div>${E.groups(rows).map((group,i)=>homeShelf(group.name,group.rows,kind+'-'+i,kind)).join('')||`<p class="note">${L.text('Carico i titoli…')}</p>`}</section>`;
    $('#home-shelves').innerHTML=section('Film',movies,'movie')+section('Serie TV',series,'series')+(data.items.length?homeShelf('Dalla tua libreria',data.items.slice(-16).reverse(),'saved','library'):'');
    main.querySelectorAll('[data-rail]').forEach(rail=>{rail.scrollLeft=railPositions.get(rail.dataset.rail)||0;});preparePosters();
  }
  function renderBrowse(kind){
    const focused=document.activeElement?.id==='browse-query';
    homeRows=(kind==='movie'?browse.movies:browse.series).slice();
    main.innerHTML=L.html(`<section class="page-intro"><div class="page-title-row"><h1>${L.text(kind==='movie'?'Film':'Serie TV')}</h1><a href="#home" class="icon-button" aria-label="Home">${icons('home')}</a></div></section><div class="browse-categories"><a class="scope-chip" href="#movies" ${kind==='movie'?'aria-current="page"':''}>Film</a><a class="scope-chip" href="#series" ${kind==='series'?'aria-current="page"':''}>Serie TV</a></div><form id="browse-search" class="browse-search" role="search"><label class="sr-only" for="browse-query">Cerca un titolo</label><input id="browse-query" name="query" type="search" minlength="2" maxlength="200" placeholder="Cerca un titolo" value="${esc(browseQueries[kind])}" required enterkeyhint="search"><button class="primary" type="submit">Cerca</button></form><p class="browse-search-note">Cerca in tutte le fonti, anche oltre i titoli mostrati.</p><div id="browse-grid" class="browse-grid"></div><p class="browse-credit">${L.text('La disponibilità viene verificata nelle fonti.')}</p>`);
    updateBrowseGrid(kind);if(focused)$('#browse-query').focus({preventScroll:true});
  }
  function updateBrowseGrid(kind){
    const query=browseQueries[kind].trim().toLocaleLowerCase('it'),rows=homeRows.map((row,index)=>({row,index})).filter(({row})=>!query||row.name.toLocaleLowerCase('it').includes(query));
    $('#browse-grid').innerHTML=rows.length?`<div class="poster-grid">${rows.map(({row,index})=>`<article class="title-card"><button class="card-open" data-action="home-detail" data-id="${index}" aria-label="${L.text('Dettagli di')} ${esc(row.name)}">${poster(row,true)}<div class="card-title">${esc(row.name)}</div></button></article>`).join('')}</div>`:L.html('<div class="empty"><p>Nessun titolo con questi filtri.</p></div>');
    preparePosters($('#browse-grid'));
  }
  const titleCache=new Map();let detailRequest='',detailSession=null,similarRows=[],playbackRecords={};
  function stopTrailers(){if(detailRequest){const id=detailRequest;detailRequest='';abortRequest(id);}sheet.querySelectorAll('video').forEach(video=>{video.pause();video.removeAttribute('src');video.load();});}
  async function refreshPlayback(){
    try{const rows=await api('playbackProgress');if(rows&&typeof rows==='object'&&!Array.isArray(rows)){playbackRecords=rows;if(page==='library'&&!sheet.open)updateLibraryGrid();}}catch{}
  }
  function detailActions(row,context){
    const stored=saved(row);
    if(context.provider!==undefined)return `<button class="primary" data-action="${row.kind==='movie'?'provider-download':'provider-episodes'}" data-id="${context.provider}">${icons(row.kind==='movie'?'download':'play')}${L.text(row.kind==='movie'?'Scarica':'Scegli episodio')}</button><button class="secondary" data-action="provider-save" data-id="${context.provider}">${icons(stored?'check':'plus')}${L.text(stored?'In libreria':'Salva in libreria')}</button>`;
    return `<button class="primary" data-action="catalog-search">${icons('search')}${L.text('Cerca nelle fonti')}</button><button class="secondary" data-action="${stored?'edit':'catalog-save'}" ${stored?`data-id="${stored.id}"`:''}>${icons(stored?'check':'plus')}${L.text(stored?'Modifica':'La mia lista')}</button>`;
  }
  function richHero(row,context){
    return `<section class="title-hero has-trailer"><div class="trailer-stage"><div class="trailer-fallback">${row.poster?`<img class="hero-art" src="${esc(row.backdrop||row.poster)}" alt="" referrerpolicy="no-referrer">`:icons('play')}</div></div><div class="hero-copy"><div class="hero-meta"><span>${esc(kindName(row.kind))}</span>${context.provider!==undefined?`<span>${esc(row.source)}</span>`:''}</div><h3>${esc(row.name)}</h3><div class="hero-actions">${detailActions(row,context)}</div></div></section>`;
  }
  function richBody(row,details={}){
    const score=row.catalog_rating,series=['series','anime'].includes(row.kind),seasons=details.seasons||[];
    similarRows=E.similar(row,[...browse.movies,...browse.series,...data.items]);
    return `<div class="detail-facts">${row.year?`<span>${row.year}</span>`:''}${row.duration?`<span>${row.duration} ${L.text(series?'min / episodio':'min')}</span>`:''}${score?`<span class="score">★ ${Number(score).toFixed(1)} / 10</span>`:`<span>${L.text('Valutazione non disponibile')}</span>`}${row.rating?`<span>${L.text('La tua valutazione')}: ${'★'.repeat(row.rating)}</span>`:''}${row.tags?.length?`<span>${esc(row.tags.join(' · '))}</span>`:''}</div>
      <h3>${L.text('Trama')}</h3><p class="description">${esc(row.description||L.text('La trama non è disponibile da questa fonte.'))}</p>
      ${row.director?`<p class="detail-meta-note">${L.text('Regia')}: ${esc(row.director)}</p>`:''}
      <h3>${L.text('Cast')}</h3>${row.cast?.length?`<div class="detail-cast">${row.cast.map(actor=>`<div class="cast-person">${actor.image?`<img src="${esc(actor.image)}" alt="" loading="lazy" referrerpolicy="no-referrer">`:''}<strong>${esc(actor.name)}</strong>${actor.role?`<small>${esc(actor.role)}</small>`:''}</div>`).join('')}</div>`:`<p class="detail-meta-note">${L.text('Cast non disponibile da questa fonte.')}</p>`}
      ${series?`<section class="detail-episodes"><h3>${L.text('Stagioni ed episodi')}</h3>${seasons.length?seasons.map((season,i)=>`<details data-season-index="${i}" ${i===0?'open':''}><summary>${L.text('Stagione')} ${Number(season.number)} <small>(${season.episodes.length} ${L.text('episodi')})</small></summary><div class="metadata-episodes">${i===0?metadataEpisodes(season):''}</div></details>`).join(''):`<p class="detail-meta-note">${L.text('L’elenco degli episodi non è disponibile da questa fonte.')}</p>`}</section>`:''}
      <h3>${L.text('Simili')}</h3>${similarRows.length?`<div class="detail-similar">${similarRows.map((item,i)=>`<button data-action="similar-detail" data-id="${i}">${poster(item)}<strong>${esc(item.name)}</strong></button>`).join('')}</div>`:`<p class="detail-meta-note">${L.text('Nessun titolo simile nel catalogo caricato.')}</p>`}
      <p class="attribution">${esc(row.metadata_source||(row.sources||[]).join(', ')||row.source||'')}${row.cast_source?` · ${esc(row.cast_source)}`:''}${row.image_source?` · <a href="${esc(row.image_source)}" data-external>${L.text('Crediti immagini')}</a>`:''}</p>
      <div class="detail-tools">${row.url?`<a class="link-button" href="${esc(row.url)}" data-external>${L.text('Apri la scheda originale')}</a>`:''}${detailSession?.context.result!==undefined?`<button class="link-button" data-action="share-result" data-id="${detailSession.context.result}">${L.text('Condividi')}</button>`:''}</div><p class="note">${L.text('La disponibilità viene verificata nelle fonti.')}</p>`;
  }
  function metadataEpisodes(season){return season.episodes.map(ep=>`<article class="metadata-episode"><span>${Number(ep.number)||'—'}</span><div><strong>${esc(ep.name)}</strong>${ep.description?`<p>${esc(ep.description)}</p>`:''}${ep.airdate?`<small>${esc(ep.airdate)}</small>`:''}</div><small>${ep.duration>0?Number(ep.duration)+' '+L.text('min'):''}</small></article>`).join('');}
  function mountTrailer(row){
    const stage=sheet.querySelector('.trailer-stage');if(!stage||stage.querySelector('video')||!row.preview)return;
    const video=document.createElement('video');video.controls=true;video.muted=true;video.defaultMuted=true;video.playsInline=true;video.autoplay=true;video.preload='metadata';video.setAttribute('aria-label',L.text('Trailer di')+' '+row.name);video.setAttribute('controlslist','nodownload');video.poster=row.poster||'';
    const note=document.createElement('p');note.className='detail-meta-note trailer-note';note.textContent=L.text('Trailer · audio disattivato. Usa i controlli per attivarlo.');stage.after(note);
    const fail=()=>{if(!video.isConnected)return;video.pause();video.removeAttribute('src');video.remove();stage.querySelector('.trailer-play')?.remove();note.textContent=L.text('Trailer non disponibile. Mostro la copertina.');};
    video.addEventListener('error',fail,{once:true});video.addEventListener('ended',()=>{video.autoplay=false;});
    video.src=row.preview;stage.append(video);
    video.play().catch(()=>{if(!video.isConnected||video.error)return;const button=document.createElement('button');button.className='trailer-play';button.innerHTML=icons('play')+esc(L.text('Riproduci trailer'));button.addEventListener('click',()=>{video.play().then(()=>button.remove()).catch(fail);});stage.append(button);});
  }
  function presentTitle(row,context={}){
    if(!row)return;activeCatalogRow=row;
    const cached=titleCache.get(M.identity(row)),initial=cached?mergeDetails(row,cached):row;
    openSheet(row.name,richHero(initial,context)+'<div class="title-support" id="rich-support"></div>');sheet.dataset.cinema='true';
    detailSession={row:initial,context,details:cached||{},revision:sheetRevision};
    $('#rich-support').innerHTML=L.html(richBody(initial,cached));preparePosters(sheet);mountTrailer(initial);Motion.expand(lastFocus,sheet.querySelector('.hero-art'));
    if(cached)return;
    const revision=sheetRevision,notice=document.createElement('p');notice.className='detail-loading';notice.setAttribute('role','status');notice.textContent=L.text('Carico trailer e informazioni…');$('#rich-support').prepend(notice);
    const request=api('titleDetails',{name:row.name,kind:row.kind,year:row.year,url:row.url||'',catalog_id:row.catalog_id||''},{timeout:32000});detailRequest=request.requestId||'';request.then(details=>{
      if(titleCache.size>=40)titleCache.delete(titleCache.keys().next().value);titleCache.set(M.identity(row),details);
      if(revision!==sheetRevision||!sheet.open||sheetClosing)return;
      const merged=mergeDetails(row,details);activeCatalogRow=merged;detailSession={row:merged,context,details,revision};
      $('#rich-support').innerHTML=L.html(richBody(merged,details));mountTrailer(merged);preparePosters(sheet);
    }).finally(()=>{if(detailRequest===request.requestId)detailRequest='';}).catch(()=>{if(revision===sheetRevision&&notice.isConnected)notice.textContent=L.text('Alcune informazioni non sono disponibili. Puoi continuare con questa scheda.');});
  }
  function mergeDetails(row,details){
    const merged={...row};for(const key of ['preview','duration','description','catalog_rating','catalog_id','director','cast','cast_source','metadata_source'])if(details[key]!==undefined&&details[key]!==null&&details[key]!==''&&(!Array.isArray(details[key])||details[key].length))merged[key]=details[key];
    if(details.tags?.length)merged.tags=[...new Set([...(row.tags||[]),...details.tags])].slice(0,8);
    try{return {...row,...M.clean(merged)};}catch{return row;}
  }

  function catalogDetail(row,library=false){presentTitle(row,{library});}
  async function catalogSearch(){
    const row=activeCatalogRow;if(!row)return;if(providerBusy){toast(L.text('Preparo il tuo video'));return;}
    selectedSource='__all__';sourceQuery=row.name;providerKind=row.kind==='movie'?'movie':row.kind==='anime'?'anime':'series';pendingSourceQuery=row.name;
    await closeSheet(true);positions.set('search',0);location.hash='search';if(page==='search'){pendingSourceQuery='';renderSources();runProviderSearch(row.name);}
  }
  async function saveCatalog(){
    if(!activeCatalogRow)return;const row=activeCatalogRow,revision=sheetRevision;
    await mutate(next=>M.add(next,{...row,state:'planned'}).data);toast(L.text('Salvato in libreria.'));
    if(sheet.open&&!sheetClosing&&revision===sheetRevision){const actions=sheet.querySelector('.hero-actions');if(actions)actions.innerHTML=L.html(detailActions(detailSession?.row||row,detailSession?.context||{}));}else if(page==='library'&&!sheet.open)updateLibraryGrid();
  }
  function renderSources() {
    const automatic=sources.filter(s=>s.automatic);
    main.innerHTML=L.html(`<section class="page-intro source-intro"><h1 id="search-heading">Cerca</h1><p id="search-caption">Film, serie e anime dalle tue fonti.</p></section>
      <section class="source-hero direct-search"><form id="source-form">
      <div class="search-line"><label class="search-input"><span class="sr-only">Il titolo del film o della serie</span><input id="source-query" type="search" minlength="2" maxlength="200" required placeholder="Cerca un titolo…" value="${esc(sourceQuery)}" enterkeyhint="search" autocomplete="off"></label><button class="primary" type="submit" ${providerSearching||providerBusy?'disabled':''}>Cerca</button><button type="button" class="secondary scope-toggle" data-action="provider-toggle-scope" hidden>Cambia filtri</button></div>
      <div id="search-scope"><div class="scope-row"><div class="scope-kinds" role="group" aria-label="Tipo">${[['all','Tutto'],['movie','Film'],['series','Serie'],['anime','Anime']].map(([key,label])=>`<button type="button" class="scope-chip" data-action="provider-kind" data-value="${key}" aria-pressed="${providerKind===key}">${label}</button>`).join('')}</div><details class="search-options"><summary>Opzioni</summary><div class="search-option-fields"><label class="field"><span>Cerca su</span><select id="source-choice"><option value="__all__">Tutte le fonti automatiche</option>${automatic.map(s=>`<option value="${esc(s.id)}" ${s.id===selectedSource?'selected':''}>${esc(s.name)}</option>`).join('')}</select></label><label class="field"><span>Tipo</span><select id="provider-kind">${[['all','Tutto'],['movie','Film'],['series','Serie'],['anime','Anime']].map(([key,label])=>`<option value="${key}" ${providerKind===key?'selected':''}>${label}</option>`).join('')}</select></label><label class="field"><span>Qualità massima</span><select id="provider-quality">${[480,720,1080].map(q=>`<option value="${q}" ${providerQuality===q?'selected':''}>${q}p</option>`).join('')}</select></label><label class="quick-option"><input id="quick-download" type="checkbox" ${quickDownload?'checked':''}><span>Download rapido<small>Scarica subito se il titolo corrisponde a un solo film.</small></span></label></div></details></div>
      <div class="source-chips" role="group" aria-label="Cerca su"><button type="button" class="scope-chip" data-action="provider-source" data-value="__all__" aria-pressed="${selectedSource==='__all__'}">Tutte le fonti</button>${automatic.map(s=>`<button type="button" class="scope-chip" data-action="provider-source" data-value="${esc(s.id)}" aria-pressed="${s.id===selectedSource}">${esc(s.name)}</button>`).join('')}</div></div></form></section>
      <div id="provider-progress" class="provider-progress" role="status" ${providerBusy?'':'hidden'}><span class="progress-orbit" aria-hidden="true">${icons('download')}</span><div><strong>Preparo il tuo video</strong><p id="provider-progress-text">${esc(resolveStage)}</p></div><button class="link-button" data-action="cancel-resolve" ${resolveRequest?'':'hidden'}>Annulla</button></div>
      <div id="provider-status" class="search-status" role="status"></div><div id="provider-results"></div>
      <section class="search-footnote"><button class="link-button" data-action="manage-sources">Gestisci le fonti</button><button class="link-button" data-action="navigate" data-page="catalog">Cerca solo informazioni</button></section>`);
    updateProviderResults();
  }

  function updateProviderResults(){
    if(page!=='search')return;
    const target=$('#provider-results');if(!target)return;
    const submit=$('#source-form button[type=submit]');if(submit)submit.disabled=providerSearching||providerBusy;
    $('#provider-status').textContent=L.text(providerMessage);
    $('#search-scope').hidden=providerResults.length>0;main.querySelector('.scope-toggle').hidden=!providerResults.length;
    $('#search-heading').textContent=providerResults.length?providerResultQuery:L.text('Cerca');
    $('#search-caption').textContent=providerResults.length?providerResults.length+' '+L.text('risultati')+' · '+(providerResultScope==='__all__'?L.text('Tutte le fonti'):sources.find(s=>s.id===providerResultScope)?.name||''):L.text('Film, serie e anime dalle tue fonti.');
    if(providerSearching){target.innerHTML=L.html(`<button class="secondary" data-action="cancel-provider-search">Interrompi ricerca</button><div class="poster-grid search-skeleton" aria-hidden="true">${Array.from({length:4},()=>'<div class="skeleton"><div class="poster"></div><div class="card-title"></div></div>').join('')}</div>`);return;}
    const visible=providerResults.map((row,index)=>({row,index})).filter(({row})=>providerFilter==='all'||row.kind===providerFilter);
    target.innerHTML=L.html(providerResults.length?`<div class="result-filters" role="group" aria-label="Mostra">${[['all','Tutto'],['movie','Film'],['series','Serie'],['anime','Anime']].filter(([key])=>key==='all'||providerResults.some(r=>r.kind===key)).map(([key,label])=>`<button class="scope-chip" data-action="provider-filter" data-value="${key}" aria-pressed="${providerFilter===key}">${label}</button>`).join('')}</div><div class="provider-grid">${visible.map(({row,index})=>`<article class="provider-result"><button class="provider-open card-open" data-action="provider-detail" data-id="${index}" aria-label="${L.text('Dettagli di')} ${esc(row.name)}">${poster(row,true)}<div class="provider-copy"><h2>${esc(row.name)}</h2><span class="provider-source">${esc(row.source)}</span></div></button></article>`).join('')}</div>`:sourceQuery&&!providerBusy?'<p class="note">Cerca con il titolo completo, oppure cambia fonte o tipo di contenuto.</p>':'');
    if(providerWarnings.length)target.insertAdjacentHTML('beforeend',L.html(`<details class="provider-errors"><summary>Alcune fonti non hanno risposto</summary>${providerWarnings.map(w=>`<p class="note">${esc(w)}</p>`).join('')}</details>`));
    preparePosters(target);
  }
  function providerHero(row){
    return `<section class="title-hero">${row.backdrop||row.poster?`<img class="hero-art" src="${esc(row.backdrop||row.poster)}" alt="" referrerpolicy="no-referrer">`:''}<div class="hero-shade"></div><div class="hero-copy"><div class="hero-meta"><span class="type-tag">${esc(kindName(row.kind))}</span><span>${esc(row.source)}</span>${row.year?`<span>${esc(row.year)}</span>`:''}</div><h3>${esc(row.name)}</h3><div class="hero-actions"><button class="primary" data-action="${row.kind==='movie'?'provider-download':'provider-episodes'}" data-id="${providerResults.indexOf(row)}" ${providerBusy?'disabled':''}>${icons(row.kind==='movie'?'download':'play')}${row.kind==='movie'?L.text('Scarica'):L.text('Scegli episodio')}</button><button class="secondary" data-action="provider-save" data-id="${providerResults.indexOf(row)}">${icons(saved(row)?'check':'plus')}${saved(row)?L.text('In libreria'):L.text('Salva in libreria')}</button></div></div></section>`;
  }
  function providerDetail(index){presentTitle(providerResults[index],{provider:index});}
  async function saveProvider(index){
    const sourceRow=providerResults[index];if(!sourceRow)return;const row=detailSession?.context.provider===index&&M.identity(detailSession.row)===M.identity(sourceRow)?detailSession.row:sourceRow,revision=sheetRevision;
    const existing=saved(row);if(existing){editTitle(existing.id);return;}
    await mutate(next=>M.add(next,{...row,sources:[row.source],state:'planned'}).data);
    toast(L.text('Salvato in libreria.'));if(sheet.open&&!sheetClosing&&revision===sheetRevision){const actions=sheet.querySelector('.hero-actions');if(actions)actions.innerHTML=L.html(detailActions(row,{provider:index}));}else if(page==='library'&&!sheet.open)updateLibraryGrid();
  }

  function abortRequest(id){
    if(!id)return;const callback=callbacks.get(id);if(callback){clearTimeout(callback.timeout);callbacks.delete(id);callback.reject(new Error(L.text('Operazione interrotta.')));}
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
    query=query.trim();if(query.length<2){toast(L.text('Scrivi almeno due caratteri.'));return;}
    const generation=++providerGeneration;abortRequest(providerRequest);providerRequest='';providerSearching=true;providerResults=[];providerWarnings=[];providerFilter='all';sourceQuery=query;providerResultQuery=query;providerResultScope=selectedSource;
    providerMessage=L.text('Cerco nelle fonti…');updateProviderResults();$('#source-query').blur();
    try{
      const request=api('providerSearch',{query,source:selectedSource,kind:providerKind},{timeout:65000});providerRequest=request.requestId||'';
      const response=await request;if(generation!==providerGeneration)return;
      providerSearching=false;providerRequest='';providerResults=groupProviderResults(response.items);providerWarnings=response.warnings||[];
      providerMessage=providerResults.length?providerResults.length+L.text(' risultati. Scegli il titolo per scaricarlo.'):L.text('Nessun titolo trovato nelle fonti che hanno risposto.');
      updateProviderResults();
      const normalizedQuery=normalizedTitle(query);
      const exact=providerResults.map((row,index)=>({row,index})).filter(x=>normalizedQuery&&x.row.kind==='movie'&&normalizedTitle(x.row.name)===normalizedQuery);
      const identities=new Set(exact.map(x=>normalizedTitle(x.row.name)+'|'+(x.row.year||'')));
      if(quickDownload&&exact.length&&identities.size===1)await downloadProvider(exact[0].index);
    }catch(error){if(generation!==providerGeneration)return;providerSearching=false;providerRequest='';providerMessage=error.message;updateProviderResults();}
  }
  function cancelProviderSearch(){++providerGeneration;abortRequest(providerRequest);providerRequest='';providerSearching=false;providerMessage=L.text('Ricerca interrotta.');updateProviderResults();}
  async function downloadProvider(index,episode=''){
    const row=providerResults[index];if(!row||providerBusy)return;
    const generation=++resolveGeneration,quality=providerQuality;providerBusy=true;resolveStage=L.text('Trovo il collegamento per ')+row.name+'…';if(page==='search')renderSources();
    try{
      let queued=false,lastError;const variants=episode?[row]:(row.variants||[row]);
      for(let attempt=0;attempt<variants.length;attempt++){
        try{
          const variant=variants[attempt];resolveStage=(attempt?L.text('Provo un’altra fonte: '):L.text('Trovo il video su '))+variant.source+'…';
          const request=api('providerResolve',{key:variant.key,episode},{timeout:65000});resolveRequest=request.requestId||'';if(page==='search')renderSources();
          const payload=await request;if(generation!==resolveGeneration)return;resolveRequest='';resolveStage=L.text('Preparo video e audio per il download…');if(page==='search')renderSources();
          await api('download',{...payload,poster:row.poster||'',titleIdentity:M.identity(row),titleName:row.name,titleKind:row.kind,quality,wifiOnly:data.preferences.wifiOnly},{timeout:40000});queued=true;break;
        }catch(error){if(generation!==resolveGeneration)return;lastError=error;}
      }
      if(!queued)throw lastError||new Error(L.text('Nessuna fonte disponibile per questo titolo.'));
      if(generation!==resolveGeneration)return;providerBusy=false;toast(L.text('Il video è in Download.'));if(page==='search'){await closeSheet(true);location.hash='files';}else if(page==='files')refreshDownloads();
    }catch(error){if(generation!==resolveGeneration)return;providerMessage=error.message;toast(error.message);}
    finally{if(generation===resolveGeneration){providerBusy=false;resolveRequest='';if(page==='search')renderSources();}}
  }
  function cancelProviderResolve(){if(!resolveRequest)return;++resolveGeneration;abortRequest(resolveRequest);resolveRequest='';providerBusy=false;providerMessage=L.text('Preparazione interrotta.');if(page==='search')renderSources();}
  async function showProviderEpisodes(index,season=-1){
    const row=providerResults[index];if(!row)return;providerEpisodeIndex=index;
    openSheet(row.name,providerHero(row)+'<div class="title-support"><p class="note" role="status">Carico le stagioni e gli episodi…</p></div>');sheet.dataset.cinema='true';
    const revision=sheetRevision;
    try{
      const response=await api('providerEpisodes',{key:row.key,season},{timeout:65000});if(revision!==sheetRevision||providerEpisodeIndex!==index||!sheet.open)return;
      providerEpisodeRows=response.episodes;
      openSheet(row.name,providerHero(row)+`<div class="title-support"><div class="season-tabs" role="group" aria-label="Stagione">${response.seasons.map(s=>`<button class="scope-chip" data-action="provider-season" data-id="${index}" data-season="${s.number}" aria-pressed="${s.number===response.season}">${esc(s.name)}</button>`).join('')}</div><label class="field sr-only"><span>Stagione</span><select id="provider-season" data-id="${index}">${response.seasons.map(s=>`<option value="${s.number}" ${s.number===response.season?'selected':''}>${esc(s.name)}</option>`).join('')}</select></label><div class="episode-section-head"><h3>${esc(response.seasons.find(s=>s.number===response.season)?.name||L.text('Stagione'))}</h3><span>${response.episodes.length} ${L.text('episodi')}</span></div><div class="episode-list">${providerEpisodeRows.map((ep,i)=>`<button class="episode-download" data-action="provider-episode-download" data-id="${i}"><span class="episode-number">${esc(ep.number)}</span><span><strong>${esc(ep.name)}</strong><small>${L.text('Episodio')} ${esc(ep.number)}</small></span>${icons('download')}</button>`).join('')||'<p class="note">Nessun episodio disponibile per questa stagione.</p>'}</div></div>`);
      sheet.dataset.cinema='true';
    }catch(error){if(revision===sheetRevision&&sheet.open)openSheet(row.name,`<p class="error-note">${esc(error.message)}</p><button class="secondary" data-action="provider-episodes" data-id="${index}">Riprova</button>`);}
  }
  function manageSources(){
    openSheet(L.text('Le fonti'),`<p class="note">${sources.filter(s=>s.automatic).length} fonti hanno ricerca e download diretti. Puoi aggiornare gli indirizzi del PC qui. Il browser con AD resta disponibile come strumento aggiuntivo.</p><label class="source-filter"><input id="source-filter" type="search" placeholder="Filtra le fonti" value="${esc(sourceFilter)}" maxlength="80" aria-label="Filtra le fonti"></label><div class="source-grid" id="source-list"></div><button class="secondary" data-action="import-sources">Importa indirizzi PC</button>`);updateSourceCards();
  }
  function updateSourceCards(){
    const target=$('#source-list');if(!target)return;
    const query=sourceFilter.trim().toLocaleLowerCase('it');
    const rows=sources.filter(s=>(s.name+' '+s.category).toLocaleLowerCase('it').includes(query));
    target.innerHTML=L.html(rows.length?rows.map((s,i)=>`<article class="source-card"><span class="source-mark">${esc(s.name.slice(0,2).toUpperCase())}</span><div class="source-copy"><strong>${esc(s.name)}</strong><small>${esc(s.category)}</small></div><button class="icon-button" data-action="open-source" data-source="${esc(s.id)}" aria-label="Apri ${esc(s.name)}">${icons('arrow')}</button><button class="source-address" data-action="edit-source" data-source="${esc(s.id)}" aria-label="Modifica indirizzo di ${esc(s.name)}">${esc(new URL(s.url).host)} · modifica</button></article>`).join(''):'<p class="note">Nessuna fonte corrisponde. Prova un altro nome.</p>');
  }
  async function openSource(id,query=''){
    await api('openSource',{id,query,wifiOnly:data.preferences.wifiOnly});
  }
  function editSource(id){
    const source=sources.find(s=>s.id===id);if(!source)return;
    openSheet(L.text('Indirizzo di ')+source.name,`<p class="note">Se il sito ha cambiato indirizzo, puoi aggiornarlo qui. Usa la pagina principale HTTPS.</p><form id="source-address-form" data-source="${esc(id)}"><label class="field"><span>Indirizzo della fonte</span><input name="url" type="url" value="${esc(source.url)}" maxlength="2500" required></label><p id="source-error" class="error-note" role="alert" hidden></p><div class="form-actions"><button type="submit" class="primary">Salva indirizzo</button></div></form>`);
  }
  async function importSources(){
    const response=await api('import',{purpose:'sources'}, {picker:true});if(response.cancelled)return;
    const report=await api('sourceImport',{data:response.data});sources=await api('sources');
    toast(report.updated+L.text(' indirizzi aggiornati.'));if(page==='search')renderSources();
  }
  function renderCatalog() {
    main.innerHTML = L.html(`<section class="page-intro"><h1>Cerca solo informazioni</h1><p>Informazioni, poster e idee per la libreria. Per scaricare il film, usa Cerca.</p><button class="link-button" data-action="navigate" data-page="search">‹ Torna ai download diretti</button></section>
      <form id="search-form"><div class="search-box"><input type="search" id="search-query" aria-label="Titolo da cercare" placeholder="Un film, una serie…" minlength="2" maxlength="200" value="${esc(searchQuery)}" enterkeyhint="search" required autocomplete="off"><button type="submit" class="primary" aria-label="Cerca">${icons('search')}</button></div></form>
      <div class="segments" aria-label="Tipo di ricerca"><button type="button" data-action="search-kind" data-kind="movie" aria-pressed="${searchKind==='movie'}">Film</button><button type="button" data-action="search-kind" data-kind="series" aria-pressed="${searchKind==='series'}">Serie</button></div>
      <div id="search-status" class="search-status" role="status">${searching?L.text('Ricerca in corso…'):results.length?results.length+L.text(' titoli trovati'):''}</div><div id="search-results"></div>`);
    updateResults();
  }
  function updateResults() {
    if (page !== 'catalog') return;
    const container = $('#search-results');
    if (searching) {
      container.innerHTML = L.html('<div class="row"><button class="secondary" data-action="cancel-search">Interrompi ricerca</button></div><div class="poster-grid search-skeleton" aria-hidden="true">' + Array.from({length:4},()=>'<div class="skeleton"><div class="poster"></div><div class="card-title"></div><div class="card-meta"></div></div>').join('') + '</div>');
    } else if (results.length) {
      container.innerHTML = L.html(`<div class="poster-grid">${results.map((r,i)=>card(r,i)).join('')}</div><p class="attribution">${searchKind==='series'?'Metadati e immagini: <a href="https://www.tvmaze.com" data-external>TVmaze</a>, <a href="https://creativecommons.org/licenses/by-sa/4.0/" data-external>CC BY-SA</a>.':'Metadati da <a href="https://www.wikidata.org" data-external>Wikidata</a>, CC0. Immagini Wikimedia Commons, con crediti e licenza nei dettagli.'}<br>La ricerca mostra informazioni sui titoli, non la disponibilità del film completo.</p>`);
    } else {
      container.innerHTML = L.html(`<section class="quiet-card"><h2>Importa la libreria</h2><p>La libreria, le note e le tue serate restano sul telefono. Hai già una libreria sul PC?</p><button class="secondary" data-action="import">${icons('download')}Importa la libreria</button></section>${data.recent.length?`<div class="section-head"><h2>Ricerche recenti</h2></div><div class="chip-list">${data.recent.map(q=>`<button class="chip" data-action="recent" data-query="${esc(q)}">${esc(q)}</button>`).join('')}</div>`:''}`);
    }
    preparePosters(container);
  }
  async function runSearch(query) {
    query = query.trim(); if (query.length < 2) { toast(L.text('Scrivi almeno due caratteri.')); return; }
    const generation = ++searchGeneration;
    abortRequest(searchRequest);
    searchQuery = query; searching = true; results = []; updateResults();
    if ($('#search-status')) $('#search-status').textContent = L.text('Cerco in ') + (searchKind==='movie'?'Wikidata':'TVmaze') + '…';
    const input = $('#search-query'); if (input) input.blur();
    try {
      const request = api('search',{query,kind:searchKind}); searchRequest = request.requestId || '';
      const response = await request;
      if (generation !== searchGeneration) return;
      results = response.items.map(r => M.clean(r)); searching = false; searchRequest = '';
      await mutate(next => { next.recent = [query,...next.recent.filter(q=>q!==query)].slice(0,8); return next; });
      if (generation !== searchGeneration) return;
      if (page === 'catalog') { $('#search-status').textContent = results.length ? `${results.length} ${L.text('titoli da')} ${response.source}${response.cached?L.text(' · Ricerca recente'):''}` : L.text('Nessun titolo trovato. Prova il titolo originale o passa a Film/Serie.'); updateResults(); }
    } catch (error) {
      if (generation !== searchGeneration) return;
      searching = false; searchRequest = ''; if (page==='catalog') { updateResults(); $('#search-status').textContent = L.text(error.message); } else toast(error.message);
    }
  }
  function cancelSearch() {
    ++searchGeneration; searching = false;
    abortRequest(searchRequest);
    searchRequest = ''; updateResults(); if ($('#search-status')) $('#search-status').textContent = L.text('Ricerca interrotta.');
  }
  async function saveResult(index) {
    const row = results[index]; if (!row) return;
    const revision=sheetRevision;
    let added;
    await mutate(next => { const result=M.add(next,row); added=result.added; return result.data; });
    toast(added?L.text('Titolo aggiunto alla tua libreria.'):L.text('Il titolo è già nella tua libreria.'));
    updateResults();
    if (sheet.open && sheetRevision === revision && results[index] === row && sheet.dataset.result === String(index)) detail(index);
  }
  function detail(index){presentTitle(results[index],{result:index});}
  function renderLibrary() {
    main.innerHTML=L.html(`<section class="page-intro"><div class="page-title-row"><h1>Libreria</h1><button class="icon-button" data-action="add-manual" aria-label="Aggiungi un titolo">${icons('plus')}</button></div><div class="library-summary"><span><strong>${data.items.filter(i=>i.state==='planned').length}</strong> Da vedere</span><span><strong>${data.items.filter(i=>i.state==='watching').length}</strong> In visione</span><span><strong>${data.items.filter(i=>i.state==='watched').length}</strong> Visti</span></div></section>
      <div class="library-filters library-toolbar"><input id="library-query" type="search" placeholder="Filtra i tuoi titoli…" aria-label="Filtra la libreria" value="${esc(filters.query)}"><select id="library-sort" aria-label="Ordinamento"><option value="recent">Più recenti</option><option value="name">Titolo A–Z</option><option value="rating">Valutazione</option><option value="duration">Durata</option></select></div>
      <div class="library-type-row"><div class="library-types" role="group" aria-label="Tipo di titolo">${[['','Tutti'],['movie','Film'],['series','Serie TV']].map(([key,label])=>`<button class="scope-chip" data-action="library-kind" data-value="${key}" aria-pressed="${filters.kind===key}">${L.text(label)}</button>`).join('')}</div><details class="library-advanced" ${filters.state||filters.collection?'open':''}><summary>Filtri</summary><div class="library-extra"><select id="library-state" aria-label="Stato"><option value="">Tutti gli stati</option><option value="planned">Da vedere</option><option value="watching">In visione</option><option value="watched">Visti</option></select><select id="library-collection" aria-label="Raccolta"><option value="">Tutte le raccolte</option>${data.collections.map(c=>`<option value="${esc(c)}">${esc(c)}</option>`).join('')}</select></div></details></div>
      <div id="library-grid"></div><div class="library-utilities"><a class="secondary" href="#tonight">${icons('moon')}Serata</a><button class="secondary" data-action="import">Importa</button><button class="link-button" data-action="export">Esporta JSON</button><button class="link-button" data-action="navigate" data-page="catalog">Cerca un titolo</button></div>`);
    $('#library-state').value=filters.state;$('#library-collection').value=filters.collection;$('#library-sort').value=filters.sort;updateLibraryGrid();
  }
  function updateLibraryGrid() {
    if (page!=='library') return;
    const rows = data.items.filter(i=>(!filters.kind||(filters.kind==='series'?['series','anime'].includes(i.kind):i.kind===filters.kind))&&(!filters.query||i.name.toLocaleLowerCase('it').includes(filters.query.toLocaleLowerCase('it')))&&(!filters.state||i.state===filters.state)&&(!filters.collection||i.collection_name===filters.collection));
    rows.sort((a,b)=>filters.sort==='name'?a.name.localeCompare(b.name,'it'):filters.sort==='rating'?b.rating-a.rating:filters.sort==='duration'?(a.duration||999)-(b.duration||999):b.updated_at.localeCompare(a.updated_at));
    const container=$('#library-grid'),focused=container.contains(document.activeElement)?document.activeElement.closest('[data-action]')?.dataset.id:null;
    const positions=new Map([...container.querySelectorAll('.title-card')].map(e=>[e.querySelector('button').dataset.id,e.getBoundingClientRect()]));
    container.innerHTML = L.html(rows.length?`<div class="poster-grid">${rows.slice(0,libraryLimit).map((r,i)=>card(r,i,true)).join('')}</div>${rows.length>libraryLimit?`<button class="secondary wide load-more" data-action="more-library">Mostra altri titoli (${rows.length-libraryLimit} rimasti)</button>`:''}`:`<div class="empty">${icons('library')}<h2>${data.items.length?L.text('Nessun titolo con questi filtri.'):L.text('La libreria è vuota.')}</h2><p>${data.items.length?L.text('Cambia il testo, lo stato o la raccolta per ritrovare gli altri titoli.'):L.text('Cerca un film o una serie, aggiungi un titolo a mano oppure importa la libreria del PC.')}</p>${data.items.length?'':`<div class="row wrap"><button class="primary" data-action="navigate" data-page="search">Cerca un titolo</button><button class="secondary" data-action="import">Importa</button></div>`}</div>`);
    preparePosters(container);if(focused)container.querySelector('[data-action="library-detail"][data-id="'+focused+'"]')?.focus({preventScroll:true});
    container.querySelectorAll('.title-card').forEach(e=>{
      const old=positions.get(e.querySelector('button').dataset.id), now=e.getBoundingClientRect();
      if(old) animate(e,[{transform:`translate(${old.left-now.left}px,${old.top-now.top}px)`},{transform:'translate(0,0)'}],{duration:420});
    });
  }
  function editTitle(id = null) {
    const row=id?data.items.find(i=>i.id===id):{name:'',kind:'movie',year:null,state:'planned',rating:0,duration:null,tags:[],notes:'',collection_name:''};
    if(!row)return;
    openSheet(id?L.text('La tua scheda'):L.text('Aggiungi un titolo'),`<form id="title-form" data-id="${id||''}"><div class="form-grid">
      <label class="field full"><span>Titolo</span><input name="name" maxlength="300" value="${esc(row.name)}" required ${id?'':'autofocus'}></label>
      <label class="field"><span>Tipo</span><select name="kind">${['movie','series','anime','song','album','book','live'].map(k=>`<option value="${k}" ${row.kind===k?'selected':''}>${esc(kindName(k))}</option>`).join('')}</select></label>
      <label class="field"><span>Anno</span><input name="year" type="number" min="1800" max="2200" inputmode="numeric" value="${row.year||''}"></label>
      <label class="field"><span>Stato</span><select name="state">${['planned','watching','watched'].map(k=>`<option value="${k}" ${row.state===k?'selected':''}>${stateName(k)}</option>`).join('')}</select></label>
      <label class="field"><span>Valutazione</span><select name="rating">${[0,1,2,3,4,5].map(r=>`<option value="${r}" ${row.rating===r?'selected':''}>${r?'★'.repeat(r):L.text('Da valutare')}</option>`).join('')}</select></label>
      <label class="field"><span>Durata, in minuti</span><input name="duration" type="number" min="1" max="600" inputmode="numeric" value="${row.duration||''}" placeholder="Per Serata"></label>
      <label class="field"><span>Raccolta</span><input name="collection_name" maxlength="80" value="${esc(row.collection_name)}" placeholder="Es. Weekend" list="collections"></label>
      <datalist id="collections">${data.collections.map(c=>`<option value="${esc(c)}"></option>`).join('')}</datalist>
      <label class="field full"><span>Atmosfere, separate da virgole</span><input name="tags" maxlength="260" value="${esc(row.tags.join(', '))}" placeholder="Es. Avventura, Relax"><small class="hint">Fino a 8. Per le serie, la durata è quella di un episodio.</small></label>
      <label class="field full"><span>Le tue note</span><textarea name="notes" maxlength="2000" placeholder="Cosa ti incuriosisce? Con chi lo vuoi vedere?">${esc(row.notes)}</textarea></label>
      </div><div id="form-error" class="error-note" role="alert" hidden></div><div class="form-actions">${id?`<button type="button" class="link-button" data-action="delete-title" data-id="${id}">Rimuovi</button>`:''}<button class="primary" type="submit">${icons('check')}Salva la scheda</button></div></form>${row.image_source?`<p class="attribution">Immagine: <a href="${esc(row.image_source)}" data-external>${esc(row.image_credit||L.text('Wikimedia Commons — crediti e licenza'))}</a></p>`:''}`);
    delete sheet.dataset.result; delete sheet.dataset.cinema;
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
      if(sheet.contains(form)&&sheet.open)await closeSheet(true); toast(L.text('Scheda salvata.'));
      if(page==='library')renderLibrary(); if(page==='catalog')updateResults(); if(page==='tonight')renderTonight();
    }catch(error){const note=form.querySelector('#form-error');if(form.isConnected&&note){note.hidden=false;note.textContent=L.text(error.message);note.scrollIntoView({block:'nearest'});}else toast(error.message);}
    finally{button.disabled=false;}
  }
  function renderTonight() {
    const moods=[...new Set([].concat(...data.items.map(i=>i.tags)))].sort((a,b)=>a.localeCompare(b,'it'));
    main.innerHTML=L.html(`<section class="tonight-hero"><svg aria-hidden="true"><use href="#icon-moon"/></svg><h1>Serata</h1><p>Una o due storie dalla tua libreria, nel tempo che hai.</p></section>
      <form id="tonight-form" class="tonight-form"><label class="field"><span>Minuti disponibili</span><input name="minutes" type="number" inputmode="numeric" min="15" max="600" value="${currentPlan?currentPlan.budget:120}" required></label><label class="field"><span>Quante visioni?</span><select name="count"><option value="1">Una storia</option><option value="2" ${currentPlan&&currentPlan.count===2?'selected':''}>Due storie</option></select></label><label class="field full"><span>Atmosfera</span><select name="mood"><option value="">Tutte le atmosfere</option>${moods.map(m=>`<option value="${esc(m)}" ${currentPlan&&currentPlan.mood===m?'selected':''}>${esc(m)}</option>`).join('')}</select></label><button class="primary full" type="submit">${icons('moon')}Componi la serata</button></form><div id="tonight-plan"></div><p class="note">Serata usa le durate e le atmosfere dei tuoi titoli. Per una serie conta un episodio; le visioni già segnate come viste vengono escluse.</p>`);
    if(currentPlan)updatePlan();
  }
  function createPlan(form, again=false) {
    const fields=formValues(form),budget=Number(fields.minutes),count=Number(fields.count),mood=fields.mood;
    currentPlan={...M.plan(data,budget,count,mood,again&&currentPlan?currentPlan.items.map(i=>i.id):[]),budget,count,mood};
    updatePlan();
  }
  function updatePlan() {
    const container=$('#tonight-plan');if(!container||!currentPlan)return;
    if(!currentPlan.items.length){container.innerHTML=L.html(`<section class="quiet-card"><h2>Completa le durate</h2><p>Nessuna combinazione entra in questo tempo. Aumenta i minuti o cambia atmosfera.${currentPlan.missing?' '+currentPlan.missing+L.text(' titoli non hanno ancora una durata.'):''}</p><button class="secondary" data-action="navigate" data-page="library">Completa le durate</button></section>`);return;}
    let elapsed=0;
    container.innerHTML=L.html(`<section class="plan"><div class="plan-head"><h2>${currentPlan.minutes} minuti per te.</h2><p>${currentPlan.budget-currentPlan.minutes} minuti liberi per una pausa.</p></div>${currentPlan.items.map(row=>{const start=elapsed;elapsed+=row.duration;return `<div class="plan-item">${poster(row)}<div><span class="time-marker">${start===0?L.text('Si comincia'):L.text('Dopo ')+start+L.text(' minuti')}</span><h3>${esc(row.name)}</h3><p>${row.duration} min${row.kind==='series'?' / episodio':''}${row.tags.length?' · '+esc(row.tags.slice(0,2).join(', ')):''}</p></div></div>`;}).join('')}<div class="plan-actions"><button class="secondary" data-action="another-plan">Un’altra proposta</button><button class="primary" data-action="share-plan">${icons('share')}Condividi</button></div></section>`);
    preparePosters(container);animate(container,[{opacity:0,transform:'translateY(14px)'},{opacity:1,transform:'translateY(0)'}],{duration:450});
  }
  function renderFiles() {
    main.innerHTML=L.html(`<section class="page-intro"><h1>Download</h1><p>I film scaricati dalle fonti e i video del telefono, nello stesso posto.</p></section><div class="file-actions"><button class="primary" data-action="pick-video">${icons('files')}Scegli video</button><button class="secondary" data-action="new-download">${icons('download')}Da un link</button></div><section class="files-section device-section"><div class="section-head"><h2>Dal dispositivo</h2><span>${data.files.length} video</span></div><div id="local-files">${data.files.length?data.files.map(file=>`<article class="local-file"><strong>${esc(file.name)}</strong><small>Collegato dal telefono</small><div class="row"><button class="secondary" data-action="play-file" data-token="${esc(file.token)}">${icons('play')}Riprendi</button><button class="link-button" data-action="forget-file" data-token="${esc(file.token)}">Scollega</button></div></article>`).join(''):'<p class="note">Scegli un video dal selettore Android. Il file rimane dove si trova e puoi riprenderlo dal punto lasciato.</p>'}</div></section><div class="download-dashboard"><div><strong id="download-active-count">—</strong><span>In corso e in coda</span></div><div><strong id="download-complete-count">—</strong><span>Disponibili offline</span></div></div><div class="download-tabs" role="group" aria-label="Filtra download">${[['all','Tutti'],['active','In corso'],['complete','Completati'],['error','Errori']].map(([key,label])=>`<button class="scope-chip" data-action="download-filter" data-value="${key}" aria-pressed="${downloadFilter===key}">${L.text(label)}</button>`).join('')}</div><section class="files-section downloads-section"><div class="section-head"><h2>Download</h2><span id="download-summary"></span><button class="icon-button" data-action="refresh-downloads" aria-label="Aggiorna download">${icons('download')}</button></div><div id="downloads"><p class="note download-loading" role="status">Leggo lo stato dei download…</p></div></section><p class="note">I download continuano con una notifica anche quando lasci l’app. HLS/DASH restano nello spazio offline di VibraVid. I file scaricati vengono rimossi se disinstalli VibraVid. I video scelti dal dispositivo restano al loro posto.</p>`);
    main.querySelector('.device-section').before(main.querySelector('.download-dashboard'),main.querySelector('.download-tabs'),main.querySelector('.downloads-section'));
  }
  let downloadsGeneration=0,downloadsLoading=null,downloadRows=[],downloadFilter='all';
  const transfers=new E.Transfers(),transferStats=new Map();
  function downloadState(row){return row.status===8?'complete':row.status===16?'error':row.paused?'paused':row.status===2?'active':row.status===4?'waiting':'queued';}
  function downloadStatus(row){return L.text(({complete:'Disponibile offline',error:'Download non riuscito',paused:'In pausa',active:'Download in corso',waiting:'In attesa della rete',queued:'In coda'})[downloadState(row)]);}
  function downloadActions(row){
    const offline=row.engine==='offline',complete=row.status===8,failed=row.status===16,id=esc(row.id),confirm=removalConfirmations.has(String(row.id));
    return `${complete?`<button class="secondary" data-action="${offline?'media-play':'open-download'}" data-id="${id}" data-name="${esc(row.name)}">${icons('play')}${L.text('Guarda offline')}</button>${offline?`<button class="link-button" data-action="media-repair" data-id="${id}">${L.text('Verifica e ripara')}</button>`:''}`:offline?`<button class="secondary" data-action="media-pause" data-id="${id}" data-paused="${!row.paused&&!failed}">${L.text(row.paused||failed?'Riprendi':'Pausa')}</button>`:''}<button class="link-button" data-action="${offline?'media-remove':'remove-download'}" data-id="${id}" data-complete="${complete}" data-confirm="${confirm?'yes':''}">${L.text(confirm?'Conferma rimozione':complete?'Elimina video':failed?'Rimuovi':'Annulla')}</button>`;
  }
  function updateDownloadCards(){
    const container=$('#downloads');if(page!=='files'||!container)return;
    const rows=downloadRows.filter(row=>downloadFilter==='all'||(downloadFilter==='complete'?row.status===8:downloadFilter==='error'?row.status===16:![8,16].includes(row.status))).slice().sort((a,b)=>(b.time||0)-(a.time||0));
    const existing=new Map([...container.querySelectorAll('.download-card')].map(card=>[card.dataset.downloadId,card]));
    container.querySelectorAll('.download-empty,.download-loading,.download-error-banner').forEach(el=>el.remove());
    const ids=new Set(rows.map(row=>String(row.id)));existing.forEach((card,id)=>{if(!ids.has(id))card.remove();});
    rows.forEach(row=>{
      const id=String(row.id),state=downloadState(row),complete=row.status===8;
      let card=existing.get(id);
      if(!card){card=document.createElement('article');card.className='download-card';card.dataset.downloadId=id;card.innerHTML=`<div class="download-art"></div><div class="download-copy"><h3></h3><span class="download-state"></span><progress max="100"></progress><div class="download-metrics"><span class="download-size"></span><span class="download-speed"></span></div><p class="error-note" hidden></p><div class="row wrap download-controls"></div><div class="download-watch"></div></div>`;container.append(card);}
      card.dataset.state=state;card.querySelector('h3').textContent=row.name;
      card.querySelector('.download-state').textContent=[row.source,downloadStatus(row)].filter(Boolean).join(' · ');
      const artKey=row.poster||'';if(card.dataset.art!==artKey||!card.querySelector('.poster')){card.dataset.art=artKey;card.querySelector('.download-art').innerHTML=poster({poster:artKey});preparePosters(card);}
      const progress=complete?100:row.percent>=0?Math.max(0,Math.min(100,row.percent)):row.total>0?Math.max(0,Math.min(100,row.bytes/row.total*100)):null;
      const bar=card.querySelector('progress');bar.setAttribute('aria-label',L.text('Avanzamento di')+' '+row.name);if(progress===null)bar.removeAttribute('value');else bar.value=progress;
      card.querySelector('.download-size').textContent=E.bytes(row.bytes)+(row.total>0?' / '+E.bytes(row.total):'')+(progress!==null?' · '+Math.round(progress)+'%':'');
      const stats=transferStats.get(id)||{};card.querySelector('.download-speed').textContent=state==='active'?(stats.speed===null||stats.speed===undefined?L.text('Calcolo velocità…'):E.bytes(stats.speed)+'/s')+(stats.eta?' · '+E.eta(stats.eta)+' '+L.text('rimanenti'):''):'';
      const error=card.querySelector('.error-note');error.hidden=state!=='error';error.textContent=state==='error'?row.error||L.text('Il collegamento potrebbe essere scaduto. Riapri la fonte e rileva nuovamente il video.'):'';
      const signature=state+'|'+removalConfirmations.has(id);if(card.dataset.actions!==signature){const controls=card.querySelector('.download-controls'),action=controls.contains(document.activeElement)?document.activeElement.dataset.action:null;controls.innerHTML=L.html(downloadActions(row));card.dataset.actions=signature;if(action)controls.querySelector(`[data-action="${action}"]`)?.focus({preventScroll:true});}
      const watched=card.querySelector('.download-watch'),p=row.playback;if(complete&&p?.duration>0){const percent=Math.round(Math.min(100,100*p.position/p.duration));watched.innerHTML=`<progress class="watch-progress" value="${percent}" max="100" aria-label="${L.text('Avanzamento di visione')}"></progress><span class="watch-caption">${percent}% ${L.text('visto')}</span>`;}else watched.replaceChildren();
    });
    if(!rows.length){const empty=document.createElement('div');empty.className='download-empty';empty.innerHTML=L.html(`${icons('download')}<h2>${downloadRows.length?'Nessun download con questi filtri.':'Il tuo cinema, anche offline'}</h2><p>${downloadRows.length?'Scegli un altro filtro per vedere gli altri video.':'Cerca un film o una serie e scaricalo. Lo ritroverai qui, pronto per il viaggio.'}</p><button class="primary" data-action="navigate" data-page="home">${L.text('Esplora i titoli')}</button>`);container.append(empty);}
    const completed=downloadRows.filter(r=>r.status===8).length,active=downloadRows.filter(r=>![8,16].includes(r.status)).length;
    $('#download-summary').textContent=downloadRows.length+' '+L.text('totali')+' · '+completed+' '+L.text('completati');
    $('#download-active-count').textContent=active;$('#download-complete-count').textContent=completed;
    main.querySelectorAll('[data-action="download-filter"]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.value===downloadFilter)));
  }
  async function refreshDownloads(){
    if(page!=='files'||downloadsLoading===downloadsGeneration)return;const generation=downloadsGeneration;downloadsLoading=generation;
    try{
      const rows=await api('downloads');if(page!=='files'||generation!==downloadsGeneration)return;
      downloadRows=Array.isArray(rows)?rows:[];const ids=new Set(downloadRows.map(row=>String(row.id)));transfers.prune(ids);
      removalConfirmations.forEach(id=>{if(!ids.has(id))removalConfirmations.delete(id);});
      const now=performance.now();downloadRows.forEach(row=>transferStats.set(String(row.id),transfers.sample(row,now)));for(const id of transferStats.keys())if(!ids.has(id))transferStats.delete(id);
      updateDownloadCards();
    }catch(error){if(page==='files'&&generation===downloadsGeneration){const box=document.createElement('p');box.className='error-note download-error-banner';box.setAttribute('role','alert');box.textContent=L.text(error.message);$('#downloads').querySelector('.download-error-banner')?.remove();$('#downloads').querySelector('.download-loading')?.remove();$('#downloads').prepend(box);}}
    finally{if(downloadsLoading===generation)downloadsLoading=null;}
  }

  function newDownload(){
    openSheet(L.text('Un video da portare con te'),`<p class="note">Incolla l’URL HTTPS di un video MP4/WebM o di una playlist HLS/DASH. Per un sito, apri la sua scheda in Fonti e avvia il player. I contenuti DRM richiedono la licenza offline del servizio.</p><form id="download-form"><div class="form-grid"><label class="field full"><span>Collegamento del file</span><input name="url" type="url" placeholder="https://…/video.mp4" maxlength="2000" required></label><label class="field full"><span>Nome del video</span><input name="name" maxlength="180" placeholder="Il mio video" required></label><label class="field full"><span>Qualità massima</span><select name="quality"><option value="720">720p</option><option value="1080">1080p</option><option value="480">480p</option></select></label></div><p class="hint">${data.preferences.wifiOnly?L.text('Il download partirà con una rete Wi-Fi.'):L.text('Può usare anche i dati mobili. Puoi cambiarlo nelle Impostazioni.')}</p><div id="download-error" class="error-note" role="alert" hidden></div><div class="form-actions"><button type="submit" class="primary">${icons('download')}Avvia download</button></div></form>`);
  }
  function renderSettings(){
    const p=data.preferences;
    main.innerHTML=L.html(`<section class="page-intro"><h1>Impostazioni</h1><p>Preferenze, fonti e aggiornamenti.</p></section>
      <section class="summary-strip settings-summary"><div><strong>${sources.filter(s=>s.automatic).length}</strong><span>Fonti automatiche</span></div><div><strong>${L.languages.length}</strong><span>Lingue</span></div><div><strong class="version-stat">3.1.0</strong><span>Versione</span></div><button class="primary" data-action="app-update">Update</button></section><div class="settings-tabs" role="tablist" aria-label="Impostazioni">${[['general','Generali'],['appearance','Aspetto'],['sources','Fonti'],['data','Libreria'],['about','Informazioni']].map(([key,label])=>`<button class="scope-chip" role="tab" id="tab-${key}" aria-controls="settings-${key}" aria-selected="${settingsTab===key}" tabindex="${settingsTab===key?'0':'-1'}" data-action="settings-tab" data-value="${key}">${L.text(label)}</button>`).join('')}</div><div id="settings-appearance" class="settings-panel" role="tabpanel" aria-labelledby="tab-appearance" ${settingsTab==='appearance'?'':'hidden'}><section class="appearance-panel" aria-labelledby="appearance-title"><h2 id="appearance-title">Aspetto</h2><p class="appearance-note">Combina layout e colori. La scelta si applica subito e resta salvata sul telefono.</p>
      <div class="appearance-compact"><label for="appearance-layout"><strong>Layout</strong><select id="appearance-layout">${M.layouts.map(option=>`<option value="${option.id}" ${p.layout===option.id?'selected':''}>${L.text(option.name)}</option>`).join('')}</select></label><label for="appearance-palette"><strong>Colori</strong><select id="appearance-palette">${M.palettes.map(option=>`<option value="${option.id}" ${p.palette===option.id?'selected':''}>${L.text(option.name)}</option>`).join('')}</select></label><div class="appearance-preview"><span class="layout-mini" data-mini="${p.layout}" aria-hidden="true"><i></i><i></i><i></i><i></i></span><div><strong>${L.text(M.layouts.find(o=>o.id===p.layout).name)}</strong><p>${L.text(M.layouts.find(o=>o.id===p.layout).note)}</p><span class="palette-swatch" data-swatch="${p.palette}" aria-hidden="true"><i></i><i></i><i></i></span></div></div></div><p class="appearance-summary" role="status" aria-live="polite" id="appearance-summary">${M.layouts.find(o=>o.id===p.layout).name} · ${M.palettes.find(o=>o.id===p.palette).name}. Locandine sempre a colori.</p></section></div><div id="settings-general" class="settings-panel" role="tabpanel" aria-labelledby="tab-general" ${settingsTab==='general'?'':'hidden'}><div class="settings-list"><label class="setting language-setting" for="app-language"><div><strong>Lingua</strong><small>Scegli la lingua dell’interfaccia. La scelta resta salvata sul telefono.</small></div><select id="app-language" name="language" aria-label="Lingua dell’app">${L.languages.map(language=>`<option value="${language.code}" ${p.language===language.code?'selected':''}>${esc(language.name)}</option>`).join('')}</select></label>
      <div class="setting"><div><strong>Modalità cinema</strong><small>Fondi più profondi. Scurisce anche le palette chiare.</small></div><button class="switch" role="switch" aria-checked="${p.theme==='cinema'}" aria-label="Modalità cinema" data-action="preference" data-key="theme"></button></div>
      <div class="setting"><div><strong>Riduci movimento</strong><small>Transizioni immediate. Rispettiamo anche la scelta del telefono.</small></div><button class="switch" role="switch" aria-checked="${p.reducedMotion}" aria-label="Riduci movimento" data-action="preference" data-key="reducedMotion"></button></div>
      <div class="setting"><div><strong>Download solo con Wi-Fi</strong><small>Si applica anche ai download in coda.</small></div><button class="switch" role="switch" aria-checked="${p.wifiOnly}" aria-label="Download solo con Wi-Fi" data-action="preference" data-key="wifiOnly"></button></div>
      </div><section class="quiet-card"><h2>Aggiornamenti</h2><p>Controlla le release Android su GitHub. Se disponibile, scarico il nuovo APK; Android chiederà conferma per installarlo.</p></section></div><div id="settings-sources" class="settings-panel" role="tabpanel" aria-labelledby="tab-sources" ${settingsTab==='sources'?'':'hidden'}><section class="quiet-card"><h2>Fonti</h2><div class="settings-source-list">${sources.filter(s=>s.automatic).map(s=>`<div><strong>${esc(s.name)}</strong><button class="link-button" data-action="edit-source" data-source="${esc(s.id)}">Modifica</button></div>`).join('')}</div><button class="secondary" data-action="manage-sources">Gestisci le fonti</button><p>Importa il file domains.json della configurazione PC per aggiornare gli indirizzi. Puoi anche modificarli dalla scheda di ogni fonte.</p><button class="secondary" data-action="import-sources">Importa indirizzi PC</button></section></div><div id="settings-data" class="settings-panel" role="tabpanel" aria-labelledby="tab-data" ${settingsTab==='data'?'':'hidden'}><section class="quiet-card"><h2>Libreria</h2><p>Importa il JSON di VibraVid sul PC o esporta la libreria del telefono. Le note esistenti vengono conservate.</p><div class="row wrap"><button class="secondary" data-action="import">Importa JSON</button><button class="primary" data-action="export">Esporta JSON</button></div></section></div><div id="settings-about" class="settings-panel" role="tabpanel" aria-labelledby="tab-about" ${settingsTab==='about'?'':'hidden'}><section class="quiet-card"><h3>VibraVid Studio per Android</h3><p>Versione 3.1.0. La ricerca interroga direttamente i cataloghi delle fonti supportate. Con Download rapido, un titolo univoco avvia il download senza aprire il sito; per titoli omonimi o episodi puoi scegliere dentro l’app. Il browser delle fonti include il blocco pubblicità, disattivabile con il pulsante AD per ogni fonte. Il download autonomo salva video, audio e sottotitoli compatibili sul telefono. HLS/DASH vengono riprodotti offline nell’app. La ricerca delle informazioni usa Wikidata e TVmaze; Gli adattatori automatici Android disponibili sono StreamingCommunity, Streamingcommunity 2, AnimeUnity, AnimeWorld, Eurostreaming e RaiPlay. Le altre fonti restano negli strumenti aggiuntivi.</p><p>Nessuna telemetria e nessun account incorporato. I dati della libreria restano nello spazio privato dell’app. Disinstallando l’app li rimuovi: esporta prima se vuoi conservarli.</p><button class="link-button" data-action="licenses">Licenze e fonti</button></section>${native?'':'<p class="error-note">Anteprima nel browser. Le funzioni del telefono sono disponibili nell’APK.</p>'}</div>`);
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
    if(event.type===102){toast(L.text('Libreria esportata.'));return;}
    if(event.type===103){await mutate(next=>{next.files=[result,...next.files].slice(0,80);return next;});location.hash='files';toast(L.text('Video collegato.'));return;}
    if(event.purpose==='sources'){const report=await api('sourceImport',{data:result.data});sources=await api('sources');toast(report.updated+L.text(' indirizzi aggiornati.'));}
    else{let report;await mutate(next=>{report=M.importLibrary(next,result.data);return report.data;});currentPlan=null;toast(report.added+L.text(' titoli importati.'));}
    navigate(page);
  }
  async function removeTitle(id,button){
    const row=data.items.find(i=>i.id===id);if(!row)return;
    if(button.dataset.confirm!=='yes'){button.dataset.confirm='yes';button.textContent=L.text('Conferma rimozione');button.className='danger';return;}
    await mutate(next=>{next.items=next.items.filter(i=>i.id!==id);return next;});
    currentPlan=null;if(sheet.contains(button)&&sheet.open)await closeSheet(true);toast(L.text('Titolo rimosso dalla libreria.'));
    if(page==='library')renderLibrary();if(page==='catalog')updateResults();if(page==='tonight')renderTonight();
  }
  document.addEventListener('submit',event=>{
    const form=event.target;
    if(form.id==='home-search'){event.preventDefault();providerKind='all';selectedSource='__all__';sourceQuery=form.elements.query.value.trim();pendingSourceQuery=sourceQuery;positions.set('search',0);location.hash='search';}
    if(form.id==='source-form'){event.preventDefault();sourceQuery=$('#source-query').value.trim();selectedSource=$('#source-choice').value;providerKind=$('#provider-kind').value;providerQuality=Number($('#provider-quality').value);runProviderSearch(sourceQuery);}
    if(form.id==='source-address-form'){event.preventDefault();const button=form.querySelector('button');button.disabled=true;api('sourceUpdate',{id:form.dataset.source,url:form.elements.url.value}).then(async()=>{sources=await api('sources');if(sheet.contains(form)&&sheet.open){await closeSheet(true);if(page==='search')renderSources();}else if($('#source-list'))updateSourceCards();toast(L.text('Indirizzo aggiornato.'));}).catch(e=>{const note=form.querySelector('#source-error');if(form.isConnected&&note){note.textContent=L.text(e.message);note.hidden=false;}else toast(e.message);}).finally(()=>{button.disabled=false;});}
    if(form.id==='browse-search'){
      event.preventDefault();providerKind=page==='movies'?'movie':'series';selectedSource='__all__';pendingSourceQuery=$('#browse-query').value.trim();sourceQuery=pendingSourceQuery;positions.set('search',0);location.hash='search';
    }
    if(form.id==='search-form'){event.preventDefault();runSearch($('#search-query').value);}
    if(form.id==='title-form'){event.preventDefault();saveTitle(form);}
    if(form.id==='tonight-form'){event.preventDefault();try{createPlan(form);}catch(e){toast(e.message);}}
    if(form.id==='download-form'){
      event.preventDefault();const button=form.querySelector('button[type=submit]');button.disabled=true;
      const payload=formValues(form);payload.wifiOnly=data.preferences.wifiOnly;
      api('download',payload).then(async()=>{if(sheet.contains(form)&&sheet.open)await closeSheet(true);toast(L.text('Video preparato. Il download è in coda.'));if(page==='files')refreshDownloads();}).catch(e=>{const note=form.querySelector('#download-error');if(form.isConnected&&note){note.textContent=L.text(e.message);note.hidden=false;}else toast(e.message);}).finally(()=>{button.disabled=false;});
    }
  });
  document.addEventListener('input',event=>{if(event.target.id==='browse-query'){const kind=page==='movies'?'movie':'series';browseQueries[kind]=event.target.value;updateBrowseGrid(kind);}
    if(event.target.id==='source-query')sourceQuery=event.target.value;if(event.target.id==='search-query')searchQuery=event.target.value;if(event.target.id==='source-filter'){sourceFilter=event.target.value;updateSourceCards();}if(event.target.id==='library-query'){filters.query=event.target.value;libraryLimit=100;updateLibraryGrid();}});
  document.addEventListener('change',async event=>{
    if(event.target.id==='appearance-layout'||event.target.id==='appearance-palette'){
      const select=event.target,key=select.id==='appearance-layout'?'layout':'palette',value=select.value;select.disabled=true;
      try{await mutate(next=>{next.preferences[key]=value;if(key==='palette'&&M.palettes.find(o=>o.id===value)?.light)next.preferences.theme='light';return next;});if(page==='settings'){renderSettings();$('#appearance-'+key)?.focus({preventScroll:true});}}
      catch(error){if(select.isConnected)select.value=data.preferences[key];toast(error.message);}finally{select.disabled=false;}return;
    }
    if(event.target.id==='app-language'){
      const select=event.target, code=select.value;
      if(!L.valid(code)){select.value=data.preferences.language;return;}
      select.disabled=true;
      try{await mutate(next=>{next.preferences.language=code;return next;});if(page==='settings')renderSettings();toast(L.text('Lingua aggiornata.'));}
      catch(error){select.value=data.preferences.language;toast(error.message);}
      finally{if(select.isConnected)select.disabled=false;}
      return;
    }
    if(event.target.id==='quick-download')quickDownload=event.target.checked;
    if(event.target.id==='source-choice'){selectedSource=event.target.value;main.querySelectorAll('[data-action="provider-source"]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.value===selectedSource)));}
    if(event.target.id==='provider-kind'){providerKind=event.target.value;main.querySelectorAll('[data-action="provider-kind"]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.value===providerKind)));}
    if(event.target.id==='provider-quality')providerQuality=Number(event.target.value);
    if(event.target.id==='provider-season')showProviderEpisodes(Number(event.target.dataset.id),Number(event.target.value));
    if(event.target.id==='library-state')filters.state=event.target.value;
    if(event.target.id==='library-collection')filters.collection=event.target.value;
    if(event.target.id==='library-sort')filters.sort=event.target.value;
    if(event.target.id.startsWith('library-')){libraryLimit=100;updateLibraryGrid();}
  });
  document.addEventListener('toggle',event=>{const section=event.target;if(section.matches?.('details[data-season-index]')&&section.open&&!section.querySelector('.metadata-episode')){const season=detailSession?.details.seasons?.[Number(section.dataset.seasonIndex)];if(season)section.querySelector('.metadata-episodes').innerHTML=L.html(metadataEpisodes(season));}},true);
  document.addEventListener('fullscreenchange',()=>{document.documentElement.dataset.videoFullscreen=String(!!document.fullscreenElement);});
  document.addEventListener('visibilitychange',()=>{if(document.hidden)sheet.querySelectorAll('video').forEach(v=>v.pause());});
  document.addEventListener('keydown',event=>{
    const tab=event.target.closest('[role="tab"]');if(!tab||!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;
    const tabs=[...tab.parentElement.querySelectorAll('[role="tab"]')],rtl=document.documentElement.dir==='rtl',step=(event.key==='ArrowRight'?1:-1)*(rtl?-1:1);
    const next=event.key==='Home'?tabs[0]:event.key==='End'?tabs.at(-1):tabs[(tabs.indexOf(tab)+step+tabs.length)%tabs.length];
    event.preventDefault();next.click();next.focus();
  });
  document.addEventListener('click',async event=>{
    if(event.target.closest('a.skip')){event.preventDefault();main.focus({preventScroll:true});main.scrollIntoView({block:'start'});return;}
    const external=event.target.closest('[data-external]');if(external){event.preventDefault();api('openLink',{url:external.href}).catch(e=>toast(e.message));return;}
    const button=event.target.closest('[data-action]');if(!button||button.disabled)return;
    const action=button.dataset.action,id=Number(button.dataset.id);
    const nativeAction=['media-play','media-pause','media-repair','media-remove','open-download','remove-download'].includes(action);if(nativeAction)button.disabled=true;
    try{
      if(action==='home-section'){event.preventDefault();document.getElementById(button.dataset.section)?.scrollIntoView({behavior:reduced()?'auto':'smooth',block:'start'});}
      if(action==='library-kind'){filters.kind=button.dataset.value;main.querySelectorAll('[data-action="library-kind"]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.value===filters.kind)));libraryLimit=100;updateLibraryGrid();}
      if(action==='download-filter'){downloadFilter=button.dataset.value;updateDownloadCards();}
      if(action==='similar-detail'){const row=similarRows[id];if(row)presentTitle(row);}
      if(action==='home-detail')catalogDetail(homeRows[id]);
      if(action==='catalog-search')await catalogSearch();
      if(action==='catalog-save'){button.disabled=true;try{await saveCatalog();}finally{if(button.isConnected)button.disabled=false;}}
      if(action==='browse-kind'){
        if(button.dataset.kind==='library')location.hash='library';
        else location.hash=button.dataset.kind==='movie'?'movies':'series';
      }
      if(action==='rail-step'){
        const rail=main.querySelector('[data-rail="'+button.dataset.railTarget+'"]');if(rail)rail.scrollBy({left:Number(button.dataset.step)*rail.clientWidth*.85*(document.documentElement.dir==='rtl'?-1:1),behavior:reduced()?'auto':'smooth'});
      }
      if(action==='settings-tab'){
        settingsTab=button.dataset.value;
        main.querySelectorAll('[role="tab"]').forEach(t=>{t.setAttribute('aria-selected',String(t.dataset.value===settingsTab));t.tabIndex=t.dataset.value===settingsTab?0:-1;});
        main.querySelectorAll('.settings-panel').forEach(p=>{p.hidden=p.id!=='settings-'+settingsTab;});
      }
      if(action==='provider-toggle-scope')$('#search-scope').hidden=!$('#search-scope').hidden;
      if(action==='provider-kind'){
        providerKind=button.dataset.value;$('#provider-kind').value=providerKind;
        main.querySelectorAll('[data-action="provider-kind"]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.value===providerKind)));
      }
      if(action==='provider-source'){
        selectedSource=button.dataset.value;$('#source-choice').value=selectedSource;
        main.querySelectorAll('[data-action="provider-source"]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.value===selectedSource)));
      }
      if(action==='provider-filter'){providerFilter=button.dataset.value;updateProviderResults();}
      if(action==='provider-detail')providerDetail(id);
      if(action==='provider-save'){button.disabled=true;try{await saveProvider(id);}finally{if(button.isConnected)button.disabled=false;}}
      if(action==='provider-season')await showProviderEpisodes(id,Number(button.dataset.season));
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
      if(action==='library-detail')catalogDetail(data.items.find(row=>row.id===id),true);
      if(action==='edit')editTitle(id);
      if(action==='add-manual')editTitle();
      if(action==='more-library'){libraryLimit+=100;updateLibraryGrid();}
      if(action==='delete-title')await removeTitle(id,button);
      if(action==='source'&&results[id])await api('openLink',{url:results[id].url});
      if(action==='preview'&&results[id])await api('play',{url:results[id].preview,name:results[id].name+L.text(' — Anteprima')});
      if(action==='share-result'&&results[id])await api('share',{text:results[id].name+(results[id].year?' ('+results[id].year+')':'')+(results[id].url?'\n'+results[id].url:'')});
      if(action==='another-plan')createPlan($('#tonight-form'),true);
      if(action==='share-plan'&&currentPlan)await api('share',{text:L.text('La nostra serata con VibraVid\n')+currentPlan.items.map(i=>i.name+' — '+i.duration+L.text(' min')).join('\n')+L.text('\nTotale: ')+currentPlan.minutes+L.text(' minuti.')});
      if(action==='import'){button.disabled=true;try{await importLibrary();}finally{button.disabled=false;}}
      if(action==='export'){
        button.disabled=true;try{await saveChain;const response=await api('export',{data:M.exportLibrary(data)},{picker:true});if(!response.cancelled)toast(L.text('Libreria esportata.'));}finally{button.disabled=false;}
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
            $('#appearance-summary').textContent=`${L.text(M.layouts.find(o=>o.id===data.preferences.layout).name)} · ${L.text(M.palettes.find(o=>o.id===data.preferences.palette).name)}. ${L.text('Locandine sempre a colori.')}`;
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
      if(action==='media-repair'){await api('mediaRepair',{id:button.dataset.id});toast(L.text('Controllo le parti salvate e recupero quelle mancanti.'));setTimeout(refreshDownloads,500);}
      if(action==='media-play')await api('mediaPlay',{id:button.dataset.id});
      if(action==='media-pause'){await api('mediaPause',{id:button.dataset.id,paused:button.dataset.paused==='true'});setTimeout(refreshDownloads,500);}
      if(action==='media-remove'){if(!removalConfirmations.has(button.dataset.id)){removalConfirmations.add(button.dataset.id);button.dataset.confirm='yes';button.textContent=L.text('Conferma rimozione');return;}await api('mediaRemove',{id:button.dataset.id});removalConfirmations.delete(button.dataset.id);setTimeout(refreshDownloads,500);}
      if(action==='new-download')newDownload();
      if(action==='refresh-downloads')refreshDownloads();
      if(action==='pick-video'){
        button.disabled=true;try{const file=await api('pickVideo',{}, {picker:true});if(!file.cancelled){await mutate(next=>{next.files=[file,...next.files].slice(0,80);return next;});if(page==='files'){renderFiles();refreshDownloads();}toast(L.text('Video collegato. Il file originale resta al suo posto.'));}}finally{button.disabled=false;}
      }
      if(action==='play-file')await api('play',{token:button.dataset.token,name:(data.files.find(f=>f.token===button.dataset.token)||{}).name});
      if(action==='forget-file'){await mutate(next=>{next.files=next.files.filter(f=>f.token!==button.dataset.token);return next;});if(page==='files'){renderFiles();refreshDownloads();}toast(L.text('Collegamento rimosso. Il video resta sul telefono.'));}
      if(action==='open-download')await api('openDownload',{id,name:button.dataset.name});
      if(action==='remove-download'){
        if(!removalConfirmations.has(String(id))){removalConfirmations.add(String(id));button.dataset.confirm='yes';button.textContent=button.dataset.complete==='true'?L.text('Conferma eliminazione'):L.text('Conferma annullamento');button.className='danger';return;}
        await api('cancelDownload',{id});removalConfirmations.delete(String(id));refreshDownloads();
      }
      if(action==='app-update'){button.disabled=true;try{toast(L.text('Controllo gli aggiornamenti…'));const report=await api('appUpdate',{}, {timeout:25000});toast(report.message);}finally{if(button.isConnected)button.disabled=false;}return;}
      if(action==='licenses'){
        openSheet(L.text('Licenze e fonti'),`<p class="note">VibraVid Studio Android: GNU GPL v3. Il codice completo accompagna l’APK.</p><p class="note">AndroidX Media3 e dipendenze AndroidX: Apache License 2.0. Inter, Bebas Neue, Nunito Sans e Bricolage Grotesque: SIL Open Font License. Font e licenze sono inclusi localmente.</p><p class="note">TVmaze: metadati CC BY-SA, con attribuzione nelle ricerche. Wikidata: metadati CC0. Wikimedia Commons: crediti degli autori e licenze delle immagini nei dettagli.</p><div class="row wrap"><button class="secondary" data-action="license-text" data-file="GPL.txt">GPL v3</button><button class="secondary" data-action="license-text" data-file="APACHE-2.0.txt">Apache 2.0</button><button class="secondary" data-action="license-text" data-file="THIRD-PARTY-NOTICES.txt">Componenti</button><button class="secondary" data-action="license-text" data-file="fonts/inter-OFL.txt">Inter</button><button class="secondary" data-action="license-text" data-file="fonts/bebas-neue-OFL.txt">Bebas Neue</button><button class="secondary" data-action="license-text" data-file="fonts/nunito-sans-OFL.txt">Nunito Sans</button><button class="secondary" data-action="license-text" data-file="fonts/bricolage-grotesque-OFL.txt">Bricolage</button></div>`);
      }
      if(action==='license-text'){
        const revision=sheetRevision;
        const response=await fetch(button.dataset.file);if(!response.ok)throw new Error(L.text('Licenza non disponibile.'));
        const text=await response.text();if(revision===sheetRevision&&sheet.open&&button.isConnected)openSheet(L.text('Testo della licenza'),`<p class="note license-text">${esc(text)}</p>`);
      }
      if(action==='reload')location.reload();
    }catch(error){toast(error.message);}finally{if(nativeAction&&button.isConnected)button.disabled=false;}
  });
  $('#open-settings').addEventListener('click',()=>{location.hash='settings';});
  window.addEventListener('hashchange',()=>{if(sheet.open)closeSheet(true);navigate(location.hash.slice(1));});
  document.addEventListener('visibilitychange',()=>{if(!document.hidden&&page==='files')refreshDownloads();});
  const viewport=window.visualViewport;
  if(viewport)viewport.addEventListener('resize',()=>{document.body.classList.toggle('keyboard',viewport.height<window.innerHeight*.72);});
  window.VibraApp={
    back(){if(sheet.open){closeSheet();return true;}if(providerSearching){cancelProviderSearch();return true;}if(resolveRequest){cancelProviderResolve();return true;}if(searching){cancelSearch();return true;}if(page!=='home'){location.hash='home';return true;}return false;},
    // A diagnostic snapshot contains counts and UI state, never notes or credentials.
    diagnostics(){return {page,library:data?data.items.length:0,collections:data?data.collections.length:0,results:results.length,providerResults:providerResults.length,providerSearching,providerBusy,searching,language:data?data.preferences.language:null,theme:data?data.preferences.theme:null,native:!!native,ready:!!data&&!loadFailed};}
  };
  (async()=>{
    try{data=M.hydrate(await api('load'));sources=await api('sources');applyPreferences();navigate(location.hash.slice(1)||'home');if(recoveredPicker){const result=recoveredPicker;recoveredPicker=null;recoverPicker(result).catch(error=>toast(error.message));}}
    catch(error){loadFailed=true;main.innerHTML=L.html(`<section class="empty"><h1>La libreria non si apre.</h1><p>${esc(error.message)}</p><p>I dati salvati non sono stati sostituiti.</p><button class="primary" data-action="reload">Riprova</button></section>`);}
  })();
})();
