"""Read-only source, color, asset and signing consistency checks for the final build."""
from pathlib import Path
import hashlib
import json
import re
import zipfile
from build_apk import ROOT, JAVA, TOOLS, run
import os
import shutil
from release_config import VERSION

build=json.loads((ROOT/'release/BUILD_MANIFEST.json').read_text(encoding='utf-8'))
assert build['version']==VERSION
apk=ROOT/'release/VibraVid_Studio_Android.apk'
assert hashlib.sha256(apk.read_bytes()).hexdigest()==build['sha256']
assert all(hashlib.sha256((ROOT/'app/src/main'/name).read_bytes()).hexdigest()==digest for name,digest in build['sources'].items())
colors=[]
for path in [ROOT/'app/src/main/assets/mobile.css',ROOT/'app/src/main/assets/cinema.css',ROOT/'app/src/main/assets/stream.css',ROOT/'app/src/main/assets/vibravid.svg',*list((ROOT/'app/src/main/res').rglob('*.xml')),*list((ROOT/'app/src/main/java').rglob('*.java'))]:
    colors += re.findall(r'#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?\b',path.read_text(encoding='utf-8'))
assert len(colors)>20
appearance_checks=__import__('subprocess').check_output([shutil.which('node') or r'C:\Program Files\nodejs\node.exe',str(ROOT/'tools/verify_appearance.cjs')],text=True)
appearance_report=json.loads(appearance_checks.strip().splitlines()[-1])
css=''.join((ROOT/'app/src/main/assets'/name).read_text(encoding='utf-8') for name in ('mobile.css','layouts.css','cinema.css','stream.css','experience.css'))
assert 'grayscale(' not in css and 'filter:none' in css
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None
    for name,digest in build['sources'].items():
        if name.startswith('assets/'):assert hashlib.sha256(archive.read(name)).hexdigest()==digest
    dex=b''.join(archive.read(name) for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex',name))
    assert all(name.encode('ascii') not in dex for name in ('AuditSmoke','ProviderSmoke','PickerSmoke','StreamSmoke','AndroidSmoke','TransportSmoke','FileFlowSmoke','AdBlockSmoke','QueueSmoke','ReliabilitySmoke','AdapterSmoke','GallerySmoke','GallerySaveTest','GalleryMuxerTest','LanguageTest','UpdateTest','CatalogPresentationTest','DiscoveryCatalogTest','ExperienceTest','ui_fixture','qa-fixture'))
env=os.environ.copy();env['JAVA_HOME']=str(JAVA);env['PATH']=str(JAVA/'bin')+os.pathsep+env.get('PATH','')
current=run([TOOLS/'apksigner.bat','verify','--verbose','--print-certs',apk],env)
certificate=lambda text:re.search(r'certificate SHA-256 digest: ([a-f0-9]+)',text).group(1)
baseline=ROOT/'release/precedente/VibraVid_Studio_Android_2.3.apk'
same_certificate=None
if baseline.exists():
    previous=run([TOOLS/'apksigner.bat','verify','--print-certs',baseline],env)
    same_certificate=certificate(current)==certificate(previous)
    assert same_certificate
def luminance(hex_color):
    channels=[int(hex_color[i:i+2],16)/255 for i in (1,3,5)]
    return sum(weight*(value/12.92 if value<=.04045 else ((value+.055)/1.055)**2.4) for weight,value in zip((.2126,.7152,.0722),channels))
ratios={}
for foreground,background in [('#ffffff','#232323'),('#b3b3b3','#333333'),('#ffffff','#e50914'),('#b3b3b3','#2b2b2b')]:
    values=sorted([luminance(foreground),luminance(background)])
    ratio=(values[1]+.05)/(values[0]+.05);assert ratio>=4.5;ratios[foreground+' on '+background]=round(ratio,2)
report={'sha256':build['sha256'],'sources_match':True,'embedded_assets_match':True,'test_runners_in_apk':False,'colors_checked':len(colors),'appearance':appearance_report,'posters_original_color':True,'contrast':ratios,'same_certificate_as_2_3':same_certificate,'certificate_sha256':certificate(current)}
(ROOT/'release/RELEASE_CHECKS.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
