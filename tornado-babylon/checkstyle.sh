#!/usr/bin/env bash
# This module is built with build.sh (it needs the Babylon JDK), outside the Maven reactor: run
# TornadoVM's checkstyle rules on it with the checkstyle jar Maven fetched for the reactor build.
cd "$(dirname "$0")"
M2=${M2:-$HOME/.m2/repository}
CS=$(find "$M2/com/puppycrawl/tools/checkstyle" -name "checkstyle-*.jar" | sort | tail -1)
DEPS=$(find "$M2/info/picocli" "$M2/antlr" "$M2/org/antlr/antlr4-runtime/4.9.3" "$M2/commons-beanutils" "$M2/commons-logging" "$M2/commons-collections" \
    "$M2/com/google/guava/guava" "$M2/org/reflections" "$M2/net/sf/saxon" "$M2/org/javassist" -name "*.jar" 2>/dev/null | tr '\n' ':')
java -cp "$CS:$DEPS" com.puppycrawl.tools.checkstyle.Main -c ../tornado-assembly/src/etc/checkstyle.xml $(find src examples -name '*.java')
