#!/bin/bash
set -e
T=/home/claude/tools
AJ=$T/plat/android-34/android.jar
rm -rf build && mkdir -p build/classes build/res
$T/aapt2 compile --dir res -o build/res.zip
$T/aapt2 link -I $AJ -A assets --manifest AndroidManifest.xml --min-sdk-version 21 --target-sdk-version 34 \
  -o build/base.apk build/res.zip --auto-add-overlay
javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath $AJ -d build/classes $(find src -name '*.java') > build/javac.log 2>&1 || { grep -vE "JAVA_TOOL_OPTIONS" build/javac.log; echo "BUILD FAILED: javac error"; exit 1; }
grep -vE "JAVA_TOOL_OPTIONS|bootstrap|source value 8|target value 8|To suppress|^[0-9]+ warnings?$|^warning: \[options\]|^Note:" build/javac.log || true
java -jar $T/dx.jar --dex --min-sdk-version=21 --output=build/classes.dex build/classes 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
python3 - <<'PY'
import zipfile
src = zipfile.ZipFile("build/base.apk")
out = zipfile.ZipFile("build/unsigned.apk", "w")
def add(name, data, stored):
    zi = zipfile.ZipInfo(name, date_time=(2026,10,24,0,0,0))
    zi.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
    if stored:
        off = out.fp.tell() + 30 + len(name.encode())
        pad = (4 - off % 4) % 4
        zi.extra = b"\0" * pad
    out.writestr(zi, data)
for it in src.infolist():
    stored = it.compress_type == zipfile.ZIP_STORED or it.filename == "resources.arsc" or it.filename.endswith(".png")
    add(it.filename, src.read(it.filename), stored)
add("classes.dex", open("build/classes.dex","rb").read(), False)
out.close()
PY
[ -f liber.keystore ] || keytool -genkeypair -keystore liber.keystore -storepass liberliber -keypass liberliber -alias liber \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Liber Rosterum" 2>&1 | grep -v JAVA_TOOL_OPTIONS
java -jar $T/apksigner.jar sign --ks liber.keystore --ks-pass pass:liberliber --ks-key-alias liber \
  --out build/LiberRosterum.apk build/unsigned.apk 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
java -jar $T/apksigner.jar verify -v build/LiberRosterum.apk 2>&1 | grep -E "Verified|Verifies|ERROR|WARNING" | head
ls -la build/LiberRosterum.apk
