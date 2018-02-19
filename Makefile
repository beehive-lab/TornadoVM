all: build

build: 
	./bin/compile.sh

clean: 
	mvn clean

example:
	tornado tornado.examples.HelloWorld

tests:
	tornado-test.py --verbose

test-slam:
	tornado-test.py --verbose tornado.unittests.slam.graphics.GraphicsTests 

eclipse:
	mvn eclipse:eclipse
