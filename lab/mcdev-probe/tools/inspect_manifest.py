import zipfile, sys
for path in sys.argv[1:]:
    print("=====", path)
    z=zipfile.ZipFile(path)
    try:
        print(z.read("META-INF/MANIFEST.MF").decode("utf-8","replace"))
    except KeyError:
        print("NO MANIFEST")
