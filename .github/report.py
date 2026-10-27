import re
log = open('build.log', errors='replace').read()
lines = log.splitlines()
hits = []
for i, l in enumerate(lines):
    if re.search(r'error|exception|what went wrong|failed', l, re.I):
        hits.extend(lines[i:i+7])
text = '\n'.join(hits)[:3000] + '\n---tail---\n' + log[-1500:]
print('::error::' + text.replace('%', '%25').replace('\r', '').replace('\n', '%0A'))
