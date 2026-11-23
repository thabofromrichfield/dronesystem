#!/usr/bin/env python3
"""
Static checks for the GHOSTGRID source tree.

This is NOT a substitute for `./gradlew assembleFullDebug` / `installSimDebug`.
It catches the class of mistakes that a Kotlin compiler would catch but that are
expensive to discover on a 15-minute cold sync: a broken flavour boundary, a
backend missing an override, an R.id that does not exist, a layout naming a
class that is not in the source set being compiled.

Run it from the repo root:  python3 tools/verify_structure.py
Exit code 0 = all checks passed.
"""
import re, os, glob, sys, collections
from xml.dom import minidom

fail = 0
def bad(*a):
    global fail
    fail += 1
    print("FAIL:", *a)

# ---- 1. flavour boundary: only src/full may import dji.* ----
for srcset in ['app/src/main/java', 'app/src/sim/java']:
    for f in glob.glob(srcset + '/**/*.kt', recursive=True):
        s = open(f).read()
        for m in re.findall(r'^import (dji\.[\w.]+|com\.cySdkyc[\w.]+)', s, re.M):
            bad("DJI import in non-full source set:", f, m)
        if 'com.cySdkyc' in s:
            bad("cySdkyc reference in non-full source set:", f)
print("flavour boundary: main + sim are DJI-free")

full_only = ['ProductBus', 'TelemetrySource', 'FlightOps', 'VirtualStickController']
for srcset in ['app/src/main/java', 'app/src/sim/java']:
    for f in glob.glob(srcset + '/**/*.kt', recursive=True):
        s = open(f).read()
        for t in full_only:
            if re.search(r'\b' + t + r'\b', s):
                bad("full-only type referenced from", srcset, "->", f, t)
print("no full-only types leak into main/sim")

# ---- 2. each flavour supplies exactly one backend factory and one FpvSurface ----
for flav in ['full', 'sim']:
    files = glob.glob(f'app/src/{flav}/java/**/*.kt', recursive=True)
    n = sum(1 for f in files if 'object UavBackendFactory' in open(f).read())
    if n != 1:
        bad(f"{flav}: UavBackendFactory declared {n} times, expected 1")
    fpv = [f for f in files if os.path.basename(f) == 'FpvSurface.kt']
    if len(fpv) != 1:
        bad(f"{flav}: FpvSurface declared {len(fpv)} times, expected 1")
    else:
        body = open(fpv[0]).read()
        for m in ['fun start()', 'fun stop()', 'fun release()']:
            if m not in body:
                bad(f"{flav} FpvSurface missing {m}")
    print(f"{flav}: 1 UavBackendFactory, 1 FpvSurface with start/stop/release")

# ---- 3. both backends implement the whole interface ----
iface = open('app/src/main/java/drone/test/core/UavBackend.kt').read()
methods = set(re.findall(r'^\s{4}fun (\w+)\s*\(', iface, re.M))
props = set(re.findall(r'^\s{4}val (\w+)', iface, re.M))
for flav in ['full', 'sim']:
    for f in glob.glob(f'app/src/{flav}/java/**/*Backend.kt', recursive=True):
        s = open(f).read()
        if 'UavBackend' not in s or 'class ' not in s:
            continue
        for m in methods:
            if not re.search(r'override fun ' + m + r'\s*\(', s):
                bad(f"{flav} backend missing override fun {m}")
        for p in props:
            if not re.search(r'override val ' + p, s):
                bad(f"{flav} backend missing override val {p}")
        print(f"{flav}: {os.path.basename(f)} implements all {len(methods)} methods")

# ---- 4. package declarations match directory layout ----
for srcset in ['app/src/main/java', 'app/src/full/java', 'app/src/sim/java']:
    for f in glob.glob(srcset + '/**/*.kt', recursive=True):
        m = re.search(r'^package\s+([\w.]+)', open(f).read(), re.M)
        if not m:
            bad("no package declaration:", f)
            continue
        if os.path.dirname(f) != srcset + '/' + m.group(1).replace('.', '/'):
            bad("package/path mismatch:", f, m.group(1))

# ---- 5. XML well-formed ----
xmls = glob.glob('app/src/main/res/**/*.xml', recursive=True) + ['app/src/main/AndroidManifest.xml']
for f in xmls:
    try:
        minidom.parse(f)
    except Exception as e:
        bad("malformed XML", f, e)
print(f"{len(xmls)} XML files parse")

# ---- 6. resource references resolve ----
ids = set()
for f in glob.glob('app/src/main/res/layout/*.xml'):
    ids |= set(re.findall(r'android:id="@\+id/(\w+)"', open(f).read()))
table = {
    'id': ids,
    'string': set(re.findall(r'<string name="(\w+)"', open('app/src/main/res/values/strings.xml').read())),
    'layout': {os.path.basename(f)[:-4] for f in glob.glob('app/src/main/res/layout/*.xml')},
    'drawable': {os.path.basename(f)[:-4] for f in glob.glob('app/src/main/res/drawable/*.xml')},
    'style': set(re.findall(r'<style name="([^"]+)"', open('app/src/main/res/values/themes.xml').read())),
    'color': set(re.findall(r'<color name="(\w+)"', open('app/src/main/res/values/colors.xml').read())),
}
allkt = glob.glob('app/src/*/java/**/*.kt', recursive=True)
used = set()
for f in allkt:
    for k, n in re.findall(r'R\.(id|string|layout|drawable|style|color)\.(\w+)', open(f).read()):
        used.add((k, n))
for k, n in sorted(used):
    if k == 'style' and n.startswith(('Widget.', 'Theme.', 'Ghost.')):
        continue
    if n not in table[k]:
        bad("unresolved resource", k, n)
print(f"{len(used)} R.* references resolve")

for f in xmls:
    for k, n in re.findall(r'"@(?:\+)?(id|string|layout|drawable|style|color|xml)/([\w.]+)"', open(f).read()):
        if k == 'id' or (k == 'xml' and n == 'accessory_filter'):
            continue
        if k == 'style' and n.startswith('Widget.'):
            continue
        if n not in table[k]:
            bad("unresolved resource in XML", f, k, n)

# ---- 7. classes named by layouts and the manifest exist ----
for f in glob.glob('app/src/main/res/layout/*.xml'):
    for cls in re.findall(r'<(drone\.test[\w.]+)', open(f).read()):
        if not any(os.path.exists(f'app/src/{d}/java/' + cls.replace('.', '/') + '.kt')
                   for d in ['main', 'full', 'sim']):
            bad("layout names a class no source set provides:", cls)
print("layout custom views resolve in both flavours")

man = open('app/src/main/AndroidManifest.xml').read()
pkg = re.search(r'package="([\w.]+)"', man).group(1)
for rel in re.findall(r'android:name="(\.[\w.]+)"', man):
    if not os.path.exists('app/src/main/java/' + (pkg + rel).replace('.', '/') + '.kt'):
        bad("manifest names a missing class:", pkg + rel)
print("manifest package", pkg, "classes resolve")

# ---- 8. per-flavour import graph ----
for flav in ['full', 'sim']:
    unit = glob.glob('app/src/main/java/**/*.kt', recursive=True) + \
           glob.glob(f'app/src/{flav}/java/**/*.kt', recursive=True)
    have = set()
    for f in unit:
        s = open(f).read()
        p = re.search(r'^package\s+([\w.]+)', s, re.M).group(1)
        for c in re.findall(r'^(?:class|object|interface|enum class|data class|sealed class)\s+(\w+)', s, re.M):
            have.add(p + '.' + c)
    for f in unit:
        for imp in re.findall(r'^import (drone\.test\.[\w.]+)', open(f).read(), re.M):
            if imp.endswith('.R'):
                continue
            if imp not in have:
                bad(f"[{flav}] unresolved internal import", imp, "in", f)
print("internal import graph resolves for both full and sim")

# ---- 9. brace balance and duplicate members ----
for f in allkt:
    t = open(f).read()
    t = re.sub(r'"""[\s\S]*?"""', '""', t)
    t = re.sub(r'"(\\.|[^"\\])*"', '""', t)
    t = re.sub(r"'(\\.|[^'\\])'", "''", t)
    t = re.sub(r'//[^\n]*', '', t)
    t = re.sub(r'/\*[\s\S]*?\*/', '', t)
    for o, c in [('{', '}'), ('(', ')'), ('[', ']')]:
        if t.count(o) != t.count(c):
            bad("unbalanced", f, o, c, t.count(o), t.count(c))
    for (n, a), k in collections.Counter(
        re.findall(r'^\s{4}(?:override |private |public |internal )*fun\s+(\w+)\s*\(([^)]*)\)', open(f).read(), re.M)
    ).items():
        if k > 1:
            bad("duplicate member function", f, n, a)

print()
if fail:
    print(f"{fail} FAILURE(S)")
    sys.exit(1)
print("ALL STRUCTURAL CHECKS PASSED")
