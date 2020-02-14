#!/bin/bash

mvn clean

if [[ $2 == "OFFLINE" ]]
then
	mvn -o -Dcmake.root.dir=$CMAKE_ROOT -P$1 package
else
	mvn -Dcmake.root.dir=$CMAKE_ROOT -P$1 package
fi

if [ $? -eq 0 ] 
then
	bash ./bin/updatePATHS.sh 
else
		
	echo -e "\n \e[91mCompilation failed\e[39m \n"
fi

