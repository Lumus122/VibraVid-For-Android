'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
const assets=path.resolve(__dirname,'../app/src/main/assets');
global.window=global;vm.runInThisContext(fs.readFileSync(path.join(assets,'locales.js'),'utf8'));
const L=require(path.join(assets,'i18n.js')),M=require(path.join(assets,'model.js'));
let checks=0;function check(fn){fn();checks++;}
const encode=value=>Array.from(value,ch=>'&#'+ch.codePointAt(0)+';').join('');
check(()=>assert.equal(L.languages.length,21));
for(const language of L.languages){
 L.select(language.code);
 check(()=>assert.equal(L.language,language.code));
 check(()=>assert.equal(M.hydrate({...M.empty(),preferences:{language:language.code}}).preferences.language,language.code));
 for(const title of ['Film','Salva video','Lingua','<script>alert(1)</script>','A & B "映画"','😀 العربية']){
  const value=encode(title);
  check(()=>assert.equal(L.html('<h2>'+value+'</h2>'),'<h2>'+value+'</h2>'));
  check(()=>assert.equal(L.html('<input value="'+value+'" title="'+value+'">'),'<input value="'+value+'" title="'+value+'">'));
 }
 check(()=>assert.equal(L.html('<a href="https://example.com/a?x=1&amp;y=2">A &amp; B</a>'),'<a href="https://example.com/a?x=1&amp;y=2">A &amp; B</a>'));
 const dictionary=JSON.parse(fs.readFileSync(path.join(assets,'locales',language.code+'.json'),'utf8'));
 for(const value of Object.values(dictionary))check(()=>assert.ok(typeof value==='string'&&value.trim()));
 check(()=>assert.equal(L.text('Salva video'),dictionary['Salva video']));
}
L.select('unknown');check(()=>assert.equal(L.language,'it'));check(()=>assert.equal(L.text(null),''));
check(()=>assert.equal(M.hydrate({...M.empty(),preferences:{language:'../unknown'}}).preferences.language,'it'));
L.select('en');check(()=>assert.equal(L.text(' Galleria '),' Gallery '));
fs.mkdirSync(path.resolve(__dirname,'../build/i18n'),{recursive:true});
fs.writeFileSync(path.resolve(__dirname,'../build/i18n/unit-results.json'),JSON.stringify({passed:checks,languages:L.languages.length},null,2));
console.log(JSON.stringify({passed:checks,languages:L.languages.length}));
