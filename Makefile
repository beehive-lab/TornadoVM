all: build

# Variables passed for build:
# JDK - which JDK is used to build Tornado. Can be one of { jdk-8, graal-jdk-8, graal-jdk-11 }. Default: jdk-8
# BACKEND - which backend to include in the build. Can be any combination of { opencl-backend, ptx-backend }. Default: opencl-backend
JDK?=jdk-8
BACKEND?=opencl-backend
build:
	./bin/compile.sh $(JDK) $(BACKEND)

offline:
	./bin/compile.sh $(JDK) $(BACKEND) OFFLINE

clean: 
	mvn clean

example:
	tornado --printKernel --debug uk.ac.manchester.tornado.examples.VectorAddInt 8192

tests:
	tornado-test.py --ea --verbose
	tornado-test.py --ea -V -J"-Dtornado.heap.allocation=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	test-native.sh 

test-slam:
	tornado-test.py -V --fast uk.ac.manchester.tornado.unittests.slam.graphics.GraphicsTests 


test-hdgraphics:
	@[ "${HDGRAPHICS_ID}" ] || ( echo ">> HDGRAPHICS_ID is not set. Please do \`export HDGRAPHICS_ID=<dev-id>\`"; exit 1 )
	tornado-test.py -V -J"-Ds0.t0.device=0:${HDGRAPHICS_ID} -Ds0.t1.device=0:${HDGRAPHICS_ID}" uk.ac.manchester.tornado.unittests.reductions.TestReductionsFloats
	tornado-test.py -V -J"-Ds0.t0.device=0:${HDGRAPHICS_ID} -Ds0.t1.device=0:${HDGRAPHICS_ID}" uk.ac.manchester.tornado.unittests.reductions.TestReductionsDoubles
	tornado-test.py -V -J"-Ds0.t0.device=0:${HDGRAPHICS_ID} -Ds0.t1.device=0:${HDGRAPHICS_ID}" uk.ac.manchester.tornado.unittests.reductions.TestReductionsIntegers
	tornado-test.py -V -J"-Ds0.t0.device=0:${HDGRAPHICS_ID} -Ds0.t1.device=0:${HDGRAPHICS_ID}" uk.ac.manchester.tornado.unittests.reductions.TestReductionsLong


eclipse:
	mvn eclipse:eclipse

clean-graphs:
	rm *.cfg *.bgv *.log

