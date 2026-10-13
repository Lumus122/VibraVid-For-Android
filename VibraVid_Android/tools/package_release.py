"""Verify the final APK's local assets and package the complete corresponding sources."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT=Path(__file__).resolve().parents[1]
DIST=ROOT/'release'
APK=DIST/'VibraVid_Studio_Android.apk'
with zipfile.ZipFile(APK) as archive:
    assert archive.testzip() is None
    for path in (ROOT/'app/src/main/assets').rglob('*'):
        if path.is_file():
            name='assets/'+path.relative_to(ROOT/'app/src/main/assets').as_posix()
            assert archive.read(name)==path.read_bytes(),name
    assert 'classes.dex' in archive.namelist()
    assert not any('AndroidSmoke' in name or 'StreamSmoke' in name or '.signing' in name for name in archive.namelist())
sources=DIST/'VibraVid_Android_sorgenti.zip'
with zipfile.ZipFile(sources,'w',zipfile.ZIP_DEFLATED) as archive:
    for name in ('README.md','DESIGN.md','QA.md','AUDIT.md','LICENSE','.gitignore','settings.gradle','build.gradle','gradle.properties'):
        if (ROOT/name).exists():archive.write(ROOT/name,'VibraVid_Android/'+name)
    for directory in ('app','tools'):
        for path in sorted((ROOT/directory).rglob('*')):
            if path.is_file() and not any(part in ('__pycache__','build','.gradle','.signing') for part in path.relative_to(ROOT).parts):
                archive.write(path,'VibraVid_Android/'+path.relative_to(ROOT).as_posix())
digest=hashlib.sha256(sources.read_bytes()).hexdigest()
(DIST/(sources.name+'.sha256')).write_text(digest+'  '+sources.name+'\n',encoding='ascii')
desktop=Path.home()/'Desktop'/APK.name
if desktop.exists() and desktop.read_bytes()!=APK.read_bytes():
    backup=DIST/'precedente'/desktop.name
    backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():shutil.copy2(desktop,backup)
shutil.copy2(APK,desktop)
assert hashlib.sha256(desktop.read_bytes()).hexdigest()==hashlib.sha256(APK.read_bytes()).hexdigest()
print(json.dumps({'apk':str(desktop),'bytes':desktop.stat().st_size,'sources':str(sources),'embedded_assets':'verified'},indent=2))
