all: build

build: 
	rm -rf fpga-source-comp/
	./bin/compile.sh

clean: 
	mvn clean
	rm -rf fpga-source-comp/

example:
	tornado uk.ac.manchester.tornado.examples.HelloWorld

tests:
	tornado-test.py --verbose

test-slam:
	tornado-test.py -V --fast uk.ac.manchester.tornado.unittests.slam.graphics.GraphicsTests 

eclipse:
	mvn eclipse:eclipse
cfgs:
	rm *.cfg *.bgv *.log

