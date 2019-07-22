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

eclipse:
	mvn eclipse:eclipse

clean-graphs:
	rm *.cfg *.bgv *.log

