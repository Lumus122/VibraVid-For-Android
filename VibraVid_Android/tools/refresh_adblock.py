"""Bundle a reproducible subset of AdGuard DNS rules; preserve domain exceptions.

Runs at development time only. No filter downloads or DNS changes on the phone.
Use --snapshot to regenerate from the supplied, unmodified upstream snapshot.
Unsupported wildcard/regex rules are omitted rather than broadened into blocks.
"""
from pathlib import Path
from urllib.request import urlopen
import hashlib
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/adblock'
SNAPSHOT = ROOT / 'tools/filter_snapshot'
URL = 'https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt'
SNAPSHOT.mkdir(parents=True, exist_ok=True)
ASSETS.mkdir(parents=True, exist_ok=True)
source = SNAPSHOT / 'adguard-dns.txt'
if '--snapshot' not in sys.argv:
    source.write_bytes(urlopen(URL, timeout=60).read())
raw = source.read_bytes()
rules = {key: set() for key in ('B', 'A', 'E', 'I')}
disabled = set()
skipped = 0
pattern = re.compile(r'(@@)?(\|\||\|)([a-z0-9_-]+(?:\.[a-z0-9_-]+)+)\^(\|)?(?:\$(important|badfilter))?')
for line in raw.decode('utf-8-sig').splitlines():
    if not line or line.startswith(('!', '[', '#')):
        continue
    match = pattern.fullmatch(line)
    if not match:
        skipped += 1
        continue
    exception, anchor, host, end, option = match.groups()
    if option == 'badfilter':
        disabled.add(host)
    elif exception:
        rules['E' if anchor == '|' else 'A'].add(host)
    elif anchor == '||' and not end:
        rules['I' if option == 'important' else 'B'].add(host)
    else:
        skipped += 1
rules['B'] -= disabled
rules['I'] -= disabled
assert len(rules['B']) > 10000
digest = hashlib.sha256(raw).hexdigest()
header = ('# AdGuard DNS filter, domain subset compiled for VibraVid. GPL-3.0.\n'
          '# Source: ' + URL + '\n# Upstream SHA256: ' + digest + '\n'
          '# B=block domain/subdomains; A=allow domain/subdomains; E=allow exact host; I=important block.\n')
(ASSETS / 'domains.txt').write_text(header + ''.join(
    key + ' ' + host + '\n' for key in rules for host in sorted(rules[key])), encoding='utf-8')
modified = next((line.split(': ', 1)[1] for line in raw.decode('utf-8-sig').splitlines()
                 if line.startswith('! Last modified: ')), 'unknown')
metadata = {'source': URL, 'homepage': 'https://github.com/AdguardTeam/AdGuardSDNSFilter',
            'license': 'GPL-3.0', 'upstream_last_modified': modified, 'upstream_sha256': digest,
            'rules': {key: len(value) for key, value in rules.items()},
            'unsupported_rules_omitted': skipped,
            'scope': 'Literal domain rules and exceptions; not a full ABP rule interpreter.'}
(ASSETS / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
print(json.dumps(metadata, indent=2))
