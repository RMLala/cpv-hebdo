#!/usr/bin/env python3
"""Reproducible single-file build; never pulls user data or credentials."""
from pathlib import Path
import base64,hashlib,json,urllib.request
ROOT=Path(__file__).resolve().parents[1]
manifest=json.loads((ROOT/'tools/ocr-manifest.json').read_text())
bundle={}
for key,item in manifest.items():
    p=ROOT/'vendor'/item['name'];p.parent.mkdir(exist_ok=True)
    if not p.exists():
        with urllib.request.urlopen(item['url'],timeout=90) as response:p.write_bytes(response.read())
    raw=p.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=item['sha256']:raise SystemExit('OCR checksum mismatch: '+item['name'])
    bundle[key]=base64.b64encode(raw).decode('ascii')
source=(ROOT/'src/template.html').read_text()
logo='data:image/png;base64,'+base64.b64encode((ROOT/'src/logo.png').read_bytes()).decode('ascii')
source=source.replace('__CPV_V3_CODE__',(ROOT/'src/v3.js').read_text()+'\n'+(ROOT/'src/license.js').read_text()+'\n'+(ROOT/'src/cloud.js').read_text())
source=source.replace('__CPV_LOGO_DATA_URI__',logo).replace('__CPV_OCR_BUNDLE__',json.dumps(bundle,separators=(',',':')))
assert '__CPV_' not in source
for p in [ROOT/'Standalone.html',ROOT/'app/src/main/assets/index.html']:
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source)
print('Built HTML + Android assets, no personal screenshots:',len(source.encode('utf-8')),'bytes')
