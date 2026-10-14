// Source migration uses Acorn; translation only touches app-owned literals, never library data.
const fs=require('fs'),path=require('path'),acorn=require('./vendor/acorn/acorn.js');
const root=path.resolve(__dirname,'..'),file=path.join(root,'app/src/main/assets/app.js');
const source=fs.readFileSync(file,'utf8'),tree=acorn.parse(source,{ecmaVersion:'latest'}),keys=new Set(),edits=[];
if(source.includes('window.VibraI18n'))throw new Error('One-time migration already applied. Edit catalogs and use build_locales.py --offline.');
const brands=new Set(['VibraVid','VibraVid Studio','Wikidata','TVmaze','StreamingCommunity','AnimeUnity','AnimeWorld','Eurostreaming','RaiPlay','Nunito Sans','Bricolage','Apache 2.0','GPL v3','CC BY-SA','AD']);
const human=value=>typeof value==='string'&&value.trim()&&!brands.has(value.trim())&&!/[#{}<>/=\\\[\]]/.test(value)&&(!/^[A-Z0-9_.-]+$/.test(value)||value.includes(' '))&&(/^[A-ZÀ-Ý‹]/.test(value.trim())||value.includes(' ')&&/[a-zA-ZÀ-ÿ]/.test(value));
function walk(node,parent){if(!node||typeof node.type!=='string')return;
  if(node.type==='Literal'&&human(node.value)&&!(parent&&parent.type==='Property'&&parent.key===node)&&!(parent&&parent.type==='CallExpression'&&parent.callee.type==='MemberExpression'&&parent.callee.object.name==='L')){
    keys.add(node.value.trim());edits.push({start:node.start,end:node.end,value:'L.text('+source.slice(node.start,node.end)+')'});
  }
  if(node.type==='AssignmentExpression'&&node.left.type==='MemberExpression'&&node.left.property.name==='innerHTML'){
    edits.push({start:node.right.start,end:node.right.start,value:'L.html('});edits.push({start:node.right.end,end:node.right.end,value:')'});
  }
  if(node.type==='CallExpression'&&node.callee.type==='MemberExpression'&&node.callee.property.name==='insertAdjacentHTML'){
    const arg=node.arguments[1];edits.push({start:arg.start,end:arg.start,value:'L.html('});edits.push({start:arg.end,end:arg.end,value:')'});
  }
  if(node.type==='TemplateElement'||node.type==='Literal'&&typeof node.value==='string'&&node.value.includes('<')){
    let html=node.type==='TemplateElement'?node.value.raw:node.value;
    for(const m of html.matchAll(/>([^<>]*)|(?:placeholder|aria-label|title)="([^"<>]*)"/g)){
      const value=(m[1]||m[2]||'').trim();if(value&&!value.includes('${')&&/[a-zA-ZÀ-ÿ]/.test(value)&&!value.includes('<'))keys.add(value);
    }
  }
  for(const [key,value] of Object.entries(node))if(key!=='parent'){if(Array.isArray(value))value.forEach(child=>walk(child,node));else if(value&&typeof value==='object'&&typeof value.type==='string')walk(value,node);}
}
walk(tree,null);
edits.sort((a,b)=>b.start-a.start||b.end-a.end);let result=source;
for(const edit of edits)result=result.slice(0,edit.start)+edit.value+result.slice(edit.end);
result=result.replace('const M = window.VibraModel,','const L = window.VibraI18n, M = window.VibraModel,');
// Numeric entities protect every escaped value from the HTML literal translator, including titles called "Film".
result=result.replace(/const esc = text =>[^\n]+;/,"const esc = text => Array.from(String(text === undefined || text === null ? '' : text), ch => '&#'+ch.codePointAt(0)+';').join('');");
acorn.parse(result,{ecmaVersion:'latest'});
fs.mkdirSync(path.join(root,'build/i18n'),{recursive:true});
fs.writeFileSync(path.join(root,'build/i18n/app-before-i18n.js'),source);
fs.writeFileSync(file,result);
// HTML shell, model's presentation vocabulary, native controls and errors share one catalog.
for(const name of ['index.html','model.js']){
  const body=fs.readFileSync(path.join(root,'app/src/main/assets',name),'utf8');
  if(name.endsWith('.js'))walkModel(acorn.parse(body,{ecmaVersion:'latest'}));
  else for(const m of body.matchAll(/>([^<>]+)<|(?:placeholder|aria-label|title)="([^"<>]+)"/g))if(human(m[1]||m[2]))keys.add((m[1]||m[2]).trim());
}
function walkModel(node){if(node.type==='Literal'&&human(node.value))keys.add(node.value.trim());for(const value of Object.values(node)){if(Array.isArray(value))value.forEach(v=>{if(v&&v.type)walkModel(v)});else if(value&&value.type)walkModel(value);}}
const dir=path.join(root,'app/src/main/java/it/vibravid/studio');
for(const name of fs.readdirSync(dir).filter(n=>n.endsWith('.java'))){
 const body=fs.readFileSync(path.join(dir,name),'utf8');
 for(const m of body.matchAll(/"((?:[^"\\]|\\.)*)"/g))try{const value=JSON.parse(m[0]);if(human(value))keys.add(value.trim());}catch{}
}
['Lingua','Lingua dell’app','Scegli la lingua dell’interfaccia. La scelta resta salvata sul telefono.','Lingua aggiornata.','Cosa scarichiamo?','Cosa','scarichiamo?'].forEach(k=>keys.add(k));
fs.writeFileSync(path.join(root,'build/i18n/keys.json'),JSON.stringify([...keys].sort(),null,2));
console.log(JSON.stringify({messages:keys.size,edits:edits.length}));
