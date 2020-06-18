#!/usr/bin/env bash

python scripts/updateMavenSettings.py
if [ $? -eq 1 ]; then
  exit 1
fi
mvn clean

if [[ $2 == "OFFLINE" ]]; then
  mvn -T1.5C -o -Dcmake.root.dir=$CMAKE_ROOT -P$1 install
else
  mvn -T1.5C -Dcmake.root.dir=$CMAKE_ROOT -P$1 install
fi

if [ $? -eq 0 ]; then
  ## Update the PATH if the compilation is correct
  bash ./bin/updatePATHS.sh
else
  echo -e "\n \e[91mCompilation failed\e[39m \n"
fi
