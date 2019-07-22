#!/bin/bash

mvn clean

if [[ $1 == "OFFLINE" ]] 
then
	mvn -o -Dcmake.root.dir=$CMAKE_ROOT package
else
	mvn -Dcmake.root.dir=$CMAKE_ROOT package
fi

if [ $? -eq 0 ] 
then
	bash ./bin/updatePATHS.sh 
else
		
	echo -e "\n \e[91mCompilation failed\e[39m \n"
fi

