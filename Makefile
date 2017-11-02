all: default_install

default_install:
	python easy-install.py

clean:
	mvn clean

example:
	tornado tornado.examples.HelloWorld

tests:
	tornado-tests tornado.unittests.TestHello

eclipse:
	mvn eclipse:eclipse
