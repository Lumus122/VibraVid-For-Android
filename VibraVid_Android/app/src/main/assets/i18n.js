'use strict';
(function(global){
  const catalog=global.VibraLocales||{languages:[{code:'it',name:'Italiano'}],messages:{it:{}}};
  let language='it';
  const escape=value=>String(value).replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  const known=Object.keys(catalog.messages.en||{}).filter(key=>key.length>=4).sort((a,b)=>b.length-a.length);
  const pattern=new RegExp(known.map(key=>key.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')).join('|')||'(?!)','g');
  function valid(code){return catalog.languages.some(item=>item.code===code);}
  function select(code){language=valid(code)?code:'it';if(global.document){document.documentElement.lang=language;document.documentElement.dir=language==='ar'?'rtl':'ltr';}return language;}
  function text(value){
    if(value===null||value===undefined)return '';const source=String(value);if(language==='it')return source;
    const key=source.trim(),dict=catalog.messages[language]||{},base=catalog.messages.en||{};
    const translation=dict[key]||base[key];if(translation)return source.slice(0,source.indexOf(key))+translation+source.slice(source.indexOf(key)+key.length);
    // Interpolated app messages keep values/titles unchanged. Only an owned prefix/suffix is replaced.
    for(const part of known){const target=dict[part]||base[part];if(!target||part===target)continue;
      if(key.startsWith(part)&&key.length>part.length&&/\s|[:·…]/.test(key[part.length]))return source.replace(part,target);
      if(key.endsWith(part)&&key.length>part.length&&/\s|[0-9·]/.test(key[key.length-part.length-1]))return source.slice(0,source.lastIndexOf(part))+target+source.slice(source.lastIndexOf(part)+part.length);
    }return source;
  }
  function html(markup){
    let value=String(markup);if(language==='it')return value;
    value=value.replace('Cosa<br>scarichiamo?',escape(text('Cosa scarichiamo?')));
    // Escaped data is numeric entities, so it cannot match a UI literal. No DOM observer edits user content.
    value=value.replace(/(>)([^<>]*)(?=<)/g,(match,start,body)=>start+translateFragment(body));
    return value.replace(/\b(placeholder|aria-label|title)="([^"<>]*)"/g,(match,attribute,body)=>attribute+'="'+translateFragment(body)+'"');
  }
  function translateFragment(body){
    if(!body.trim()||/^(&#\d+;|\s)+$/.test(body))return body;
    if(body.includes('&#'))return body.split(/(&#\d+;)/).map(part=>/^&#\d+;$/.test(part)?part:translateFragment(part)).join('');
    const dict=catalog.messages[language]||{},base=catalog.messages.en||{};
    if(dict[body.trim()]||base[body.trim()])return escape(text(body));
    return body.replace(pattern,(key,index,full)=>{
      if(index>0&&/[A-Za-zÀ-ÿ]/.test(full[index-1])&&/[A-Za-zÀ-ÿ]/.test(key[0]))return key;
      if(index+key.length<full.length&&/[A-Za-zÀ-ÿ]/.test(full[index+key.length])&&/[A-Za-zÀ-ÿ]/.test(key[key.length-1]))return key;
      return escape(dict[key]||base[key]||key);
    });
  }
  function shell(){if(!global.document)return;
    document.querySelectorAll('[data-i18n]').forEach(element=>{element.textContent=text(element.dataset.i18n);});
    document.querySelectorAll('[data-i18n-label]').forEach(element=>{element.setAttribute('aria-label',text(element.dataset.i18nLabel));});
  }
  global.VibraI18n={languages:catalog.languages,select,text,html,shell,valid,get language(){return language;}};
  if(typeof module!=='undefined')module.exports=global.VibraI18n;
})(typeof window!=='undefined'?window:globalThis);
