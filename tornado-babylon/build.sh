#!/usr/bin/env bash
# Compiles the Babylon frontend + TornadoVM HAT backend into build/tornado-babylon.jar.
set -euo pipefail
cd "$(dirname "$0")"
: "${TORNADOVM_HOME:?source the TornadoVM setvars.sh first}"
source ./env.sh
CP=$(ls $TORNADO_JARS/*.jar | tr '\n' ':')$HAT_BUILD/hat-core-1.0.jar:$HAT_BUILD/hat-optkl-1.0.jar
rm -rf build/classes && mkdir -p build/classes
javac -Xlint:-preview --enable-preview --release "$(java -XshowSettings:properties -version 2>&1 | awk '/java.specification.version/{print $3}')" \
    --add-modules jdk.incubator.code -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/classes/
jar cf build/tornado-babylon.jar -C build/classes .
echo "built build/tornado-babylon.jar"
# The @Reflect TornadoVM-API example (examples/src), into build/examples.
rm -rf build/examples && mkdir -p build/examples
javac -Xlint:-preview --enable-preview --release "$(java -XshowSettings:properties -version 2>&1 | awk '/java.specification.version/{print $3}')" \
    --add-modules jdk.incubator.code -cp "$CP" -d build/examples $(find examples/src -name '*.java')
echo "built build/examples"
