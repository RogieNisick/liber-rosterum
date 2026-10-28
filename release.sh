#!/bin/bash
# Сборка + version.json для папки обновлений. Заметки — release_notes.txt
set -e
python3 - <<'PY0'
import re, json, os
m=open('AndroidManifest.xml').read()
code=int(re.search(r'versionCode="(\d+)"',m).group(1)); name=re.search(r'versionName="([^"]+)"',m).group(1)
notes=open('release_notes.txt').read().strip() if os.path.exists('release_notes.txt') else ''
hist=json.load(open('notes_history.json')) if os.path.exists('notes_history.json') else []
hist=[x for x in hist if x["code"]!=code]+[{"code":code,"name":name,"notes":notes}]
hist.sort(key=lambda x:x["code"])
json.dump(hist,open('assets/notes_history.json','w'),ensure_ascii=False,indent=1)
PY0
./build.sh > /tmp/liber_build.log 2>&1 || { cat /tmp/liber_build.log; echo "RELEASE ABORTED"; exit 1; }
python3 - <<'PY'
import re, json, os
m=open('AndroidManifest.xml').read()
code=int(re.search(r'versionCode="(\d+)"',m).group(1)); name=re.search(r'versionName="([^"]+)"',m).group(1)
notes=open('release_notes.txt').read().strip() if os.path.exists('release_notes.txt') else ''
apk='build/LiberRosterum.apk'
hist=json.load(open('notes_history.json')) if os.path.exists('notes_history.json') else []
hist=[x for x in hist if x["code"]!=code]+[{"code":code,"name":name,"notes":notes}]
hist.sort(key=lambda x:x["code"])
json.dump(hist,open('notes_history.json','w'),ensure_ascii=False,indent=1)
# старые версии приложения показывают только поле notes — кладём туда всю историю (новые сверху)
cum="\n\n".join("ВЕРСИЯ "+x["name"]+"\n"+x["notes"] for x in reversed(hist[-20:]))
json.dump({"versionCode":code,"versionName":name,"apk":"LiberRosterum.apk","size":os.path.getsize(apk),"notes":cum,"latest":notes,"history":hist[-20:]},open('build/version.json','w'),ensure_ascii=False,indent=1)
json.dump(hist,open('assets/notes_history.json','w'),ensure_ascii=False,indent=1)
print(open('build/version.json').read())
PY
