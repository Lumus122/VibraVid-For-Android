'use strict';
const {chromium}=require('playwright'),fs=require('fs'),path=require('path'),assert=require('assert/strict');
const root=path.resolve(__dirname,'..'),out=process.env.VIBRAVID_UI_OUTPUT||path.join(root,'build/streaming');fs.mkdirSync(out,{recursive:true});
const M=require(root+'/app/src/main/assets/model.js'),browse=JSON.parse(fs.readFileSync(root+'/app/src/main/assets/discovery.json','utf8'));
const sources=JSON.parse(fs.readFileSync(root+'/app/src/main/assets/providers.json','utf8')).map(s=>({...s,automatic:['streamingcommunity','streamingcommunity_2','animeunity','animeworld','eurostreaming','raiplay'].includes(s.id)}));
const checks=[],errors=[];const check=(name,value)=>{assert.ok(value,name);checks.push(name);};
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe'});
 try{
 const context=await browser.newContext({viewport:{width:390,height:844}}),page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
 let seed=M.add(M.empty(),{name:'Film',kind:'movie',year:2024,notes:'Nota da conservare <script> & "映画" 😀',duration:90}).data;
 seed=M.add(seed,{name:'Serie',kind:'series',year:2023,notes:'العربية',duration:45,state:'watching'}).data;
 await context.addInitScript(({seed,browse,sources})=>{
  window.qaCalls=[];
  const rows=[{...browse.movies[0],key:'film',provider:'streamingcommunity',source:'StreamingCommunity'},{...browse.series[0],key:'serie',provider:'streamingcommunity',source:'StreamingCommunity'},{...browse.series[1],name:'Anime di prova',kind:'anime',key:'anime',provider:'animeunity',source:'AnimeUnity'}];
  const downloads=[{id:'1'.repeat(64),engine:'offline',name:'Video offline',source:'Fonte QA',status:8,percent:100,bytes:4000000,total:4000000},{id:'2'.repeat(64),engine:'offline',name:'Video in corso',source:'Fonte QA',status:2,percent:40,bytes:2000000,total:5000000}];
  window.VibraAndroid={request(id,action,payload){
   const p=JSON.parse(payload);qaCalls.push({action,payload:p});let value={};
   if(action==='load')value=JSON.parse(localStorage.getItem('qa.stream.library')||'null')||seed;
   if(action==='save'){localStorage.setItem('qa.stream.library',JSON.stringify(p));value={saved:true};}
   if(action==='sources')value=sources;
   if(action==='discover')value=browse;
   if(action==='providerSearch')value={items:rows.filter(r=>p.kind==='all'||p.kind===r.kind),warnings:[]};
   if(action==='providerEpisodes')value={season:p.season<0?1:p.season,seasons:[{number:1,name:'Stagione 1'},{number:2,name:'Stagione 2'}],episodes:Array.from({length:8},(_,i)=>({number:i+1,name:'Episodio di prova '+(i+1),key:'episode-'+i}))};
   if(action==='providerResolve')value={name:'Video QA',url:'https://example.com/video.mp4',mime:'video/mp4'};
   if(action==='downloads')value=downloads;
   if(action==='mediaPause'){const row=downloads.find(r=>r.id===p.id);if(row){row.paused=p.paused;row.status=p.paused?4:2;}}
   if(action==='appUpdate')value={message:'Nessun aggiornamento disponibile.'};
   if(action==='import'||action==='pickVideo')value={cancelled:true};
   setTimeout(()=>VibraNativeReceive({id,ok:true,value}),action==='providerSearch'?100:0);
  }};
 },{seed,browse,sources});
 const settle=async()=>page.waitForFunction(()=>!VibraMotion.active&&!document.querySelector('#sheet').getAnimations().some(a=>a.playState==='running')&&!document.querySelector('#main').getAnimations().some(a=>a.playState==='running'));
 const close=async()=>{await page.locator('[data-action="close-sheet"]').click();await page.waitForFunction(()=>!document.querySelector('#sheet').open);};
 const route=async name=>{await page.evaluate(name=>{location.hash=name},name);await page.waitForFunction(name=>VibraApp.diagnostics().page===name,name);};
 const capture=async name=>{await settle();await page.locator('#toast').waitFor({state:'hidden'});await page.evaluate(()=>window.scrollTo({top:0,behavior:'instant'}));await page.screenshot({path:path.join(out,name+'.png'),fullPage:false});};
 await page.goto(process.env.VIBRAVID_PREVIEW_URL||'http://127.0.0.1:38912/');
 await page.waitForFunction(()=>VibraApp?.diagnostics().ready&&document.querySelectorAll('.home-open').length>20);await page.evaluate(()=>document.fonts.ready);
 check('First launch opens the sketched home',await page.evaluate(()=>VibraApp.diagnostics().page==='home'));
 check('Film and TV rows are present',await page.locator('.catalog-section').count()===2&&await page.locator('.home-shelf').count()>10);
 check('Three primary destinations remain at the bottom',await page.locator('.bottom-nav a').count()===3&&await page.locator('.bottom-nav').evaluate(e=>Math.abs(e.getBoundingClientRect().bottom-innerHeight)<2));
 check('Requested red is exact',await page.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--signal').trim()==='#e50914'));
 check('Inter is bundled and loaded',await page.evaluate(()=>document.fonts.check('16px Inter')&&getComputedStyle(document.body).fontFamily.startsWith('Inter')));
 check('The first poster is not clipped by scroll snapping',await page.locator('.home-open').first().evaluate(e=>e.getBoundingClientRect().left>=18));
 await page.evaluate(()=>Promise.race([Promise.all([...document.querySelectorAll('.home-open img')].filter(i=>{const r=i.getBoundingClientRect();return r.left<innerWidth&&r.right>0&&r.top<innerHeight}).map(i=>i.decode().catch(()=>{}))),new Promise(r=>setTimeout(r,8000))]));
 await capture('01-home');
 await page.locator('.catalog-section').first().locator(':scope > .section-head .shelf-all').click();await page.waitForSelector('#browse-grid .title-card');
 check('See all opens the complete movie shelf',await page.locator('#browse-grid .title-card').count()===browse.movies.length);
 await page.locator('#browse-query').fill(browse.movies[0].name);check('Browse filter keeps the selected title',await page.locator('#browse-grid .title-card').count()>=1&&await page.locator('#browse-grid .title-card').count()<browse.movies.length);
 await page.locator('#browse-query').fill('');await page.evaluate(()=>scrollTo(0,380));await route('settings');await route('movies');
 check('Returning to a catalogue restores vertical position',await page.evaluate(()=>Math.abs(scrollY-380)<2));
 await route('home');
 // Native horizontal scrolling and page restoration.
 await page.locator('[data-rail="movie-1"]').evaluate(e=>{e.scrollLeft=310});await page.waitForFunction(()=>document.querySelector('[data-rail="movie-1"]').scrollLeft>250);
 const railBefore=await page.locator('[data-rail="movie-1"]').evaluate(e=>e.scrollLeft);
 await page.locator('#open-settings').click();await route('home');
 check('Returning Home restores shelf position',Math.abs(await page.locator('[data-rail="movie-1"]').evaluate(e=>e.scrollLeft)-railBefore)<2);
 await page.locator('[data-rail="movie-1"]').evaluate(e=>{e.scrollLeft=0});await page.waitForFunction(()=>document.querySelector('[data-rail="movie-1"]').scrollLeft<2);
 await page.locator('.home-open').first().click();
 check('Opening a poster creates the shared-artwork transition',await page.evaluate(()=>VibraMotion.active||!!document.querySelector('.shared-artwork')));
 await settle();check('Transition layers are cleaned after expansion',await page.locator('.shared-artwork').count()===0);
 await capture('02-dettaglio');
 await page.locator('[data-action="catalog-save"]').click();await page.waitForFunction(()=>VibraApp.diagnostics().library===3);await settle();
 check('Home titles can be saved without downloading',await page.evaluate(()=>!qaCalls.some(c=>c.action==='download')));
 await close();check('Closing detail restores focus to its poster',await page.locator('.home-open').first().evaluate(e=>e===document.activeElement));
 await page.locator('.home-open').first().click();await settle();await page.locator('[data-action="catalog-search"]').click();await page.waitForSelector('.provider-open');
 check('Discovery searches the existing native source engine',await page.evaluate(()=>qaCalls.some(c=>c.action==='providerSearch'&&c.payload.kind==='movie')));
 await page.locator('.scope-toggle').click();await page.locator('[data-action="provider-kind"][data-value="all"]').click();await page.locator('#source-form button[type=submit]').click();await page.waitForFunction(()=>VibraApp.diagnostics().providerResults===3);
 check('Search retains movies, series and anime',await page.locator('.provider-result').count()===3);
 await capture('03-ricerca');
 await page.locator('[data-action="provider-filter"][data-value="series"]').click();check('Series result filter',await page.locator('.provider-result').count()===1);
 await page.locator('.provider-open').first().click();await settle();await page.locator('[data-action="provider-episodes"]').click();await page.waitForSelector('.episode-download');
 await page.locator('[data-action="provider-season"][data-season="2"]').click();await page.waitForFunction(()=>document.querySelector('[data-season="2"]')?.getAttribute('aria-pressed')==='true');
 check('Season selection reaches the engine',await page.evaluate(()=>qaCalls.some(c=>c.action==='providerEpisodes'&&c.payload.season===2)));
 await capture('04-episodi');await page.locator('.episode-download').first().click();await page.waitForFunction(()=>VibraApp.diagnostics().page==='files');
 check('Episode selection still prepares a real download payload',await page.evaluate(()=>qaCalls.some(c=>c.action==='providerResolve'&&c.payload.episode==='episode-0')&&qaCalls.some(c=>c.action==='download')));
 await page.waitForSelector('[data-action="media-play"]');await page.locator('[data-action="media-play"]').click();
 check('Offline player action is preserved',await page.evaluate(()=>qaCalls.some(c=>c.action==='mediaPlay')));
 await capture('05-download');
 await route('library');await page.locator('.library-card .card-open[data-id="1"]').click();await page.locator('#sheet [data-action="edit"]').click();
 check('Existing notes survive the new detail flow',await page.locator('textarea[name=notes]').inputValue()==='Nota da conservare <script> & "映画" 😀');await close();
 await capture('06-libreria');
 check('Serata remains directly accessible from the library',await page.locator('.library-utilities a[href="#tonight"]').isVisible());
 await page.locator('.library-utilities a[href="#tonight"]').click();await page.waitForSelector('#tonight-form');await page.locator('#tonight-form button[type=submit]').click();await page.waitForSelector('.plan');
 check('Serata still uses stored durations',await page.locator('.plan-item').count()===1);await capture('07-serata');
 await page.locator('#open-settings').click();await page.waitForSelector('[role=tab]');await capture('08-impostazioni');
 await page.locator('[data-action="settings-tab"][data-value="appearance"]').click();
 check('Ten layouts and ten palettes are retained',await page.locator('#appearance-layout option').count()===10&&await page.locator('#appearance-palette option').count()===10);
 for(const width of [320,390,900]){
  await page.setViewportSize({width,height:844});
  for(const layout of M.layouts){
   await route('settings');await page.locator('[data-action="settings-tab"][data-value="appearance"]').click();await page.locator('#appearance-layout').selectOption(layout.id);await page.waitForFunction(id=>document.documentElement.dataset.layout===id,layout.id);
   for(const name of ['home','movies','series','search','library','files','tonight','settings']){
    await route(name);check(`Fits viewport ${width}/${layout.id}/${name}`,await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2));
   }
  }
 }
 await page.setViewportSize({width:390,height:844});await page.locator('[data-action="settings-tab"][data-value="appearance"]').click();await page.locator('#appearance-layout').selectOption('classic');
 for(const palette of M.palettes){await page.locator('#appearance-palette').selectOption(palette.id);await page.waitForFunction(id=>document.documentElement.dataset.palette===id,palette.id);check('Persists palette '+palette.id,await page.locator('html').getAttribute('data-palette')===palette.id);}
 await page.locator('#appearance-palette').selectOption('graphite');await page.waitForFunction(()=>document.documentElement.dataset.palette==='graphite');
 await page.locator('[data-action="settings-tab"][data-value="general"]').click();
 for(const lang of ['en','de','ar','ja','bn','it']){
  await page.locator('#app-language').selectOption(lang);await page.waitForFunction(code=>VibraApp.diagnostics().language===code,lang);
  for(const name of ['home','search','library','settings']){await route(name);check('Long or RTL copy fits '+lang+'/'+name,await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2));}
 }
 await page.locator('[data-action="preference"][data-key="reducedMotion"]').click();await page.waitForFunction(()=>document.documentElement.dataset.reduced==='true');await route('home');
 await page.locator('.home-open').first().click();check('Reduced motion skips shared artwork',await page.locator('.shared-artwork').count()===0);await close();
 await page.locator('#open-settings').click();await page.locator('[data-action="preference"][data-key="reducedMotion"]').click();await page.waitForFunction(()=>document.documentElement.dataset.reduced==='false');
 await route('home');await page.locator('.home-open').first().click();await page.locator('[data-action="close-sheet"]').click();await page.waitForFunction(()=>!document.querySelector('#sheet').open);
 check('Rapid open-close leaves no transition layer or scroll lock',await page.locator('.shared-artwork').count()===0&&await page.locator('html').getAttribute('data-modal')===null);
 await page.locator('#home-search input').fill('Titolo QA');await page.locator('#home-search button').click();await page.waitForSelector('.provider-open');
 check('Home search submits the entered title',await page.evaluate(()=>qaCalls.some(c=>c.action==='providerSearch'&&c.payload.query==='Titolo QA')));
 await page.evaluate(()=>VibraApp.back());await page.waitForFunction(()=>VibraApp.diagnostics().page==='home');check('Android back returns to Home before exiting',await page.evaluate(()=>VibraApp.back()===false));
 await page.reload();await page.waitForFunction(()=>VibraApp?.diagnostics().ready);check('Saved titles persist across restart',await page.evaluate(()=>VibraApp.diagnostics().library===3));
 check('No runtime browser errors',errors.length===0);
 const report={passed:checks.length,checks,errors,note:'Actual packaged UI; Android transport mocked. Browse metadata is a snapshot; downloads and playback are simulated. No physical phone FPS or decoder claim.'};
 fs.writeFileSync(path.join(out,'VERIFICA-UI.json'),JSON.stringify(report,null,2));fs.mkdirSync(root+'/build/streaming',{recursive:true});fs.writeFileSync(root+'/build/streaming/VERIFICA-UI.json',JSON.stringify(report,null,2));
 console.log(JSON.stringify({passed:checks.length,errors}));
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
