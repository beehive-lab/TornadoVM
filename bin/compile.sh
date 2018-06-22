#!/bin/bash
mvn clean
mvn -o -Dcmake.root.dir=$CMAKE_ROOT package
bash ./bin/updatePATHS.sh 
