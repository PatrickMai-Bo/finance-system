#!/usr/bin/env bash
# Reusable Maven launcher for this machine (Git Bash friendly).
# Uses JDK17 + Maven 3.9.12 via the classworlds launcher directly,
# bypassing the broken mvn shell script path conversion.
set -e
MH="D:\\maven\\apache-maven-3.9.12"
JH="D:\\java\\JDK17"
PROJDIR="$(pwd -W 2>/dev/null || pwd)"
"$JH\\bin\\java.exe" \
  -classpath "$MH\\boot\\plexus-classworlds-2.9.0.jar" \
  "-Dclassworlds.conf=$MH\\bin\\m2.conf" \
  "-Dmaven.home=$MH" \
  "-Dmaven.multiModuleProjectDirectory=$PROJDIR" \
  "-Dmaven.conf=$MH\\conf" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
