#!/bin/bash

mvn clean
mvn -o -Dcmake.root.dir=$CMAKE_ROOT package

if [ $? -eq 0 ] 
then
	bash ./bin/updatePATHS.sh 
else
		
	echo -e "\n \e[91mCompilation failed\e[39m \n"
fi

