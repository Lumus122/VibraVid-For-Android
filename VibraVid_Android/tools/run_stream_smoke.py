"""Run only against the task's software QA emulator, never other attached devices."""
from pathlib import Path
import subprocess
import time
import sys

root = Path(__file__).resolve().parents[1]
adb = root / 'toolchain/sdk/platform-tools/adb.exe'
prefix = [str(adb), '-s', 'emulator-5582']

def run(*args, timeout=60):
    return subprocess.run(prefix + list(args), capture_output=True, text=True, timeout=timeout)

until = time.monotonic() + 1200
while time.monotonic() < until:
    try:
        probe = run('shell', 'getprop', 'sys.boot_completed', timeout=15)
        package = run('shell', 'pm', 'path', 'it.vibravid.studio', timeout=20) if probe.stdout.strip() == '1' else None
        if package is not None and package.returncode == 0 and package.stdout.startswith('package:'): break
    except subprocess.TimeoutExpired: pass
    time.sleep(3)
else: raise SystemExit('QA emulator boot timed out')
print('QA emulator and package service ready', flush=True)
packages = ('release/VibraVid_Studio_Android.apk', 'build/smoke/smoke.apk') if len(sys.argv) == 1 else ('build/smoke/smoke.apk',)
for apk in packages:
        result = run('install', '--no-streaming', '-r', str(root/apk), timeout=300)
        print(result.stdout, result.stderr, flush=True)
        if result.returncode: raise SystemExit(result.returncode)
if len(sys.argv) == 1:
    result = run('shell', 'pm', 'clear', 'it.vibravid.studio', timeout=180)
    if result.returncode or 'Success' not in result.stdout: raise SystemExit('Cannot clear the dedicated QA fixture before the download test.')

args = ['shell', 'am', 'instrument', '-w']
if len(sys.argv)>1: args += ['-e', 'mode', sys.argv[1]]
args += ['it.vibravid.studio.tests/.StreamSmoke']
name = 'stream-smoke-' + (sys.argv[1] if len(sys.argv)>1 else 'download') + '.txt'
with (root/'release'/name).open('w', encoding='utf-8') as log:
    process = subprocess.Popen(prefix+args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    passed = False
    for line in process.stdout:
        log.write(line); log.flush(); print(line, end='', flush=True)
        if 'INSTRUMENTATION_RESULT: status=passed' in line: passed = True
    code = process.wait()
if code: raise SystemExit(code)
if not passed: raise SystemExit('Native media checks failed; inspect the saved instrumentation log.')
