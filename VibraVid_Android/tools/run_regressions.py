"""Run a bounded native regression against only the dedicated QA emulator."""
from pathlib import Path
import json
import subprocess
import sys
import hashlib
import os
import re
from types import SimpleNamespace
from datetime import datetime, timezone
from release_config import VERSION, LOG_VERSION

ROOT=Path(__file__).resolve().parents[1]
ADB=ROOT/'toolchain/sdk/platform-tools/adb.exe'
if (ROOT/'build/pause-regressions').exists():raise SystemExit('QA batch paused for rebuilding; the current regression has completed.')
runner=sys.argv[1]
assert runner in ('AuditSmoke','PickerSmoke','TransportSmoke','ProviderSmoke','AdBlockSmoke','AndroidSmoke','StreamSmoke','QueueSmoke','ReliabilitySmoke','AdapterSmoke','GallerySmoke')
mode=sys.argv[2] if len(sys.argv)>2 else ''
serial=sys.argv[3] if len(sys.argv)>3 else 'emulator-5582'
assert serial in ('emulator-5582','emulator-5584'),'Only isolated local QA emulator ports are permitted'
name=runner.replace('Smoke','').lower()+'-smoke-'+LOG_VERSION+('-'+mode if mode else '')
apk=ROOT/'release/VibraVid_Studio_Android.apk'
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
manifest=json.loads((ROOT/'release/BUILD_MANIFEST.json').read_text(encoding='utf-8'))
assert manifest['version']==VERSION and manifest['sha256']==digest

# Instrumentation and APK installation share one emulator: concurrent runs invalidate results.
lock=(ROOT/'build/native-regression.lock').open('a+b');lock.write(b'0');lock.flush();lock.seek(0)
try:
    if os.name=='nt':
        import msvcrt
        msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
    else:
        import fcntl
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
except OSError:
    raise SystemExit('Another QA emulator regression is running. Wait for it to finish.')

def run(args,timeout=120):
    return subprocess.run([str(value) for value in args],cwd=ROOT,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)

build=run([sys.executable,ROOT/'tools/build_smoke.py',runner])
(ROOT/'build'/('build-'+runner+'.log')).write_text(build.stdout+build.stderr,encoding='utf-8')
if build.returncode:raise SystemExit('Instrumentation build failed: '+runner)
package=run([ADB,'-s',serial,'shell','pm','path','it.vibravid.studio'])
paths=[line.split(':',1)[1].strip() for line in package.stdout.splitlines() if line.startswith('package:')]
installed=run([ADB,'-s',serial,'shell','sha256sum',paths[0]]) if len(paths)==1 and re.fullmatch(r'/data/app/[A-Za-z0-9_./+=-]+\.apk',paths[0]) else None
if installed is None or installed.returncode or not installed.stdout.startswith(digest):
    updated=run([ADB,'-s',serial,'install','--no-streaming','-r',apk],300)
    if updated.returncode or 'Success' not in updated.stdout:raise SystemExit(updated.stdout+updated.stderr)
    package=run([ADB,'-s',serial,'shell','pm','path','it.vibravid.studio'])
    paths=[line.split(':',1)[1].strip() for line in package.stdout.splitlines() if line.startswith('package:')]
    assert len(paths)==1 and re.fullmatch(r'/data/app/[A-Za-z0-9_./+=-]+\.apk',paths[0]),'Unexpected QA APK path'
    installed=run([ADB,'-s',serial,'shell','sha256sum',paths[0]])
    assert installed.returncode==0 and installed.stdout.startswith(digest),'QA device does not contain the reported APK'
install=run([ADB,'-s',serial,'install','--no-streaming','-r',ROOT/'build/smoke/smoke.apk'],300)
if install.returncode or 'Success' not in install.stdout:raise SystemExit(install.stdout+install.stderr)
args=[ADB,'-s',serial,'shell','am','instrument','-w']
if runner=='AndroidSmoke':args+=['-e','noCapture','true']
if mode:args+=['-e','mode',mode]
args+=['it.vibravid.studio.tests/.'+runner]
live=ROOT/'build'/(name+'-live.txt')
try:
    with live.open('w',encoding='utf-8') as output:
        process=subprocess.Popen([str(value) for value in args],cwd=ROOT,stdout=output,stderr=subprocess.STDOUT)
        try:code=process.wait(timeout=600 if runner=='GallerySmoke' else 420)
        except subprocess.TimeoutExpired:
            process.kill();process.wait(timeout=15);raise
    result=SimpleNamespace(returncode=code,stdout=live.read_text(encoding='utf-8',errors='replace'),stderr='')
except subprocess.TimeoutExpired as error:
    text=live.read_text(encoding='utf-8',errors='replace')
    (ROOT/'release'/(name+'.txt')).write_text(text+'\nHOST TIMEOUT: not a passed check\n',encoding='utf-8');raise SystemExit('Native regression timed out: '+runner)
log=result.stdout+result.stderr
(ROOT/'release'/(name+'.txt')).write_text(log,encoding='utf-8')
history=ROOT/'release'/('logs-'+LOG_VERSION);history.mkdir(exist_ok=True)
(history/(name+'-'+datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')+'.txt')).write_text(log,encoding='utf-8')
checks=next((line.split('checks=',1)[1] for line in log.splitlines() if line.startswith('INSTRUMENTATION_RESULT: checks=')), '[]')
passed=result.returncode==0 and 'INSTRUMENTATION_RESULT: status=passed' in log
report={'version':VERSION,'sha256':digest,'utc':datetime.now(timezone.utc).isoformat(),'runner':runner,'mode':mode,'passed':passed,'checks':len(json.loads(checks)),'log':str(ROOT/'release'/(name+'.txt'))}
sdk=run([ADB,'-s',serial,'shell','getprop','ro.build.version.sdk'],30)
report['android_sdk']=sdk.stdout.strip() if sdk.returncode==0 else 'unavailable'
report['serial']=serial
(ROOT/'release'/(name+'.json')).write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report),flush=True)
if not passed:print(log[-6000:]);raise SystemExit(1)
