#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

TARGET_VERSION="$1"
BASE_DIR="$(pwd)/tornado"

echo "Generating checksum files under: $BASE_DIR for version: $TARGET_VERSION"

# Loop through all package directories
for package in "$BASE_DIR"/*; do
  [ -d "$package" ] || continue

  echo "Processing package: $(basename "$package")"

  versiondir="$package/$TARGET_VERSION"
  if [ -d "$versiondir" ]; then
    echo "  Found version: $TARGET_VERSION"

    # Find all JAR and POM files in that version folder
    find "$versiondir" -type f \( -name "*.jar" -o -name "*.pom" \) | while read -r artifact; do
      for algo in md5 sha1 sha256 sha512; do
        checksumfile="${artifact}.${algo}"
        if [ ! -f "$checksumfile" ]; then
          case $algo in
            md5)    md5sum "$artifact" | awk '{print $1}' > "$checksumfile" ;;
            sha1)   sha1sum "$artifact" | awk '{print $1}' > "$checksumfile" ;;
            sha256) sha256sum "$artifact" | awk '{print $1}' > "$checksumfile" ;;
            sha512) sha512sum "$artifact" | awk '{print $1}' > "$checksumfile" ;;
          esac
          echo "    + Generated $(basename "$checksumfile")"
        else
          echo "    - Exists: $(basename "$checksumfile"), skipping"
        fi
      done
    done
  else
    echo "  Version $TARGET_VERSION not found in $(basename "$package"), skipping."
  fi
done

echo "✅ Done generating checksums for version $TARGET_VERSION."

