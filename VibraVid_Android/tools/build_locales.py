"""Developer-only translation drafting. The app ships complete local catalogs and never calls a translator.

Only static UI copy is sent: never titles, notes, library contents, credentials or user files.
Use --offline to rebuild the JS bundle from bundled JSON catalogs without network.
Generated drafts require contextual review; normal APK builds never invoke this tool.
"""
import json,urllib.request,urllib.parse,time,re,concurrent.futures,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
LANGUAGES=[('it','Italiano'),('en','English'),('es','Español'),('pt','Português'),('fr','Français'),('de','Deutsch'),('nl','Nederlands'),('pl','Polski'),('ro','Română'),('uk','Українська'),('ru','Русский'),('tr','Türkçe'),('ar','العربية'),('hi','हिन्दी'),('bn','বাংলা'),('zh','简体中文'),('ja','日本語'),('ko','한국어'),('id','Bahasa Indonesia'),('vi','Tiếng Việt'),('th','ไทย')]
OUT=ASSETS/'locales';OUT.mkdir(exist_ok=True)
def translate(value,lang,source='en'):
 query=urllib.parse.urlencode({'client':'gtx','sl':source,'tl':lang,'dt':'t','q':value})
 url='https://translate.googleapis.com/translate_a/single?'+query
 for attempt in range(4):
  try:
   with urllib.request.urlopen(url,timeout=25) as response:data=json.load(response)
   return ''.join(row[0] for row in data[0] if row[0])
  except Exception:
   if attempt==3:raise
   time.sleep(1+attempt)
def batches(items,limit=2600):
 batch=[];length=0
 for key,value in items:
  if batch and length+len(value)>limit:yield batch;batch=[];length=0
  batch.append((key,value.replace('\n',' ')));length+=len(value)+1
 if batch:yield batch
OVERRIDES={
 'Cosa scarichiamo?':'What shall we download?','Cosa':'What','scarichiamo?':'shall we download?',
 'Aspetto':'Appearance','Studio':'Studio',
 'Studio, in tasca':'Studio, in your pocket',
 'Lingua':'Language','Lingua dell’app':'App language','Scegli la lingua dell’interfaccia. La scelta resta salvata sul telefono.':'Choose the interface language. Your choice is saved on this phone.',
 'Lingua aggiornata.':'Language updated.','Galleria':'Gallery','Salva video':'Save video','Salva in galleria':'Save to gallery','Salvataggio in galleria':'Saving to gallery',
 'Salvato in galleria · Filmati/VibraVid':'Saved to gallery · Movies/VibraVid','Filmati/VibraVid':'Movies/VibraVid',
 'Scarica':'Download','Cerca e scarica':'Search and download','Impostazioni':'Settings','Libreria':'Library','Serata':'Movie night','Da vedere':'To watch','In visione':'Watching','Visto':'Watched',
 'Preparo il tuo video':'Preparing your video','Guardiamo qualcosa?':'What shall we watch?','Il titolo del film o della serie':'Movie or series title','Brano':'Song','Libro':'Book',
 'Riduci movimento':'Reduce motion','Adatta':'Fit','Riempi':'Fill','Sblocca i controlli':'Unlock controls','Blocca i controlli':'Lock controls','Schermo bloccato':'Screen locked',
 'Indietro di 10 secondi':'Back 10 seconds','Avanti di 10 secondi':'Forward 10 seconds','Mini player':'Mini player','Luminosità ·':'Brightness ·',
 'VIDEO OFFLINE':'OFFLINE VIDEO','VIDEO DEL DISPOSITIVO':'DEVICE VIDEO','VIDEO ONLINE':'ONLINE VIDEO','Nome del video':'Video name',
 'Audio e sottotitoli':'Audio and subtitles','Audio / sottotitoli':'Audio / subtitles','Sottotitoli':'Subtitles','Disattivati':'Off','Traccia audio':'Audio track',
 'Conferma rimozione':'Confirm removal','Verifica e ripara':'Check and repair','Disponibile offline':'Available offline','In attesa della rete':'Waiting for network',
 'Inserisci un nome per il video, fino a 180 caratteri.':'Enter a video name, up to 180 characters.',
 'Spazio esaurito. Libera spazio e riprova.':'Storage is full. Free up space and try again.',
 'Riempi schermo':'Fill screen','Proporzioni originali':'Original aspect ratio','Torna alla libreria':'Back to library','Creazione MP4':'Creating MP4',
 'Seleziona un video o un collegamento HTTPS valido.':'Select a video or a valid HTTPS link.',
 'Il salvataggio non è riuscito. Controlla spazio, permessi e integrità del video.':'Saving failed. Check free space, permissions and the video file.',
 'Nessuna fonte disponibile per questo titolo.':'No source is available for this title.',
 'Si applica anche ai download in coda.':'Also applies to queued downloads.',
 'A modo tuo.':'Your app, your way.','Le storie, con te.':'Your videos, with you.','Le tue fonti viaggiano.':'Manage your sources.','Le tue storie viaggiano.':'Take your library with you.'}
def build_language(code,keys,english):
 file=OUT/(code+'.json');existing=json.loads(file.read_text(encoding='utf-8')) if file.exists() else {}
 if code=='it':existing={key:key for key in keys}
 else:
  missing=[(key,english.get(key,key)) for key in keys if key not in existing]
  for batch in batches(missing):
   lines=translate('\n'.join(value for _,value in batch),code).splitlines()
   if len(lines)==len(batch):existing.update({key:value.strip() for (key,_),value in zip(batch,lines)})
   else:
    for key,value in batch:existing[key]=translate(value,code).strip()
   file.write_text(json.dumps(existing,ensure_ascii=False,indent=2),encoding='utf-8')
 if any(not existing.get(key) for key in keys):raise ValueError('Missing translations: '+code)
 file.write_text(json.dumps({key:existing[key] for key in keys},ensure_ascii=False,indent=2),encoding='utf-8')
 print(code,len(keys),'messages',flush=True)
 return code,existing
def main():
 keyfile=ROOT/'build/i18n/keys.json'
 keys=json.loads(keyfile.read_text(encoding='utf-8')) if keyfile.exists() else sorted(json.loads((OUT/'it.json').read_text(encoding='utf-8')))
 if '--offline' not in sys.argv:
  file=OUT/'en.json';english=json.loads(file.read_text(encoding='utf-8')) if file.exists() else {}
  for batch in batches([(key,key) for key in keys if key not in english and key not in OVERRIDES]):
   lines=translate('\n'.join(value for _,value in batch),'en','it').splitlines()
   if len(lines)==len(batch):english.update({key:value.strip() for (key,_),value in zip(batch,lines)})
   else:
    for key,value in batch:english[key]=translate(value,'en','it').strip()
   file.write_text(json.dumps(english,ensure_ascii=False,indent=2),encoding='utf-8')
  english.update(OVERRIDES)
  for key,value in english.items():
   if 'galleria' in key.lower():english[key]=re.sub(r'\btunnel\b','gallery',value,flags=re.I)
  file.write_text(json.dumps(english,ensure_ascii=False,indent=2),encoding='utf-8')
  with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
   futures=[pool.submit(build_language,code,keys,english) for code,_ in LANGUAGES]
   for future in concurrent.futures.as_completed(futures):future.result()
 messages={code:json.loads((OUT/(code+'.json')).read_text(encoding='utf-8')) for code,_ in LANGUAGES}
 assert all(set(value)==set(keys) for value in messages.values())
 catalog={'languages':[{'code':code,'name':name} for code,name in LANGUAGES],'messages':messages}
 (ASSETS/'locales.js').write_text("'use strict';window.VibraLocales="+json.dumps(catalog,ensure_ascii=False,separators=(',',':'))+';\n',encoding='utf-8')
 (OUT/'catalog.json').write_text(json.dumps(catalog['languages'],ensure_ascii=False,indent=2),encoding='utf-8')
 print('Local bundle ready:',len(LANGUAGES),'languages',flush=True)
if __name__=='__main__':main()
