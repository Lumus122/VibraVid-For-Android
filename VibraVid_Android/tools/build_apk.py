"""Build with the official Android SDK, JDK and no globally installed build tools."""
from pathlib import Path
import hashlib
import json
import os
import secrets
import shutil
import subprocess
import sys
import zipfile
from release_config import VERSION, VERSION_CODE

ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / 'toolchain/sdk'
JAVA = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'))
TOOLS = SDK / 'build-tools/35.0.0'
PLATFORM = SDK / 'platforms/android-35/android.jar'
SOURCE = ROOT / 'app/src/main'
BUILD = ROOT / 'build'
DIST = ROOT / 'release'
SIGN = ROOT / '.signing'


def run(arguments, env=None):
    process = subprocess.run([str(a) for a in arguments], cwd=ROOT, env=env, text=True, capture_output=True)
    if process.returncode:
        print(process.stdout)
        print(process.stderr)
        raise SystemExit(process.returncode)
    if process.stderr.strip():
        print(process.stderr.strip())
    return process.stdout


def build():
    for folder in (BUILD / 'generated', BUILD / 'classes', BUILD / 'dex', DIST, SIGN):
        folder.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env['JAVA_HOME'] = str(JAVA)
    env['PATH'] = str(JAVA / 'bin') + os.pathsep + env.get('PATH', '')
    gradle = ROOT / 'toolchain/gradle-8.13/bin/gradle.bat'
    if not gradle.exists():
        raise SystemExit('Install Gradle 8.13 in toolchain/gradle-8.13 before building.')
    (ROOT / 'local.properties').write_text('sdk.dir=' + SDK.as_posix().replace(':', '\\:') + '\n', encoding='utf-8')
    output = run([gradle, '--no-daemon', 'assembleRelease'], env)
    (BUILD / 'gradle-build.log').write_text(output, encoding='utf-8')
    unsigned = ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk'
    aligned = BUILD / 'aligned.apk'
    run([TOOLS / 'zipalign.exe', '-f', '4', unsigned, aligned])
    password = SIGN / 'password.txt'
    key = SIGN / 'vibravid-release.p12'
    if not key.exists():
        password.write_text(secrets.token_urlsafe(32), encoding='ascii')
        key_env = env.copy()
        key_env['VIBRAVID_SIGN_PASSWORD'] = password.read_text(encoding='ascii')
        run([JAVA / 'bin/keytool.exe', '-genkeypair', '-keystore', key, '-storetype', 'PKCS12',
             '-alias', 'vibravid', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
             '-dname', 'CN=VibraVid Studio,OU=Android,O=VibraVid',
             '-storepass:env', 'VIBRAVID_SIGN_PASSWORD', '-keypass:env', 'VIBRAVID_SIGN_PASSWORD'], key_env)
    elif not password.exists():
        raise SystemExit('Existing signing key has no password file. Restore it before building updates.')
    apk = DIST / 'VibraVid_Studio_Android.apk'
    if apk.exists():
        previous = DIST / 'precedente' / apk.name
        previous.parent.mkdir(parents=True, exist_ok=True)
        if not previous.exists(): shutil.copy2(apk, previous)
    run([TOOLS / 'apksigner.bat', 'sign', '--ks', key, '--ks-key-alias', 'vibravid',
         '--ks-pass', 'file:' + str(password),
         '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
         '--out', apk, aligned], env)
    verification = run([TOOLS / 'apksigner.bat', 'verify', '--verbose', '--print-certs', apk], env)
    (DIST / 'signature.txt').write_text(verification, encoding='utf-8')
    badging = run([TOOLS / 'aapt.exe', 'dump', 'badging', apk])
    assert "versionName='" + VERSION + "'" in badging
    assert "versionCode='" + str(VERSION_CODE) + "'" in badging
    (DIST / 'manifest.txt').write_text(badging, encoding='utf-8')
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    (DIST / (apk.name + '.sha256')).write_text(digest + '  ' + apk.name + '\n', encoding='ascii')
    manifest = {'file': apk.name, 'bytes': apk.stat().st_size, 'sha256': digest,
                'package': 'it.vibravid.studio', 'version': VERSION, 'version_code': VERSION_CODE, 'min_android': 8,
                'min_sdk': 26, 'target_sdk': 35, 'architecture': 'all (no native ABI dependency)',
                'permissions': [node.attrib['{http://schemas.android.com/apk/res/android}name'] for node in __import__('xml.etree.ElementTree', fromlist=['']).parse(SOURCE / 'AndroidManifest.xml').getroot().findall('uses-permission')], 'signature_verified': True,
                'sources': {p.relative_to(SOURCE).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                            for p in sorted(SOURCE.rglob('*')) if p.is_file()}}
    (DIST / 'BUILD_MANIFEST.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding='utf-8')
    print(json.dumps({k: manifest[k] for k in ('file', 'bytes', 'sha256', 'signature_verified')}, indent=2))


if __name__ == '__main__':
    build()
