all: build

build:
	mvn install

install:
	python bin/easy-install.py

clean:
	mvn clean

example:
	tornado tornado.examples.HelloWorld

tests:
	tornado-test.py --verbose

eclipse:
	mvn eclipse:eclipse
