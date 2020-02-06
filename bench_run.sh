#!/bin/sh

echo "Type machine id "
read machine

sed -i 's/("tornado.oclscheduler", "false")/("tornado.oclscheduler", "true")/g' runtime/src/main/java/uk/ac/manchester/tornado/runtime/common/Tornado.java

make 

python2 tornado-benchmarks.py --sizes --skipSeq >> ${machine}_tune.log

sed -i 's/("tornado.oclscheduler", "true")/("tornado.oclscheduler", "false")/g' runtime/src/main/java/uk/ac/manchester/tornado/runtime/common/Tornado.java

make

python2 tornado-benchmarks.py --sizes --skipSeq >> ${machine}_default.log

echo "**** END ****"
