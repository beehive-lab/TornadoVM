#!/bin/bash
mvn clean
mvn -Dcmake.root.dir=$CMAKE_ROOT package
bash ./bin/updatePATHS.sh 
