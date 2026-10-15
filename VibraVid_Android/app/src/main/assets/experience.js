'use strict';
(function(global){
  const norm=value=>String(value||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('it').trim();
  const genres=[
    ['Thriller',['thriller','suspense']],['Azione',['azione','action','action & adventure','azione e avventura']],
    ['Commedia',['commedia','commedie','comedy']],['Horror',['horror']],['Drammatico',['drammatico','drammatici','drama']],
    ['Fantascienza',['fantascienza','science-fiction','science fiction','sci-fi','sci-fi & fantasy','fantascienza e fantasy']],
    ['Fantasy',['fantasy','fantascienza e fantasy']],['Avventura',['avventura','adventure','azione e avventura']],['Crime',['crime','giallo']],
    ['Animazione',['animazione','animation','anime']],['Romantico',['romance','romantico','romantici']],
    ['Documentari',['documentari','documentary','documentario']],['Famiglia',['family','famiglia','per tutta la famiglia','kids']]
  ];
  function categories(row){
    const tags=(row.tags||[]).map(norm),out=[];
    genres.forEach(([name,aliases])=>{if(tags.some(t=>aliases.some(a=>t===a||t.split(/[,/]/).some(s=>s.trim()===a))))out.push(name);});
    return out;
  }
  function groups(rows){
    const used=new Set(),out=genres.map(([name])=>({name,rows:rows.filter(row=>categories(row).includes(name)).slice(0,20)})).filter(g=>g.rows.length);
    out.forEach(g=>g.rows.forEach(r=>used.add(r)));
    const other=rows.filter(r=>!used.has(r)).slice(0,20);if(other.length)out.push({name:'Da scoprire',rows:other});
    return out;
  }
  function similar(row,rows){
    const genre=categories(row),key=global.VibraModel.identity(row);
    return rows.filter(r=>r.kind===row.kind&&global.VibraModel.identity(r)!==key)
      .map((r,i)=>({row:r,score:categories(r).filter(g=>genre.includes(g)).length,index:i}))
      .filter(r=>r.score>0).sort((a,b)=>b.score-a.score||a.index-b.index).slice(0,10).map(r=>r.row);
  }
  function progress(row,records){
    const key=global.VibraModel.identity(row),record=records[key];
    if(!record||!Number.isFinite(record.duration)||record.duration<=0||!Number.isFinite(record.position))return null;
    const position=Math.max(0,Math.min(record.duration,record.position));
    return {...record,position,percent:Math.min(100,Math.round(100*position/record.duration))};
  }
  class Transfers{
    constructor(){this.samples=new Map();}
    sample(row,now){
      const id=String(row.id),previous=this.samples.get(id),active=row.status===2&&!row.paused;
      let speed=null;
      if(active&&previous?.active&&now-previous.time>=500&&now-previous.time<30000&&row.bytes>=previous.bytes){
        const measured=(row.bytes-previous.bytes)*1000/(now-previous.time);
        speed=measured>0?(previous.speed>0?.4*measured+.6*previous.speed:measured):0;
      }
      if(active&&previous?.active&&now-previous.time<500)return {speed:previous.speed,eta:previous.speed>0&&row.total>row.bytes?Math.ceil((row.total-row.bytes)/previous.speed):null};
      this.samples.set(id,{bytes:Math.max(0,row.bytes||0),time:now,active,speed});
      return {speed,eta:speed>0&&row.total>row.bytes?Math.ceil((row.total-row.bytes)/speed):null};
    }
    prune(ids){for(const key of this.samples.keys())if(!ids.has(key))this.samples.delete(key);}
  }
  const bytes=n=>!Number.isFinite(n)||n<0?'—':n>=1073741824?(n/1073741824).toFixed(1)+' GB':n>=1048576?(n/1048576).toFixed(1)+' MB':Math.round(n/1024)+' KB';
  const eta=seconds=>seconds<60?Math.max(1,Math.ceil(seconds))+' s':seconds<3600?Math.ceil(seconds/60)+' min':Math.floor(seconds/3600)+' h '+Math.ceil(seconds%3600/60)+' min';
  const api={genres,categories,groups,similar,progress,Transfers,bytes,eta};
  global.VibraExperience=api;if(typeof module!=='undefined')module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
