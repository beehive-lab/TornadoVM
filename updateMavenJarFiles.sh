#!/bin/bash 

# Author: Juan Fumero
# Date  : 25/02/2019

if [ -z $TORNADO_SDK ]
then
	echo "Set the TORNADO_SDK variable"
	exit 0
fi

VERSION=1.1.0
GROUPID="tornado"
API_PATH=$TORNADO_SDK/share/java/tornado

declare -a artifacts=("tornado-api" "tornado-annotation" "tornado-benchmarks" "tornado-examples" "tornado-drivers-opencl" "tornado-matrices" "tornado-runtime" "tornado-drivers-common" "tornado-drivers-ptx" "tornado-drivers-spirv")

for artifact in "${artifacts[@]}"
do
	echo "Installing artifact: $artifact"
	mvn install:install-file \
		-DgroupId=${GROUPID} \
		-DartifactId=${artifact} \
		-Dversion=$VERSION \
		-Dfile=${API_PATH}/$artifact-${VERSION}.jar \
		-Dsources=${API_PATH}/$artifact-${VERSION}.jar \
		-Dpackaging=jar \
		-DgeneratePom=true \
		-DlocalRepositoryPath=. \
		-DcreateChecksum=true 
done

echo "Installing artifact: tornado-drivers-opencl-jni"
mvn install:install-file \
	-DgroupId=${GROUPID} \
	-DartifactId=tornado-drivers-opencl-jni \
	-Dversion=$VERSION \
	-Dfile=${API_PATH}/tornado-drivers-opencl-jni-${VERSION}-libs.jar \
	-Dsources=${API_PATH}/tornado-drivers-opencl-jni-${VERSION}-libs.jar \
	-Dpackaging=jar \
	-DgeneratePom=true \
	-DlocalRepositoryPath=. \
	 -DcreateChecksum=true 


echo "Installing artifact: tornado-drivers-ptx-jni"
mvn install:install-file \
	-DgroupId=${GROUPID} \
	-DartifactId=tornado-drivers-ptx-jni \
	-Dversion=$VERSION \
	-Dfile=${API_PATH}/tornado-drivers-ptx-jni-${VERSION}-libs.jar \
	-Dsources=${API_PATH}/tornado-drivers-ptx-jni-${VERSION}-libs.jar \
	-Dpackaging=jar \
	-DgeneratePom=true \
	-DlocalRepositoryPath=. \
	 -DcreateChecksum=true 
