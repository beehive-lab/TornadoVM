#!/usr/bin/env bash

# Parameters passed to this script:
# $1 - which JDK is used to build Tornado { jdk-8, graal-jdk-8, graal-jdk-11 }
# $2 - backends selected for the build. Can be any combination of { opencl-backend, ptx-backend }
# $3 - build Tornado OFFLINE

python scripts/updateMavenSettings.py
if [ $? -eq 1 ]; then
  exit 1
fi
mvn -Popencl-backend,ptx-backend clean

if [[ $3 == "OFFLINE" ]]; then
  mvn -T1.5C -o -Dcmake.root.dir=$CMAKE_ROOT -P$1,$2 install
else
  mvn -T1.5C -Dcmake.root.dir=$CMAKE_ROOT -P$1,$2 install
fi

if [ $? -eq 0 ]; then
  ## Update the PATH if the compilation is correct
  bash ./bin/updatePATHS.sh
else
  echo -e "\n \e[91mCompilation failed\e[39m \n"
fi
