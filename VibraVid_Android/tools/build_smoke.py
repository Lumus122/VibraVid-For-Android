"""Build the optional instrumentation APK separately; never ships its runner in the app."""
from pathlib import Path
import shutil
import zipfile
from build_apk import ROOT, JAVA, PLATFORM, TOOLS, SIGN, run
import os
import sys
runner = sys.argv[1] if len(sys.argv)>1 else "AndroidSmoke"
assert runner in ("AndroidSmoke", "StreamSmoke", "AdBlockSmoke", "ProviderSmoke", "AuditSmoke", "TransportSmoke", "FileFlowSmoke", "PickerSmoke", "QueueSmoke", "ReliabilitySmoke", "AdapterSmoke")

build=ROOT/'build/smoke'
for name in ('generated','classes','dex'): (build/name).mkdir(parents=True,exist_ok=True)
manifest=build/'AndroidManifest.xml'
manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="it.vibravid.studio.tests"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:label="VibraVid test"/><instrumentation android:name=".{runner}" android:targetPackage="it.vibravid.studio"/></manifest>''',encoding='utf-8')
env=os.environ.copy();env['JAVA_HOME']=str(JAVA);env['PATH']=str(JAVA/'bin')+os.pathsep+env.get('PATH','')
apk=build/'unsigned.apk'
run([TOOLS/'aapt.exe','package','-f','-M',manifest,'-I',PLATFORM,'-F',apk])
run([JAVA/'bin/javac.exe','--release','8','-encoding','UTF-8','-classpath',PLATFORM,'-d',build/'classes',ROOT/('tools/'+runner+'.java')])
run([JAVA/'bin/jar.exe','cf',build/'classes.jar','-C',build/'classes','.'])
run([TOOLS/'d8.bat','--release','--min-api','26','--lib',PLATFORM,'--output',build/'dex',build/'classes.jar'],env)
with zipfile.ZipFile(apk,'a',zipfile.ZIP_DEFLATED) as archive:
    for file in (build/'dex').glob('*.dex'):archive.write(file,file.name)
run([TOOLS/'zipalign.exe','-f','4',apk,build/'aligned.apk'])
run([TOOLS/'apksigner.bat','sign','--ks',SIGN/'vibravid-release.p12','--ks-key-alias','vibravid','--ks-pass','file:'+str(SIGN/'password.txt'),'--out',build/'smoke.apk',build/'aligned.apk'],env)
print(build/'smoke.apk')
