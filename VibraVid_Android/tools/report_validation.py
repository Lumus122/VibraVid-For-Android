"""Record measured results for the corresponding signed APK, without treating host tests as phone tests."""
from pathlib import Path
from datetime import datetime,timezone
import json,hashlib,xml.etree.ElementTree as ET,collections
ROOT=Path(__file__).resolve().parents[1]
manifest=json.loads((ROOT/'release/BUILD_MANIFEST.json').read_text(encoding='utf-8'))
apk=ROOT/'release'/manifest['file'];assert hashlib.sha256(apk.read_bytes()).hexdigest()==manifest['sha256']
cases=[]
for file in sorted((ROOT/'app/build/test-results/testReleaseUnitTest').glob('TEST-*.xml')):
 suite=ET.parse(file).getroot()
 assert int(suite.get('failures',0))==int(suite.get('errors',0))==int(suite.get('skipped',0))==0
 for case in suite.findall('testcase'):cases.append({'suite':suite.get('name'),'case':case.get('name'),'passed':True})
assert len(cases)>=20
ui=json.loads((ROOT/'build/i18n/ui-results.json').read_text(encoding='utf-8'));assert ui['languages']==21 and not ui['pageErrors']
languages=json.loads((ROOT/'build/i18n/unit-results.json').read_text(encoding='utf-8'));assert languages['languages']==21
lint=ET.parse(ROOT/'app/build/reports/lint-results-release.xml').getroot();issues=collections.Counter(issue.get('severity') for issue in lint.findall('issue'))
assert not issues['Error'] and not issues['Fatal']
report={'version':manifest['version'],'apk_sha256':manifest['sha256'],'recorded_at':datetime.now(timezone.utc).isoformat(),
 'host_android_tests':{'framework':'Robolectric 4.16.1','sdk':35,'passed':len(cases),'cases':cases},
 'gallery_scope':'Strict modern MediaStore provider contract, rollback/recovery, startup failure and busy cancellation; real Java InAppMuxer round trip of H264/AAC encoded samples. No physical phone codec or MediaStore verification in this release.',
 'language_catalogs':languages,'browser_ui':ui,'model_checks':22,'appearance_checks':123,'lint':dict(issues),
 'limits':['Physical phone unavailable. Landscape, cutouts and system bars still require validation on the user device; host tests cover window configuration and control visibility.',
           'No complete HLS/DASH conversion or provider-download end-to-end run on a physical device for this build.',
           'Long translations are machine-drafted and not all reviewed by native speakers.'],
 'domain_and_update_scope':'Known-source feed changes, malformed entries, manual override preservation, numeric versions and stable owned APK selection tested on host. No new live provider, central-feed or GitHub-release check was performed for this UI build. Future APK installation not performed on a phone.',
 'experience_ui':json.loads((ROOT/'build/experience/ui-results.json').read_text(encoding='utf-8')),
 'experience_model':json.loads((ROOT/'build/experience/model-results.json').read_text(encoding='utf-8')),
 'source_audit':json.loads((ROOT/'build/experience/source-audit.json').read_text(encoding='utf-8')),
 'redesign_ui':json.loads((ROOT/'build/streaming/VERIFICA-UI.json').read_text(encoding='utf-8')),
 'home_catalog_scope':'Apple Movies and TVmaze metadata, bundled snapshot and separate private cache. Live feeds read during preparation; parser, cache, timeout retention and retry spacing tested on host. Source availability and streaming are resolved separately.',
 'typefaces':'Inter for interface and native chrome; Bebas Neue for VibraVid wordmark. Both bundled under SIL OFL. Netflix Sans is not bundled.',
 'motion_scope':'Shared artwork, modal cleanup, rapid taps, page/shelf position restoration and reduced-motion setting tested in isolated Chromium. No measured physical-phone FPS or comparison with Netflix.',
 'publication':'Local build only; not uploaded to GitHub.',
 'historical_results':'Native device results for 2.6.0 are preserved separately and are not claimed for this APK.'}
(ROOT/'release/VALIDATION.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'version':report['version'],'android_tests':len(cases),'ui_checks':ui['passed'],'language_checks':languages['passed'],'lint':dict(issues)},indent=2))
