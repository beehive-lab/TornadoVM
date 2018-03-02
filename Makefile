all: build

build: 
	./bin/compile.sh

clean: 
	mvn clean

example:
	tornado uk.ac.manchester.tornado.examples.HelloWorld

tests:
	tornado-test.py --verbose

eclipse:
	mvn eclipse:eclipse
