import zipfile, sys
z=zipfile.ZipFile(sys.argv[1])
print("=== namelist ===")
for n in z.namelist():
    print(n)
print("=== mods.toml tail ===")
print(z.read("META-INF/mods.toml").decode("utf-8","replace")[-1200:])
print("=== mixins.json ===")
try:
    print(z.read("mcdev_probe.mixins.json").decode("utf-8","replace"))
except KeyError:
    print("MISSING")
print("=== refmap ===")
try:
    print(z.read("mcdev_probe.refmap.json").decode("utf-8","replace"))
except KeyError:
    print("MISSING")
