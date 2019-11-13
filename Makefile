all: build

build: 
	./bin/compile.sh

offline:
	./bin/compile.sh OFFLINE

clean: 
	mvn clean

example:
	tornado --printKernel --debug uk.ac.manchester.tornado.examples.VectorAddInt 8192

tests:
	tornado-test.py --verbose
	test-native.sh 

test-slam:
	tornado-test.py -V --fast uk.ac.manchester.tornado.unittests.slam.graphics.GraphicsTests 


test-hdgraphics:
	tornado-test.py -V -J"-Ds0.t0.device=0:3" uk.ac.manchester.tornado.unittests.reductions.TestReductionsFloats
	tornado-test.py -V -J"-Ds0.t0.device=0:3" uk.ac.manchester.tornado.unittests.reductions.TestReductionsDoubles
	tornado-test.py -V -J"-Ds0.t0.device=0:3" uk.ac.manchester.tornado.unittests.reductions.TestReductionsIntegers


eclipse:
	mvn eclipse:eclipse

clean-graphs:
	rm *.cfg *.bgv *.log

