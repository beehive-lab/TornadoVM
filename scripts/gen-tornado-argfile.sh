#!/usr/bin/env bash
#
# gen-tornado-options.sh
# Generate tornado-options-full.txt from tornado --printJavaFlags and export lists
#

# Project root: go two levels up from $TORNADO_SDK (…/TornadoVM/bin/sdk -> …/TornadoVM)
PROJECT_ROOT="$( cd "$TORNADO_SDK/../.." && pwd )"

TORNADO_BIN="tornado"
OUT="$PROJECT_ROOT/tornado-argfile"
EXPORT_COMMON="$TORNADO_SDK/etc/exportLists/common-exports"
EXPORT_OPENCL="$TORNADO_SDK/etc/exportLists/opencl-exports"
EXPORT_SPIRV="$TORNADO_SDK/etc/exportLists/spirv-exports"
EXPORT_PTX="$TORNADO_SDK/etc/exportLists/ptx-exports"

echo "[INFO] Cleaning old file..."
rm -f "$OUT"

echo "[INFO] Generating $OUT ..."
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

# List of export files to include (some may not exist)
EXPORT_FILES=(
  "$EXPORT_COMMON"
  "$EXPORT_OPENCL"
  "$EXPORT_SPIRV"
  "$EXPORT_PTX"
)

for export_file in "${EXPORT_FILES[@]}"; do
  if [[ -f "$export_file" && -s "$export_file" ]]; then
    echo "# From: $(basename "$export_file")"
    strip_comments "$export_file"
    echo
  else
    echo "# Skipping missing or empty export file: $(basename "$export_file")"
    echo
  fi
done


} > "$OUT"

echo "[INFO] Done. Generated fresh $OUT"
echo "[INFO] File path: $OUT"
