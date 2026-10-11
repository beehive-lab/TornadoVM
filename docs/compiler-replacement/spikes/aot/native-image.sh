#!/usr/bin/env bash
# GraalVM CE 25 native image of the headless compiler (harness/NativeCold.java). TC reads class bytes
# through BytecodeSource.classpath, so nothing is loaded at run time and no reflection config is needed.
# Usage: native-image.sh <tc-jdk25.jar>
set -euo pipefail
G=${GRAALVM:-$(ls -d $HOME/tools/graalvm-community-openjdk-25* | head -1)}
here=$(cd "$(dirname "$0")" && pwd); work=$(mktemp -d)
"$G/bin/javac" --release 25 -cp "$1" -d "$work" "$here/harness/NativeCold.java"
"$G/bin/native-image" --no-fallback -O2 -cp "$1:$work" -o tc-native NativeCold
CPS="$HOME/tornadovm-compiler/tornado-benchmarks/target/classes:$HOME/tornadovm-compiler/tornado-api/target/classes"
./tc-native "$CPS" uk.ac.manchester.tornado.benchmarks.ComputeKernels blackscholes 1
