#!/bin/bash 

## Update all PATHs 
bash ./bin/updatePATHS.sh

## Update the compiled backends file
echo "tornado.backends="${selected_backends} > ${TORNADO_SDK}/etc/tornado.backend

# Place the Graal jars in the TornadoVM distribution onlt if the JDK 11+ rule is used. 
if [[ ! $JAVA_VERSION == "1.8" && ! $JAVA_VERSION_OUTPUT == *"GraalVM"* ]]; then
  mkdir -p $TORNADO_SDK/share/java/graalJars
  cp $PWD/graalJars/* $TORNADO_SDK/share/java/graalJars/
fi

