#!/bin/bash
# Copy freshly built TC jars into the SDK (avoids a full `make` after TC-only changes).
cd ~/tornadovm-compiler && source setvars.sh >/dev/null
for j in $TORNADOVM_HOME/share/java/tornado/tc-*.jar; do
  m=$(basename $j | sed -E 's/-[0-9].*//')
  src=$(ls -t tornado-compiler/$m/target/$m-*.jar | grep -v sources | head -1)
  cp "$src" "$j" || echo "missing $m"
done
