#!/usr/bin/env bash
#
# Copyright (c) 2025, APT Group, Department of Computer Science,
# The University of Manchester.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#
# gen-tornado-options.sh
# Generate tornado-options-full.txt from tornado --printJavaFlags and export lists
#

# Project root: go two levels up from $TORNADO_SDK (…/TornadoVM/bin/sdk -> …/TornadoVM)
PROJECT_ROOT="$( cd "$TORNADO_SDK/../.." && pwd )"

BACKENDS=$1

TORNADO_BIN="tornado"
OUT="$PROJECT_ROOT/tornado-argfile"
EXPORT_COMMON="$TORNADO_SDK/etc/exportLists/common-exports"
EXPORT_OPENCL="$TORNADO_SDK/etc/exportLists/opencl-exports"
EXPORT_SPIRV="$TORNADO_SDK/etc/exportLists/spirv-exports"
EXPORT_PTX="$TORNADO_SDK/etc/exportLists/ptx-exports"

echo "[INFO] Cleaning old file"
rm -f "$OUT"

echo "[INFO] Generating TornadoVM argfile"
FLAGS=$("$TORNADO_BIN" --printJavaFlags)
FLAGS=${FLAGS#*java }

read -r -a JAVA_FLAGS <<< "$FLAGS"

{
  echo "# === JVM mode and memory settings ==="
  for f in "${JAVA_FLAGS[@]}"; do
    [[ $f == -XX* || $f == -server || $f == --enable-preview ]] && echo "$f"
  done

  echo
  echo "# === Native library path ==="
  for f in "${JAVA_FLAGS[@]}"; do
    [[ $f == -Djava.library.path* ]] && echo "$f"
  done

  echo
  echo "# === Tornado runtime classes ==="
  for f in "${JAVA_FLAGS[@]}"; do
    [[ $f == -Dtornado* ]] && echo "$f"
  done

  echo
  echo "# === Module system ==="
  for ((i=0; i<${#JAVA_FLAGS[@]}; i++)); do
    f="${JAVA_FLAGS[$i]}"
    if [[ $f == --module-path || $f == --upgrade-module-path || $f == --add-modules ]]; then
      echo "$f ${JAVA_FLAGS[$((i+1))]}"
      ((i++))
    fi
  done

echo
echo "# === Export lists ==="
echo

# Helper: strip comment and empty lines from export lists
strip_comments() {
  grep -vE '^\s*#' "$1" | grep -vE '^\s*$'
}
# Set IFS to comma and space
IFS=','

 # Read the comma-separated list into an array
read -ra arr <<< "$BACKENDS"

echo "# ===: $(basename "$EXPORT_COMMON") ==="
strip_comments "$EXPORT_COMMON"
echo

 # Iterate over the array
for i in "${arr[@]}"; do
   if [ "$i" == "opencl" ]; then
      echo "# === $(basename "$EXPORT_OPENCL") ==="
      strip_comments "$EXPORT_OPENCL"
      echo
   fi
   if [ "$i" == "ptx" ]; then
       echo "# === $(basename "$EXPORT_PTX") ==="
       strip_comments "$EXPORT_PTX"
       echo
    fi
    if [ "$i" == "spirv" ]; then
        echo "# === $(basename "$EXPORT_EXPORT_SPIRV") ==="
        strip_comments "$EXPORT_SPIRV"
        echo
     fi
done



} > "$OUT"

echo "[INFO] Done. Generated fresh argfile"
echo "[INFO] File path: $OUT"
