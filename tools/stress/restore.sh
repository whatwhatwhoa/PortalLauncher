#!/bin/sh
# Puts the launcher's data back after a stress run (F28): the preferences and files/ from a
# tar made before it with
#   adb exec-out run-as io.github.whatwhatwhoa.portallauncher tar -cf - shared_prefs files > app.tar
# Usage (Git Bash): sh tools/stress/restore.sh path/to/app.tar
# One adb shell line stops the launcher, swaps the data and starts it, so it can't write its
# old copy in between (gotchas.md *Pull app files with adb exec-out*). Debug builds only (run-as).
set -e
export MSYS_NO_PATHCONV=1
ADB="${ADB:-C:/Users/Jim/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
P=io.github.whatwhatwhoa.portallauncher
"$ADB" push "$1" /data/local/tmp/portal-restore.tar >/dev/null
"$ADB" shell "chmod 644 /data/local/tmp/portal-restore.tar && am force-stop $P && run-as $P sh -c 'rm -rf shared_prefs files && tar -xf /data/local/tmp/portal-restore.tar' && rm /data/local/tmp/portal-restore.tar && am start -n $P/.MainActivity >/dev/null"
"$ADB" shell run-as $P ls -la shared_prefs files/art
