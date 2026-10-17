import zipfile, sys, json
jar = sys.argv[1]
z = zipfile.ZipFile(jar)
for name in ["META-INF/mods.toml", "carpet.mixins.json"]:
    print("=====", name)
    try:
        print(z.read(name).decode("utf-8", "replace")[:4000])
    except KeyError:
        print("MISSING")
print("===== refmap keys containing m_129783_")
try:
    rm = json.loads(z.read("carpet.refmap.json").decode("utf-8"))
    for section in ("mappings", "data"):
        pass
    s = json.dumps(rm)
    idx = s.find("m_129783_")
    print("found at", idx)
    if idx >= 0:
        print(s[max(0,idx-300):idx+300])
except KeyError:
    print("MISSING refmap")
