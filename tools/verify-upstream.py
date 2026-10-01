#!/usr/bin/env python3
"""Verify the complete vendored ZXing source tree against the immutable source ledger."""
import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'vendor/zxing'
ledger=json.loads((root/'UPSTREAM.json').read_text())
expected=set(ledger['files'])
actual={str(p.relative_to(root)) for p in (root/'src/main').rglob('*') if p.is_file()}
assert actual=={p for p in expected if p.startswith('src/main/')},'Upstream source tree members changed'
for name,digest in ledger['files'].items():
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest,f'Upstream bytes changed: {name}'
print(f"Verified {len(actual)} unchanged upstream source files at {ledger['commit']}")
