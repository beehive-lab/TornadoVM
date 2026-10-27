#!/usr/bin/env bash
# Makes build/sdk-reflect-all: a copy of $TORNADOVM_HOME whose tornado-unittests are compiled by Babylon's
# javac with every static method annotated @Reflect (kernels are static; the tests themselves are instance
# methods), plus tornado-babylon as a module. -XDreflectAll is not used: it also makes every lambda and
# method reference reflectable, which changes the task lambdas TornadoVM inspects.
# Running the unit tests on it with -Dtornado.tc.frontend=babylon compiles every kernel from its code model:
#   ./reflect-all-sdk.sh && ./reflect-all-tests.sh ...
set -euo pipefail
cd "$(dirname "$0")"
: "${TORNADOVM_HOME:?source the TornadoVM setvars.sh first}"
source ./env.sh
ROOT=$PWD
REPO=$(cd .. && pwd)
SDK=${REFLECT_ALL_SDK:-$ROOT/build/sdk-reflect-all}
rm -rf "$SDK" && cp -a "$TORNADOVM_HOME/." "$SDK"
JARS=$SDK/share/java/tornado
UT=$(ls "$JARS"/tornado-unittests-*.jar)
CLASSES=$SDK.classes
SRC=$SDK.src
rm -rf "$CLASSES" "$SRC" && mkdir -p "$CLASSES" && cp -r "$REPO/tornado-unittests/src/main/java" "$SRC"
python3 "$ROOT/annotate-static-methods.py" "$SRC"
RELEASE=$(java -XshowSettings:properties -version 2>&1 | awk '/java.specification.version/{print $3}')
javac -g -Xlint:-preview --enable-preview --release "$RELEASE" -nowarn -Xmaxerrs 20 \
    --module-path "$(ls "$JARS"/*.jar | grep -v tornado-unittests | tr '\n' ':')" \
    --add-modules jdk.incubator.code --add-reads tornado.unittests=jdk.incubator.code \
    -d "$CLASSES" $(find "$SRC" -name '*.java')
[[ -d "$REPO/tornado-unittests/src/main/resources" ]] && cp -r "$REPO/tornado-unittests/src/main/resources/." "$CLASSES/"
rm "$UT" && jar cf "$UT" -C "$CLASSES" .
cp "$ROOT/build/tornado-babylon.jar" "$JARS/tornado-babylon.jar"
sed "s|\${TORNADOVM_HOME}|$SDK|g" "$SDK/tornado-argfile.template" > "$SDK/tornado-argfile"
echo "built $SDK ($(unzip -l "$UT" | tail -1 | awk '{print $2}') files in $(basename "$UT"))"
