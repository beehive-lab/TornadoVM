#!/bin/bash

mvn clean

if [[ $1 == "OFFLINE" ]] 
then
	mvn -e -o -Dcmake.root.dir=$CMAKE_ROOT package
else
	mvn -e -Dcmake.root.dir=$CMAKE_ROOT package
fi

if [ $? -eq 0 ] 
then
	bash ./bin/updatePATHS.sh 
else
		
	echo -e "\n \e[91mCompilation failed\e[39m \n"
fi

