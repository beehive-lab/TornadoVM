#!/usr/bin/env bash

# Parameters passed to this script:
# $1 - which JDK is used to build TornadoVM { graal-jdk-17-plus, jdk-17-plus }
# $2 - backends selected for TornadoVM. It can be any combination of { opencl, ptx, spirv }
# $3 - build TornadoVM with maven offline mode. Use "OFFLINE"

JAVA_CMD=${JAVA_HOME}/bin/java
JAVA_VERSION_OUTPUT=$("$JAVA_CMD" -version 2>&1)
JAVA_VERSION=$(echo "$JAVA_VERSION_OUTPUT" | awk -F[\"\.] -v OFS=. 'NR==1{print $2,$3}')

# If we have a JDK 17+ version that is not a GraalVM build, then we need to make sure we have the Graal jars available
if [[ ! $JAVA_VERSION_OUTPUT == *"GraalVM"* ]]; then
  bash ./bin/pullGraalJars.sh
fi

## Maven clean-up
echo "mvn -Popencl-backend,ptx-backend,spirv-backend clean"
mvn -Popencl-backend,ptx-backend,spirv-backend clean

# The maven profiles of each backend use the naming {ptx,opencl}-backend
selected_backends=''
IFS=',' read -ra selected_backends_list <<< "$2"
for ((i=0;i<${#selected_backends_list[@]};i++)); do
    selected_backends_list[i]="${selected_backends_list[i]}-backend"
    if ((i!=${#selected_backends_list[@]}-1)); then
      selected_backends_list[i]="${selected_backends_list[i]},"
    fi
    selected_backends=${selected_backends}${selected_backends_list[i]}
done

## Automatic Build for the SPIR-V Beehive Toolkit and Intel Level Zero
if [[ $selected_backends == *spirv* ]]
then
	current=$PWD
	spirvToolkit="beehive-spirv-toolkit"
  if [[ ! -d beehive-spirv-toolkit ]]
  then
    git clone https://github.com/beehive-lab/beehive-spirv-toolkit.git
  fi
  cd $spirvToolkit
  git pull origin master
  mvn clean package
  mvn install
  cd $current

	levelZeroLib="level-zero"
  if [[ ! -d levelZeroLib ]]
  then
    git clone https://github.com/oneapi-src/level-zero
    cd $levelZeroLib
    mkdir build
    cd build
    cmake ..
    cmake --build . --config Release
    cd $current
  fi

  export ZE_SHARED_LOADER="$PWD/level-zero/build/lib/libze_loader.so"
  export CPLUS_INCLUDE_PATH=$PWD/level-zero/include:$CPLUS_INCLUDE_PATH
  export C_INCLUDE_PATH=$PWD/level-zero/include:$C_INCLUDE_PATH
  export LD_LIBRARY_PATH=$PWD/level-zero/build/lib:$LD_LIBRARY_PATH

  cd $current
fi

options="-T1.5C -Dcmake.root.dir=$CMAKE_ROOT -P$1,${selected_backends} "
if [[ $3 == "OFFLINE" ]]; then
  options="-o $options"
fi

echo "mvn $options install"
mvn $options install

## Post installation
if [ $? -eq 0 ]; 
then
  ## Update all PATHs 
  bash ./bin/updatePATHS.sh

  ## Update the compiled backends file
  echo "tornado.backends="${selected_backends} > ${TORNADO_SDK}/etc/tornado.backend

  # Place the Graal jars in the TornadoVM distribution only if the JDK 17+ rule is used.
  if [[ ! $JAVA_VERSION_OUTPUT == *"GraalVM"* ]]; then
    mkdir -p $TORNADO_SDK/share/java/graalJars
    cp $PWD/graalJars/* $TORNADO_SDK/share/java/graalJars/
  fi

else
  echo -e "\n \e[91mCompilation failed\e[39m \n"
fi
