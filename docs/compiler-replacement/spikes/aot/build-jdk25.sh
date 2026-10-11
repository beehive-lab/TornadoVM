#!/usr/bin/env bash
# TC as one classpath jar for JDK 25 (the Maven build targets JDK 21 with --enable-preview, which a
# JDK 25 runtime will not load). Usage: build-jdk25.sh <out-dir>
set -euo pipefail
out=${1:-$HOME/tc-perf/coldstart/jdk25}; J25=${J25:-$HOME/.sdkman/candidates/java/25.0.2-open}
cd "$(git rev-parse --show-toplevel)/tornado-compiler"
mkdir -p "$out/classes"
find tc-check tc-ir tc-frontend tc-opt tc-lower tc-codegen tc-tornado tc-runtime -path "*/src/main/java/*.java" ! -name module-info.java > "$out/sources.txt"
"$J25/bin/javac" --release 25 -XDstringConcat=inline -nowarn -d "$out/classes" @"$out/sources.txt"
for m in tc-ir tc-frontend tc-codegen tc-runtime; do [ -d $m/src/main/resources ] && cp -r $m/src/main/resources/. "$out/classes/"; done
"$J25/bin/jar" cf "$out/tc-jdk25.jar" -C "$out/classes" .
echo "$out/tc-jdk25.jar"
