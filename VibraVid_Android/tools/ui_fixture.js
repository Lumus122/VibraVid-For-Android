'use strict';
// Test transport only: never bundled in the APK, never contacts a video provider.
(() => {
  const sources=/*SOURCES*/[],query=new URLSearchParams(location.search),held=[];
  let hold=false;
  const copy=v=>JSON.parse(JSON.stringify(v));
  function seed(){
    let d=VibraModel.empty();
    for(let i=0;i<16;i++)d=VibraModel.add(d,{name:i===0?'QA titolo da modificare':'Una storia con un titolo particolarmente lungo '+i,year:2020+i%7,duration:40+i*3,notes:'La nota che deve restare',tags:['Relax'],rating:i%6,collection_name:'Weekend'}).data;
    d.files=[{token:'7aa54ee5-d1e1-4da5-8d42-994a79c7641f',name:'Video con un nome molto lungo senza spazi '+ 'x'.repeat(100)}];
    return d;
  }
  const rows=[1,2,4,8,16].map((status,i)=>({id:String(i+1).repeat(64),name:'Titolo scaricato con nome lungo '+i+' '+ 'x'.repeat(80),engine:'offline',source:'Fonte QA',status,bytes:status===8?4600000:1200000,total:4600000,percent:status===8?100:26,paused:status===4,error:status===16?'La fonte ha rifiutato il download. Riprova o scegli un’altra fonte.':''}));
  const notify=(id,ok,value)=>setTimeout(()=>VibraNativeReceive({id,ok,value}),0);
  function request(id,action,payload){
    const input=JSON.parse(payload);
    if(action==='save'&&hold){held.push([id,action,payload]);document.querySelector('#qa-state').textContent='Salvataggio in attesa';return;}
    try{
      let value={};
      if(action==='load'){
        value=JSON.parse(localStorage.getItem('vv.audit.fixture')||'null')||seed();
        if(query.has('layout'))value.preferences.layout=query.get('layout');
        if(query.has('palette'))value.preferences.palette=query.get('palette');
      }
      if(action==='save'){localStorage.setItem('vv.audit.fixture',JSON.stringify(input));value={saved:true};}
      if(action==='sources')value=copy(sources);
      if(action==='downloads')value=copy(rows);
      if(action==='search')value={items:[{name:'QA risultato del catalogo',kind:'movie',year:2025,duration:90,sources:['Wikidata'],notes:''}],source:'Wikidata',cached:false};
      if(action==='providerSearch')value={items:[{name:'QA film con nome lungo che deve rimanere leggibile',kind:'movie',year:2025,provider:'streamingcommunity',source:'StreamingCommunity',key:'movie'},{name:'QA serie con stagioni ed episodi',kind:'series',year:2026,provider:'streamingcommunity',source:'StreamingCommunity',key:'series'}],warnings:['Fonte QA: connessione assente.'],successful:1};
      if(action==='providerEpisodes')value={season:input.season<0?0:input.season,seasons:[{number:0,name:'Speciali'},{number:1,name:'Stagione 1'}],episodes:Array.from({length:12},(_,i)=>({key:String(i),number:i+1,name:'Un episodio con un titolo lungo '+i}))};
      if(action==='providerResolve')value={name:'Video di prova',url:'https://example.com/test.mp4',mime:'video/mp4'};
      if(action==='import')value={data:{format:'vibravid-library',version:1,items:[{name:'QA importato',kind:'movie',duration:65}]}};
      if(action==='export')value={saved:true};
      if(action==='pickVideo')value={cancelled:true};
      if(action==='mediaRemove'){const i=rows.findIndex(r=>r.id===input.id);if(i>=0)rows.splice(i,1);}
      if(action==='mediaPause'){const row=rows.find(r=>r.id===input.id);if(row){row.paused=input.paused;row.status=input.paused?4:2;}}
      notify(id,true,value);
    }catch(error){notify(id,false,error.message);}
  }
  window.VibraAndroid={request};
  addEventListener('DOMContentLoaded',()=>{
    document.querySelector('#qa-hold').onclick=()=>{hold=true;document.querySelector('#qa-state').textContent='Prossimo salvataggio ritardato';setTimeout(()=>{hold=false;held.splice(0).forEach(args=>request(...args));document.querySelector('#qa-state').textContent='Salvataggi completati';},10000);};
    document.querySelector('#qa-release').onclick=()=>{hold=false;held.splice(0).forEach(args=>request(...args));document.querySelector('#qa-state').textContent='Salvataggi completati';};
  });
})();
