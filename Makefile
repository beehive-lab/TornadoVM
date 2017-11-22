all: build

build: 
	TORNADO_REVISION=$(shell echo `git rev-parse --short HEAD`) \
	mvn -T 1C install

install:
	TORNADO_REVISION=$(shell echo `git rev-parse --short HEAD`) \
	python bin/easy-install.py

clean: 
	TORNADO_REVISION=$(shell echo `git rev-parse --short HEAD`) \
	mvn clean

example:
	tornado tornado.examples.HelloWorld

tests:
	tornado-test.py --verbose

eclipse:
	mvn eclipse:eclipse
