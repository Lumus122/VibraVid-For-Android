'use strict';
const assert=require('node:assert/strict');
const M=require('../app/src/main/assets/model.js');
let checks=0,minContrast=Infinity;
const test=(name,fn)=>{fn();checks++;console.log('OK '+name);};
const luminance=hex=>[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255).reduce((sum,v,i)=>sum+[.2126,.7152,.0722][i]*(v<=.04045?v/12.92:((v+.055)/1.055)**2.4),0);
test('Exactly ten unique layouts and palettes',()=>{
 for(const options of [M.layouts,M.palettes]){assert.equal(options.length,10);assert.equal(new Set(options.map(o=>o.id)).size,10);}
});
test('Existing installations retain cinema, motion, Wi-Fi and all library data',()=>{
 let old=M.add(M.empty(),{name:'Una storia',year:2024,notes:'Le mie note',rating:4,collection_name:'Preferiti'}).data;
 old.preferences={theme:'cinema',reducedMotion:true,wifiOnly:true};
 const next=M.hydrate(old);assert.deepEqual(next.items,old.items);assert.deepEqual(next.collections,old.collections);
 assert.deepEqual(next.preferences,{theme:'cinema',layout:'classic',palette:'graphite',reducedMotion:true,wifiOnly:true});
});
test('Unknown and malformed appearance values safely fall back',()=>{
 for(const value of [null,'unknown',42,{},['list'],'<script>']){
  const d=M.empty();d.preferences.layout=value;d.preferences.palette=value;
  assert.deepEqual(M.appearance(M.hydrate(d).preferences),{layout:'classic',palette:'graphite'});
 }
});
for(const layout of M.layouts)for(const palette of M.palettes){
 test('Save, restart and edit retain '+layout.name+' / '+palette.name,()=>{
  let d=M.add(M.empty(),{name:'Titolo',notes:'Note da conservare',duration:95}).data;
  d.preferences.layout=layout.id;d.preferences.palette=palette.id;d.preferences.wifiOnly=true;
  const restored=M.hydrate(JSON.parse(JSON.stringify(d)));
  assert.equal(restored.preferences.layout,layout.id);assert.equal(restored.preferences.palette,palette.id);
  const edited=M.edit(restored,1,{rating:5});assert.deepEqual(edited.preferences,restored.preferences);assert.equal(edited.items[0].notes,'Note da conservare');
  const imported=M.importLibrary(edited,M.exportLibrary(M.add(M.empty(),{name:'Secondo titolo'}).data)).data;
  assert.deepEqual(imported.preferences,restored.preferences);
 });
}
for(const palette of M.palettes)for(const theme of ['light','cinema']){
 test('Readable text and valid native colors: '+palette.name+' / '+theme,()=>{
  const colors=M.paletteColors({palette:palette.id,theme});
  for(const value of Object.values(colors))assert.match(value,/^#[0-9a-f]{6}$/);
  for(const bg of ['paper','surface','soft','raised','recess'])for(const fg of ['ink','muted','accent']){
   const a=luminance(colors[fg]),b=luminance(colors[bg]),ratio=(Math.max(a,b)+.05)/(Math.min(a,b)+.05);
   assert.ok(ratio>=4.5,`${fg} on ${bg}: ${ratio}`);minContrast=Math.min(minContrast,ratio);
  }
  const a=luminance(colors.accent),b=luminance(colors['on-accent']);assert.ok((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=4.5);
  assert.equal(M.lightPalette({palette:palette.id,theme}),palette.light===true&&theme!=='cinema');
 });
}
console.log(JSON.stringify({checks,minimum_text_contrast:Number(minContrast.toFixed(2))}));
