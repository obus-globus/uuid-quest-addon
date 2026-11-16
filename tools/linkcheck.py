"""Checks that every Minecraft, Fabric API and Mod Menu class/member a Fabric jar references exists in
other Minecraft versions, to decide whether a jar can cover them (see CLAUDE.md, "Fabric nodes").

usage: python3 tools/linkcheck.py <mod.jar> <compile-version> <target-version> [<target-version> ...]
e.g.   python3 tools/linkcheck.py build/libs/uuid-quest-fabric-1.0.0+1.21.5-1.21.8.jar 1.21.5 1.21.4 1.21.8 1.21.9

Versions below 26 are checked in the intermediary namespace (client jar + intermediary mappings), 26.x in
official names; the newest Fabric API and the Mod Menu release for each target are included. Also reports
overridden methods the target lacks and abstract methods left unimplemented. Downloads (about 60 MB per
version) are cached in ~/.cache/uuid-quest-linkcheck. Exit status 1 if any target has problems.
"""
import io, json, os, re, struct, sys, urllib.request, zipfile

CACHE = os.path.expanduser('~/.cache/uuid-quest-linkcheck')
MANIFEST = None


def fetch(url, path):
    if not os.path.exists(path):
        request = urllib.request.Request(url, headers={'User-Agent': 'curl/8 (linkcheck)'})
        data = urllib.request.urlopen(request).read()
        open(path, 'wb').write(data)
    return path


def version_json(v):
    global MANIFEST
    p = f'{CACHE}/{v}.json'
    if not os.path.exists(p):
        if MANIFEST is None:
            MANIFEST = json.load(open(fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json',
                                            f'{CACHE}/manifest.json')))
        url = next(x['url'] for x in MANIFEST['versions'] if x['id'] == v)
        fetch(url, p)
    return json.load(open(p))


def obfuscated(v):
    return not v.startswith('26.')


def parse_class(data):
    """-> (name, super, interfaces, access, methods{(n,d):acc}, fields{(n,d)}, refs[(kind,owner,name,desc)], classrefs)"""
    f = io.BytesIO(data)
    f.read(8)
    count, = struct.unpack('>H', f.read(2))
    cp = [None] * count
    i = 1
    while i < count:
        tag = f.read(1)[0]
        if tag == 1:
            ln, = struct.unpack('>H', f.read(2)); cp[i] = ('utf8', f.read(ln).decode('utf-8', 'replace'))
        elif tag in (3, 4):
            f.read(4)
        elif tag in (5, 6):
            f.read(8); i += 1
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = (tag, struct.unpack('>H', f.read(2))[0])
        elif tag in (9, 10, 11, 12, 17, 18):
            cp[i] = (tag,) + struct.unpack('>HH', f.read(4))
        elif tag == 15:
            cp[i] = (tag, f.read(1)[0], struct.unpack('>H', f.read(2))[0])
        else:
            raise ValueError(f'bad tag {tag}')
        i += 1
    u = lambda k: cp[k][1]
    cls = lambda k: u(cp[k][1]) if k else None
    access, this, sup = struct.unpack('>HHH', f.read(6))
    n_if, = struct.unpack('>H', f.read(2))
    ifaces = [cls(struct.unpack('>H', f.read(2))[0]) for _ in range(n_if)]

    def members():
        out = {}
        n, = struct.unpack('>H', f.read(2))
        for _ in range(n):
            acc, ni, di, na = struct.unpack('>HHHH', f.read(8))
            for _ in range(na):
                f.read(2); ln, = struct.unpack('>I', f.read(4)); f.read(ln)
            out[(u(ni), u(di))] = acc
        return out
    fields = members()
    methods = members()
    refs, classrefs = [], set()
    for e in cp:
        if not e:
            continue
        if e[0] in (9, 10, 11):
            owner = cls(e[1]); nt = cp[e[2]]
            refs.append(('field' if e[0] == 9 else 'method', owner, u(nt[1]), u(nt[2])))
        elif e[0] == 7:
            classrefs.add(u(e[1]))
    return cls(this), cls(sup), ifaces, access, methods, fields, refs, classrefs


def load_tiny(v):
    jar = fetch(f'https://maven.fabricmc.net/net/fabricmc/intermediary/{v}/intermediary-{v}-v2.jar',
                f'{CACHE}/intermediary-{v}.jar')
    text = zipfile.ZipFile(jar).read('mappings/mappings.tiny').decode()
    classes, methods, fields = {}, {}, {}
    owner = None
    for line in text.splitlines()[1:]:
        p = line.split('\t')
        if p[0] == 'c':
            owner = p[1]; classes[p[1]] = p[2]
        elif len(p) > 2 and p[1] == 'm':
            methods[(owner, p[3], p[2])] = p[4]
        elif len(p) > 2 and p[1] == 'f':
            fields[(owner, p[3])] = p[4]
    return classes, methods, fields


# Mod Menu's newest release per Minecraft version (Modrinth); extend when adding versions.
MODMENU = {'1.20.1': '7.2.2', '1.20.2': '8.0.1', '1.20.3': '9.2.0', '1.20.4': '9.2.0', '1.20.5': '10.0.0',
           '1.20.6': '10.0.0', '1.21': '11.0.5', '1.21.1': '11.0.5', '1.21.2': '12.0.1', '1.21.3': '12.0.1',
           '1.21.4': '13.0.4', '1.21.5': '14.0.2', '1.21.6': '15.0.2', '1.21.7': '15.0.2', '1.21.8': '15.0.2',
           '1.21.9': '16.0.1', '1.21.10': '16.0.1', '1.21.11': '17.0.1', '26.1': '18.0.2', '26.1.1': '18.0.2',
           '26.1.2': '18.0.2', '26.2': '20.0.3', '26.3': '21.0.1'}


def mod_jars(v):
    """The newest Fabric API for v (its nested module jars) and Mod Menu, already in the mod's namespace."""
    metadata = open(fetch('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml',
                          f'{CACHE}/fabric-api-metadata.xml')).read()
    fapi = [x for x in re.findall(r'<version>([^<]+)</version>', metadata) if x.endswith('+' + v)][-1]
    outer = fetch(f'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{fapi}/fabric-api-{fapi}.jar',
                  f'{CACHE}/fapi-{fapi}.jar')
    jars = []
    z = zipfile.ZipFile(outer)
    for entry in z.namelist():
        if entry.startswith('META-INF/jars/') and entry.endswith('.jar'):
            path = f'{CACHE}/fapi-{fapi}-{os.path.basename(entry)}'
            if not os.path.exists(path):
                open(path, 'wb').write(z.read(entry))
            jars.append(path)
    mm = MODMENU[v]
    jars.append(fetch(f'https://maven.terraformersmc.com/releases/com/terraformersmc/modmenu/{mm}/modmenu-{mm}.jar',
                      f'{CACHE}/modmenu-{mm}.jar'))
    return jars


DESC_CLASS = re.compile(r'L([^;]+);')


class Universe:
    """Classes of one Minecraft version (plus authlib and brigadier) in the mod's namespace."""

    def __init__(self, v):
        vj = version_json(v)
        jars = [fetch(vj['downloads']['client']['url'], f'{CACHE}/{v}-client.jar')]
        for lib in vj['libraries']:
            name = lib['name']
            if any(k in name for k in ('authlib', 'brigadier', 'datafixerupper')):
                jars.append(fetch(lib['downloads']['artifact']['url'], f'{CACHE}/{v}-{name.split(":")[1]}.jar'))
        jars += mod_jars(v)
        tiny = load_tiny(v) if obfuscated(v) else ({}, {}, {})
        cmap, mmap, fmap = tiny
        mapc = lambda n: cmap.get(n, n)
        mapd = lambda d: DESC_CLASS.sub(lambda m: 'L' + mapc(m.group(1)) + ';', d)
        raw = {}
        for jar in jars:
            z = zipfile.ZipFile(jar)
            for entry in z.namelist():
                if not entry.endswith('.class') or entry.startswith('META-INF'):
                    continue
                parsed = parse_class(z.read(entry))
                raw.setdefault(parsed[0], parsed)

        def method_name(owner, n, d, seen=None):
            """Intermediary only names a method where it is declared first; overrides inherit the name."""
            if n.startswith('<'):
                return n
            seen = seen if seen is not None else set()
            todo = [owner]
            while todo:
                c = todo.pop(0)
                if c in seen:
                    continue
                seen.add(c)
                if (c, n, d) in mmap:
                    return mmap[(c, n, d)]
                if c in raw:
                    if raw[c][1]:
                        todo.append(raw[c][1])
                    todo.extend(raw[c][2])
            return n

        self.classes = {}
        for name, (_, sup, ifaces, acc, methods, fields, _, _) in raw.items():
            self.classes[mapc(name)] = (
                mapc(sup) if sup else None,
                [mapc(i) for i in ifaces],
                acc,
                {(method_name(name, n, d) if obfuscated(v) else n, mapd(d)): a for (n, d), a in methods.items()},
                {(fmap.get((name, n), n), mapd(d)) for (n, d) in fields},
            )

    def hierarchy(self, name):
        seen, todo = [], [name]
        while todo:
            c = todo.pop(0)
            if c in seen or c not in self.classes:
                continue
            seen.append(c)
            sup, ifaces = self.classes[c][0], self.classes[c][1]
            if sup:
                todo.append(sup)
            todo.extend(ifaces)
        return seen

    def has_method(self, owner, name, desc):
        return any((name, desc) in self.classes[c][3] for c in self.hierarchy(owner))

    def has_field(self, owner, name, desc):
        return any((name, desc) in self.classes[c][4] for c in self.hierarchy(owner))

    def leaves_known_world(self, owner):
        """True if the hierarchy reaches a class outside this universe (JDK etc.), where members can't be checked."""
        for c in self.hierarchy(owner):
            sup, ifaces = self.classes[c][0], self.classes[c][1]
            for s in ([sup] if sup else []) + ifaces:
                if s not in self.classes and not s.startswith('java/lang/Object'):
                    return True
        return False


def mc(name):
    return name.startswith(('net/minecraft/', 'com/mojang/', 'net/fabricmc/fabric/api/', 'com/terraformersmc/'))


def check(mod_jar, compile_u, target_u):
    z = zipfile.ZipFile(mod_jar)
    problems = []
    mod_classes = {}
    for entry in z.namelist():
        if entry.endswith('.class'):
            parsed = parse_class(z.read(entry))
            mod_classes[parsed[0]] = parsed
    for name, (_, sup, ifaces, acc, methods, fields, refs, classrefs) in mod_classes.items():
        for c in classrefs:
            c = c.lstrip('[')
            if c.startswith('L'):
                c = c[1:-1]
            if mc(c) and c not in target_u.classes:
                problems.append(f'{name}: missing class {c}')
        for kind, owner, n, d in refs:
            owner = owner.lstrip('[')
            if not mc(owner) or owner in mod_classes:
                continue
            if owner not in target_u.classes:
                continue  # reported as missing class
            ok = target_u.has_method(owner, n, d) if kind == 'method' else target_u.has_field(owner, n, d)
            if not ok and not (kind == 'method' and target_u.leaves_known_world(owner) and n in ('toString', 'hashCode', 'equals', 'getClass')):
                problems.append(f'{name}: missing {kind} {owner}.{n}{d}')
        supers = [s for s in [sup] + ifaces if s and mc(s)]
        for s in supers:
            if s not in target_u.classes:
                continue
            # Overrides that would silently stop overriding.
            for (n, d), a in methods.items():
                if n.startswith('<') or a & 0x0008 or a & 0x0002:
                    continue
                if compile_u.has_method(s, n, d) and not target_u.has_method(s, n, d):
                    problems.append(f'{name}: overrides {s}.{n}{d}, which the target lacks')
            # Abstract methods we would leave unimplemented.
            if not acc & 0x0400:
                implemented = set(methods)
                for c in target_u.hierarchy(s):
                    for (n, d), a in target_u.classes[c][3].items():
                        if not a & 0x0400:
                            implemented.add((n, d))
                for c in target_u.hierarchy(s):
                    for (n, d), a in target_u.classes[c][3].items():
                        if a & 0x0400 and (n, d) not in implemented:
                            problems.append(f'{name}: does not implement abstract {c}.{n}{d}')
    return sorted(set(problems))


if __name__ == '__main__':
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    os.makedirs(CACHE, exist_ok=True)
    jar, compile_v, *targets = sys.argv[1:]
    compile_u = Universe(compile_v)
    failed = False
    for t in targets:
        target_u = compile_u if t == compile_v else Universe(t)
        problems = check(jar, compile_u, target_u)
        print(f'{os.path.basename(jar)} on {t}: ' + ('OK' if not problems else f'{len(problems)} problem(s)'))
        for p in problems:
            print('   ', p)
        failed |= bool(problems)
    sys.exit(1 if failed else 0)
