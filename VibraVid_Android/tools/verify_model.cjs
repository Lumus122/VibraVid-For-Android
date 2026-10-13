'use strict';
const assert = require('node:assert/strict');
const M = require('../app/src/main/assets/model.js');
let count=0;
function test(name,fn){fn();count++;process.stdout.write('OK '+name+'\n');}
const row=(name,extra={})=>({name,kind:'movie',year:2025,...extra});
test('Offline empty library hydrates',()=>assert.deepEqual(M.hydrate(M.empty()),M.empty()));
test('Names with accents merge while notes survive',()=>{
  let d=M.add(M.empty(),row('Caffè')).data;
  d=M.edit(d,1,{notes:'Non perdere questa nota',rating:5,duration:90});
  const result=M.add(d,row('CAFFE',{sources:['Altra fonte']}));
  assert.equal(result.added,false);assert.equal(result.data.items.length,1);assert.equal(result.data.items[0].notes,'Non perdere questa nota');assert.equal(result.data.items[0].rating,5);
});
test('Film, series and release years retain distinct identities',()=>{
  let d=M.empty();for(const r of [row('Titolo'),row('Titolo',{kind:'series'}),row('Titolo',{year:2024})])d=M.add(d,r).data;assert.equal(d.items.length,3);
});
test('Desktop format round trip retains library details',()=>{
  const d=M.add(M.empty(),row('Storia',{notes:'Note',rating:4,duration:110,tags:['Relax'],collection_name:'Weekend'})).data;
  const next=M.importLibrary(M.empty(),M.exportLibrary(d));assert.equal(next.added,1);assert.equal(next.data.items[0].notes,'Note');assert.deepEqual(next.data.collections,['Weekend']);assert.equal(next.data.items[0].duration,110);
});
test('Import preserves existing personal edits',()=>{
  const old=M.add(M.empty(),row('Storia',{notes:'Mie',rating:5})).data;
  const incoming=M.exportLibrary(M.add(M.empty(),row('Storia',{notes:'Altre',rating:1,sources:['PC']})).data);
  const result=M.importLibrary(old,incoming);assert.equal(result.merged,1);assert.equal(result.data.items[0].notes,'Mie');assert.equal(result.data.items[0].rating,5);assert.deepEqual(result.data.items[0].sources,['PC']);
});
test('Invalid import is atomic',()=>{
  const original=M.add(M.empty(),row('Originale')).data, snapshot=JSON.stringify(original);
  assert.throws(()=>M.importLibrary(original,{format:'vibravid-library',version:1,items:[row('Valido'),row('Invalido',{duration:-1})]}));assert.equal(JSON.stringify(original),snapshot);
});
test('Unsupported format and oversized imports fail',()=>{
  assert.throws(()=>M.importLibrary(M.empty(),{format:'other',version:1,items:[]}));assert.throws(()=>M.importLibrary(M.empty(),{format:'vibravid-library',version:1,items:Array(2001).fill(row('A'))}));
});
test('Data validation rejects unsafe URLs and invalid ranges',()=>{
  for(const extra of [{poster:'javascript:alert(1)'},{url:'https://user:pass@example.com'},{year:10},{rating:8},{duration:0},{state:'unknown'},{tags:Array(9).fill('Tag')},{notes:'a'.repeat(2001)}])assert.throws(()=>M.clean(row('A',extra)));
});
test('Text remains text and is not stripped or executed',()=>assert.equal(M.clean(row('<img src=x onerror=alert(1)>')).name,'<img src=x onerror=alert(1)>'));
test('Editing into an existing identity is refused',()=>{
  let d=M.add(M.empty(),row('Uno')).data;d=M.add(d,row('Due')).data;assert.throws(()=>M.edit(d,2,{name:'Uno'}));assert.equal(d.items[1].name,'Due');
});
test('Planner respects time, mood, count and watched state',()=>{
  let d=M.empty();for(const r of [row('Uno',{duration:60,tags:['Relax']}),row('Due',{duration:50,tags:['Relax']}),row('Visto',{duration:60,tags:['Relax'],state:'watched'}),row('Lungo',{duration:200})])d=M.add(d,r).data;
  const p=M.plan(d,120,2,'relax');assert.equal(p.items.length,2);assert.equal(p.minutes,110);assert.deepEqual(p.items.map(i=>i.name).sort(),['Due','Uno']);assert.equal(M.plan(d,50,2).items.length,0);
});
test('Another plan avoids the previous combination when possible',()=>{
  let d=M.empty();for(const name of ['Uno','Due','Tre'])d=M.add(d,row(name,{duration:60})).data;
  const first=M.plan(d,120,2),next=M.plan(d,120,2,'',first.items.map(i=>i.id));assert.notDeepEqual(first.items.map(i=>i.id).sort(),next.items.map(i=>i.id).sort());
});
test('Saved data with duplicate identifiers is refused',()=>{
  const d=M.add(M.empty(),row('Uno')).data;d.items.push({...d.items[0],name:'Due'});assert.throws(()=>M.hydrate(d));
});
test('Library capacity and collection capacity are enforced',()=>{
  const d=M.empty();d.items=Array.from({length:2000},(_,i)=>({...M.clean(row('Storia '+i)),id:i+1,updated_at:'2026-10-12'}));assert.throws(()=>M.add(d,row('Nuova')));
  const c=M.empty();c.collections=Array.from({length:40},(_,i)=>'Raccolta '+i);assert.throws(()=>M.add(c,row('Nuova',{collection_name:'Altra'})));
});
test('Malformed optional file links do not prevent opening the library',()=>{
  const d=M.empty();d.files=[null,{},42,{token:'bdbab35d-9d41-4fe7-86a5-9f3b513a7663',name:'Video'},{token:'bdbab35d-9d41-4fe7-86a5-9f3b513a7663',name:'Duplicate'}];
  assert.equal(M.hydrate(d).files.length,1);d.collections={broken:true};assert.throws(()=>M.hydrate(d),/raccolte/);
});
test('Invalid artwork types produce a clear validation error',()=>assert.throws(()=>M.clean(row('A',{poster:4})),/testo/));
test('Merged artwork retains its attribution',()=>{
  const d=M.add(M.empty(),row('A')).data, incoming=row('A',{poster:'https://example.com/art.jpg',image_source:'https://example.com/credits',image_credit:'Autore · CC BY'});
  const added=M.add(d,incoming).data.items[0],imported=M.importLibrary(d,{format:'vibravid-library',version:1,items:[incoming]}).data.items[0];
  for(const item of [added,imported]){assert.equal(item.image_credit,incoming.image_credit);assert.equal(item.image_source,incoming.image_source);}
});
test('Identifier limit permits adding and importing without corrupting persisted IDs',()=>{
  let d=M.add(M.empty(),row('A')).data;d.items[0].id=2147483647;
  d=M.add(d,row('B')).data;assert.equal(d.items[1].id,1);
  d=M.importLibrary(d,{format:'vibravid-library',version:1,items:[row('C'),row('D')]}).data;
  assert.equal(new Set(d.items.map(i=>i.id)).size,4);assert.equal(M.hydrate(d).items.length,4);
});
test('Automatic matching retains non-Latin letters and cannot conflate different titles',()=>{
  assert.notEqual(M.normalizedTitle('QA فيلم'),M.normalizedTitle('QA فيلم آخر'));assert.equal(M.normalizedTitle('千と千尋の神隠し'),'千と千尋の神隠し');
  assert.equal(M.normalizedTitle('Caffè — Film!'),M.normalizedTitle('CAFFE FILM'));assert.equal(M.normalizedTitle('?!'), '');
});
test('Two-title plans consider all 2,000 titles rather than only the first 200',()=>{
  const d=M.empty();d.items=Array.from({length:220},(_,i)=>({...M.clean(row('Lungo '+i,{duration:90,rating:5})),id:i+1}));
  d.items.push({...M.clean(row('Breve',{duration:30,rating:1})),id:221});
  for(let n=0;n<20;n++){const result=M.plan(d,120,2);assert.equal(result.items.length,2);assert.equal(result.minutes,120);assert(result.items.some(i=>i.id===221));}
});
test('Full-capacity planner keeps a bounded pool and avoids previous pairs',()=>{
  const d=M.empty();d.items=Array.from({length:2000},(_,i)=>({...M.clean(row('Film '+i,{duration:30,rating:5})),id:i+1}));
  const start=performance.now(),a=M.plan(d,120,2),b=M.plan(d,120,2,'',a.items.map(i=>i.id));
  assert.equal(a.items.length,2);assert.notDeepEqual(a.items.map(i=>i.id).sort(),b.items.map(i=>i.id).sort());
  assert(performance.now()-start<1000,'Planner unnecessarily scans or stores millions of equivalent plans');
});
test('Planner matches exhaustive scoring with a deterministic choice',()=>{
  const d=M.empty();d.items=Array.from({length:70},(_,i)=>({...M.clean(row('Film '+i,{duration:15+(i*17)%130,rating:i%6,state:i%3===0?'watching':'planned'})),id:i+1}));
  const score=i=>i.duration/150*60+i.rating*3+(i.state==='planned'?5:0),valid=[];
  for(let i=0;i<d.items.length;i++)for(let j=i+1;j<d.items.length;j++)if(d.items[i].duration+d.items[j].duration<=150)valid.push(score(d.items[i])+score(d.items[j]));
  const random=Math.random;try{Math.random=()=>0;assert.equal(M.plan(d,150,2).items.reduce((s,i)=>s+score(i),0),Math.max(...valid));}finally{Math.random=random;}
});
process.stdout.write(count+' checks passed\n');
